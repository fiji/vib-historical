/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

/* Copyright 2006, 2007 Mark Longair */

/*
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License as
    published by the Free Software Foundation; either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
   Test this with:

   ./fiji -eval 'run("Scale, Merge and Unpack","scale=50 filename=/home/mark/scans/71yAAeastmost.lsm directory=/tmp/foo r=2 g=2 b=1");'

*/

package stacks;

import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.LookUpTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ColorProcessor;
import ij.process.ByteProcessor;

import java.text.DecimalFormat;
import java.io.*;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.IndexColorModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.Iterator;

import javax.imageio.ImageIO;

import util.BatchOpener;
import util.Limits;
import server.Job_Server;

import fastpng.Native_PNG_Writer;
import fastjpeg.Native_JPEG_Writer;

public class Scale_Merge_Unpack implements PlugIn {

	long lastProgressUpdate	= Long.MIN_VALUE;
	long minimumUpdateInterval = 500; // milliseconds
	float slicesToDo;

	void reportProgress( int done ) {
		long now = System.currentTimeMillis();
		if( lastProgressUpdate == Long.MIN_VALUE ||
		    ((now - lastProgressUpdate) > minimumUpdateInterval) ) {
			Job_Server.updateProgressInDirectory( destinationDirectory, done / slicesToDo );
			lastProgressUpdate = now;
		}
	}

	String destinationDirectory;

	public void run(String pluginArguments) {

		Native_PNG_Writer pngWriter = new Native_PNG_Writer();
		Native_JPEG_Writer jpegWriter = new Native_JPEG_Writer();

		String realArguments = null;
		String macroArguments = Macro.getOptions();

		if( (macroArguments == null) || (macroArguments.equals("")) ) {

			if( (pluginArguments == null) || (pluginArguments.equals("")) ) {
				throw new RuntimeException("No parameters supplied either as macro options or a plugin argument.");
			} else {
				realArguments = pluginArguments;
			}

		} else {
			realArguments = macroArguments;
		}

		int scalePercent = 100;

		String scaleString = Macro.getValue(
			realArguments,
			"scale",
			null );
		if( scaleString != null ) {
			try {
				scalePercent = Integer.parseInt(scaleString);
			} catch( NumberFormatException e ) {
				throw new RuntimeException("Malformed parameter 'scale' supplied: '"+scaleString+"'");
			}
		}

		String filename = Macro.getValue(
			realArguments,
			"filename",
			"");

		if( filename.equals("") )
			throw new RuntimeException("No macro parameter 'filename' supplied");

		destinationDirectory = Macro.getValue(
			realArguments,
			"directory",
			"");

		if( destinationDirectory.equals("") )
			throw new RuntimeException("No macro parameter 'directory' supplied");

		String redChannel = Macro.getValue(
			realArguments,
			"r",
			"" );
		String greenChannel = Macro.getValue(
			realArguments,
			"g",
			"" );
		String blueChannel = Macro.getValue(
			realArguments,
			"b",
			"" );

		String outputFormat = Macro.getValue(
			realArguments,
			"format",
			"png");

		outputFormat = outputFormat.toLowerCase();

		if( ! (outputFormat.equals("png") || outputFormat.equals("jpg")) ) {
			throw new RuntimeException("Unknown output format '"+outputFormat+"'");
		}

		int redIsChannel = -1;
		int greenIsChannel = -1;
		int blueIsChannel = -1;

		try {
			if( redChannel.length() > 0 )
				if( ! redChannel.equals("N") )
					redIsChannel = Integer.parseInt(redChannel) - 1;
			if( greenChannel.length() > 0 )
				if( ! greenChannel.equals("N") )
					greenIsChannel = Integer.parseInt(greenChannel) - 1;
			if( blueChannel.length() > 0 )
				if( ! blueChannel.equals("N") )
					blueIsChannel = Integer.parseInt(blueChannel) - 1;
		} catch(  NumberFormatException e ) {
			throw new RuntimeException("One of the channel colour assignments was malformed: r=["+
			      redChannel+"] g=["+
			      greenChannel+"] b=["+
			      blueChannel+"]");
		}

		// First load the image to an array of ImagePlus objects:
		ImagePlus [] images = BatchOpener.open( filename );
		if( images == null || images.length == 0 )
			throw new RuntimeException("Failed to load: "+filename);

		for( int c = 0; c < images.length; ++c ) {
			int imageType = images[c].getType();
			if( imageType == ImagePlus.GRAY16 || imageType == ImagePlus.GRAY32 ) {
				float [] limits = Limits.getStackLimits(images[c]);
				ImageProcessor p = images[c].getProcessor();
				p.setMinAndMax(limits[0],limits[1]);
			}
		}

		if( (redIsChannel < -1 || redIsChannel >= images.length) ||
		    (greenIsChannel < -1 || greenIsChannel >= images.length) ||
		    (blueIsChannel < -1 || blueIsChannel >= images.length) )
			throw new RuntimeException("One of the channel color assigments was out of range: r=["+
						   redIsChannel+"] g=["+
						   greenIsChannel+"] b=["+
						   blueIsChannel+"]");

		int bitDepth = images[0].getBitDepth();

		// Set the LUTs for each individual image:

		if( bitDepth == 8 || bitDepth == 16 ) {
			for( int c = 0; c < images.length; ++c ) {
				byte [] red   = new byte[256];
				byte [] green = new byte[256];
				byte [] blue  = new byte[256];
				if( redIsChannel == c )
					for( int i = 0; i < 256; ++i )
						red[i] = (byte)i;
				if( greenIsChannel == c )
					for( int i = 0; i < 256; ++i )
						green[i] = (byte)i;
				if( blueIsChannel == c )
					for( int i = 0; i < 256; ++i )
						blue[i] = (byte)i;
				ColorModel colorModel = new IndexColorModel(8, 256, red, green, blue);
				images[c].getProcessor().setColorModel(colorModel);
				if (images[c].getStackSize() > 1)
					images[c].getStack().setColorModel(colorModel);
			}
		}

		int newWidth = images[0].getWidth();
		int newHeight = images[0].getHeight();
		int depth = images[0].getStackSize();

		int slicesToResize = (scalePercent == 100) ? 0 : (images.length * depth);
		int slicesToOutput = images.length * depth;
		int slicesInMerge = 0;
		if( (bitDepth == 8 || bitDepth == 16) && images.length > 1 ) {
			slicesToOutput += depth;
			slicesInMerge += depth;
		}

		slicesToDo = slicesToResize + slicesInMerge + slicesToOutput;
		int slicesDone = 0;

		// Scale the images (if necessary):

		if( scalePercent != 100 ) {

			newWidth = (newWidth * scalePercent) / 100;
			newHeight = (newHeight * scalePercent) / 100;

			for( int i = 0; i < images.length; ++i ) {
				ImagePlus currentImage = images[i];
				ImageStack stack = currentImage.getStack();
				ImageStack newStack = new ImageStack(newWidth,newHeight);
				for( int z = 0; z < currentImage.getStackSize(); ++z ) {
					ImageProcessor ip = stack.getProcessor( z + 1 );
					ip.setInterpolate(true);
					ImageProcessor newIp = ip.resize(newWidth,newHeight);
					newStack.addSlice("",newIp);
					++ slicesDone;
					reportProgress( slicesDone );
				}
				ImagePlus scaledImage = new ImagePlus("scaled-"+currentImage.getTitle(),newStack);
				images[i] = scaledImage;
				currentImage.close();
			}


		}

		try {
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(destinationDirectory+"/dimensions.js"),"UTF-8"));
			pw.println("var stack_width = "+newWidth+";");
			pw.println("var stack_height = "+newHeight+";");
			pw.println("var stack_depth = "+depth+";");
			pw.close();
		} catch( IOException e ) {
			throw new RuntimeException("IOException: "+e);
		}

		// Make a merged version:

		ImagePlus mergedImagePlus = null;

		if( (bitDepth == 8 || bitDepth == 16) && images.length > 1 ) {
			ImageStack [] stacks = new ImageStack[images.length];
			for( int i = 0; i < images.length; ++i ) {
				stacks[i] = images[i].getStack();
			}
			ImageStack mergedStack = new ImageStack(newWidth,newHeight);
			for( int z = 0; z < depth; ++z ) {
				byte [] redPixels;
				byte [] greenPixels;
				byte [] bluePixels;
				if( bitDepth == 8 ) {
					redPixels = (byte[])stacks[redIsChannel].getPixels(z+1);
					greenPixels = (byte[])stacks[greenIsChannel].getPixels(z+1);
					bluePixels = (byte[])stacks[blueIsChannel].getPixels(z+1);
				} else { // i.e. GRAY16
					redPixels = byteArrayForGRAY16(stacks[redIsChannel],z+1);
					greenPixels = byteArrayForGRAY16(stacks[greenIsChannel],z+1);
					bluePixels = byteArrayForGRAY16(stacks[blueIsChannel],z+1);
				}

				ColorProcessor cp = new ColorProcessor(newWidth,newHeight);
				cp.setRGB(redPixels,greenPixels,bluePixels);

				mergedStack.addSlice("",cp);
				++ slicesDone;
				reportProgress( slicesDone );
			}
			mergedImagePlus = new ImagePlus("[Unseen Title] Merged",mergedStack);
		}

		DecimalFormat f2 = new DecimalFormat("00");
		DecimalFormat f5 = new DecimalFormat("00000");

		String [] colorLetters = { "r", "g", "b" };
		int [] colorChannels = { redIsChannel, greenIsChannel, blueIsChannel };

		ArrayList<String> channelMap = new ArrayList<String>();

		// First extract each channel:
		for( int i = 0; i < images.length; ++i ) {

			String channelString = "";
			for( int color = 0; color < 3; ++ color ) {
				channelString += colorLetters[color];
				int channel = colorChannels[color];
				if( i == channel )
					channelString += f2.format(channel);
				else
					channelString += "NN";
				channelString += "-";
			}

			channelMap.add("  [ \"Channel "+(i+1)+"\", \""+channelString+"\" ]");

			ImagePlus imagePlus = images[i];
			ImageStack stack = imagePlus.getStack();

			int imageType = imagePlus.getType();

			for( int z = 0; z < depth; ++z ) {

				ImageProcessor ip = stack.getProcessor(z+1);

				String sliceString = f5.format(z);
				String outputFileName = destinationDirectory + "/" + channelString + sliceString + "." + outputFormat;

				LookUpTable lut = imagePlus.createLut();
				IndexColorModel cm = null;
				byte [] reds   = null;
				byte [] greens = null;
				byte [] blues  = null;
				if( lut != null ) {
					int size = lut.getMapSize();
					if( size > 0 ) {
						reds = lut.getReds();
						greens = lut.getGreens();
						blues = lut.getBlues();
						cm = new IndexColorModel(8,size,reds,greens,blues);
					}
				}

				if( outputFormat.equals("png") ) {
					if( bitDepth == 8 ) {
						pngWriter.write8BitPNG( (byte[])ip.getPixels(),
									newWidth,
									newHeight,
									reds,
									greens,
									blues,
									outputFileName );
					} else if( bitDepth == 16 ) {
						ImageProcessor bp = ip.convertToByte(true);
						byte [] pixelData8Bit = (byte[])bp.getPixels();
						pngWriter.write8BitPNG( pixelData8Bit,
									newWidth,
									newHeight,
									reds,
									greens,
									blues,
									outputFileName);
					} else if( imageType == ImagePlus.GRAY32 ) {
						ImageProcessor bp = ip.convertToByte(true);
						byte [] pixelData8Bit = (byte[])bp.getPixels();
						pngWriter.write8BitPNG( pixelData8Bit,
									newWidth,
									newHeight,
									reds,
									greens,
									blues,
									outputFileName);
					} else if( imageType == ImagePlus.COLOR_RGB ) {
						int [] pixelDataRGB = (int[])ip.getPixels();
						pngWriter.writeFullColourPNG( pixelDataRGB, newWidth, newHeight, outputFileName );
					}
				} else { // Must be JPEG:
					if( imageType == ImagePlus.COLOR_RGB ) {
						int [] pixelDataRGB = (int[])ip.getPixels();
						jpegWriter.writeFullColourJPEG( pixelDataRGB, newWidth, newHeight, outputFileName );
					} else {
						ImageProcessor cp = ip.convertToRGB();
						int [] pixelDataRGB = (int[])ip.getPixels();
						jpegWriter.writeFullColourJPEG( pixelDataRGB, newWidth, newHeight, outputFileName );
					}
				}
				++ slicesDone;
				reportProgress( slicesDone );
			}
		}

		// Now extract the merged stack:

		String channelString = "merged-";

		if (mergedImagePlus != null) {
			channelMap.add("  [ \"Merged\", \""+channelString+"\" ]");

			ImageStack stack = mergedImagePlus.getStack();

			for( int z = 0; z < depth; ++z ) {

				ImageProcessor ip = stack.getProcessor(z+1);

				String sliceString = f5.format(z);
				String outputFileName = destinationDirectory + "/" + channelString + sliceString + ".png";

				int [] pixelDataRGB = (int[])ip.getPixels();
				if( outputFormat.equals("png") )
					pngWriter.writeFullColourPNG( pixelDataRGB, newWidth, newHeight, outputFileName );
				else // Must be JPEG:
					jpegWriter.writeFullColourJPEG( pixelDataRGB, newWidth, newHeight, outputFileName );
				++ slicesDone;
				reportProgress( slicesDone );
			}
		}

		try {
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(destinationDirectory+"/channels.js"),"UTF-8"));
			pw.println("var channelMap = [");
			for( Iterator<String> i = channelMap.iterator();
			     i.hasNext(); ) {
				pw.print(i.next());
				if( i.hasNext() )
					pw.print(",");
				pw.print("\n");
			}
			pw.println("];");
			pw.close();
		} catch( IOException e ) {
			throw new RuntimeException("IOException: "+e);
		}

		Job_Server.finishDirectory(destinationDirectory);
	}

	// Taken from "RGB Merge"'s implementation:
	byte [] byteArrayForGRAY16( ImageStack stack, int sliceOneIndexed ) {
		ImageProcessor ip = stack.getProcessor(sliceOneIndexed);
		ip = ip.convertToByte(true);
		return (byte[])ip.getPixels();
	}
}
