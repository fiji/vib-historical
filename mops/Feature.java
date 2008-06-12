package mops;

import process3d.Smooth_;
import vib.InterpolatedImage;
import vib.FastMatrix;
import vib.BenesNamedPoint;
import Jama.EigenvalueDecomposition;
import Jama.Matrix;

import javax.vecmath.Point3d;

import ij.measure.Calibration;
import ij.gui.NewImage;

public class Feature extends BenesNamedPoint
{
	/*
	 * descriptor
	 */
	private float[] desc;
	public float[] getDesc(){ return desc; }

	// Just for debuggin
	public Point3d[] vertices = new Point3d[8];

	/*
	 * rotation matrix which aligns the patch in a way that
	 * the steepest gradient is in x-direction and the
	 * 2nd steepest is in y direction.
	 */
	private FastMatrix orientation = null;
	public FastMatrix getOrientation() { return orientation; }
	
	/**
	 * scale = sigma of the feature relative to the octave
	 */
	private double scale;
	public double getScale() {return scale;}

	Feature( double x, double y, double z, double scale, FastMatrix a, float[] desc )
	{
		super( "point", x, y, z );
		this.scale = scale;
		this.desc = desc;
		this.orientation = a;
	}
	
	public float descriptorDistance(Feature other) {
		float d = 0;
		for(int i = 0; i < desc.length; i++) {
			float a = desc[i] - other.desc[i];
			d += a * a;
		}
		return (float)Math.sqrt(d);
	}

	/*
	 * Creates an InterpolatedImage from the local descriptor
	 * of this feature.
	 */
	public InterpolatedImage extractDescriptor() {
		int fdWidth = (int)Math.round(Math.pow(desc.length, 1.0/3));
		InterpolatedImage ret = new InterpolatedImage(
			NewImage.createFloatImage(
				"descriptor", fdWidth, fdWidth, fdWidth, NewImage.FILL_BLACK));
		InterpolatedImage.Iterator it = ret.iterator();
		int i = 0;
		while(it.next() != null) {
			ret.setFloat(it.i, it.j, it.k, desc[i++]);
		}
		return ret;
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

