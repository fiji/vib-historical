import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import vib.BenesNamedPoint;
import vib.PointList;
import vib.InterpolatedImage;
import vib.FastMatrix;

import mops.Feature;
import mops.MOPS3D;
import mops.Filter;
import mops.Octave;
import mops.RigidModel3D;

import ij.gui.GUI;
import ij.gui.GenericDialog;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.process.StackConverter;
import ij.plugin.PlugIn;
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
 * @author Bene
 *
 */
public class MOPS3D_Test implements PlugIn
{

	public static final float MAX_EPSILON = 100.0f;
	public static final float MIN_INLIER_RATIO = 0.05f;
 	public static final float ROD = 1;
// 	public static final float ROD = 0.92f;

	private ImagePlus templ, templ_org;
	private ImagePlus model, model_org;
	
	private FastMatrix bestRigid;
	public FastMatrix getBestRigid() { return bestRigid; }

	public MOPS3D_Test() { }

	public MOPS3D_Test(ImagePlus templ, ImagePlus model) {
		this.templ = templ;
		this.model = model;
		this.model_org = new InterpolatedImage(model).cloneImage().getImage();
		this.templ_org = new InterpolatedImage(templ).cloneImage().getImage();
	}
	
	public void run( String arg ) {
		GenericDialog gd = new GenericDialog("Test MOPS");
		int[] wIDs = WindowManager.getIDList();
		if(wIDs == null){
			IJ.error("No images open");
			return;
		}
		String[] titles = new String[wIDs.length];
		for(int i=0;i<wIDs.length;i++){
			titles[i] = WindowManager.getImage(wIDs[i]).getTitle();
		}

		gd.addChoice("Template", titles, titles[0]);
		gd.addChoice("Model", titles, titles[0]);
		gd.showDialog();
		if (gd.wasCanceled())
			return;

		templ = WindowManager.getImage(gd.getNextChoice());
		model = WindowManager.getImage(gd.getNextChoice());
		this.model_org = new InterpolatedImage(model).cloneImage().getImage();
		this.templ_org = new InterpolatedImage(templ).cloneImage().getImage();

		
		PointList tFeatures = extractFeatures(templ);
		PointList mFeatures = extractFeatures(model);

		HashMap<Point, Feature> tmap = new HashMap<Point, Feature> ();
		HashMap<Point, Feature> mmap = new HashMap<Point, Feature> ();

		List<PointMatch> candidates = 
			MOPS3D.createMatches(mFeatures, tFeatures, ROD, mmap, tmap);

		// now fill new PointLists with the inliers
		PointList tInliers = new PointList();
		PointList mInliers = new PointList();
		for(int i = 0; i < candidates.size(); i++) {
			PointMatch match = candidates.get(i);
			Feature fm = mmap.get(match.getP1());
			mInliers.add(fm);
			mInliers.rename(fm, "Point" + i);
			Feature ft = tmap.get(match.getP2());
			tInliers.add(ft);
			tInliers.rename(ft, "Point" + i);
		}
		visualizeMatches(mInliers, tInliers);


		// filter matches
// 		List<PointMatch> inliers = new ArrayList< PointMatch >();
// 		Model model = null;
// 		Class< ? extends Model> modelClass = RigidModel3D.class;
// 		try {
// 			model = Model.filterRansac(modelClass, candidates,
// 				inliers, 1000, MAX_EPSILON, MIN_INLIER_RATIO);
// 		} catch(Exception e) {
// 			IJ.error(e.getMessage());
// 		}
// 
// 		// now fill new PointLists with the inliers
// 		PointList tInliers = new PointList();
// 		PointList mInliers = new PointList();
// 		for(int i = 0; i < inliers.size(); i++) {
// 			PointMatch match = inliers.get(i);
// 			Feature fm = mmap.get(match.getP1());
// 			mInliers.add(fm);
// 			mInliers.rename(fm, "Point" + i);
// 			Feature ft = tmap.get(match.getP2());
// 			tInliers.add(ft);
// 			tInliers.rename(ft, "Point" + i);
// 		}
// 
// 		visualizeMatches(mInliers, tInliers);
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
		int descWidth = 16;
		MOPS3D mops = new MOPS3D( descWidth );
System.out.println("Initializing ... ");
		mops.init( img, 3, 1.6f, 32, 1024 );
		
		Octave[] octaves = mops.getOctaves();
		PointList featurelist = new PointList();
System.out.println( "Processing " + octaves.length + " octaves:" );
		for ( int oi = 0; oi < octaves.length; ++oi ) {
System.out.println("Processing octave " + oi);
			if ( octaves[ oi ].getImg()[ 0 ] == null ) {
System.out.println( "Skipping empty octave." );
				continue;
			}
			List< Feature > features = mops.runOctave( oi );
			for ( Feature f : features )
				featurelist.add(f);
			
			octaves[ oi ].getImg()[ 0 ].getImage().show();
			
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

	/*
	 * Show feature list in 3D viewer
	 */
	public void visualizeMatches(PointList mFeatures, PointList tFeatures) {
		// create model universe
		Image3DUniverse univ_m = new Image3DUniverse(512, 512);
		univ_m.show();
		// add model
		Image3DMenubar menu_m = univ_m.getMenuBar();
		final Content c_m = univ_m.addVoltex(
			model_org, null, "model", 0, new boolean[] {true, true, true}, 2);
// ImagePlus tmp = new InterpolatedImage(model_org).cloneImage().getImage();
// tmp.setTitle("added to viewer");
// tmp.show();
		// fill model point list with model features
		final PointList pl_m = c_m.getPointList();
		for(BenesNamedPoint bnp : mFeatures)
			pl_m.add((Feature)bnp);
		c_m.showPointList(true);

		// create template universe
		Image3DUniverse univ_t = new Image3DUniverse(512, 512);
		univ_t.show();
		// add template
		Image3DMenubar menu_t = univ_t.getMenuBar();
		final Content c_t = univ_t.addVoltex(
			templ_org, null, "template", 0, new boolean[] {true, true, true}, 2);
		// fill model point list with model features
		final PointList pl_t = c_t.getPointList();
		for(BenesNamedPoint bnp : tFeatures)
			pl_t.add((Feature)bnp);
		c_t.showPointList(true);

		// Create a panel box which allows to automatically show in
		// both universes the corresponding features
		Panel panel = new Panel(new GridLayout(tFeatures.size(), 1));
		for(BenesNamedPoint bnp : tFeatures) {
			Button b = new Button(bnp.getName());
			b.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					String name = e.getActionCommand();
					Feature ft = (Feature)pl_t.get(name);
					Feature fm = (Feature)pl_m.get(name);
					c_m.setFeature(fm);
					c_t.setFeature(ft);
				}
			});
			panel.add(b);
		}
		ScrollPane scroll = new ScrollPane();
		scroll.add(panel);
		GenericDialog gd = new GenericDialog("Show point correspondences");
		Panel pp = new Panel();
		pp.add(scroll);
		gd.addPanel(pp);
		gd.setModal(false);
		gd.showDialog();
	}
}
