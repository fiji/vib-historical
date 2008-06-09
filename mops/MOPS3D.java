package mops;

/**
 * Multi-Scale Oriented Patches after \cite{BrownAl05} for 3d images.
 * 
 * This implementation actually uses the DoG-detector as described by
 * \cite{Lowe04}.
 *  
 * 
 * BibTeX:
 * <pre>
 * &#64;inproceedings{BrownAl05,
 *   author    = {Matthew Brown and Richard Szeliski and Simon Winder},
 *   title     = {Multi-Image Matching Using Multi-Scale Oriented Patches},
 *   booktitle = {CVPR '05: Proceedings of the 2005 IEEE Computer Society Conference on Computer Vision and Pattern Recognition (CVPR'05) - Volume 1},
 *   year      = {2005},
 *   isbn      = {0-7695-2372-2},
 *   pages     = {510--517},
 *   publisher = {IEEE Computer Society},
 *   address   = {Washington, DC, USA},
 *   doi       = {http://dx.doi.org/10.1109/CVPR.2005.235},
 *   url       = {http://www.cs.ubc.ca/~mbrown/papers/cvpr05.pdf},
 * }
 * &#64;article{Lowe04,
 *   author  = {David G. Lowe},
 *   title   = {Distinctive Image Features from Scale-Invariant Keypoints},
 *   journal = {International Journal of Computer Vision},
 *   year    = {2004},
 *   volume  = {60},
 *   number  = {2},
 *   pages   = {91--110},
 * }
 * </pre>
 * 
 * 
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * @author Stephan Saalfeld <saalfeld@mpi-cbg.de> and Benjamin Schmid <Bene.Schmid@gmail.com>
 * @version 0.1b
 */

import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import ij.measure.Calibration;

import java.util.Vector;
import java.util.List;
import java.util.HashMap;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;

import vib.InterpolatedImage;
import vib.FastMatrix;

public class MOPS3D
{
	final int O_SCALE = 5;
	final int O_SCALE_LD2 = 2;
	
	/**
	 * Width of the feature descriptor square in samples.
	 */
	private final int fdWidth;;
	
	/**
	 * octaved scale space
	 */
	private Octave[] octaves;
	public Octave[] getOctaves(){ return octaves; }
	public Octave getOctave( int i ){ return octaves[ i ]; }
	
	/**
	 * Constructor
	 * 
	 * @param feature_descriptor_size
	 */
	public MOPS3D(
			int fdWidth )
	{
		this.fdWidth = fdWidth; 
		octaves = null;
	}
	
	/**
	 * initialize the scale space as a scale pyramid having octave stubs only
	 * 
	 * @param src image having a generating gaussian kernel of initial_sigma
	 * 	 img must be a 2d-array of float values in range [0.0f, ..., 1.0f]
	 * @param steps gaussian smooth steps steps per scale octave
	 * @param initialSigma sigma of the generating gaussian kernel of img
	 * @param minSize minimal size of a scale octave in pixel
	 * @param maxSize maximal size of an octave to be taken into account
	 *   Use this to save memory and procesing time, if processing higher
	 *   resolutions is not necessary.
	 */
	public void init(
			InterpolatedImage src,
			int steps,
			float initialSigma,
			int minSize,
			int maxSize )
	{
		float[] sigma = new float[ steps + 3 ];
		sigma[ 0 ] = initialSigma;
		float[] sigmaDiff = new float[ steps + 3 ];
		sigmaDiff[ 0 ] = 0.0f;
		float[][] kernelDiff = new float[ steps + 3 ][];
		
		for ( int i = 1; i < steps + 3; ++i )
		{
			sigma[ i ] = initialSigma * ( float )Math.pow( 2.0f, ( float )i / ( float )steps );
			sigmaDiff[ i ] = ( float )Math.sqrt( sigma[ i ] * sigma[ i ] - initialSigma * initialSigma );
			
			//kernelDiff[ i ] = Filter.createGaussianKernel( sigmaDiff[ i ], true );
		}
		
		// estimate the number of octaves needed using a simple while loop instead of ld
		int o = 0;
		float w = ( float )src.getWidth();
		float h = ( float )src.getHeight();
		float d = ( float )src.getDepth();
		while ( w > minSize && h > minSize && d > minSize )
		{
			w /= 2.0f;
			h /= 2.0f;
			d /= 2.0f;
			++o;
		}
		octaves = new Octave[ o ];
		
		InterpolatedImage next;
		
		for ( int i = 0; i < octaves.length; ++i )
		{
			octaves[ i ] = new Octave(
					src,
					sigma,
					sigmaDiff );
			next = octaves[ i ].resample();
			if ( src.getWidth() > maxSize || src.getHeight() > maxSize || src.getDepth() > maxSize )
				octaves[ i ].clear();
			src = next;
		}
	}
	
	/**
	 * Sum up the gaussian weighted derivative in a sigma-dependent
	 * environment around the position of this feature. The result
	 * is normalized and stored in <code>orientation</code>.
	 *
	 * @param c location and scale of a feature candidate (x,y,z,si)
	 * @param smoothed is expected to be an image with sigma = 4.5 * {@link #sigma}
	 *   detected
	 */
	public FastMatrix extractOrientation(
			float[] f,
			float os = octaves[ 0 ].sigma[ 0 ] * ( float )Math.pow( 2.0f, f[ 3 ] / ( float )octaves[ 0 ].steps ),
			InterpolatedImage octaveStep,
			InterpolatedImage smoothed )
	{
		int ix = Math.round( f[ 0 ] );
		int iy = Math.round( f[ 1 ] );
		int iz = Math.round( f[ 2 ] );
		
		float v2 = 2 * smoothed.getNoCheck( ix, iy, iz );
		
		double[][] h = new double[ 3 ][ 3 ];
		
		h[ 0 ][ 0 ] =
			smoothed.getNoCheck( ix + 1, iy, iz ) -
			v2 +
			smoothed.getNoCheck( ix - 1, iy, iz );
		h[ 1 ][ 1 ] =
			smoothed.getNoCheck( ix, iy + 1, iz ) -
			v2 +
			smoothed.getNoCheck( ix, iy - 1, iz );
		h[ 2 ][ 2 ] =
			smoothed.getNoCheck( ix, iy, iz + 1 ) -
			v2 +
			smoothed.getNoCheck( ix, iy, iz - 1 );
		
		h[ 0 ][ 1 ] = h[ 1 ][ 0 ] =
			( smoothed.getNoCheck( ix + 1, iy + 1, iz ) -
			  smoothed.getNoCheck( ix - 1, iy + 1, iz ) ) / 4 -
			( smoothed.getNoCheck( ix + 1, iy - 1, iz ) -
			  smoothed.getNoCheck( ix - 1, iy - 1, iz ) ) / 4;
		h[ 0 ][ 2 ] = h[ 2 ][ 0 ] =
			( smoothed.getNoCheck( ix + 1, iy, iz + 1 ) -
			  smoothed.getNoCheck( ix - 1, iy, iz + 1 ) ) / 4 -
			( smoothed.getNoCheck( ix + 1, iy, iz - 1 ) -
			  smoothed.getNoCheck( ix - 1, iy, iz - 1 ) ) / 4;
		h[ 1 ][ 2 ] = h[ 2 ][ 1 ] =
			( smoothed.getNoCheck( ix, iy + 1, iz + 1 ) -
			  smoothed.getNoCheck( ix, iy - 1, iz + 1 ) ) / 4 -
			( smoothed.getNoCheck( ix, iy + 1, iz - 1 ) -
			  smoothed.getNoCheck( ix, iy - 1, iz - 1 ) ) / 4;
		
		EigenvalueDecomposition evd =
			new EigenvalueDecomposition( new Matrix( h ) );
		
		double[] ev = evd.getRealEigenvalues();
		double[][] evect = evd.getV().getArray();
		
		
		// Sort the eigenvalues by ascending size.
		int i0 = 0;
		int i1 = 1;
		int i2 = 2;
		
		ev[ 0 ] = Math.abs( ev[ 0 ] );
		ev[ 1 ] = Math.abs( ev[ 1 ] );
		ev[ 2 ] = Math.abs( ev[ 2 ] );
		
		if ( ev[ i1 ] < ev[ i0 ] )
		{
			int temp = i0;
			i0 = i1;
			i1 = temp;
		}
		if ( ev[ i2 ] < ev[ i1 ] )
		{
			int temp = i1;
			i1 = i2;
			i2 = temp;
			if ( ev[ i1 ] < ev[ i0 ] )
			{
				temp = i0;
				i0 = i1;
				i1 = temp;
			}
		}
		
		evect = new double[][]{
				{ evect[ 0 ][ i2 ], evect[ 0 ][ i1 ], evect[ 0 ][ i0 ] },
				{ evect[ 1 ][ i2 ], evect[ 1 ][ i1 ], evect[ 1 ][ i0 ] },
				{ evect[ 2 ][ i2 ], evect[ 2 ][ i1 ], evect[ 2 ][ i0 ] } };
		ev = new double[]{ ev[ i2 ], ev[ i1 ], ev[ i0 ] }; 
		
		float[][][] gauss_k = Filter.create3DGaussianKernelOffset( os, new float[]{ f[ 0 ] - ix, f[ 1 ] - iy, f[ 2 ] - iz }, true );
		
		int r = gauss_k.length / 2;
		
		float[] alpha = new float[ 2 ];
		//float[] beta = new float[ 2 ];
		float[] gamma = new float[ 2 ];
		
		for ( int z = 0; z < gauss_k.length; z++ ) {
			int lz = iz - r + z;
			for ( int y = 0; y < gauss_k.length; y++ ) {
				int ly = iy - r + y;
				for ( int x = 0; x < gauss_k.length; x++ ) {
					int lx = ix - r + x;
					
					float vx = ( float )lx - f[ 0 ];
					float vy = ( float )ly - f[ 1 ];
					float vz = ( float )lz - f[ 2 ];
					
					int sAlpha = ( ( vx * evect[ 0 ][ 0 ] + vy * evect[ 1 ][ 0 ] + vz * evect[ 2 ][ 0 ] ) < 0 ) ? 0 : 1;
					//int sBeta = ( ( vx * evect[ 0 ][ 1 ] + vy * evect[ 1 ][ 1 ] + vz * evect[ 2 ][ 1 ] ) < 0 ) ? 0 : 1;
					int sGamma = ( ( vx * evect[ 0 ][ 2 ] + vy * evect[ 1 ][ 2 ] + vz * evect[ 2 ][ 2 ] ) < 0 ) ? 0 : 1;
					
					float val = octaveStep.getNoInterpolPingPongFloat( lx, ly, lz ) * gauss_k[ z ][ y ][ x ];
					alpha[ sAlpha ] += val;
					//alpha[ sBeta ] += val;
					alpha[ sGamma ] += val;
				}
			}
		}
		
		if ( alpha[ 0 ] < alpha[ 1 ] )
		{
			evect[ 0 ][ 0 ] = -evect[ 0 ][ 0 ];
			evect[ 1 ][ 0 ] = -evect[ 1 ][ 0 ];
			evect[ 2 ][ 0 ] = -evect[ 2 ][ 0 ];
		}
		if ( gamma[ 0 ] < gamma[ 1 ] )
		{
			evect[ 0 ][ 2 ] = -evect[ 0 ][ 2 ];
			evect[ 1 ][ 2 ] = -evect[ 1 ][ 2 ];
			evect[ 2 ][ 2 ] = -evect[ 2 ][ 2 ];
		}
		
		float lAlpha = ( float )Math.sqrt( evect[ 0 ][ 0 ] * evect[ 0 ][ 0 ] + evect[ 1 ][ 0 ] * evect[ 1 ][ 0 ] + evect[ 2 ][ 0 ] * evect[ 2 ][ 0 ] );
		float lGamma = ( float )Math.sqrt( evect[ 0 ][ 2 ] * evect[ 0 ][ 2 ] + evect[ 1 ][ 2 ] * evect[ 1 ][ 2 ] + evect[ 2 ][ 2 ] * evect[ 2 ][ 2 ] );
		
		evect[ 0 ][ 0 ] /= lAlpha;
		evect[ 1 ][ 0 ] /= lAlpha;
		evect[ 2 ][ 0 ] /= lAlpha;
		
		evect[ 0 ][ 2 ] /= lGamma;
		evect[ 1 ][ 2 ] /= lGamma;
		evect[ 2 ][ 2 ] /= lGamma;
		
		evect[ 0 ][ 1 ] = evect[ 1 ][ 0 ] * evect[ 2 ][ 2 ] - evect[ 2 ][ 0 ] * evect[ 1 ][ 2 ];
		evect[ 1 ][ 1 ] = evect[ 2 ][ 0 ] * evect[ 0 ][ 2 ] - evect[ 0 ][ 0 ] * evect[ 2 ][ 2 ];
		evect[ 2 ][ 1 ] = evect[ 0 ][ 0 ] * evect[ 1 ][ 2 ] - evect[ 1 ][ 0 ] * evect[ 0 ][ 2 ];
		
		return new FastMatrix(evect);
	}
	
	/**
	 * sample the scaled and rotated gradients in a region around the
	 * features location, the regions size is defined by
	 * FEATURE_DESCRIPTOR_WIDTH^2
	 * 
	 * @param c candidate 0=>x, 1=>y, 2=>scale index
	 * @param o octave index
	 * @param octave_sigma sigma of the corresponding gaussian kernel with
	 *   respect to the scale octave
	 * @param orientation orientation [-&pi; ... &pi;]
	 */
	private float[] createDescriptor(
			float[] f,
			float os,
			InterpolatedImage smoothed,
			FastMatrix o )
	{
		float[] desc = new float[ fdWidth * fdWidth * fdWidth ];
		
		// transpose o for inverse rotation
		o.transpose3x3();
		
		int i = 0;
		float max = Float.MIN_VALUE;
		float min = Float.MAX_VALUE;
		
		//! sample the region arround the keypoint location
		for ( int z = 0; z < fdWidth; ++z )
		{
			float zs =
				( ( float )z - ( float )fdWidth / 2.0f + 0.5f ) * os * O_SCALE; //!< scale z around 0,0
			for ( int y = 0; y < fdWidth; ++y )
			{
				float ys =
					( ( float )y - ( float )fdWidth / 2.0f + 0.5f ) * os * O_SCALE; //!< scale y around 0,0
				for ( int x = 0; x < fdWidth; ++x )
				{
					float xs =
						( ( float )x - ( float )fdWidth / 2.0f + 0.5f ) * os * O_SCALE; //!< scale x around 0,0

					o.apply( xs, ys, zs );
					
					// translate
					int zg = ( int )( Math.round( o.z + f[ 2 ] ) );
					int yg = ( int )( Math.round( o.y + f[ 1 ] ) );
					int xg = ( int )( Math.round( o.x + f[ 0 ] ) );
	
					desc[ i ] = smoothed.getNoInterpolPingPongFloat( xg, yg, zg );
					
					if ( desc[ i ] > max ) max = desc[ i ];
					else if ( desc[ i ] < min ) min = desc[ i ];
					++i;
				}
			}
		}
		
		// normalize
		float n = max - min;
		for ( i = 0; i < desc.length; ++i )
			desc[ i ] = ( desc[ i ] - min ) / n;
		
		return desc;
	}
	
	/**
	 * assign orientation to the given candidate, if more than one orientations
	 * found, duplicate the feature for each orientation
	 * 
	 * estimate the feature descriptor for each of those candidates
	 * 
	 * @param c candidate 0=>x, 1=>y, 2=>scale index
	 * @param o octave index
	 * @param features finally contains all processed candidates
	 */
	void processCandidate(
			float[] c,
			int o,
			Vector< Feature > features )
	{
		final int ORIENTATION_BINS = 36;
		final float ORIENTATION_BIN_SIZE = 2.0f * ( float )Math.PI / ( float )ORIENTATION_BINS;
		float[] histogram_bins = new float[ ORIENTATION_BINS ];
		
		int scale = ( int )Math.pow( 2, o );
		
		FloatArray2DScaleOctave octave = octaves[ o ];
		
		float octave_sigma = octave.SIGMA[ 0 ] * ( float )Math.pow( 2.0f, c[ 2 ] / ( float )octave.STEPS );
				
		// create a circular gaussian window with sigma 1.5 times that of the feature
		FloatArray2D gaussianMask =
			Filter.createGaussianKernelOffset(
					octave_sigma * 1.5f,
					c[ 0 ] - ( float )Math.floor( c[ 0 ] ),
					c[ 1 ] - ( float )Math.floor( c[ 1 ] ),
					false );
		//FloatArrayToImagePlus( gaussianMask, "gaussianMask", 0, 0 ).show();
		
		// get the gradients in a region arround the keypoints location
		FloatArray2D[] src = octave.getL1( Math.round( c[ 2 ] ) );
		FloatArray2D[] gradientROI = new FloatArray2D[ 2 ];
		gradientROI[ 0 ] = new FloatArray2D( gaussianMask.width, gaussianMask.width );
		gradientROI[ 1 ] = new FloatArray2D( gaussianMask.width, gaussianMask.width );
		
		int half_size = gaussianMask.width / 2;
		int p = gaussianMask.width * gaussianMask.width - 1;
		for ( int yi = gaussianMask.width - 1; yi >= 0; --yi )
		{
			int ra_y = src[ 0 ].width * Math.max( 0, Math.min( src[ 0 ].height - 1, ( int )c[ 1 ] + yi - half_size ) );
			int ra_x = ra_y + Math.min( ( int )c[ 0 ], src[ 0 ].width - 1 );

			for ( int xi = gaussianMask.width - 1; xi >= 0; --xi )
			{
				int pt = Math.max( ra_y, Math.min( ra_y + src[ 0 ].width - 2, ra_x + xi - half_size ) );
				gradientROI[ 0 ].data[ p ] = src[ 0 ].data[ pt ];
				gradientROI[ 1 ].data[ p ] = src[ 1 ].data[ pt ];
				--p;
			}
		}
		
		// and mask this region with the precalculated gaussion window
		for ( int i = 0; i < gradientROI[ 0 ].data.length; ++i )
		{
			gradientROI[ 0 ].data[ i ] *= gaussianMask.data[ i ];
		}
		
		// TODO this is for test
		//---------------------------------------------------------------------
		//ImageArrayConverter.FloatArrayToImagePlus( gradientROI[ 0 ], "gaussianMaskedGradientROI", 0, 0 ).show();
		//ImageArrayConverter.FloatArrayToImagePlus( gradientROI[ 1 ], "gaussianMaskedGradientROI", 0, 0 ).show();

		// build an orientation histogram of the region
		for ( int i = 0; i < gradientROI[ 0 ].data.length; ++i )
		{
			int bin = Math.max( 0, ( int )( ( gradientROI[ 1 ].data[ i ] + Math.PI ) / ORIENTATION_BIN_SIZE ) );
			histogram_bins[ bin ] += gradientROI[ 0 ].data[ i ];
		}

		// find the dominant orientation and interpolate it with respect to its two neighbours
		int max_i = 0;
		for ( int i = 0; i < ORIENTATION_BINS; ++i )
		{
			if ( histogram_bins[ i ] > histogram_bins[ max_i ] ) max_i = i;
		}
		
		/*
		 * Interpolate orientation.
		 * Estimate the offset from center of the
		 * parabolic extremum of the taylor series through env[1], derivatives
		 * via central difference and laplace.
		 */
		float e0 = histogram_bins[ ( max_i + ORIENTATION_BINS - 1 ) % ORIENTATION_BINS ];
		float e1 = histogram_bins[ max_i ];
		float e2 = histogram_bins[ ( max_i + 1 ) % ORIENTATION_BINS ];
		float offset = ( e0 - e2 ) / 2.0f / ( e0 - 2.0f * e1 + e2 );
		float orientation = ( ( float )max_i + offset ) * ORIENTATION_BIN_SIZE - ( float )Math.PI;

		// assign descriptor and add the Feature instance to the collection
		features.addElement(
				new Feature(
						octave_sigma * scale,
						orientation,
						new float[]{ c[ 0 ] * scale, c[ 1 ] * scale },
						//new float[]{ ( c[ 0 ] + 0.5f ) * scale - 0.5f, ( c[ 1 ] + 0.5f ) * scale - 0.5f },
						createDescriptor( c, o, octave_sigma, orientation ) ) );
		
		// TODO this is for test
		//---------------------------------------------------------------------
		//ImageArrayConverter.FloatArrayToImagePlus( pattern, "test", 0f, 1.0f ).show();
		
		/**
		 * check if there is another significant orientation ( > 80% max )
		 * if there is one, duplicate the feature and 
		 */
		for ( int i = 0; i < ORIENTATION_BINS; ++i )
		{
			if (
					i != max_i &&
					( max_i + 1 ) % ORIENTATION_BINS != i &&
					( max_i - 1 + ORIENTATION_BINS ) % ORIENTATION_BINS != i &&
					histogram_bins[ i ] > 0.8 * histogram_bins[ max_i ] )
			{
				/**
				 * interpolate orientation estimate the offset from center of
				 * the parabolic extremum of the taylor series through env[1],
				 * derivatives via central difference and laplace
				 */
				e0 = histogram_bins[ ( i + ORIENTATION_BINS - 1 ) % ORIENTATION_BINS ];
				e1 = histogram_bins[ i ];
				e2 = histogram_bins[ ( i + 1 ) % ORIENTATION_BINS ];

				if ( e0 < e1 && e2 < e1 )
				{
					offset = ( e0 - e2 ) / 2.0f / ( e0 - 2.0f * e1 + e2 );
					orientation = ( ( float )i + 0.5f + offset ) * ORIENTATION_BIN_SIZE - ( float )Math.PI;

					features.addElement(
							new Feature(
									octave_sigma * scale,
									orientation,
									new float[]{ c[ 0 ] * scale, c[ 1 ] * scale },
									//new float[]{ ( c[ 0 ] + 0.5f ) * scale - 0.5f, ( c[ 1 ] + 0.5f ) * scale - 0.5f },
									createDescriptor( c, o, octave_sigma, orientation ) ) );
					
					// TODO this is for test
					//---------------------------------------------------------------------
					//ImageArrayConverter.FloatArrayToImagePlus( pattern, "test", 0f, 1.0f ).show();
				}
			}
		}
		return;
	}

	float orientation
	/**
	 * detect features in the specified scale octave
	 * 
	 * @param o octave index
	 * 
	 * @return detected features
	 */
	public Vector< Feature > runOctave( int o )
	{
		Vector< Feature > features = new Vector< Feature >();
		Octave octave = octaves[ o ];
		octave.build();
		dog.run( octave );
		Vector< float[] > candidates = dog.getCandidates();
		for ( float[] c : candidates )
		{
			this.processCandidate( c, o, features );
		}
		//System.out.println( features.size() + " candidates processed in octave " + o );
		
		return features;
	}
	
	/**
	 * Detect features in all scale octaves.
	 * 
	 * Note that there are O_SCALE_LD2 more octaves needed for descriptor extraction. 
	 * 
	 * @return detected features
	 */
	public Vector< Feature > run()
	{
		Vector< Feature > features = new Vector< Feature >();
		for ( int o = 0; o < octaves.length; ++o )
		{
			if ( octaves[ o ].state == FloatArray2DScaleOctave.State.EMPTY ) continue;
			octaves[ o ].build();
		}
		for ( int o = 0; o < octaves.length - O_SCALE_LD2; ++o )
		{
			if ( octaves[ o ].state == FloatArray2DScaleOctave.State.EMPTY ) continue;
			Vector< Feature > more = runOctave( o );
			features.addAll( more );
		}
		return features;
	}
	
	/**
	 * Detect features in all scale octaves.
	 * 
	 * Note that there are O_SCALE_LD2 more octaves needed for descriptor extraction. 
	 * 
	 * @return detected features
	 */
	public Vector< Feature > run( int max_size )
	{
		Vector< Feature > features = new Vector< Feature >();
		for ( int o = 0; o < octaves.length; ++o )
			if ( octaves[ o ].width <= max_size && octaves[ o ].height <= max_size )
				octaves[ o ].build();
		for ( int o = 0; o < octaves.length - O_SCALE_LD2; ++o )
			if ( octaves[ o ].width <= max_size && octaves[ o ].height <= max_size )
			{
				Vector< Feature > more = runOctave( o );
				features.addAll( more );
			}
		
		//System.out.println( features.size() + " candidates processed in all octaves" );
		return features;
	}
	
	/**
	 * Identify corresponding features
	 * 
	 * @param fs1 feature collection from set 1
	 * @param fs2 feature collection from set 2
	 * 
	 * @return matches
	 */
	public static Vector< PointMatch > createMatches(
			List< Feature > fs1,
			List< Feature > fs2 )
	{
		Vector< PointMatch > matches = new Vector< PointMatch >();
		
		for ( Feature f1 : fs1 )
		{
			Feature best = null;
			float best_d = Float.MAX_VALUE;
			float second_best_d = Float.MAX_VALUE;
			
			for ( Feature f2 : fs2 )
			{
				float d = f1.descriptorDistance( f2 );
				//System.out.println( d );
				if ( d < best_d )float orientation
				{
					second_best_d = best_d;
					best_d = d;
					best = f2;
				}
				else if ( d < second_best_d )
					second_best_d = d;
			}
			//if ( best != null && second_best_d < Float.MAX_VALUE && best_d / second_best_d < 0.92 )
			if ( best != null )
				matches.addElement(
						new PointMatch(
								new Point(
										new float[] { f1.location[ 0 ], f1.location[ 1 ] } ),
								new Point(
										new float[] { best.location[ 0 ], best.location[ 1 ] } ),
								( f1.scale + best.scale ) / 2.0f ) );
			else
				System.out.println( "No match found." );
		}
		
		// now remove ambiguous matches
		for ( int i = 0; i < matches.size(); )
		{
			boolean amb = false;
			PointMatch m = matches.get( i );
			float[] m_p2 = m.getP2().getL(); 
			for ( int j = i + 1; j < matches.size(float orientation); )
			{
				PointMatch n = matches.get( j );
				float[] n_p2 = n.getP2().getL(); 
				if ( m_p2[ 0 ] == n_p2[ 0 ] && m_p2[ 1 ] == n_p2[ 1 ] )
				{
					amb = true;
					matches.removeElementAt( j );
				}
				else ++j;
			}
			if ( amb )
				matches.removeElementAt( i );
			else ++i;
		}
		return matches;
	}
	
	/**
	 * Identify corresponding features.
	 * Fill a HashMap that stores the Features for each positive PointMatch.
	 * 
	 * @param fs1 feature collection from set 1
	 * @param fs2 feature collection from set 2
	 * 
	 * @return matches
	 */
	public static Vector< PointMatch > createMatches(
			List< Feature > fs1,
			List< Feature > fs2,
			HashMap< Point, Feature > m1,
			HashMap< Point, Feature > m2 )
	{
		Vector< PointMatch > matches = new Vector< PointMatch >();
		
		for ( Feature f1 : fs1 )
		{
			Feature best = null;
			float best_d = Float.MAX_VALUE;
			float second_best_d = Float.MAX_VALUE;
			
			for ( Feature f2 : fs2 )
			{
				float d = f1.descriptorDistance( f2 );
				//System.out.println( d );
				if ( d < best_d )
				{
					second_best_d = best_d;
					best_d = d;
					best = f2;
				}
				else if ( d < second_best_d )
					second_best_d = d;
			}
			//if ( best != null && second_best_d < Float.MAX_VALUE && best_d / second_best_d < 0.92 )
			if ( best != null )
			{
				Point p1 = new Point( new float[] { f1.location[ 0 ], f1.location[ 1 ] } );
				Point p2 = new Point( new float[] { best.location[ 0 ], best.location[ 1 ] } );
						
				matches.addElement(	new PointMatch( p1,	p2,	( f1.scale + best.scale ) / 2.0f ) );
				
				m1.put( p1, f1 );
				m2.put( p2, best );
			}
			else
				System.out.println( "No match found." );
		}
		
		// now remove ambiguous matches
		for ( int i = 0; i < matches.size(); )
		{
			boolean amb = false;
			PointMatch m = matches.get( i );
			float[] m_p2 = m.getP2().getL(); 
			for ( int j = i + 1; j < matches.size(); )
			{
				PointMatch n = matches.get( j );
				float[] n_p2 = n.getP2().getL(); 
				if ( m_p2[ 0 ] == n_p2[ 0 ] && m_p2[ 1 ] == n_p2[ 1 ] )
				{
					m1.remove( n.getP1() );
					m2.remove( n.getP2() );
					amb = true;
					matches.removeElementAt( j );
				}
				else ++j;
			}
			if ( amb )
			{
				matches.removeElementAt( i );
				m1.remove( m.getP1() );
				m2.remove( m.getP2() );
			}
			else ++i;
		}
		return matches;
	}
	
	
	/**
	 * identify corresponding features using spatial constraints
	 * 
	 * @param fs1 feature collection from set 1 sorted by decreasing size
	 * @param fs2 feature collection from set 2 sorted by decreasing size
	 * @param max_sd maximal difference in size (ratio max/min)
	 * @param model transformation model to be applied to fs2
	 * @param max_id maximal distance in image space ($\sqrt{x^2+y^2}$)
	 * 
	 * @return matches
	 * 
	 * TODO implement the spatial constraints
	 */
	public static Vector< PointMatch > createMatches(
			List< Feature > fs1,
			List< Feature > fs2,
			float max_sd,
			Model model,
			float max_id )
	{
		Vector< PointMatch > matches = new Vector< PointMatch >();
		float min_sd = 1.0f / max_sd;
		
		int size = fs2.size();
		int size_1 = size - 1;
		
		for ( Feature f1 : fs1 )
		{
			Feature best = null;
			float best_d = Float.MAX_VALUE;
			float second_best_d = Float.MAX_VALUE;
			
			int first = 0;
			int last = size_1;
			int s = size / 2 + size % 2;
			if ( max_sd < Float.MAX_VALUE )
			{
				while ( s > 1 )
				{
					Feature f2 = fs2.get( last );
					if ( f2.scale / f1.scale < min_sd ) last = Math.max( 0, last - s );
					else last = Math.min( size_1, last + s );
					f2 = fs2.get( first );
					if ( f2.scale / f1.scale < max_sd ) first = Math.max( 0, first - s );
					else first = Math.min( size_1, first + s );
					s = s / 2 + s % 2;
				}
				//System.out.println( "first = " + first + ", last = " + last + ", first.scale = " + fs2.get( first ).scale + ", last.scale = " + fs2.get( last ).scale + ", this.scale = " + f1.scale );
			}
			
			//for ( Feature f2 : fs2 )
			
			for ( int i = first; i <= last; ++i )
			{
				Feature f2 = fs2.get( i );
				float d = f1.descriptorDistance( f2 );
				if ( d < best_d )
				{
					second_best_d = best_d;
					best_d = d;
					best = f2;
				}
				else if ( d < second_best_d )
					second_best_d = d;
			}
			if ( best != null && second_best_d < Float.MAX_VALUE && best_d / second_best_d < 0.92 )
				// not weighted
//				matches.addElement(
//						new PointMatch(
//								new Point(
//										new float[] { f1.location[ 0 ], f1.location[ 1 ] } ),
//								new Point(
//										new float[] { best.location[ 0 ], best.location[ 1 ] } ) ) );
				// weighted with the features scale
				matches.addElement(
						new PointMatch(
								new Point(
										new float[] { f1.location[ 0 ], f1.location[ 1 ] } ),
								new Point(
										new float[] { best.location[ 0 ], best.location[ 1 ] } ),
								( f1.scale + best.scale ) / 2.0f ) );
		}
		// now remove ambiguous matches
		for ( int i = 0; i < matches.size(); )
		{
			boolean amb = false;
			PointMatch m = matches.get( i );
			float[] m_p2 = m.getP2().getL(); 
			for ( int j = i + 1; j < matches.size(); )
			{
				PointMatch n = matches.get( j );
				float[] n_p2 = n.getP2().getL(); 
				if ( m_p2[ 0 ] == n_p2[ 0 ] && m_p2[ 1 ] == n_p2[ 1 ] )
				{
					amb = true;
					//System.out.println( "removing ambiguous match at " + j );
					matches.removeElementAt( j );
				}
				else ++j;
			}
			if ( amb )
			{
				//System.out.println( "removing ambiguous match at " + i );
				matches.removeElementAt( i );
			}
			else ++i;
		}
		return matches;
	}
	
	/**
	 * get a histogram of feature sizes
	 * 
	 * @param rs
	 */
	public static float[] featureSizeHistogram(
			Vector< Feature > features,
			float min,
			float max,
			int bins )
	{
		System.out.print( "estimating feature size histogram ..." );
		int num_features = features.size();
		float h[] = new float[ bins ];
		int hb[] = new int[ bins ];
		
		for ( Feature f : features )
		{
			int bin = ( int )Math.max( 0, Math.min( bins - 1, ( int )( Math.log( f.scale ) / Math.log( 2.0 ) * 28.0f ) ) );
			++hb[ bin ];
		}
		for ( int i = 0; i < bins; ++i )
		{
			h[ i ] = ( float )hb[ i ] / ( float )num_features;
		}
		System.out.println( " done" );
		return h;
	}
}
