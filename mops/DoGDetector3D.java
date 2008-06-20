package mops;

import ij.IJ;
import ij.measure.Calibration;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import Jama.EigenvalueDecomposition;
import Jama.Matrix;

import vib.InterpolatedImage;

public class DoGDetector3D
{
	private float minContrast; 
//  	private final float maxCurvatureRatio = 10;
	private final float maxCurvatureRatio = 5;
	
	Octave octave;
	
	/**
	 * detected candidates as float triples 0=>x, 1=>y, 2=>scale index
	 */
	final ArrayList< float[] > candidates = new ArrayList< float[] >();
	public ArrayList< float[] > getCandidates()
	{
		return candidates;
	}
	
	/**
	 * Constructor
	 */
	public DoGDetector3D()
	{
		octave = null;
	}
	
	public void run( Octave octave )
	{
		candidates.clear();
		// scale the minimal contrast threshold proposed in Lowe (2004, p. 11) with respect
		// to the step size of the scale octave (see Lowe 2004, p. 6) 
		//minContrast = 0.03f * ( ( float )Math.pow( 2.0, 1.0 / ( sigma.length - 3 ) ) - 1.0f );
		
		// less restrictive contrast filter
// 		minContrast = 0.025f * ( octave.k - 1.0f );
		minContrast = 0.1f * (octave.k - 1.0f);
		
		this.octave = octave;
		octave.dog();
		
		for ( int di = 1; di < octave.dog.length - 1; ++di )
		{
			detectCandidates( di );
		}
	}

	
	private void detectCandidates( int di )
	{
		IJ.write("Detect candidates\n");
		InterpolatedImage dog = octave.dog[ di ];
		InterpolatedImage.Iterator it = dog.iterator( false, 1, 1, 1, dog.getWidth() - 2, dog.getHeight() - 2, dog.getDepth() - 2 );
		Calibration c = dog.getImage().getCalibration();
I:		while(it.next() != null) {
			boolean isMax = true, isMin = true;
			float v = dog.getNoCheckFloat(it.i, it.j, it.k);
			for(int i = 0; i < 40; i++)
			{
				int ms = i / 27 - 1;
				int mz = ( i % 27 ) / 9 - 1;
				int my = ( i % 9 ) / 3 - 1;
				int mx = ( i % 3 ) - 1;
				float v2 = octave.dog[ di + ms ].getNoInterpolFloat(
					it.i + mx, it.j + my, it.k + mz);
				if(v2 > v) isMax = false;
				if(v2 < v) isMin = false;
				if(!( isMin || isMax) )
					continue I;
			}
			for(int i = 41; i < 81; i++)
			{
				int ms = i / 27 - 1;
				int mz = ( i % 27 ) / 9 - 1;
				int my = ( i % 9 ) / 3 - 1;
				int mx = ( i % 3 ) - 1;
				float v2 = octave.dog[ di + ms ].getNoInterpolFloat(
					it.i + mx, it.j + my, it.k + mz);
				if(v2 > v) isMax = false;
				if(v2 < v) isMin = false;
				if(!( isMin || isMax) )
					continue I;
			}
			
			// so it is an extremum, try to localize it with subpixel
			// accuracy, if it has to be moved for more than 0.5 in at
			// least one direction, try it again there but maximally 5
			// times
			
			boolean isLocalized = false;
			boolean isLocalizable = true;

			float v2 = 2 * v;
			float[] d = new float[ 4 ];
			double[][] h = new double[ 4 ][ 4 ];
			float[] o = new float[ 4 ];
			float[] f = new float[ 4 ];
			int[] i = new int[ 4 ];
			
			float od = Float.MAX_VALUE;      // offset square distance
			
			int t = 5; // maximal number of re-localizations 
		    do
			{
		    	--t;
		    	
		    	// current location
		    	int xi = it.i + i[ 0 ];
		    	int yi = it.j + i[ 1 ];
		    	int zi = it.k + i[ 2 ];
		    	int si = di + i[ 3 ];
		    	
		    	InterpolatedImage dogc = octave.dog[ si ];
		    	InterpolatedImage dogm1 = octave.dog[ si - 1 ];
				InterpolatedImage dogp1 = octave.dog[ si + 1 ];
				
				v = dogc.getNoCheckFloat( xi, yi, zi );
				
		    	
try {
				// derive at (x, y, i) by center of difference
				d[ 0 ] = ( dogc.getNoInterpolFloat( xi + 1, yi, zi ) - dogc.getNoInterpolFloat( xi - 1, yi, zi ) ) / 2; 
				d[ 1 ] = ( dogc.getNoInterpolFloat( xi, yi + 1, zi ) - dogc.getNoInterpolFloat( xi, yi - 1, zi ) ) / 2; 
				d[ 2 ] = ( dogc.getNoInterpolFloat( xi, yi, zi + 1 ) - dogc.getNoInterpolFloat( xi, yi, zi - 1 ) ) / 2;
				d[ 3 ] = ( dogp1.getNoInterpolFloat( xi, yi, zi ) - dogm1.getNoInterpolFloat( xi, yi, zi ) ) / 2;
} catch(Exception e) {
System.out.println("dogc = " + dogc + " dogm1 = " + dogm1 + " dogp1 = " + dogp1 + " current = " + si);
}

				
				h[ 0 ][ 0 ] =
					dogc.getNoInterpolFloat( xi + 1, yi, zi ) -
					v2 +
					dogc.getNoInterpolFloat( xi - 1, yi, zi );
				h[ 1 ][ 1 ] =
					dogc.getNoInterpolFloat( xi, yi + 1, zi ) -
					v2 +
					dogc.getNoInterpolFloat( xi, yi - 1, zi );
				h[ 2 ][ 2 ] =
					dogc.getNoInterpolFloat( xi, yi, zi + 1 ) -
					v2 +
					dogc.getNoInterpolFloat( xi, yi, zi - 1 );
				h[ 3 ][ 3 ] =
					dogp1.getNoInterpolFloat( xi, yi, zi ) -
					v2 +
					dogm1.getNoInterpolFloat( xi, yi, zi );
				
				h[ 0 ][ 1 ] = h[ 1 ][ 0 ] =
					( dogc.getNoInterpolFloat( xi + 1, yi + 1, zi ) -
					  dogc.getNoInterpolFloat( xi - 1, yi + 1, zi ) ) / 4 -
					( dogc.getNoInterpolFloat( xi + 1, yi - 1, zi ) -
					  dogc.getNoInterpolFloat( xi - 1, yi - 1, zi ) ) / 4;
				h[ 0 ][ 2 ] = h[ 2 ][ 0 ] =
					( dogc.getNoInterpolFloat( xi + 1, yi, zi + 1 ) -
					  dogc.getNoInterpolFloat( xi - 1, yi, zi + 1 ) ) / 4 -
					( dogc.getNoInterpolFloat( xi + 1, yi, zi - 1 ) -
					  dogc.getNoInterpolFloat( xi - 1, yi, zi - 1 ) ) / 4;
				h[ 1 ][ 2 ] = h[ 2 ][ 1 ] =
					( dogc.getNoInterpolFloat( xi, yi + 1, zi + 1 ) -
					  dogc.getNoInterpolFloat( xi, yi - 1, zi + 1 ) ) / 4 -
					( dogc.getNoInterpolFloat( xi, yi + 1, zi - 1 ) -
					  dogc.getNoInterpolFloat( xi, yi - 1, zi - 1 ) ) / 4;
				
				h[ 0 ][ 3 ] = h[ 3 ][ 0 ] =
					( dogp1.getNoInterpolFloat( xi + 1, yi, zi ) -
					  dogp1.getNoInterpolFloat( xi - 1, yi, zi ) ) / 4 -
					( dogm1.getNoInterpolFloat( xi + 1, yi, zi ) -
					  dogm1.getNoInterpolFloat( xi - 1, yi, zi ) ) / 4;
				h[ 1 ][ 3 ] = h[ 3 ][ 1 ] =
					( dogp1.getNoInterpolFloat( xi, yi + 1, zi ) -
					  dogp1.getNoInterpolFloat( xi, yi - 1, zi ) ) / 4 -
					( dogm1.getNoInterpolFloat( xi, yi + 1, zi ) -
					  dogm1.getNoInterpolFloat( xi, yi - 1, zi ) ) / 4;
				h[ 2 ][ 3 ] = h[ 3 ][ 2 ] =
					( dogp1.getNoInterpolFloat( xi, yi, zi + 1 ) -
					  dogp1.getNoInterpolFloat( xi, yi, zi - 1 ) ) / 4 -
					( dogm1.getNoInterpolFloat( xi, yi, zi + 1 ) -
					  dogm1.getNoInterpolFloat( xi, yi, zi - 1 ) ) / 4;
				
				// invert hessian
			    Matrix H = new Matrix( h, 4, 4 );
			    Matrix H_inv;
			    try
			    {
			    	H_inv = H.inverse();
			    }
			    catch ( RuntimeException e )
			    {
			    	continue ;
			    }
			    double[][] h_inv = H_inv.getArray();
			    
			    // estimate the location of zero crossing being the offset of the extremum
			    o[ 0 ] = ( float )( -h_inv[ 0 ][ 0 ] * d[ 0 ] - h_inv[ 0 ][ 1 ] * d[ 1 ] - h_inv[ 0 ][ 2 ] * d[ 2 ] - h_inv[ 0 ][ 3 ] * d[ 3 ] );
				o[ 1 ] = ( float )( -h_inv[ 1 ][ 0 ] * d[ 0 ] - h_inv[ 1 ][ 1 ] * d[ 1 ] - h_inv[ 1 ][ 2 ] * d[ 2 ] - h_inv[ 1 ][ 3 ] * d[ 3 ] );
				o[ 2 ] = ( float )( -h_inv[ 2 ][ 0 ] * d[ 0 ] - h_inv[ 2 ][ 1 ] * d[ 1 ] - h_inv[ 2 ][ 2 ] * d[ 2 ] - h_inv[ 2 ][ 3 ] * d[ 3 ] );
				o[ 3 ] = ( float )( -h_inv[ 3 ][ 0 ] * d[ 0 ] - h_inv[ 3 ][ 1 ] * d[ 1 ] - h_inv[ 3 ][ 2 ] * d[ 2 ] - h_inv[ 3 ][ 3 ] * d[ 3 ] );
				
				float odc =
					o[ 0 ] * o[ 0 ] +
					o[ 1 ] * o[ 1 ] + 
					o[ 2 ] * o[ 2 ] + 
					o[ 3 ] * o[ 3 ];
			    
			    if ( odc < 2.0f )
			    {
			    	if (
			    			( Math.abs( o[ 0 ] ) > 0.5 ||
			    			  Math.abs( o[ 1 ] ) > 0.5 ||
			    			  Math.abs( o[ 2 ] ) > 0.5 ||
			    			  Math.abs( o[ 2 ] ) > 0.5 ) && odc < od )
			    	{
			    		od = odc;
				    	
				    	i[ 0 ] = ( int )Math.round( ( float )i[ 0 ] + o[ 0 ] );
				    	i[ 1 ] = ( int )Math.round( ( float )i[ 1 ] + o[ 1 ] );
				    	i[ 2 ] = ( int )Math.round( ( float )i[ 2 ] + o[ 2 ] );
				    	i[ 3 ] = ( int )Math.round( ( float )i[ 3 ] + o[ 3 ] );
				    	
				    	
				    	if (
				    			it.i + i[ 0 ] < 1 ||
				    			it.j + i[ 1 ] < 1 ||
				    			it.k + i[ 2 ] < 1 ||
				    			di + i[ 3 ] < 1 ||
				    			
				    			it.i + i[ 0 ] > dogc.getWidth() - 2 ||
				    			it.j + i[ 1 ] > dogc.getHeight() - 2 ||
				    			it.k + i[ 2 ] > dogc.getDepth() - 2 ||
				    			di + i[ 3 ] > octave.dog.length - 2 )
				    		isLocalizable = false;
			    	}
			    	else
			    	{
			    		f[ 0 ] = ( float )xi + o[ 0 ];
			    		f[ 1 ] = ( float )yi + o[ 1 ];
			    		f[ 2 ] = ( float )zi + o[ 2 ];
			    		f[ 3 ] = ( float )si + o[ 3 ];
				    	
			    		if (
				    			f[ 0 ] < 1 ||
				    			f[ 1 ] < 1 ||
				    			f[ 2 ] < 1 ||
				    			f[ 3 ] < 1 ||
				    			
				    			f[ 0 ] > dogc.getWidth() - 2 ||
				    			f[ 1 ] > dogc.getHeight() - 2 ||
				    			f[ 2 ] > dogc.getDepth() - 2 ||
				    			f[ 3 ] > octave.dog.length - 2 )
				    		isLocalizable = false;
			    		else
				    		isLocalized = true;
			    	}
			    }
			    else isLocalizable = false;
			}
		    while ( !isLocalized && isLocalizable && t >= 0 );
		    
		    // reject detections that could not be localized properly
		    if ( !isLocalized )	continue;
			
		    // reject detections with very low contrast
			if ( Math.abs( v + 0.5f * ( d[ 0 ] * o[ 0 ] + d[ 1 ] * o[ 1 ] + d[ 2 ] * o[ 2 ] + d[ 3 ] * o[ 3 ] ) ) < minContrast ) continue;
			
			// reject detections with high max/min principal curvature ratio
			double[][] hxyz = new double[][]{
					{ h[ 0 ][ 0 ], h[ 0 ][ 1 ], h[ 0 ][ 2 ] }, 
					{ h[ 1 ][ 0 ], h[ 1 ][ 1 ], h[ 1 ][ 2 ] },
					{ h[ 2 ][ 0 ], h[ 2 ][ 1 ], h[ 2 ][ 2 ] } };
			EigenvalueDecomposition evd =
				new EigenvalueDecomposition( new Matrix( hxyz, 3, 3 ) );
			
			double[] ev = evd.getRealEigenvalues();
			
			ev[ 0 ] = Math.abs( ev[ 0 ] );
			ev[ 1 ] = Math.abs( ev[ 1 ] );
			ev[ 2 ] = Math.abs( ev[ 2 ] );
			
			double min_ev = ev[ 0 ];
			double max_ev = ev[ 0 ];
			
			if ( ev[ 1 ] < min_ev ) min_ev = ev[ 1 ];
			else if ( ev[ 1 ] > max_ev ) max_ev = ev[ 1 ];
			if ( ev[ 2 ] < min_ev ) min_ev = ev[ 2 ];
			else if ( ev[ 2 ] > max_ev ) max_ev = ev[ 2 ];
			
			if ( max_ev / min_ev > maxCurvatureRatio ) continue;
			
			candidates.add( f );
		}
	}
}
