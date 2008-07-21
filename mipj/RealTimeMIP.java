/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package mipj;

import java.io.*;
import java.awt.*;
import java.awt.image.*;
import java.util.Arrays;

public class RealTimeMIP implements Serializable
{

	private int[] valuePointer;
	private int[] position;
	private byte[] buf;
	private int ID;
	private IndexColorModel icm;
	private MemoryImageSource mis;
	private Image img;

	int width, height, depth;

	/** Creates a RealTimeMIP structure from a list of pointers to
	    indicate values 1-255 within a list of positions. Also
	    requires width, height and depth of dataset. */

	public RealTimeMIP( int[] pointers, int[] pos, int width, int height, int depth ) {
		valuePointer = pointers;
		position = pos;
		this.width = width;
		this.height = height;
		this.depth = depth;
		buf = null;
		icm = null;
		mis = null;
	}

	public int getX() {
		return width;
	}
	public int getY() {
		return height;
	}

	/** Pack three integer values into a single integer (2047, 2047, 1023) */

	public static int encode( int x, int y, int z ) {
		return ((x<<21) | (y<<10) | z );
	}

	private void makeColourModel() {

		byte[] rLUT = new byte[256];
		byte[] gLUT = new byte[256];
		byte[] bLUT = new byte[256];

		for(int i=0; i<256; i++) {
			rLUT[i]=(byte)i;
			gLUT[i]=(byte)i;
			bLUT[i]=(byte)i;
		}

		icm = new IndexColorModel(8, 256, rLUT, gLUT, bLUT);

	}

	/** Draw image onto buffer and then to screen */

	public void drawImage(Graphics gfx, Matrix4f rot, int threshold, int posx, int posy) {

		float hw, hh, hd;
		hw = ( (float) width / 2.0f );
		hh = ( (float) height / 2.0f );
		hd = ( (float) depth / 2.0f );
		float hw5, hh5, hd5;
		hw5 = hw + 0.5f;
		hh5 = hh + 0.5f;

		if ( buf == null )
			buf = new byte[width*height];
		else
			Arrays.fill( buf, (byte)0 );

		if ( icm == null )
			makeColourModel();

		if ( mis == null ) {
			mis = new MemoryImageSource(width, height, icm, buf, 0,  width);
			mis.setAnimated(true);
			mis.setFullBufferUpdates(true);
			img = Toolkit.getDefaultToolkit().createImage(mis);
		}

		for( int i = threshold ; i < 255 ; ++i ) { // 0 is actually colour 1, so 254 is 255

			int grey = i + 1;

			Vector3f pos = new Vector3f(  );

			for ( int element = valuePointer[i] ; element < valuePointer[i+1] ; element++) {

				int x = ( position[element] >>> 21 );
				int y = ( ( position[element] >> 10 ) & 0x7FF );
				int z = ( position[element] & 0x3FF );

				pos.x = ((float)(x) - (hw));
				pos.y = ((float)(y) - (hh));
				pos.z = ((float)(z) - (hd));

				rot.transform( pos );

				int ix = (int) (pos.x + hw5);
				int iy = (int) (pos.y + hh5);

				if (ix >= 0 && ix < width && iy >= 0 && iy < height)
					buf[ix+(width*iy)] = (byte)grey;

			}

		}

		mis.newPixels();

		gfx.drawImage( img, posx, posy, null );

	}

	/*

	public void draw(Graphics gfx, Matrix4f rot)
	{

		int hw, hh, hd;
		hw = width >> 1;
		hh = height >> 1;
		hd = depth >> 1;


		for( int i = 0 ; i < 255 ; ++i ) {

			gfx.setColor( new Color( i, i, i ) );

			for ( int element = valuePointer[i] ; element < valuePointer[i+1] ; element++) {

				int x = ( position[element] >>> 21 );
				int y = ( ( position[element] >> 10 ) & 0x7FF );
				int z = ( position[element] & 0x3FF );

				Vector3f pos = new Vector3f( (float) (x - (hw)),
							     (float) (y - (hh)),
							     (float) (z - (hd)) );

				rot.transform( pos );

				int ix = (int) (pos.x + 0.5f);
				int iy = (int) (pos.y + 0.5f);

				ix += (hw);
				iy += (hh);

				if (ix >= 0 && ix < width && iy >= 0 && iy < height) {
					gfx.drawRect(ix, iy, 0, 0);
				}

			}

		}

	}



	public void writetmp(File f, Matrix4f rot) throws Exception
	{

		int hw, hh, hd;
		hw = width >> 1;
		hh = height >> 1;
		hd = depth >> 1;

		FileOutputStream fos = new FileOutputStream( f );

		byte[] buffer = new byte[256*256];

		for( int i = 0 ; i < 255 ; ++i ) {

			for ( int element = valuePointer[i] ; element < valuePointer[i+1] ; element++) {

				int x = ( position[element] >>> 21 );
				int y = ( ( position[element] >> 10 ) & 0x7FF );
				int z = ( position[element] & 0x3FF );

				Vector3f pos = new Vector3f( (float) (x - (hw)),
							     (float) (y - (hh)),
							     (float) (z - (hd)) );

				rot.transform( pos );

				int ix = (int) (pos.x + 0.5f);
				int iy = (int) (pos.y + 0.5f);

				ix += (hw);
				iy += (hh);

				if (ix >= 0 && ix < width && iy >= 0 && iy < height)
					buffer[ix + (width*iy)] = (byte)i;

			}

		}

		fos.write(buffer);

		fos.close();

	}

	public void writetmp(File f) throws Exception
	{

		FileOutputStream fos = new FileOutputStream( f );

		byte[] buffer = new byte[256*256];

		System.out.println(System.currentTimeMillis());

		for( int i = 0 ; i < 255 ; ++i ) {

			for ( int element = valuePointer[i] ; element < valuePointer[i+1] ; element++) {
				int x = ( position[element] >>> 21 );
				int y = ( ( position[element] >> 10 ) & 0x7FF );
				buffer[x + (width*y)] = (byte)i;
			}

		}

		System.out.println(System.currentTimeMillis());

		fos.write(buffer);

		fos.close();


	}

	*/

}
