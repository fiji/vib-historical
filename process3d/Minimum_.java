package process3d;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.PlugInFilter;
import ij.gui.GenericDialog;
import ij.process.ImageProcessor;
import ij.measure.Calibration;


public class Minimum_ implements PlugInFilter {
	
	private ImagePlus image;

	public void run(ImageProcessor ip) {
		MinMaxMedian.convolve(image, MinMaxMedian.MINIMUM).show();
	}

	public int setup(String arg, ImagePlus img) {
		this.image = img;
		return DOES_8G | NO_CHANGES;
	}
}
