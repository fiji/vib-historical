package ij3d;

import ij.ImageStack;
import ij.ImagePlus;
import java.util.TreeMap;

import customnode.CustomMesh;
import customnode.CustomMultiMesh;
import customnode.CustomMeshNode;

import javax.vecmath.Color3f;

public class ContentCreator {

	public static Content createContent(
				String name,
				ImagePlus image,
				int type) {
		int resf = Content.getDefaultResamplingFactor(image, type);
		return createContent(name, image, type, resf, -1);
	}

	public static Content createContent(
				String name,
				ImagePlus image,
				int type,
				int resf) {
		return createContent(name, image, type, resf, -1);
	}

	public static Content createContent(
				String name,
				ImagePlus image,
				int type,
				int resf,
				int tp) {
		int thr = Content.getDefaultThreshold(image, type);
		return createContent(name, image, type, resf, tp, null, thr, new boolean[] {true, true, true});
	}

	public static Content createContent(
				String name,
				ImagePlus image,
				int type,
				int resf,
				int tp,
				Color3f color,
				int thresh,
				boolean[] channels) {

		TreeMap<Integer, ContentInstant> instants =
			new TreeMap<Integer, ContentInstant>();
		ImagePlus[] images = getImages(image);
		for(ImagePlus imp : images) {
			ContentInstant content = new ContentInstant(name);
			content.image = imp;
			content.color = color;
			content.threshold = thresh;
			content.channels = channels;
			content.resamplingF = resf;
			content.showCoordinateSystem(UniverseSettings.
					showLocalCoordinateSystemsByDefault);
			content.displayAs(type);
			content.compile();
			instants.put(tp++, content);
		}
		return new Content(name, instants);
	}

	public static Content createContent(CustomMesh mesh, String name) {
		return createContent(mesh, name, -1);
	}

	public static Content createContent(CustomMesh mesh, String name, int tp) {
		Content c = new Content(name, tp);
		ContentInstant content = c.getInstant(tp);
		content.color = mesh.getColor();
		content.transparency = mesh.getTransparency();
		content.shaded = mesh.isShaded();
		content.showCoordinateSystem(
			UniverseSettings.showLocalCoordinateSystemsByDefault);
		content.display(new CustomMeshNode(mesh));
		return c;
	}

	public static Content createContent(CustomMultiMesh node, String name) {
		return createContent(node, name, -1);
	}

	public static Content createContent(CustomMultiMesh node, String name, int tp) {
		Content c = new Content(name, tp);
		ContentInstant content = c.getInstant(tp);
		content.color = null;
		content.transparency = 0f;
		content.shaded = false;
		content.showCoordinateSystem(
			UniverseSettings.showLocalCoordinateSystemsByDefault);
		content.display(node);
		return c;
	}

	/**
	 * Get an array of images of the specified image, which is assumed to
	 * be a hyperstack. The hyperstack should contain of only one channes,
	 * with the different images as different frames.
	 * @param imp
	 * @return
	 */
	public static ImagePlus[] getImages(ImagePlus imp) {
		if(!imp.isHyperStack())
			return new ImagePlus[] {imp};

		int nSlices = imp.getNSlices();
		int nFrames = imp.getNFrames();
		ImagePlus[] ret = new ImagePlus[nFrames];
		int w = imp.getWidth(), h = imp.getHeight();

		ImageStack oldStack = imp.getStack();
		String oldTitle = imp.getTitle();
		for(int i = 0, slice = 1; i < nFrames; i++) {
			ImageStack newStack = new ImageStack(w, h);
			for(int j = 0; j < nSlices; j++, slice++) {
				newStack.addSlice(
					oldStack.getSliceLabel(slice),
					oldStack.getPixels(slice));
			}
			ret[i] = new ImagePlus(oldTitle
				+ " (frame " + i + ")", newStack);
			ret[i].setCalibration(imp.getCalibration().copy());
		}
		return ret;
	}

}

