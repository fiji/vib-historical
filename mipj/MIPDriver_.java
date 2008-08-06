/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package mipj;

import java.io.*;
import ij.util.StringSorter;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.StackConverter;
import java.text.*;
import java.util.Locale;
import ij.plugin.PlugIn;
import util.BatchOpener;
import util.Limits;
import util.RGB_to_Luminance;
import ij.Macro;
import server.Job_Server;

public class MIPDriver_ extends Thread implements PlugIn
{

	private String outputFileName;
	private String currentParse;
	private int mipType;
	private int resx, resy;
	private int interpolationType;
	private float scale, zScale;
	private float rayCastInc;
	private boolean dmip;
	private int threshold;
	private int nImages;
	private String inputDir;
	private Matrix4f rot;
	float animx, animy, animz;
	MIPImageStack ist = null;
	private boolean plugin = false;
	private boolean forceWrite = false;
	private static boolean outputJpeg = false;

	public final static int SPLATTING = 0;
	public final static int RAYCASTING = 1;
	public final static int REALTIME = 2;


	/** Creates a MIPDriver_ from a lot of arguments */

	public MIPDriver_(
		int type,
		int resx,
		int resy,
		int interpolationType,
		float scale,
		float zscale,
		float rayinc,
		boolean dmip,
		int threshold,
		int n,
		MIPImageStack ist,
		Matrix4f rotangle,
		float[] rotresult,
		String name
		) {

		outputFileName = new String(name);
		mipType = type;
		this.resx = resx;
		this.resy = resy;
		this.interpolationType = interpolationType;
		this.scale = scale;
		this.zScale = zscale;
		rayCastInc = rayinc;
		this.dmip = dmip;
		this.threshold = threshold;
		nImages = n;
		animx = rotresult[0];
		animy = rotresult[1];
		animz = rotresult[2];
		rot = rotangle;
		this.ist = ist;
		plugin = true;

	}

	/* This is the PlugIn interface.  We only care about this
	 * being able to parse one set of options here:

	      input    =>  this.inputDir
	      output   =>  this.outputFileName
	      mip      =>  this.mipType
	      zscale   =>  this.zScale


	   Example call:

	      java -Xmx1024 -jar ij.jar -eval "run('MIP Driver', 'input=[foo.lsm] output=[/var/tmp/foo/] mip=rt zscale=1.2');"

           To render a nice quality projection, we use values like these:

              java -Xmx1024 -jar ij.jar -eval "run('MIP Driver', 'input=[foo.lsm] output=[/var/tmp/foo/] j f mip=splat t=nn zscale=1.2 i=0.1 h=255 rotx=-30 roty=89 rotz=-31 scale=1.0 channel=0

	*/

	public MIPDriver_() { }

	public void run( String argument ) {

		// Set some defaults here:
		resx = resy = 0;
		interpolationType = MIP.TRILINEAR;
		scale = 1;
		rayCastInc = 0.5f;
		dmip = false;
		threshold = 255;
		nImages = 1;
		animx = animy = animz = 0.0f;

		String options = Macro.getOptions();

		// The options up to ------ are needed to generate the realtime output

		String inputFilename = Macro.getValue( options, "input", "" );
		if( inputFilename.equals("") )
			throw new RuntimeException( "No input filename supplied for MIPDriver_");

		String outputFilenameBasename = Macro.getValue( options, "output", "" );
		if( outputFilenameBasename.equals("") )
			throw new RuntimeException( "No output filename basename supplied for MIPDriver_");

		// We insist for the macro version that the output is a directory:

		File f = new File( outputFilenameBasename );
		if( ! f.isDirectory() ) {
			throw new RuntimeException( "The 'output' parameter must be a directory" );
		}

		String mipTypeSupplied = Macro.getValue( options, "mip", "" );
		if( mipTypeSupplied.equals( "" ) )
			throw new RuntimeException( "No MIP type supplied");

		if (mipTypeSupplied.equals("ray"))
			mipType = RAYCASTING;
		else if ( mipTypeSupplied.equals("splat") )
			mipType = SPLATTING;
		else if ( mipTypeSupplied.equals("rt") )
			mipType = REALTIME;
		else {
			throw new RuntimeException( "Unknown MIP type supplied" );
		}

		String zScaleSupplied = Macro.getValue( options, "zscale", "" );
		if( zScaleSupplied.equals("") )
			throw new RuntimeException( "No Z scale supplied" );

		try {
			zScale = Float.parseFloat( zScaleSupplied );
		} catch( NumberFormatException nfe ) {
			throw new RuntimeException( "Malformed Z scale supplied: " + zScaleSupplied );
		}

		String scaleSupplied = Macro.getValue( options, "scale", "1" );
		if( scaleSupplied.equals("") )
			throw new RuntimeException( "No scale supplied" );
		try {
			scale = Float.parseFloat( scaleSupplied );
		} catch( NumberFormatException nfe ) {
			throw new RuntimeException( "Malformed scale supplied: " + scaleSupplied );
		}

		// ------
		// Now the ones needed to generate the nice images:

		if( mipType != REALTIME ) {

			outputJpeg = isMatch( options, "j" );

			forceWrite = isMatch( options, "f" );

			String suppliedInterpolation = Macro.getValue( options, "t", "nn" );
			if (suppliedInterpolation.equals("nn"))
				interpolationType =  MIP.NEARESTNEIGHBOUR;
			else if ( suppliedInterpolation.equals("tri") )
				interpolationType = MIP.TRILINEAR;
			else
				throw new RuntimeException("Malformed 't' (interpolation type) parameter suppled: " + suppliedInterpolation);

			String suppliedRayInc = Macro.getValue( options, "i", "0.5" );
			try {
				rayCastInc = Float.parseFloat( suppliedRayInc );
			} catch( NumberFormatException nfe ) {
				throw new RuntimeException( "Malformed rayCastInc supplied: " + suppliedRayInc );
			}

			String suppliedH = Macro.getValue( options, "h", "255" );
			try {
				threshold = Integer.parseInt(suppliedH);
				if( threshold < 1 || threshold > 255 )
					threshold = 255;
			} catch( NumberFormatException nfe ) {
				throw new RuntimeException( "Malformed threshold (h) supplied: " + suppliedH );
			}
		}

		rot = new Matrix4f();

		if( mipType != REALTIME ) {
			String suppliedRotX = Macro.getValue( options, "rotx", "" );
			String suppliedRotY = Macro.getValue( options, "roty", "" );
			String suppliedRotZ = Macro.getValue( options, "rotz", "" );
			try {
				rot.rotByX( Float.parseFloat( suppliedRotX ) * MIP.ONEDEGREE );
				rot.rotByY( Float.parseFloat( suppliedRotY ) * MIP.ONEDEGREE );
				rot.rotByZ( Float.parseFloat( suppliedRotZ ) * MIP.ONEDEGREE );
			} catch( NumberFormatException nfe ) {
				throw new RuntimeException( "Malformed parameters supplied: rotx=["+suppliedRotX+"] roty=["+suppliedRotY+"] rotz=["+suppliedRotZ+"]" );
			}
		}

		plugin = false;

		ImagePlus [] images = BatchOpener.open( inputFilename );

		if( images == null || images.length < 1 )
			throw new RuntimeException( "Couldn't open the input file " + inputFilename );

		int maxWidth = 400;
		int maxHeight = 400;

		boolean resizeFirst = false;
		int newWidth = -1;
		int newHeight = -1;
		int originalWidth = images[0].getWidth();
		int originalHeight = images[0].getHeight();
		if( originalWidth > maxWidth || originalHeight > maxHeight ) {
			resizeFirst = true;
			if( originalWidth > originalHeight ) {
				// Scale so that the width is maxWidth:
				newWidth = maxWidth;
				newHeight = (originalHeight * maxWidth) / originalWidth;
			} else {
				// Scale so that the height is maxHeight:
				newHeight = maxHeight;
				newWidth = (originalWidth * maxHeight) / originalHeight;
			}
		}

		float totalProgressPoints = images.length * 7 + (resizeFirst ? images.length : 0);
		int lastProgressPoint = 0;

		DecimalFormat f2 = new DecimalFormat("00");

		for( int c = 0; c < images.length; ++c ) {

			ImagePlus imageToUse = null;
			if( resizeFirst ) {
				System.out.println("Resizing down to "+newWidth+" x "+newHeight );
				ImageStack stack = images[c].getStack();
				ImageStack newStack = new ImageStack(newWidth,newHeight);
				for( int z = 0; z < images[c].getStackSize(); ++z ) {
					ImageProcessor ip = stack.getProcessor( z + 1 );
					ip.setInterpolate(true);
					ImageProcessor newIp = ip.resize(newWidth,newHeight);
					newStack.addSlice("",newIp);
				}
				Job_Server.updateProgressInDirectory( outputFilenameBasename, (lastProgressPoint++) / totalProgressPoints );
				imageToUse = new ImagePlus("scaled-"+images[c].getTitle(),newStack);
				images[c].close();
			} else {
				imageToUse = images[c];
			}

			System.out.println("After possibly resizing, each slice is: "+imageToUse.getWidth()+" x "+imageToUse.getWidth());

			/* Now if the images is either 16 bit or 32
			   bit, find the minimum and maximum and
			   convert to 8bit: */

			int imageType = imageToUse.getType();
			if( imageType == ImagePlus.GRAY16 || imageType == ImagePlus.GRAY32 ) {
				float [] limits = Limits.getStackLimits(imageToUse);
				ImageProcessor p = imageToUse.getProcessor();
				p.setMinAndMax( limits[0], limits[1]);
				StackConverter converter=new StackConverter(imageToUse);
				converter.convertToGray8();
			} else if( imageType == ImagePlus.COLOR_RGB ) {
				imageToUse = RGB_to_Luminance.convertToLuminance(imageToUse);
			}

			Job_Server.updateProgressInDirectory( outputFilenameBasename, (lastProgressPoint++) / totalProgressPoints );

			MIPImageStack ist = new MIPImageStack( imageToUse.getStack() );

			Job_Server.updateProgressInDirectory( outputFilenameBasename, (lastProgressPoint++) / totalProgressPoints );

			if( mipType == REALTIME ) {

				String realOutputFileName = outputFilenameBasename + File.separator + "projection-" + f2.format(c) + "." + mipTypeSupplied;

				if ( zScale != 1.0f )
					ist.zScale(zScale);

				Job_Server.updateProgressInDirectory( outputFilenameBasename, (lastProgressPoint++) / totalProgressPoints );

				Discard d = new Discard( ist );

				Job_Server.updateProgressInDirectory( outputFilenameBasename, (lastProgressPoint++) / totalProgressPoints );


				d.discardNN();

				Job_Server.updateProgressInDirectory( outputFilenameBasename, (lastProgressPoint++) / totalProgressPoints );

				RealTimeMIP r = d.calculate(Discard.FRONTMAIN, Discard.UPPER);

				Job_Server.updateProgressInDirectory( outputFilenameBasename, (lastProgressPoint++) / totalProgressPoints );

				try {
					ObjectOutputStream oos = new ObjectOutputStream( new FileOutputStream( realOutputFileName ) );
					oos.writeObject( r );
					oos.close();
				} catch( IOException e ) {
					throw new RuntimeException( "Got an IOException when writing to " + realOutputFileName );
				}

				Job_Server.updateProgressInDirectory( outputFilenameBasename, (lastProgressPoint++) / totalProgressPoints );

			} else {

				try {

					// need to resample to do zscaling for splatting
					if (mipType == SPLATTING && zScale != 1.0f) {
						if (plugin)
							IJ.showStatus("ZScaling");
						ist.zScale( zScale );
					}

					MIP m = new MIP( ist, !plugin );

					m.setPlugin(plugin);
					m.setForceWrite( forceWrite );

					if ( resx != 0 && resy != 0 )
						m.setResolution( resx, resy );

					m.setZScale( zScale );

					m.setRayCastType( interpolationType );
					m.setThreshold( threshold );
					m.setRayCastIncrement( rayCastInc );
					m.setDMIP( dmip );

					float incX, incY, incZ;

					incX = incY = incZ = 0.0f;

					if (nImages > 1 ) {
						float f_ = 1.0f / (nImages-1);

						incX = animx * f_ * MIP.ONEDEGREE;
						incY = animy * f_ * MIP.ONEDEGREE;
						incZ = animz * f_ * MIP.ONEDEGREE;

					}

					ij.ImageStack buildStack = null;
					ImagePlus buildImg = null;

					for( int i = 0 ; i < nImages ; ++i ) {

						String realOutputFileName = outputFilenameBasename + File.separator + "projection-" + f2.format(c) + "-" + mipTypeSupplied;
						String extension = outputJpeg ? ".jpg" : ".tif";
						realOutputFileName += extension;

						m.setOutputFile( new File( realOutputFileName ) );

						if ( mipType == SPLATTING ) {
							if ( !plugin )
								m.projectByMatrix( rot );
							else {
								ImagePlus img =  m.projectByMatrix( rot );

								if ( i == 0 && nImages > 1 ) { // set up the stack for adding to

									buildStack = new ij.ImageStack( img.getWidth(), img.getHeight(), img.getProcessor().getColorModel() );
									byte[] ba = (byte[]) img.getProcessor().getPixels();

									buildStack.addSlice( null, ba );

									for( int n = 1 ; n < nImages ; ++n ) {
										buildStack.addSlice( null , new byte[ba.length] );
									}

									buildImg = new ImagePlus( m.getOutputFile().getName(), buildStack );
									buildImg.show();
									buildImg.setSlice(1);
								} else if ( nImages > 1 ) {
									buildStack.setPixels( img.getProcessor().getPixels() , buildImg.getCurrentSlice()+1 );
									if (buildImg == null)
										return;
									buildImg.setSlice( buildImg.getCurrentSlice()+1 );
								} else {
									img.setTitle( m.getOutputFile().getName() );
									img.show();
								}
							}
						} else if ( mipType == RAYCASTING ) {
							if ( !plugin )
								m.rayCastByMatrix( rot );
							else {
								ImagePlus img =  m.rayCastByMatrix( rot );

								if ( i == 0 && nImages > 1 ) { // set up the stack for adding to

									buildStack = new ij.ImageStack( img.getWidth(), img.getHeight(), img.getProcessor().getColorModel() );
									byte[] ba = (byte[]) img.getProcessor().getPixels();

									buildStack.addSlice( null, ba );

									for( int n = 1 ; n < nImages ; ++n ) {
										buildStack.addSlice( null , new byte[ba.length] );
									}

									buildImg = new ImagePlus( m.getOutputFile().getName(), buildStack );
									buildImg.show();
									buildImg.setSlice(1);
								} else if ( nImages > 1 ) {
									buildStack.setPixels( img.getProcessor().getPixels() , buildImg.getCurrentSlice()+1 );
									if (buildImg == null)
										return;
									buildImg.setSlice( buildImg.getCurrentSlice()+1 );
								} else {
									img.setTitle( m.getOutputFile().getName() );
									img.show();
								}
							}
						}

						rot.rotByX( incX );
						rot.rotByY( incY );
						rot.rotByZ( incZ );

					}
				} catch( Exception e ) {
					throw new RuntimeException("Caught an exception: "+e);
				}
			}
			imageToUse.close();
			images[c] = null;
		}

		Job_Server.updateProgressInDirectory( outputFilenameBasename, 1.0f );

		Job_Server.finishDirectory( outputFilenameBasename );
	}

	/** Creates a MIPDriver_ class from a set of command-line options */

	public MIPDriver_( String[] args ) throws Exception {

		outputFileName = new String("projection");
		mipType = SPLATTING;
		resx = resy = 0; // (no change)
		interpolationType = MIP.TRILINEAR;
		scale = zScale = 1.0f;
		rayCastInc = 0.5f;
		dmip = false;
		threshold = 255;
		nImages = 1;
		animx = animy = animz = 0.0f;

		plugin = false;

		rot = new Matrix4f();


		if ( args.length < 1 ) {
			System.out.println("Usage: java MIPDriver_ inputdir ...");
			throw new Exception();
		} else
			inputDir = new String(args[0]);


		try {

			if (args.length > 0) {

				int i = 1; // missing out inputdir

				if ( args.length > 1 ) {
					if ( !args[1].startsWith("-") ) {
						i = 2;
						outputFileName = new String( args[1] );
						// spot directories
						if ( new File(outputFileName).isDirectory() ) {
							outputFileName = new String( outputFileName + File.separatorChar + "projection" );
						}
					}
				}

				while (i < args.length) {

					currentParse = new String ( args[i] );

					args[i] = args[i].toLowerCase();

					if (args[i].equals("-m") || args[i].equals("--mip") ) {
						++i;
						args[i] = args[i].toLowerCase();
						if (args[i].equals("ray"))
							mipType = RAYCASTING;
						else if ( args[i].equals("splat") )
							mipType = SPLATTING;
						else if ( args[i].equals("rt") )
							mipType = REALTIME;
						else {
							System.out.println("Unrecognised argument after -m/--mip: " + args[i]);
							throw new Exception();
						}
					} else if (args[i].equals("-r") || args[i].equals("--res") ) {
						++i;
						resx = Integer.parseInt( args[i++] );
						resy = Integer.parseInt( args[i] );
					} else if (args[i].equals("-t") || args[i].equals("--raytype") ) {
						++i;
						args[i] = args[i].toLowerCase();
						if (args[i].equals("nn"))
							interpolationType =  MIP.NEARESTNEIGHBOUR;
						else if ( args[i].equals("tri") )
							interpolationType = MIP.TRILINEAR;
						else {
							System.out.println("Unrecognised argument after -t/--raytype: " + args[i]);
							throw new Exception();
						}
					} else if ( args[i].equals("-s") || args[i].equals("--zscale") ) {
						++i;
						zScale = Float.parseFloat( args[i] );
					} else if ( args[i].equals("-o") || args[i].equals("--scale") ) {
						++i;
						scale = Float.parseFloat( args[i] );
					} else if ( args[i].equals("-i") || args[i].equals("--rayinc") ) {
						++i;
						rayCastInc = Float.parseFloat( args[i] );
					} else if ( args[i].equals("-d") || args[i].equals("--dmip") ) {
						dmip = true;
					} else if ( args[i].equals("-h") || args[i].equals("--maxt") ) {
						++i;
						int t = Integer.parseInt( args[i] );
						if ( t < 1 || t > 255) {
							System.out.println("Max threshold out of bounds. Must be between 1 and 255. Ignoring...");
						} else
							threshold = t;
					} else if ( args[i].equals("-n") || args[i].equals("--nimages") ) {
						++i;
						int t = Integer.parseInt( args[i] );
						if ( t < 1 || t > 1000) {
							System.out.println("No. Images out of bounds. Must be between 1 and 1000. Ignoring...");
						} else
							nImages = t;
					} else if ( args[i].equals("-x") || args[i].equals("--rotx") ){
						++i;
						float f = Float.parseFloat( args[i] );
						rot.rotByX( f * MIP.ONEDEGREE );
					} else if ( args[i].equals("-y") || args[i].equals("--roty") )  {
						++i;
						float f = Float.parseFloat( args[i] );
						rot.rotByY( f * MIP.ONEDEGREE );
					} else if ( args[i].equals("-z") || args[i].equals("--rotz") ) {
						++i;
						float f = Float.parseFloat( args[i] );
						rot.rotByZ( f * MIP.ONEDEGREE );
					} else if ( args[i].equals("-ax") || args[i].equals("--animx") ) {
						++i;
						animx = Float.parseFloat( args[i] );
					} else if ( args[i].equals("-ay") || args[i].equals("--animy") ) {
						++i;
						animy = Float.parseFloat( args[i] );
					} else if ( args[i].equals("-az") || args[i].equals("--animz") ) {
						++i;
						animz = Float.parseFloat( args[i] );
					} else if ( args[i].equals("-f") || args[i].equals("--forcewrite") ) {
						forceWrite = true;
					} else if ( args[i].equals("-j") || args[i].equals("--jpeg") ) {
						outputJpeg = true;
					} else {
						System.out.println("Unrecognised command line option: " + args[i] );
						throw new Exception();
					}

					++i;

				} // end while

			} else {
				System.out.println("Usage: java MIPDriver_ inputdir [output file] [options]");
			}

		} catch(ArrayIndexOutOfBoundsException aob) {
			System.out.println("Command line parse error at " + currentParse);
			aob.printStackTrace();
			throw new Exception();
		} catch ( NumberFormatException nfe ) {
			System.out.println("Command line number parse error at " + currentParse);
			throw new Exception();
		}



	}

	public static boolean outputJpeg() {
		return outputJpeg;
	}

	/** Creates the images requested from the command-line */

	public void run() {

		try {

			if ( mipType == REALTIME ) {

				if ( ist == null )
					ist = new MIPImageStack( inputDir, 256 );

				if ( zScale != 1.0f )
					ist.zScale(zScale);

				Discard d = new Discard( ist );
				d.discardNN();

				RealTimeMIP r = d.calculate(Discard.FRONTMAIN, Discard.UPPER);

				System.out.print("Writing file:\n" + outputFileName + ".rt");
				ObjectOutputStream oos = new ObjectOutputStream( new FileOutputStream( outputFileName + ".rt" ) );
				System.out.println(" : DONE");
				oos.writeObject( r );
				oos.close();
				return;

			}

			if ( ist == null ) {
				ist = new MIPImageStack( inputDir, scale );
			}

			if (mipType == SPLATTING && zScale != 1.0f) { // need to resample to do zscaling for splatting

				if (plugin)
					IJ.showStatus("ZScaling");
				ist.zScale( zScale );
			}

			MIP m = new MIP( ist, !plugin );

			m.setPlugin(plugin);
			m.setForceWrite( forceWrite );

			FileNameGenerator fng = new FileNameGenerator( outputFileName, plugin, nImages );

			if ( resx != 0 && resy != 0 )
				m.setResolution( resx, resy );

			m.setZScale( zScale );

			m.setRayCastType( interpolationType );
			m.setThreshold( threshold );
			m.setRayCastIncrement( rayCastInc );
			m.setDMIP( dmip );

			float incX, incY, incZ;

			incX = incY = incZ = 0.0f;

			if (nImages > 1 ) {
				float f = 1.0f / (nImages-1);

				incX = animx * f * MIP.ONEDEGREE;
				incY = animy * f * MIP.ONEDEGREE;
				incZ = animz * f * MIP.ONEDEGREE;

			}


			ij.ImageStack buildStack = null;
			ImagePlus buildImg = null;

			System.out.print( "Processing Projection: " );

			for( int i = 0 ; i < nImages ; ++i ) {

				m.setOutputFile( new File( fng.next() ) );

				if (!plugin)
					System.out.print( "." );

				if ( mipType == SPLATTING ) {
					if ( !plugin )
						m.projectByMatrix( rot );
					else {
						ImagePlus img =  m.projectByMatrix( rot );

						if ( i == 0 && nImages > 1 ) { // set up the stack for adding to

							buildStack = new ij.ImageStack( img.getWidth(), img.getHeight(), img.getProcessor().getColorModel() );
							byte[] ba = (byte[]) img.getProcessor().getPixels();

							buildStack.addSlice( null, ba );

							for( int n = 1 ; n < nImages ; ++n ) {
								buildStack.addSlice( null , new byte[ba.length] );
							}

							buildImg = new ImagePlus( m.getOutputFile().getName(), buildStack );
							buildImg.show();
							buildImg.setSlice(1);
						} else if ( nImages > 1 ) {
							buildStack.setPixels( img.getProcessor().getPixels() , buildImg.getCurrentSlice()+1 );
							if (buildImg == null)
								return;
							buildImg.setSlice( buildImg.getCurrentSlice()+1 );
						} else {
							img.setTitle( m.getOutputFile().getName() );
							img.show();
						}
					}
				} else if ( mipType == RAYCASTING ) {
					if ( !plugin )
						m.rayCastByMatrix( rot );
					else {
						ImagePlus img =  m.rayCastByMatrix( rot );

						if ( i == 0 && nImages > 1 ) { // set up the stack for adding to

							buildStack = new ij.ImageStack( img.getWidth(), img.getHeight(), img.getProcessor().getColorModel() );
							byte[] ba = (byte[]) img.getProcessor().getPixels();

							buildStack.addSlice( null, ba );

							for( int n = 1 ; n < nImages ; ++n ) {
								buildStack.addSlice( null , new byte[ba.length] );
							}

							buildImg = new ImagePlus( m.getOutputFile().getName(), buildStack );
							buildImg.show();
							buildImg.setSlice(1);
						} else if ( nImages > 1 ) {
							buildStack.setPixels( img.getProcessor().getPixels() , buildImg.getCurrentSlice()+1 );
							if (buildImg == null)
								return;
							buildImg.setSlice( buildImg.getCurrentSlice()+1 );
						} else {
							img.setTitle( m.getOutputFile().getName() );
							img.show();
						}
					}
				}

				rot.rotByX( incX );
				rot.rotByY( incY );
				rot.rotByZ( incZ );

			}
			System.out.println( "" );

		} catch(OutOfMemoryError oom) {
			if ( plugin )
				IJ.error("Insufficient memory, try java -mxNm where N is MB of memory");
			System.out.println("Insufficient memory: try java -mxNm where N is MB of memory");
		} catch(Exception e) {
			e.printStackTrace();
		}


	}


	public static void main(String args[]) {

		try {

			MIPDriver_ mipd = new MIPDriver_( args );
			mipd.start();


		} catch(Exception io) {
			io.printStackTrace();
		}

	}

	// This is just copied from GenericDialog; it should be in Macro, I think.

	// Returns true if s2 is in s1 and not in a bracketed literal (e.g., "[literal]")
	boolean isMatch(String s1, String s2) {
		if (s1.startsWith(s2))
			return true;
		s2 = " " + s2;
		int len1 = s1.length();
		int len2 = s2.length();
		boolean match, inLiteral=false;
		char c;
		for (int i=0; i<len1-len2+1; i++) {
			c = s1.charAt(i);
			if (inLiteral && c==']')
				inLiteral = false;
			else if (c=='[')
				inLiteral = true;
			if (c!=s2.charAt(0) || inLiteral || (i>1&&s1.charAt(i-1)=='='))
				continue;
			match = true;
			for (int j=0; j<len2; j++) {
				if (s2.charAt(j)!=s1.charAt(i+j))
					{match=false; break;}
			}
			if (match) return true;
		}
		return false;
	}

}

class FileNameGenerator
{

	private String stub;
	private String append;
	private NumberFormat nf;
	private int pos;
	boolean onlyStub = true;

	public FileNameGenerator( String filename, boolean notif, int n) {
		stub = filename;
		if ( !notif )
			append = ".tif";
		else
			append = "";
		pos = 0;
		if ( n > 1 )
			onlyStub = false;
		nf = NumberFormat.getIntegerInstance(Locale.UK);
		nf.setGroupingUsed(false);
		nf.setMinimumIntegerDigits( 3 );
		nf.setMaximumIntegerDigits( 3 );
	}

	/** Obtains the next filename */

	public String next() {
		if (onlyStub)
			return new String( stub + append );
		else
			return new String( stub + nf.format( pos++ ) + append );
	}


}
