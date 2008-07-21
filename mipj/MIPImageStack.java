/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package mipj;

import ij.io.*;
import ij.ImagePlus;
import ij.util.*;
import ij.IJ;
import ij.process.*;
import java.io.*;
import java.text.NumberFormat;
import java.util.Vector;
import java.util.ArrayList;
import java.awt.*;
import java.awt.image.*;

public class MIPImageStack
{

	private String path;
	private String prefix;
	private String suffix;

	private int width;
	private int height;
	private int depth;
	private float openScale = 1.0f;
	private int realTimeSize = 0;

	private boolean copied = false;

	private byte[][] stack = null;
	private String[] name;

	// this could do with a bit more file-system sanity checking

	/** Converts an ImageJ ImageStack into one which mipj understands */

	public MIPImageStack( ij.ImageStack ijs ) throws ClassCastException {

		width = ijs.getWidth();
		height = ijs.getHeight();
		depth = ijs.getSize();

		stack = new byte[ depth ][];

		// ImageJ goes 1 to len, not 0 to len-1...

		for( int i = 1 ; i <= depth ; ++i ) {
			stack[i-1] = (byte[]) ijs.getPixels(i);
		}

	}

	/** Constructor which reads files in scaled by a scale factor */

	public MIPImageStack( String fileOrDirectory, float scale ) throws Exception {
		if (scale < 0.0f)
			throw new Exception("MIPImageStack: A negative scale doesn't make sense!");
		openScale = scale;
		loadFiles( fileOrDirectory );
	}

	public MIPImageStack( String fileOrDirectory, int rtsize ) throws Exception {
		if (rtsize != 256)
			throw new Exception("RealTime Datasets should be 256 in size (for now)");
		realTimeSize = rtsize;
		loadFiles( fileOrDirectory );
	}


	public MIPImageStack( String fileOrDirectory ) throws Exception {

		loadFiles( fileOrDirectory );

	}

	private void loadFiles( String fileOrDirectory ) throws Exception {
		System.out.println( "realTimeSize is " + realTimeSize );
		System.out.println( "openScale is " + openScale );

		if( new File(fileOrDirectory).isDirectory() ) {

			String list[] = new File(fileOrDirectory).list();

			if (list == null)
				throw new Exception("No files in that directory!");

			StringSorter.sort( list );

			boolean dimSet = false;

			ArrayList pics = new ArrayList();

			int count = 0;

			int resultwidth, resultheight;

			if ( realTimeSize == 0 ) {
				resultwidth = resultheight = 256;
			} else {
				resultwidth = resultheight = realTimeSize;
			}

			System.out.println( "Initially resultwidth: " + resultwidth );
			System.out.println( "Initially resultheight: " + resultheight );

			for(int i = 0 ; i < list.length ; ++i ) {

				if ( !list[i].endsWith("txt") && !list[i].endsWith("jbf") ) {

					ImagePlus img = new Opener().openImage(fileOrDirectory, list[i]);

					if (img != null) {
						if (!dimSet) {

							width = img.getWidth();
							height = img.getHeight();
							dimSet = true;

							if ( realTimeSize == 0 ) {

								if (openScale != 1.0f) {
									resultwidth = Math.round( openScale * width );
									resultheight = Math.round( openScale * height );
								} else {
									resultwidth = width;
									resultheight = height;
								}

							}

						} else if ( width != img.getWidth() || height != img.getHeight())
							continue;

						ImageProcessor ip = img.getProcessor();

						ByteProcessor bp = (ByteProcessor) ip.convertToByte(true);

						// change the size of the image if needed

						if ( openScale != 1.0f || realTimeSize != 0) {
							bp.setInterpolate( true );
							ip = bp.resize( resultwidth, resultheight );
						}

						pics.add( ip.getPixels() );

						System.out.print("Reading stack..." + (++count) + " read\r");

					}

				}

			}

			System.out.println("Reading stack...DONE (" + count + " read)");

			stack = new byte[ pics.size() ][];

			depth = pics.size();

			System.out.println( "Set depth to: " + depth );

			for( int i = 0 ; i < pics.size() ; ++i )
				stack[i] = (byte[]) pics.get(i);

			width = resultwidth;
			height = resultheight;

			System.out.println( "Finally resultwidth and width: " + resultwidth );
			System.out.println( "Finally resultheight and height: " + resultheight );

		} else {

			int resultwidth, resultheight;

			if ( realTimeSize == 0 ) {
				resultwidth = resultheight = 256;
			} else {
				resultwidth = resultheight = realTimeSize;
			}

			System.out.println( "Going to load the file: " + fileOrDirectory );
			// It's a file...
			ImagePlus img = new Opener().openImage(fileOrDirectory);

			width = img.getWidth();
			height = img.getHeight();
			depth = img.getStackSize();

			System.out.println( "Set depth to: " + depth );

			width = img.getWidth();
			height = img.getHeight();

			if ( realTimeSize == 0 ) {

				if (openScale != 1.0f) {
					resultwidth = Math.round( openScale * width );
					resultheight = Math.round( openScale * height );
				} else {
					resultwidth = width;
					resultheight = height;
				}

			}

			System.out.println( "Initially resultwidth: " + resultwidth );
			System.out.println( "Initially resultheight: " + resultheight );

			stack = new byte[ depth ][];

			for( int i = 1; i <= depth; ++i ) {

				img.setSlice( i );

				ImageProcessor ip = img.getProcessor();

				ByteProcessor bp = (ByteProcessor) ip.convertToByte(true);

				if ( openScale != 1.0f || realTimeSize != 0) {
					bp.setInterpolate( true );
					ip = bp.resize( resultwidth, resultheight );
				}

				byte allBytes[] = (byte[])ip.getPixels();

				stack[i-1] = new byte[ width * height ];
				System.arraycopy( allBytes, 0, stack[i-1], 0, resultwidth * resultheight );
				// System.arraycopy( allBytes, 0, stack[i-1], 0, width * height );
			}

			width = resultwidth;
			height = resultheight;

			System.out.println( "Finally resultwidth and width: " + resultwidth );
			System.out.println( "Finally resultheight and height: " + resultheight );

		}


	}

	/** Returns a pointer to the stack of images byte[z][xy] */

	public byte[][] getStack() {

		return stack;

	}


	public byte[][] getStackCopy() {
		if (copied) { // if already been copied

			return stack;
		} else {

			byte[][] copy = new byte[stack.length][];

			for(int i = 0 ; i < stack.length ; ++i ) {
				copy[i] = new byte[stack[i].length];
				System.arraycopy( stack[i], 0, copy[i], 0, stack[i].length );
			}

			return copy;

		}

	}

    /** Resizes all the images in the stack by the given factor. It
	uses ImageJ to perform this. */

	public void scale( float scaleby ) {

		if ( scaleby <= 0.0f)
			return;

		copied = true;

		int newwidth = Math.round( scaleby * width );
		int newheight = Math.round( scaleby * height );

		BufferedImage bi = new BufferedImage( width, height, BufferedImage.TYPE_INT_RGB );

		for( int z = 0 ; z < depth ; ++z ) {

			ByteProcessor proc = new ByteProcessor( width, height );
			proc.setPixels( (Object) stack[z] );
			proc.setInterpolate(true);
			ImageProcessor ip = proc.resize( newwidth, newheight );
			stack[z] = (byte[]) ip.getPixels();

		}


		height = newheight;
		width = newwidth;


	}

	/** Scales the stack itself in the Z direction */

	public void zScale( float scaleby ) {

		if ( scaleby <= 0.0f )
			return;

		copied = true;

		float newdepth = depth * scaleby;

		int newdepthi = Math.round( newdepth );

		// increment value for resampling dataset

		float inc = ((float)(depth-1)) / ((float)newdepthi-1);

		byte[][] newstack = new byte[ newdepthi ][];

		float pos = 0.0f;

		System.out.println( "Z-Scaling..." );

		for( int i = 0 ; i < newdepthi ; ++i ) {

			newstack[i] = new byte[width*height];

			pos = (inc*i);

			int a = (int)pos;

			// this is intended to reduce memory usage
			// by removing bits of the old stack once finished using

			if (a-1 >= 0 && stack[a-1] != null) {
				stack[a-1] = null;
				System.gc();
			}

			float afactor = pos - a;
			afactor = 1.0f - afactor;
			float bfactor = 1.0f - afactor;

			for( int y = 0 ; y < height ; ++y ) {
				for( int x = 0 ; x < width ; ++x ) {

					// --->--->---a--->-pos->--->---b--->--->...

					int arraypos = x+(width*y);

					float aval = 0.0f;
					float bval = 0.0f;

					aval = (float) (stack[a][arraypos] & 0xff);
					if (bfactor != 0.0f && (a+1) < stack.length) {
						bval = (float) (stack[a+1][arraypos] & 0xff);
					}

					float interpVal = (afactor*aval) + (bfactor*bval);

					int currentVal = (int) ( (interpVal > 0.0f) ? (interpVal + 0.5f) : (interpVal - 0.5f) );

					newstack[i][arraypos] = (byte) currentVal;

				}

			}

			if (MIPMainWindow.running)
				IJ.showProgress( (((i+1)*100)/newdepthi)/100.0 );
			else
				; // System.out.print( "Z-Scaling..." + ((i+1)*100)/newdepthi + "%\r" );

		}

		float actual = (newdepthi*100)/depth;

		actual /= 100.0f;

		if (MIPMainWindow.running)
			IJ.showProgress(1.0);
		else
			System.out.println("Z-Scaling...DONE (Actual ZScale: " + actual + ")");

		stack = newstack;
		depth = newdepthi;

	}


	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getDepth() {
		return depth;
	}

}
