import java.util.List;

import vib.InterpolatedImage;
import mops.Feature;
import mops.MOPS3D;
import mops.Filter;
import mops.Octave;
import ij.IJ;
import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

/**
 * @author Bene
 *
 */
public class MOPS3D_Test implements PlugInFilter
{
	private ImagePlus image;
	
	/* (non-Javadoc)
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run( ImageProcessor ip )
	{
		MOPS3D mops = new MOPS3D( 8 );
		InterpolatedImage img = new InterpolatedImage( image );
		Filter.enhance( img, 1.0f );
		img = Filter.gauss(
				img,
				( float )Math.sqrt( 1.6 * 1.6 - 0.25 ),
				( float )Math.sqrt( 1.6 * 1.6 - 0.25 ),
				( float )Math.sqrt( 1.6 * 1.6 - 1.1f * 1.1f ));
		
		img.getImage().show();
		
		System.out.println("Initializing ... ");
		
		mops.init( img, 3, 1.6f, 64, 1024 );
		
		Octave[] octaves = mops.getOctaves();
		System.out.println( "Processing " + octaves.length + " octaves:" );
		for ( int oi = 0; oi < octaves.length; ++oi )
		{
			System.out.println("Processing octave " + oi);
			if ( octaves[ oi ].getImg()[ 0 ] == null )
			{
				System.out.println( "Skipping empty octave." );
				continue;
			}
			List< Feature > features = mops.runOctave( oi );
			
			for ( Feature f : features )
			{
				octaves[ oi ].getImg()[ 0 ].setFloat( ( int )Math.round( f.x ), ( int )Math.round( f.y ), ( int )Math.round( f.z ), 2f );
				System.out.println("Found feature at (" + (float)f.x + ", " + (float)f.y + (float)f.z + " in octave " + oi);
			}
			
			octaves[ oi ].getImg()[ 0 ].getImage().show();
			
			// free memory for processing the next octave
			octaves[ oi ].clear();
			
			IJ.showProgress( oi, octaves.length );
		}
	}

	/* (non-Javadoc)
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup( String arg, ImagePlus imp )
	{
		this.image = imp;
		return DOES_32;
	}

}
