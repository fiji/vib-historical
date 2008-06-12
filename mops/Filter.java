package mops;

import vib.InterpolatedImage;
import process3d.Smooth_;

public class Filter {

	public static InterpolatedImage gauss(InterpolatedImage ii, float sig) {
		return new InterpolatedImage(Smooth_.smooth(
					ii.getImage(), true, sig, false));
	}

	public static InterpolatedImage gauss(InterpolatedImage ii,
		float sigX, float sigY, float sigZ) {
		return new InterpolatedImage(Smooth_.smooth(
			ii.getImage(), true, sigX, sigY, sigZ, false));
	}

	/**
	 * subtract i2 from i1
	 * The images must have the same size; this is not checked.
	 */
	public static InterpolatedImage sub(InterpolatedImage i1, InterpolatedImage i2) {
		InterpolatedImage ret = i1.cloneDimensionsOnly();
		InterpolatedImage.Iterator it = ret.iterator();
		while(it.next() != null) {
			ret.setFloat(it.i, it.j, it.k,
				i1.getNoCheckFloat(it.i, it.j, it.k) -
				i2.getNoCheckFloat(it.i, it.j, it.k));
		}
		return ret;
	}

	public static InterpolatedImage gradX(InterpolatedImage ii) {
		InterpolatedImage ret = ii.cloneDimensionsOnly();
		InterpolatedImage.Iterator it = ret.iterator();
		while(it.next() != null) {
			ret.setFloat(it.i, it.j, it.k,
				(ii.getNoInterpolFloat(it.i+1, it.j, it.k) - 
				ii.getNoInterpolFloat(it.i-1, it.j, it.k) / 2));
		}
		return ret;
	}

	public static InterpolatedImage gradY(InterpolatedImage ii) {
		InterpolatedImage ret = ii.cloneDimensionsOnly();
		InterpolatedImage.Iterator it = ret.iterator();
		while(it.next() != null) {
			ret.setFloat(it.i, it.j, it.k,
				(ii.getNoInterpolFloat(it.i, it.j+1, it.k) - 
				ii.getNoInterpolFloat(it.i, it.j-1, it.k) / 2));
		}
		return ret;
	}

	public static InterpolatedImage gradZ(InterpolatedImage ii) {
		InterpolatedImage ret = ii.cloneDimensionsOnly();
		InterpolatedImage.Iterator it = ret.iterator();
		while(it.next() != null) {
			ret.setFloat(it.i, it.j, it.k,
				(ii.getNoInterpolFloat(it.i, it.j, it.k+1) - 
				ii.getNoInterpolFloat(it.i, it.j, it.k-1) / 2));
		}
		return ret;
	}

	public static void suppNonExtremum(InterpolatedImage ii) {
		InterpolatedImage.Iterator it = ii.iterator();
		while(it.next() != null) {
			float v1 = ii.getNoCheckFloat(it.i, it.j, it.k);
			boolean isMax = true, isMin = true;
			for(int i = 0; i < 27; i++) {
				if(i == 27/2)
					continue;
				int mz = i / 9 - 1; // 1 = d/2
				int my = (i % 9) / 3 - 1; // 1 = h/2
				int mx = (i % 9) % 3 - 1; // 1 = w/2
				float v2 = ii.getNoInterpolFloat(
					it.i + mx, it.j + my, it.k + mz);
				if(v2 > v1) isMax = false;
				if(v2 < v1) isMin = false;
				if(!isMin && !isMax)
					ii.setFloat(it.i, it.j, it.k, 0);
			}
		}
	}

	/**
	 * In place enhance all values of an InterPolatedImage to fill the given range.
	 * 
	 * @param src source
	 * @param scale defines the range 
	 */
	public static final void enhance( InterpolatedImage src, float scale )
	{
		float min = src.getNoCheckFloat( 0, 0, 0 );
		float max = min;
		InterpolatedImage.Iterator it = src.iterator();
		it.next();
		while ( it.next() != null )
		{
			float v = src.getNoCheckFloat( it.i, it.j, it.k );
			if ( v < min ) min = v;
			else if ( v > max ) max = v;
		}
		scale /= ( max - min );
		it = src.iterator();
		while ( it.next() != null )
			src.setFloat(
				it.i, it.j, it.k,
				scale * ( src.getNoCheckFloat( it.i, it.j, it.k ) - min ) );
	}

	/**
	 * Create a normalized 3d gaussian impulse with appropriate size with its
	 * center slightly moved away from the middle.
	 * 
	 * @param offset 3d-offset vector
	 * 
	 * @return gaussian kernel addressed as [z][y][x]
	 * 
	 */
	static public float[][][] create3DGaussianKernelOffset( float sigma, float[] offset, boolean normalize )
	{
		int size = 3;
		float[][][] kernel;
		if ( sigma == 0 )
		{
			kernel = new float[3][3][3];
			kernel[1][ 1 ][1] = 1;
		}
		else
		{
			size = Math.max( 3, ( int )( 2 * Math.round( 3 * sigma ) + 1 ) );
			float twoSqSigma = 2 * sigma * sigma;
			kernel = new float[size][size][size];
			float sum = 0;
			for ( int z = 0; z < size; ++z )
			{
				float fz = ( float )( z - size / 2 ) - offset[ 2 ];
				fz *= fz;
				for ( int y = size - 1; y >= 0; --y )
				{
					float fy = ( float ) ( y - size / 2 ) - offset[ 1 ];
					fy *= fy;
					for ( int x = size - 1; x >= 0; --x )
					{
						float fx = ( float ) ( x - size / 2 ) - offset[ 0 ];
						fx *= fx;
						
						kernel[ z ][ y ][ x ] = ( float )( Math.exp( -( fx + fy + fz ) / twoSqSigma ) );
						
						sum += kernel[ z ][ y ][ x ];
					}
				}
			}
			if ( normalize )
			{
				for ( float[][] a : kernel )
					for ( float[] b : a )
						for ( int i = 0; i < size; ++i )
							b[ i ] /= sum;
			}
		}
		return kernel;
	}
}

