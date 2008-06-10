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
import java.util.ArrayList;
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
	 * Difference of Gaussian detector
	 */
	private DoGDetector3D dog;
	
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
		dog = new DoGDetector3D();
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
		float[] smoothedSigmaDiff = new float[ steps + 3 ];
		smoothedSigmaDiff[ 0 ] = ( float )Math.sqrt( O_SCALE * initialSigma * O_SCALE * initialSigma - initialSigma * initialSigma );
		float[][] kernelDiff = new float[ steps + 3 ][];
		
		for ( int i = 1; i < steps + 3; ++i )
		{
			sigma[ i ] = initialSigma * ( float )Math.pow( 2.0f, ( float )i / ( float )steps );
			sigmaDiff[ i ] = ( float )Math.sqrt( sigma[ i ] * sigma[ i ] - initialSigma * initialSigma );
			smoothedSigmaDiff[ i ] = ( float )Math.sqrt( O_SCALE * sigma[ i ] * O_SCALE * sigma[ i ] - initialSigma * initialSigma );
			
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
					sigmaDiff,
					smoothedSigmaDiff);
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
			float os,
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
	 * Illustration of the 2D case, which transfers to the 3D case:
	 *
	 *    patch after                     patch before
	 *    rotation:                       rotation
	 *
	 *   +----------+        M^-1         +---..
	 *   |          |    ------------>   /      ---..
	 *   |     +----|    <------------  /            -+
	 *   |          |         M        /      +..     /
	 *   +----------+                 +---..     --. /
	 *                                      ---..   /
	 *                                           -+
	 *  
	 *  One wants to get the coordinates (and values) of the patch
	 *  before rotation - those after the rotation are known, since
	 *  mid point and edge length are known.
	 *
	 *  So each point coordinate - as is after rotation - is transformed
	 *  by M^-1 (M being a rotational matrix).
	 *
	 *  The angle between the axes can be obtained by 
	 *  the rotation matrix M by the axis-angle definition.
	 *  
	 * 
	 * @param c candidate 0=>x, 1=>y, 2=>scale index
	 * @param a octave index
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
		FastMatrix a = new FastMatrix( o );
		a.transpose3x3();
		
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

					a.apply( xs, ys, zs );
					
					// translate
					int zg = ( int )( Math.round( a.z + f[ 2 ] ) );
					int yg = ( int )( Math.round( a.y + f[ 1 ] ) );
					int xg = ( int )( Math.round( a.x + f[ 0 ] ) );
	
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
	 * detect features in the specified scale octave
	 * 
	 * @param oi octave index
	 * 
	 * @return detected features
	 */
	public List< Feature > runOctave( int oi )
	{
		int octScale = ( int )Math.pow( 2, oi );
		ArrayList< Feature > features = new ArrayList< Feature >();
		Octave octave = octaves[ oi ];
		dog.run( octave );
		ArrayList< float[] > candidates = dog.getCandidates();
		for ( float[] f : candidates )
		{
			float os = octave.sigma[ 0 ] * ( float )Math.pow( 2.0f, f[ 3 ] / ( float )octave.steps );
			FastMatrix o = extractOrientation(
					f,
					os,
					octave.img[ Math.round( f[ 3 ] ) ],
					octave.smoothed[ Math.round( f[ 3 ] ) ] );
			float desc[] = createDescriptor(
					f,
					os,
					octave.smoothed[ Math.round( f[ 3 ] ) ],
					o );
			features.add(
					new Feature( f[ 0 ] * octScale, f[ 1 ] * octScale, f[ 2 ] * octScale, os * octScale, o, desc ) );
		}
		
		System.out.println( features.size() + " MOPSes extracted in octave " + oi );
		
		return features;
	}
	
	/**
	 * Detect features in all scale octaves.
	 * 
	 * Note that there are O_SCALE_LD2 more octaves needed for descriptor extraction. 
	 * 
	 * @return detected features
	 */
	public List< Feature > run()
	{
		ArrayList< Feature > features = new ArrayList< Feature >();
		for ( int oi = 0; oi < octaves.length; ++oi )
		{
			if ( octaves[ oi ].img == null ) continue;
			List< Feature > more = runOctave( oi );
			features.addAll( more );
			
			// free memory for processing the next octave
			octaves[ oi ].clear();
			
			IJ.showProgress( oi, octaves.length );
		}
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
//	public static List< PointMatch > createMatches(
//			List< Feature > fs1,
//			List< Feature > fs2 )
//	{
//		ArrayList< PointMatch > matches = new ArrayList< PointMatch >();
//		
//		for ( Feature f1 : fs1 )
//		{
//			Feature best = null;
//			float best_d = Float.MAX_VALUE;
//			float second_best_d = Float.MAX_VALUE;
//			
//			for ( Feature f2 : fs2 )
//			{
//				float d = f1.descriptorDistance( f2 );
//				//System.out.println( d );
//				if ( d < best_d )float orientation
//				{
//					second_best_d = best_d;
//					best_d = d;
//					best = f2;
//				}
//				else if ( d < second_best_d )
//					second_best_d = d;
//			}
//			//if ( best != null && second_best_d < Float.MAX_VALUE && best_d / second_best_d < 0.92 )
//			if ( best != null )
//				matches.addElement(
//						new PointMatch(
//								new Point(
//										new float[] { f1.location[ 0 ], f1.location[ 1 ] } ),
//								new Point(
//										new float[] { best.location[ 0 ], best.location[ 1 ] } ),
//								( f1.scale + best.scale ) / 2.0f ) );
//			else
//				System.out.println( "No match found." );
//		}
//		
//		// now remove ambiguous matches
//		for ( int i = 0; i < matches.size(); )
//		{
//			boolean amb = false;
//			PointMatch m = matches.get( i );
//			float[] m_p2 = m.getP2().getL(); 
//			for ( int j = i + 1; j < matches.size(float orientation); )
//			{
//				PointMatch n = matches.get( j );
//				float[] n_p2 = n.getP2().getL(); 
//				if ( m_p2[ 0 ] == n_p2[ 0 ] && m_p2[ 1 ] == n_p2[ 1 ] )
//				{
//					amb = true;
//					matches.removeElementAt( j );
//				}
//				else ++j;
//			}
//			if ( amb )
//				matches.removeElementAt( i );
//			else ++i;
//		}
//		return matches;
//	}
//	
//	/**
//	 * Identify corresponding features.
//	 * Fill a HashMap that stores the Features for each positive PointMatch.
//	 * 
//	 * @param fs1 feature collection from set 1
//	 * @param fs2 feature collection from set 2
//	 * 
//	 * @return matches
//	 */
//	public static Vector< PointMatch > createMatches(
//			List< Feature > fs1,
//			List< Feature > fs2,
//			HashMap< Point, Feature > m1,
//			HashMap< Point, Feature > m2 )
//	{
//		Vector< PointMatch > matches = new Vector< PointMatch >();
//		
//		for ( Feature f1 : fs1 )
//		{
//			Feature best = null;
//			float best_d = Float.MAX_VALUE;
//			float second_best_d = Float.MAX_VALUE;
//			
//			for ( Feature f2 : fs2 )
//			{
//				float d = f1.descriptorDistance( f2 );
//				//System.out.println( d );
//				if ( d < best_d )
//				{
//					second_best_d = best_d;
//					best_d = d;
//					best = f2;
//				}
//				else if ( d < second_best_d )
//					second_best_d = d;
//			}
//			//if ( best != null && second_best_d < Float.MAX_VALUE && best_d / second_best_d < 0.92 )
//			if ( best != null )
//			{
//				Point p1 = new Point( new float[] { f1.location[ 0 ], f1.location[ 1 ] } );
//				Point p2 = new Point( new float[] { best.location[ 0 ], best.location[ 1 ] } );
//						
//				matches.addElement(	new PointMatch( p1,	p2,	( f1.scale + best.scale ) / 2.0f ) );
//				
//				m1.put( p1, f1 );
//				m2.put( p2, best );
//			}
//			else
//				System.out.println( "No match found." );
//		}
//		
//		// now remove ambiguous matches
//		for ( int i = 0; i < matches.size(); )
//		{
//			boolean amb = false;
//			PointMatch m = matches.get( i );
//			float[] m_p2 = m.getP2().getL(); 
//			for ( int j = i + 1; j < matches.size(); )
//			{
//				PointMatch n = matches.get( j );
//				float[] n_p2 = n.getP2().getL(); 
//				if ( m_p2[ 0 ] == n_p2[ 0 ] && m_p2[ 1 ] == n_p2[ 1 ] )
//				{
//					m1.remove( n.getP1() );
//					m2.remove( n.getP2() );
//					amb = true;
//					matches.removeElementAt( j );
//				}
//				else ++j;
//			}
//			if ( amb )
//			{
//				matches.removeElementAt( i );
//				m1.remove( m.getP1() );
//				m2.remove( m.getP2() );
//			}
//			else ++i;
//		}
//		return matches;
//	}
//	
//	
//	/**
//	 * identify corresponding features using spatial constraints
//	 * 
//	 * @param fs1 feature collection from set 1 sorted by decreasing size
//	 * @param fs2 feature collection from set 2 sorted by decreasing size
//	 * @param max_sd maximal difference in size (ratio max/min)
//	 * @param model transformation model to be applied to fs2
//	 * @param max_id maximal distance in image space ($\sqrt{x^2+y^2}$)
//	 * 
//	 * @return matches
//	 * 
//	 * TODO implement the spatial constraints
//	 */
//	public static Vector< PointMatch > createMatches(
//			List< Feature > fs1,
//			List< Feature > fs2,
//			float max_sd,
//			Model model,
//			float max_id )
//	{
//		Vector< PointMatch > matches = new Vector< PointMatch >();
//		float min_sd = 1.0f / max_sd;
//		
//		int size = fs2.size();
//		int size_1 = size - 1;
//		
//		for ( Feature f1 : fs1 )
//		{
//			Feature best = null;
//			float best_d = Float.MAX_VALUE;
//			float second_best_d = Float.MAX_VALUE;
//			
//			int first = 0;
//			int last = size_1;
//			int s = size / 2 + size % 2;
//			if ( max_sd < Float.MAX_VALUE )
//			{
//				while ( s > 1 )
//				{
//					Feature f2 = fs2.get( last );
//					if ( f2.scale / f1.scale < min_sd ) last = Math.max( 0, last - s );
//					else last = Math.min( size_1, last + s );
//					f2 = fs2.get( first );
//					if ( f2.scale / f1.scale < max_sd ) first = Math.max( 0, first - s );
//					else first = Math.min( size_1, first + s );
//					s = s / 2 + s % 2;
//				}
//				//System.out.println( "first = " + first + ", last = " + last + ", first.scale = " + fs2.get( first ).scale + ", last.scale = " + fs2.get( last ).scale + ", this.scale = " + f1.scale );
//			}
//			
//			//for ( Feature f2 : fs2 )
//			
//			for ( int i = first; i <= last; ++i )
//			{
//				Feature f2 = fs2.get( i );
//				float d = f1.descriptorDistance( f2 );
//				if ( d < best_d )
//				{
//					second_best_d = best_d;
//					best_d = d;
//					best = f2;
//				}
//				else if ( d < second_best_d )
//					second_best_d = d;
//			}
//			if ( best != null && second_best_d < Float.MAX_VALUE && best_d / second_best_d < 0.92 )
//				// not weighted
////				matches.addElement(
////						new PointMatch(
////								new Point(
////										new float[] { f1.location[ 0 ], f1.location[ 1 ] } ),
////								new Point(
////										new float[] { best.location[ 0 ], best.location[ 1 ] } ) ) );
//				// weighted with the features scale
//				matches.addElement(
//						new PointMatch(
//								new Point(
//										new float[] { f1.location[ 0 ], f1.location[ 1 ] } ),
//								new Point(
//										new float[] { best.location[ 0 ], best.location[ 1 ] } ),
//								( f1.scale + best.scale ) / 2.0f ) );
//		}
//		// now remove ambiguous matches
//		for ( int i = 0; i < matches.size(); )
//		{
//			boolean amb = false;
//			PointMatch m = matches.get( i );
//			float[] m_p2 = m.getP2().getL(); 
//			for ( int j = i + 1; j < matches.size(); )
//			{
//				PointMatch n = matches.get( j );
//				float[] n_p2 = n.getP2().getL(); 
//				if ( m_p2[ 0 ] == n_p2[ 0 ] && m_p2[ 1 ] == n_p2[ 1 ] )
//				{
//					amb = true;
//					//System.out.println( "removing ambiguous match at " + j );
//					matches.removeElementAt( j );
//				}
//				else ++j;
//			}
//			if ( amb )
//			{
//				//System.out.println( "removing ambiguous match at " + i );
//				matches.removeElementAt( i );
//			}
//			else ++i;
//		}
//		return matches;
//	}
//	
//	/**
//	 * get a histogram of feature sizes
//	 * 
//	 * @param rs
//	 */
//	public static float[] featureSizeHistogram(
//			Vector< Feature > features,
//			float min,
//			float max,
//			int bins )
//	{
//		System.out.print( "estimating feature size histogram ..." );
//		int num_features = features.size();
//		float h[] = new float[ bins ];
//		int hb[] = new int[ bins ];
//		
//		for ( Feature f : features )
//		{
//			int bin = ( int )Math.max( 0, Math.min( bins - 1, ( int )( Math.log( f.scale ) / Math.log( 2.0 ) * 28.0f ) ) );
//			++hb[ bin ];
//		}
//		for ( int i = 0; i < bins; ++i )
//		{
//			h[ i ] = ( float )hb[ i ] / ( float )num_features;
//		}
//		System.out.println( " done" );
//		return h;
//	}
}
