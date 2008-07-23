import java.util.HashMap;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

import vib.BenesNamedPoint;
import vib.PointList;
import vib.InterpolatedImage;
import vib.TransformedImage;
import vib.RigidRegistration_;
import vib.FastMatrix;
import vib.Resample_;

import mops.Feature;
import mops.MOPS3D;
import mops.Filter;
import mops.Octave;
import mops.RigidModel3D;
import mops.FeatureMatch;

import ij.gui.GUI;
import ij.gui.GenericDialog;
import ij.IJ;
import ij.Macro;
import ij.ImagePlus;
import ij.WindowManager;
import ij.process.StackConverter;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.measure.Calibration;

import ij3d.Image3DMenubar;
import ij3d.Image3DUniverse;
import ij3d.Content;

import mpicbg.models.Model;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;

import java.awt.Button;
import java.awt.Panel;
import java.awt.GridLayout;
import java.awt.ScrollPane;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Bene and Stephan
 *
 */
public class MOPS3D_Feature_Extract implements PlugInFilter
{

	public static final int DESC_WIDTH = 16;
	public static final float MAX_EPSILON = 100.0f;
	public static final float MIN_INLIER_RATIO = 0.05f;
 	public static final float ROD = 1;
// 	public static final float ROD = 0.92f;

	private ImagePlus image, image_org;
	
	private FastMatrix bestRigid;
	public FastMatrix getBestRigid() { return bestRigid; }

	public MOPS3D_Feature_Extract() { }

	public MOPS3D_Feature_Extract(ImagePlus image) {
		this.image = image;
		this.image_org = new InterpolatedImage(image).cloneImage().getImage();
	}
	
	public void run(ImageProcessor ip) {

		image_org = new InterpolatedImage(image).cloneImage().getImage();
		PointList features = extractFeatures(image);
		visualizeMatches(features);
	}

	public PointList extractFeatures(ImagePlus image) {

		// create anisotropic image and convert to float
		Calibration cal = image.getCalibration();
		double pw = cal.pixelWidth, ph = cal.pixelHeight;
		double pd = cal.pixelDepth;
		if(pw != ph)
			IJ.showMessage("Image is not isotropic in x-y direction");

		double resliceFactor = 1;
		if(pw != pd) {
			image = new Reslice_Z(image).resliceByte(pw);
			resliceFactor = pw / pd;
		}
		new StackConverter(image).convertToGray32();
		InterpolatedImage img = new InterpolatedImage( image );

		// pre-filter image with a Gaussian kernel
		double sigmaZ = 1.6 * 1.6 - resliceFactor * resliceFactor * 0.5 * 0.5;
		if(sigmaZ < 0)
			IJ.error("Please shrink widht and height");
		img = Filter.gauss(
				img,
				( float )Math.sqrt( 1.6 * 1.6 - 0.25 ),
				( float )Math.sqrt( 1.6 * 1.6 - 0.25 ),
				( float )Math.sqrt( sigmaZ ));

		
		// scale to range [0;1];
		Filter.enhance( img, 1.0f );
		img.getImage().show();
		
		// Initialize MOPS
		MOPS3D mops = new MOPS3D( DESC_WIDTH );
		println("Initializing ... ");
		mops.init( img, 3, 1.6f, 32, 1024 );
		
		Octave[] octaves = mops.getOctaves();
		PointList featurelist = new PointList();
		println( "Processing " + octaves.length + " octaves:" );
		for ( int oi = 0; oi < octaves.length; ++oi ) {
			println("Processing octave " + oi);
			if ( octaves[ oi ].getImg()[ 0 ] == null ) {
				println( "Skipping empty octave." );
				continue;
			}
			List< Feature > features = mops.runOctave( oi );
			println("Found " + features.size() + " in octave " + oi);
			for ( Feature f : features )
				featurelist.add(f);
			
// 			octaves[ oi ].getImg()[ 0 ].getImage().show();
			
			// free memory for processing the next octave
			octaves[ oi ].clear();
			
			IJ.showProgress( oi, octaves.length );
		}

		// transform points to real world coordinates
		Calibration c = img.getImage().getCalibration();
		pw = c.pixelWidth;
		ph = c.pixelHeight;
		pd = c.pixelDepth;
		for(int i = 0; i < featurelist.size(); i++) {
			Feature f = (Feature)featurelist.get(i);
			f.x *= pw;
			f.y *= ph;
			f.z *= pd;
		}
		return featurelist;
	}

	private void println(String s) {
		IJ.write(s + "\n");
	}

	/*
	 * Show feature list in 3D viewer
	 */
	public void visualizeMatches(final PointList matches) {
		// create model universe
		Image3DUniverse univ = new Image3DUniverse(512, 512);
		univ.show();
		// add model
		final Content c = univ.addVoltex(
			image_org, null, image.getTitle(), 0, new boolean[] {true, true, true}, 2);

		// fill point lists with model and template features
		final PointList pl = c.getPointList();
		for(BenesNamedPoint bp : matches) {
			pl.add(bp);
		}
		c.showPointList(true);
	}

	public int setup(String arg, ImagePlus imp) {
		this.image = imp;
		return DOES_8G;
	}
}
