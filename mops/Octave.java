package mops;

import java.util.List;
import java.util.ArrayList;

import ij.ImagePlus;
import ij.IJ;
import ij.ImageStack;
import ij.gui.NewImage;
import ij.measure.Calibration;

import vib.InterpolatedImage;

public class Octave {

	int steps;
	
	float[] sigma;
	private float[] sigma_diff;
	private float[] smoothedSigma_diff;
	final float k;

	InterpolatedImage[] img;
	public InterpolatedImage[] getImg(){ return img; }
	
	InterpolatedImage[] dog;
	InterpolatedImage[] smoothed;
	
	final public int getWidth(){ return img[ 0 ].getWidth(); }
	final public int getHeight(){ return img[ 0 ].getHeight(); }
	final public int getDepth(){ return img[ 0 ].getDepth(); }

	public Octave( InterpolatedImage image, float[] sigma, float[] sigma_diff, float[] smoothedSigma_diff )
	{
		steps = sigma.length - 3;
		k = ( float )Math.pow( 2.0, 1.0 / ( steps ) );
		this.sigma = sigma;
		this.sigma_diff = sigma_diff;
		this.smoothedSigma_diff = smoothedSigma_diff;
		img = new InterpolatedImage[ sigma.length ];
		img[ 0 ] = image;
		img[ steps ] = Filter.gauss(img[ 0 ], sigma_diff[steps]);
	}
	
	public InterpolatedImage resample()
	{
		ImagePlus imp = NewImage.createFloatImage(
				"",
				getWidth() / 2 + getWidth() % 2,
				getHeight() / 2 + getHeight() % 2,
				getDepth() / 2 + getDepth() % 2,
				NewImage.FILL_BLACK );
		Calibration c = img[ 0 ].getImage().getCalibration().copy();
		c.pixelWidth *= 2;
		c.pixelHeight *= 2;
		c.pixelDepth *= 2;
		imp.setCalibration( c );
		InterpolatedImage tmp = new InterpolatedImage( imp );
		int w = tmp.getWidth(), h = tmp.getHeight(), d = tmp.getDepth();
		
		for ( int z = 0; z < d; z++ )
			for ( int y = 0; y < h; y++ )
				for ( int x = 0; x < w; x++)
					tmp.setFloat( x, y, z, img[ steps ].getNoCheckFloat(
						x*2, y*2, z*2 ) );

		return tmp;
	}

	public void dog() {
		int s = sigma_diff.length;
		dog = new InterpolatedImage[s - 1];
		smoothed = new InterpolatedImage[ s ];
		smoothed[ 0 ] = Filter.gauss(img[ 0 ], smoothedSigma_diff[0]);
		for(int i = 1; i < s; i++) {
			if(i != steps)
				img[ i ] = Filter.gauss(img[ 0 ], sigma_diff[i]);
			dog[ i - 1 ] = Filter.sub( img[ i ], img[ i - 1 ] );
			IJ.write("calculating dog[" + (i-1) + "]\n");
			smoothed[ i ] = Filter.gauss(img[ 0 ], smoothedSigma_diff[i]);
		}
	}

	public void clear()
	{
		this.dog = null;
		this.img = null;
		this.smoothed = null;
	}
}

