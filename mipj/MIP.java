/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package mipj;

import java.io.*;
import java.util.Arrays;
//import javax.vecmath.*;
import java.awt.image.*;
import ij.io.*;
import ij.ImagePlus;
import ij.process.*;
import ij.IJ;

public class MIP
{

/*
for transferring the viewpoint, you have to get a vector from the origin to the view pixel. then
transform that
Also get the lookat vector (i.e. 0 0 1) and transform that. Then you have a start point, and a vector
with which to trace the ray.
w*h transforms + ray trace
compared to w*h*d transforms
*/

	public static final float ONEDEGREE = 0.0174533f;
	public static final int TRILINEAR = 1;
	public static final int NEARESTNEIGHBOUR = 2;

	private boolean plugin; // acting as a plugin for ImageJ?
	private MIPImageStack stack;
	private byte[][] voxel = null;
	private File outputFile;
	private float zScale;
	private int resX, resY;
	private boolean writeToFile = false;
	private float rayCastIncrement = 1.0f;
	private int rayCastType = TRILINEAR;
	private boolean dmip;
	private int max_threshold = 255;
	private boolean forceWrite = false;

	private int stackwidth, stackheight, stackdepth;

	/** Create MIP using an ImageStack */

	public MIP( MIPImageStack i, boolean write ) throws Exception {

		stack = i;

		voxel = stack.getStack();

		if (voxel == null)
			throw new Exception("Failed to read the stack");

		zScale = 1.0f;
		resX = stack.getWidth();
		resY = stack.getHeight();
		outputFile = new File("projection000.tif");
		writeToFile = write;

	}

	public void setForceWrite( boolean b ) {
		forceWrite = b;
	}

	public void setPlugin( boolean b ) {
		plugin = b;
	}

	/** Set the threshold above which value rays will be terminated */

	public void setThreshold( int i ) throws Exception {
		if ( i > 1 && i < 256 )
			max_threshold = i;
		else
			throw new Exception("Threshold out of range...1 to 255 only");
	}

	/** Returns the threshold value */

	public int getThreshold() {
		return max_threshold;
	}

	/** Set the X:Z ratio of the slices */

	public void setZScale( float f ) {
		zScale = f;
		if (zScale == 0.0f) {
			zScale = (stack.getWidth() / stack.getDepth());
		}
	}

	/** Returns the ZScale value which has been set */

	public float getZScale() {
		return zScale;
	}

	/** Set the file for output to be written to */

	public void setOutputFile( File f ) {
		outputFile = f;
	}

	/** Return the output file to be written to */

	public File getOutputFile() {
		return outputFile;
	}

	/** Set the resolution for the output data */

	public void setResolution( int x, int y ) throws Exception {

		if ( x < 1 || y < 1 )
			throw new Exception("You can't have a negative resolution!");

		resX = x;
		resY = y;

	}

	/** Returns the resolution of the output image */

	public int[] getResolution() {
		int[] res = new int[2];
		res[0] = resX;
		res[1] = resY;
		return res;
	}

	/** Returns true if writing to a file, false if otherwise */

	public boolean writingToFile() {
		return writeToFile;
	}

	/** Set whether or not data will be written to the output file or not. */

	public void writeToFile( boolean b ) {
		writeToFile = b;
	}

	/** Returns the ray cast iteration increment */

	public float getRayCastIncrement() {
		return rayCastIncrement;
	}

	/** Set the distance "stepped" each iteration while raycasting. 1.0f would step the distance of one whole voxel at a time. */

	public void setRayCastIncrement( float f ) throws Exception {
		if ( f <= 0.0f )
			throw new Exception("Raycast increment must be greater than zero.");

		rayCastIncrement = f;
	}

	/** Returns the type of the raycasting */

	public int getRayCastType() {
		return rayCastType;
	}

	/** Set the type of raycasting interpolation: either MIP.TRILINEAR or MIP.NEARESTNEIGHBOUR */

	public void setRayCastType( int t ) throws Exception {
		if ( t != TRILINEAR && t != NEARESTNEIGHBOUR )
			throw new Exception("Unknown raycast type, must be MIP.TRILINEAR or MIP.NEARESTNEIGHBOUR.");

		rayCastType = t;
	}

	/** Set to enable Depth-MIP, where intensity is reduced for points further away. */

	public void setDMIP( boolean s ) {
		dmip = s;
	}

	/** True if Depth-MIP is on, false if not */

	public boolean getDMIP() {
		return dmip;
	}

	/** Turns the byte array, given the dimensions, into an Image */

	private ImagePlus makeImage( byte[] answer, int width, int height ) {
		ByteProcessor proc = new ByteProcessor( width, height );
		proc.setPixels( (Object) answer );
		return new ImagePlus( "tmp", proc );
	}

	/** Writes a byte array to the output file */

	private void writeFile( ImagePlus ip ) {

		System.out.print("Writing File... (to '" + outputFile + "')");

		try {

			if ( forceWrite || !outputFile.exists() ) {

				FileOutputStream fos = new FileOutputStream( outputFile );

				TiffEncoder tif = new TiffEncoder( ip.getFileInfo() );

				tif.write( new DataOutputStream(fos) );

				if ( !plugin && MIPDriver_.outputJpeg() ) {
					FileSaver fs = new FileSaver( ip );
					String jpgName = outputFile.getAbsolutePath();
					jpgName = jpgName.substring(0, jpgName.length() - 3 ) + "jpg"; // remove tif replace with jpg
					fs.saveAsJpeg( jpgName );
				}

			} else {
				System.out.println("Won't overwrite existing file, use -f to override. " + outputFile);
				return;
			}

		} catch( IOException io ) {
			System.out.println("Failed to write file in MIP.class");
			io.printStackTrace();
			return;
		}

		System.out.println("Written");

	}

	/** Works out the value nearest to the given position. */

	private int nearestNeighbour( Vector3f pos ) {


		int x, y, z;

		x = (int)(pos.x > 0.0f ? pos.x + 0.5f : pos.x - 0.5f);
		y = (int)(pos.y > 0.0f ? pos.y + 0.5f : pos.y - 0.5f);
		z = (int)(pos.z > 0.0f ? pos.z + 0.5f : pos.z - 0.5f);

		return 0xff & voxel[z][x + (stack.getWidth()*y)];


	}

	/** Works out the intensity of a given value within the voxelspace, according to trilinear interpolation. */

	private int trilinearIntensity( Vector3f pos, int max ) {

		int x, y, z;

		x = (int) (pos.x);
		y = (int) (pos.y);
		z = (int) (pos.z);

		if ( x == (stackwidth-1) ) {
			pos.x = ((float)(x))-0.000001f;
			x--;
		}
		if (y == (stackheight-1)) {
			pos.y = ((float)(y))-0.000001f;
			y--;
		}
		if (z == (stackdepth-1)) {
			pos.z = ((float)(z))-0.000001f;
			z--;
		}

		float v000 = 0.0f, v100 = 0.0f, v010 = 0.0f, v001 = 0.0f, v101 = 0.0f, v011 = 0.0f, v110 = 0.0f, v111 = 0.0f;

		v000 = (float) (0xff & voxel[z][x + (stackwidth*y)]);
		v100 = (float) (0xff & voxel[z][(x+1) + (stackwidth*y)]);
		v010 = (float) (0xff & voxel[z][x + (stackwidth*(y+1))]);
		v001 = (float) (0xff & voxel[z+1][x + (stackwidth*y)]);
		v101 = (float) (0xff & voxel[z+1][x+1 + (stackwidth*y)]);
		v011 = (float) (0xff & voxel[z+1][x + (stackwidth*(y+1))]);
		v110 = (float) (0xff & voxel[z][x+1 + (stackwidth*(y+1))]);
		v111 = (float) (0xff & voxel[z+1][x+1 + (stackwidth*(y+1))]);

		float maxf = (float) max;
		if ( v000 < maxf && v100 < maxf && v010 < maxf && v001 < maxf && v101 < maxf && v011 < maxf && v110 < maxf && v111 < maxf )
			return 0;

		pos.x -= (float)x;
		pos.y -= (float)y;
		pos.z -= (float)z;

		float oneminusx = (1.0f - pos.x);
		float oneminusy = (1.0f - pos.y);
		float oneminusz = (1.0f - pos.z);



		float value = v000 * oneminusx * oneminusy * oneminusz +
			v100 * pos.x * oneminusy * oneminusz +
			v010 * oneminusx * pos.y * oneminusz +
			v001 * oneminusx * oneminusy * pos.z +
			v101 * pos.x * oneminusy * pos.z +
			v011 * oneminusx * pos.y * pos.z +
			v110 * pos.x * pos.y * oneminusz +
			v111 * pos.x * pos.y * pos.z;

		int val = (int)(value > 0.0f ? value + 0.5f : value - 0.5f);

		return val;


	}

	/** Simply projects the data set directly onto the screen. */

	public ImagePlus projectZ() {

		byte[] answer = new byte[stack.getWidth()*stack.getHeight()];

		int maxpix = stack.getWidth()*stack.getHeight();
		int max;

		for ( int i = 0 ; i < maxpix ; ++i ) {

			max =  0xff & voxel[0][i];

			for (int j = 1 ; j < stack.getDepth() ; ++j) {
				int test = 0xff & voxel[j][i];
				if (test > max)
					max = test;
			}

			answer[i] = (byte) max;

		}


		ImagePlus ip = makeImage( answer , stack.getWidth(), stack.getHeight() );

		if (writeToFile)
			writeFile( ip );

		return ip;

	}

	/** Performs MIP by projecting every point in the dataset by a matrix. */

	public ImagePlus projectByMatrix( Matrix4f rotin ) {

		Matrix4f rot = new Matrix4f( rotin );

		byte[] answer = new byte[ stack.getWidth() * stack.getHeight() ];

		int w = stack.getWidth();
		int h = stack.getHeight();
		int d = stack.getDepth();
		int hw = (w>>1);
		int hh = (h>>1);
		int hd = (d>>1);

		int maxpix = stack.getWidth() * stack.getHeight();
		int val;

		Vector3f pos = new Vector3f();

		Matrix4f mat = new Matrix4f();

		mat.translateBy( -hw, -hh, -hd );

		rot.mul(mat);

		rot.translateBy( hw, hh, hd );

		for ( int z = 0 ; z < d ; ++z ) {

			if (plugin) {
				double prog = ((z*100)/d) / 100.0;
				IJ.showProgress( prog );
			} else
				System.out.print("."); // System.out.print(((z*100)/d)+"%\r");

			for( int y = 0 ; y < h ; ++y ) {

				for (int x = 0 ; x < w ; ++x ) {

					pos.x = x;
					pos.y = y;
					pos.z = z;

					rot.transform( pos );

					int ix = (int)(pos.x > 0.0f ? pos.x + 0.5f : pos.x - 0.5f);
					int iy = (int)(pos.y > 0.0f ? pos.y + 0.5f : pos.y - 0.5f);

					if (ix >= 0 && ix < w && iy >= 0 && iy < h) {

						val = 0xff & answer[ix + (w*iy)];

						if ( val < (0xff & voxel[z][x + (w*y)]) )
							answer[ix + (w*iy)] = voxel[z][x + (w*y)];

					}

				}


			}


		}

		if (plugin)
			IJ.showProgress( 1.0 );
		else
			System.out.println("100%...DONE");

		ImagePlus ip = makeImage( answer , w, h );

		if (writeToFile)
			writeFile( ip );

		return ip;

	}

	/** Performs simple ray-casting, treating each voxel as a box which may be passed through. */

	public ImagePlus rayCastByMatrixSimple( Matrix4f rotin ) {


		Matrix4f rot = new Matrix4f( rotin );

		byte[] answer = new byte[ stack.getWidth() * stack.getHeight() ];

		int w = stack.getWidth();
		int h = stack.getHeight();
		float d = (float)stack.getDepth() * zScale;
		int hw = (w>>1);
		int hh = (h>>1);
		float hd = (d * 0.5f);

		// for every pixel in the view-space (at the moment, it's the width and height of input data,
		// but in future it could be any resolution

		// transform the ray-casting direction

		Vector3f direction = new Vector3f( 0.0f, 0.0f, rayCastIncrement );
		rot.transform( direction );

		// how many steps needed to ray-trace?

		Vector3f centre = new Vector3f( (float)hw, (float)hh, hd  );

		// distance to a corner ( i.e. biggest distance within a cube )

		float max = centre.length() + 1.0f;

		int steps = (int) max;

		steps *= 2;

		Vector3f pos = new Vector3f();

		for ( int x = 0 ; x < w ; ++x ) {

			for( int y = 0 ; y < h ; ++y ) { // transform each ray to be casted's start position

				// could probably precalculate and just increment instead of transforming, but
				// there could be rounding problems

				pos.x = (x - hw);
				pos.y = (y - hh);
				pos.z = -max;

				rot.transform( pos );

				for( int i = 0 ; i < steps ; ++i ) {

					int px, py, pz;

					px = (int) pos.x;
					py = (int) pos.y;

					if ( pos.z < hd && pos.z > (-hd) &&
					     py < hh && py > (-hh) &&
					     px < hw && px > (-hw) ) {
						pz = (int)((pos.z + hd) / zScale);
						py += hh;
						px += hw;

						int val = 0xff & answer[x + (w*y)];

						if ( val < (0xff & voxel[pz][px + (w*py)]) )
							answer[x + (w*y)] = voxel[pz][px+(w*py)];

					}

					pos.add( direction );

				}

			}

		}

		ImagePlus ip = makeImage( answer , w, h );

		if (writeToFile)
			writeFile( ip );

		return ip;

	}

/** Performs simple ray-casting, treating each voxel as a box which may be passed through. */

	public ImagePlus rayCastByMatrixInteger( Matrix4f rotin ) {

		Matrix4f rot = new Matrix4f( rotin );

		byte[] answer = new byte[ stack.getWidth() * stack.getHeight() ];

		int w = stack.getWidth();
		int h = stack.getHeight();
		float d = (float)stack.getDepth() * zScale;

		int id = stack.getDepth();
		int hw = (w>>1);
		int hh = (h>>1);
		float hd = (d * 0.5f);
		int ihd = (int)hd;


		// for every pixel in the view-space (at the moment, it's the width and height of input data,
		// but in future it could be any resolution

		// how many steps needed to ray-trace?

		Vector3f centre = new Vector3f( (float)hw, (float)hh, (float)hd);

		// distance to a corner ( i.e. biggest distance within a cube )

		float max = centre.length() + 1.0f;

		// transform the ray-casting direction

		Vector3f direction = new Vector3f( 0.0f, 0.0f, max*2 );
		rot.transform( direction );

		int[] start = new int[3];
		int[] end = new int[3];

		Vector3f pos = new Vector3f();

		// generates a template from IntegerRayCast and uses this instead of
		// calculating what to do each time

		pos.x = -hw;
		pos.y = -hh;
		pos.z = -max;
		rot.transform( pos );
		start[0] = (int)pos.x;
		start[1] = (int)pos.y;
		start[2] = (int)pos.z;
		start[0] += hw;
		start[1] += hh;
		start[2] += ihd;
		start[2] = (int)(((float)start[2]) / zScale);
		pos.add( direction );
		end[0] = (int)pos.x;
		end[1] = (int)pos.y;
		end[2] = (int)pos.z;
		end[0] += hw;
		end[1] += hh;
		end[2] += ihd;
		end[2] = (int)(((float)end[2]) / zScale);

		IntegerRayCast irc = new IntegerRayCast( start, end );

		byte[] template = irc.createTemplate();
		int[] steps = irc.getSteps();

		for ( int y = 0 ; y < h ; ++y ) {


			for( int x = 0 ; x < w ; ++x ) { // transform each ray to be casted's start position

				// could probably precalculate and just increment instead of transforming, but
				// there could be rounding problems

				pos.x = (x - hw);
				pos.y = (y - hh);
				pos.z = -max;

				rot.transform( pos );
				start[0] = (int)pos.x;
				start[1] = (int)pos.y;
				start[2] = (int)pos.z;
				start[0] += hw;
				start[1] += hh;
				start[2] += ihd;
				start[2] = (int)(((float)start[2]) / zScale);

				int maxpt = 0;

				boolean visited = false;

				for(int i = 0 ; i < template.length ; ++i ) {

					if ( (template[i] & 0x01) != 0 )
						start[0] += steps[0];
					if ( (template[i] & 0x02) != 0 )
						start[1] += steps[1];
					if ( (template[i] & 0x04) != 0 )
						start[2] += steps[2];

					if ( start[0] >= 0 && start[0] < w &&
					     start[1] >= 0 && start[1] < h &&
					     start[2] >= 0 && start[2] < id ) {

						visited = true;

						if (maxpt < ( 0xff & voxel[start[2]][start[0] + (w*start[1])]) ) {
							maxpt = 0xff & voxel[start[2]][start[0] + (w*start[1])];
							if (maxpt >= max_threshold)
								break;
						}

					} else {
						if (visited == true)
							break;
					}

				}

				answer[x + (w*y)] = (byte) maxpt;

			}

		}

		ImagePlus ip = makeImage( answer , w, h );

		if (writeToFile)
			writeFile( ip );

		return ip;

	}

    /** Performs trilinear interpolation to produce the colour of the volume at the given position. Uses
    <pre>
    Vxyz =  V000 (1 - x) (1 - y) (1 - z) +
    V100 x (1 - y) (1 - z) +
    V010 (1 - x) y (1 - z) +
    V001 (1 - x) (1 - y) z +
    V101 x (1 - y) z +
    V011 (1 - x) y z +
    V110 x y (1 - z) +
    V111 x y z
    </pre>
    */



    /**

    Conceptually this works by taking the voxels and sticking them at the corners of virtual boxes. So a nxn data set
    actually ends up being (n-1)x(n-1) boxes.

    Transformations are performed on the viewpoint, then rays are cast from the viewer's position through the dataset,
    and the point of maximum intensity is noted. The viewpoint is in front of the data-set, and the view vector is
    (0 0 1) -- into the dataset. These are translated by the matrix given into the method to obtain rotation about
    any angle.

    According to the fields within this class, different parameters are used.

    */


	public ImagePlus rayCastByMatrix( Matrix4f rotin ) {

		Matrix4f rot = new Matrix4f( rotin );
		rot.invert();

		byte[] answer = new byte[ resX * resY ];
		int[] depthbuf = null;

		if (dmip) {
			depthbuf = new int[ resX * resY ];
			Arrays.fill( depthbuf, -1 );
		}

		// transform the ray-casting direction

		Vector3f direction = new Vector3f( 0.0f, 0.0f, rayCastIncrement );
		rot.transform( direction );

		// configure dimensions of the number of "boxes" in the dataset

		float dataWidth = (float) (stack.getWidth() - 1);
		float dataHeight = (float) (stack.getHeight() - 1);
		float dataDepth = (float) (stack.getDepth() - 1);
		dataDepth *= zScale; // fiddle the z depth

		// distance between each box

		float xStep = dataWidth / ( (float) ( resX - 1) );
		float yStep = dataHeight / ( (float) ( resY - 1) );

		Vector3f centre = new Vector3f( dataWidth / 2.0f, dataHeight / 2.0f, dataDepth / 2.0f  );

		// distance to a corner ( i.e. biggest distance within a cube )

		float farthest = centre.length() + 1.0f;

		// steps required to go through the whole voxelspace

		int maxsteps = (int) ( ( (farthest * 2.0f) + 1 ) / rayCastIncrement);

		float depthfactor = (1.0f / maxsteps);

		Vector3f pos = new Vector3f();
		Vector3f realpos = new Vector3f(  );

		stackwidth = stack.getWidth();
		stackdepth = stack.getDepth();
		stackheight = stack.getHeight();

		for ( int y = 0 ; y < resY ; ++y ) {

			if (plugin) {
				double prog = ((y+1)/(double)resY);
				IJ.showProgress( prog );
			} else
				System.out.print((y+1) + "/" + resY + " Lines\r");

			for ( int x = 0 ; x < resX ; ++x ) {

				pos.x = (x * xStep) - centre.x; // position of viewpoint
				pos.y = (y * yStep) - centre.y;
				pos.z = -farthest;

				rot.transform( pos );

				int max = 0;

				boolean visited = false;

				for ( int i = 0 ; i < maxsteps ; ++i ) { // move into image from viewpoint

					// it's thanks to this check that trilinear intensity needs no checks. Note >= ... <

					if (pos.x >= -centre.x && pos.x < centre.x &&
					    pos.y >= -centre.y && pos.y < centre.y &&
					    pos.z >= -centre.z && pos.z < centre.z ) {

						visited = true;

						realpos.x = pos.x;
						realpos.y = pos.y;
						realpos.z = pos.z;

						realpos.add( centre );

						realpos.z /= zScale;

						int tmp;

						if ( rayCastType == NEARESTNEIGHBOUR )
							tmp = nearestNeighbour( realpos );
						else
							tmp = trilinearIntensity( realpos, max );

						if (dmip) { // depth mip enabled
							float tmpf = (float) tmp;
							float dcalc = i * depthfactor;
							dcalc = 1.0f - dcalc;
							tmpf *= (dcalc); // dcalc^2 more "realistic"
							tmp = (int)( tmpf > 0.0f ? tmpf + 0.5f : tmpf - 0.5f);
						}

						if ( tmp > max ) {

							max = tmp;
							answer[x + (resX*y)] = (byte) tmp;
							if ( max >= max_threshold ) // terminate if at highest value
								break;
						}

					} else
						if (visited)
							break; // if left the voxelspace, quit!

					pos.add( direction );

				} // for i

			} // for x

		} // for y

		if (plugin)
			IJ.showProgress(1.0);
		else
			System.out.println("");


		ImagePlus ip = makeImage( answer , resX, resY );

		if (writeToFile)
			writeFile( ip );

		return ip;


	}


}
