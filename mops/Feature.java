package mops;

import process3d.Smooth_;
import vib.InterpolatedImage;
import vib.FastMatrix;
import Jama.EigenvalueDecomposition;
import Jama.Matrix;

import math3d.Point3d;

import ij.measure.Calibration;

public class Feature extends Point3d
{
	/*
	 * Feature descriptor width. For simplicity, assume a 
	 * cubic descriptor for the moment.
	 */
	public static final int FD_WIDTH = 16;

	/*
	 * descriptor
	 */
	private float[] desc;
	public float[] getDesc(){ return desc; }

	/*
	 * rotation matrix which aligns the patch in a way that
	 * the steepest gradient is in x-direction and the
	 * 2nd steepest is in y direction.
	 */
	private FastMatrix orientation = null;
	
	/**
	 * scale = sigma of the feature relative to the octave
	 */
	private double scale;

	Feature( double x, double y, double z, double scale, float[] desc )
	{
		super( x, y, z );
		this.scale = scale;
		this.desc = desc;
	}
	
	
	
	public double featureDistance(Feature other) {
		return -1;
	}

	/*
	 * creates a 3D gaussian kernel in a 1D float array where
	 * z is the index changing slowest, then y and then x.
	 * kernel-diameter >= 5 * sigma
	 * The returned kernel is normalized.
	 */
	public float[] create3DGaussianKernel(float sigma) {
		// radius should at least be 2.5 * sigma
		int d = (int)Math.ceil(5 * sigma);
		d = d % 2 == 0 ? d + 1 : d;
		int r = d / 2;
		int wh = d * d;
		float[] kernel = new float[d * d * d];
		float sum = 0;
		float sigma_2 = sigma * sigma;
		for(int i = 0; i < kernel.length; i++) {
			int z = i / wh - r;
			int y = (i % wh) / d - r;
			int x = (i % wh) % d - r;
			float n = (float)Math.sqrt(x*x + y*y + z*z);
			kernel[i] = (float)Math.exp(-n*n / (2*sigma_2));
			sum += kernel[i];
		}
		// normalize
		for(int i = 0; i < kernel.length; i++)
			kernel[i] /= sum;

		return kernel;
	}

	public float[] create2DGaussianKernel(float sigma) {
		// radius should at least be 2.5 * sigma
		int d = (int)Math.ceil(5 * sigma);
		d = d % 2 == 0 ? d + 1 : d;
		int r = d / 2;
		float[] kernel = new float[d * d];
		float sum = 0;
		float sigma_2 = sigma * sigma;
		for(int i = 0; i < kernel.length; i++) {
			int y = i / d - r;
			int x = i % d - r;
			float n = (float)Math.sqrt(x*x + y*y);
			kernel[i] = (float)Math.exp(-n*n / (2*sigma_2));
			sum += kernel[i];
		}
		// normalize
		for(int i = 0; i < kernel.length; i++)
			kernel[i] /= sum;

		return kernel;
	}
}

