import ij.process.ImageProcessor;
import ij.ImageStack;
import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.gui.GenericDialog;

import java.awt.FlowLayout;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.Checkbox;
import java.awt.Button;
import java.awt.Frame;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.*;

import java.util.Vector;

import vib.Resample_;
import vib.InterpolatedImage;

import marchingcubes.MCPanel;

public class Viewer_3D implements PlugInFilter {

	private ImagePlus image;
	private ImagePlus ret;
	private int minX = Integer.MAX_VALUE;
	private int maxX = Integer.MIN_VALUE;
	private int w;
	private int h;
	private int d;
	private int threshold;
	private byte[][][] voxData;
	private MCPanel canvas;
	private Frame frame;

	public void run(ImageProcessor ip) {
		GenericDialog gd = new GenericDialog("3D view");
		gd.addNumericField("Threshold", 50, 0);
		gd.addCheckbox("Smooth", false);
		gd.addNumericField("Resample x", 4, 0);
		gd.addNumericField("Resample y", 4, 0);
		gd.addNumericField("Resample z", 2, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;

		int threshold = (int)gd.getNextNumber();
		boolean smooth = gd.getNextBoolean();
		int resampleX = (int)gd.getNextNumber();
		int resampleY = (int)gd.getNextNumber();
		int resampleZ = (int)gd.getNextNumber();
		process(threshold, smooth, resampleX, resampleY, resampleZ);
		if(frame == null)
			showViewer(voxData);
		else 
			updateViewer(voxData);
	}

	public void process(int threshold, boolean smooth,
			int resampleX, int resampleY, int resampleZ) { 
		init();
		if(smooth)
			smooth();
		if(resampleX != 1 || resampleY != 1 || resampleZ != 1)
			resample(resampleX, resampleY, resampleZ);

		byte[][][] voxData = calcVoxData();
	}

	private void init() {
		ret = new InterpolatedImage(image).cloneImage().getImage();
		w = ret.getWidth();
		h = ret.getHeight();
		d = ret.getStackSize();
	}
	
	private void smooth() {
		for(int z=0; z< d; z++)	{
			ImageStack stack = ret.getStack();
			ImageProcessor ip = stack.getProcessor(z+1);
			ip.smooth();
		}
	}

	public void resample(int facX, int facY, int facZ) {
		ImagePlus resampled = Resample_.resample(ret, facX, facY, facZ);
		w = resampled.getWidth();
		h = resampled.getHeight();
		d = resampled.getStackSize();
		ret = resampled;
	}

	public byte[][][] calcVoxData() {
		voxData = new byte[w][h][d];
		ImageStack stack = ret.getStack();
		for(int z=0; z<d; z++){
			byte[] pixels = (byte[])stack.getProcessor(z+1).getPixels();
			for(int y=0; y<h; y++){
				for(int x=0; x<w; x++){
					voxData[x][y][z] = pixels[x*w+y];
				}
			}
		}
		return voxData;
	}

	public void showViewer(byte[][][] voxData) {
		canvas = new MCPanel(voxData, w, threshold);
		frame = new Frame();
		frame.addWindowListener(new WindowAdapter(){
			public void windowClosing(WindowEvent e){
				frame.dispose();
				frame = null;
			}
		});
		frame.setTitle("3D Viewer");
		frame.setSize(512,512);
		frame.add(canvas);
		frame.setVisible(true);
	}

	public void updateViewer(byte[][][]voxData) {
		canvas.updateShape(voxData, w, threshold);
	}

	public int setup(String arg, ImagePlus img) {
		this.image = img;
		return DOES_8G;
	}
}
