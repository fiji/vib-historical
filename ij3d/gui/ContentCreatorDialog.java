package ij3d.gui;

import ij.util.Java2;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.IJ;
import ij.WindowManager;
import ij.ImagePlus;

import ij3d.Content;
import ij3d.ContentCreator;
import ij3d.Image3DUniverse;
import ij3d.ColorTable;

import javax.swing.JFileChooser;
import javax.swing.filechooser.*;
import java.awt.event.*;
import java.awt.*;

import java.util.Vector;

import java.io.File;

import javax.vecmath.Color3f;

public class ContentCreatorDialog {

	private Color3f color;
	private int threshold;
	private String name;
	private int resamplingFactor;
	private boolean[] channels;
	private int timepoint;
	private int type;
	private boolean fromFile;
	private ImagePlus image;
	private File file;
	private Content content;

	private GenericDialog gd;

	public Content showDialog(Image3DUniverse univ, ImagePlus imp) {
		// setup default values
		int img_count = WindowManager.getImageCount();
		Vector windows = new Vector();
		for(int i=1; i<=img_count; i++) {
			int id = WindowManager.getNthImageID(i);
			ImagePlus iimp = WindowManager.getImage(id);
			if(iimp != null && !iimp.getTitle().equals("3d"))
				 windows.add(iimp.getTitle());
		}
		windows.add("Open from file...");
		final String[] images = new String[windows.size()];
		windows.toArray(images);
		name = image == null ? images[0] : imp.getTitle();
		String[] types = new String[] {
			"Volume", "Orthoslice", "Surface", "Surface Plot 2D"};
		type = type < 0 ? 0 : type;
		threshold = type == Content.SURFACE ? 50 : 0;
		resamplingFactor = 2;
		file = null;

		// create dialog
		gd = new GenericDialog("Add ...", univ.getWindow());
		gd.addChoice("Image", images, name);
		gd.addStringField("Name", name, 10);
		gd.addChoice("Display as", types, types[type]);
		gd.addChoice("Color", ColorTable.colorNames,
						ColorTable.colorNames[0]);
		gd.addNumericField("Threshold", threshold, 0);
		gd.addNumericField("Resampling factor", resamplingFactor, 0);
		gd.addMessage("Channels");
		gd.addCheckboxGroup(1, 3,
				new String[] {"red", "green", "blue"},
				new boolean[]{true, true, true});
		gd.addNumericField("Start at time point",
				univ.getCurrentTimepoint(), 0);

		// automatically set threshold if surface is selected
		final TextField th = (TextField)gd.getNumericFields().get(0);
		final Choice di = (Choice)gd.getChoices().get(1);
		di.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				if(di.getSelectedIndex() == Content.SURFACE)
					th.setText(Integer.toString(50));
				else
					th.setText(Integer.toString(0));
			}
		});
		// automatically update name if a different image is selected
		final Choice im = (Choice)gd.getChoices().get(0);
		final TextField na = (TextField)gd.getStringFields().get(0);
		im.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				int idx = im.getSelectedIndex();
				if(idx < images.length - 1) {
					na.setText(im.getSelectedItem());
					file = null;
					return;
				}
				File f = openFileOrDir("Open from file...");
				if(f == null)
					return;
				file = f;
				na.setText(file.getAbsolutePath());
			}
		});
		gd.showDialog();
		if(gd.wasCanceled())
			return null;

		String imChoice = gd.getNextChoice();
		fromFile = imChoice.equals("Open from file...");
		if(!fromFile)
			image = WindowManager.getImage(imChoice);

		name = gd.getNextString();
		type = gd.getNextChoiceIndex();
		color = ColorTable.getColor(gd.getNextChoice());
		threshold = (int)gd.getNextNumber();
		resamplingFactor = (int)gd.getNextNumber();
		channels = new boolean[] { gd.getNextBoolean(),
					gd.getNextBoolean(),
					gd.getNextBoolean() };
		timepoint = (int)gd.getNextNumber();

		if(univ.contains(name)) {
			IJ.error("Could not add new content. A content with " +
				"name \"" + name + "\" exists already.");
			return null;
		}

		ImagePlus[] imps = fromFile ?
			ContentCreator.getImages(file) :
			ContentCreator.getImages(image);

		if(imps == null || imps.length == 0)
			return null;

		// check image type
		int imaget = imps[0].getType();
		if(imaget != ImagePlus.GRAY8 && imaget != ImagePlus.COLOR_RGB) {
			// TODO correct message
			if(IJ.showMessageWithCancel("Convert...",
				"8-bit image required. Convert?")) {
				for(ImagePlus ip : imps)
					ContentCreator.convert(ip);
			} else {
				return null;
			}
		}

		Content c = ContentCreator.createContent(
			name, imps, type, resamplingFactor,
			timepoint, color, threshold,
			channels);
		return c;
	}

	public File getFile() {
		return file;
	}

	public ImagePlus getImage() {
		return image;
	}

	public Color3f getColor() {
		return color;
	}

	public boolean[] getChannels() {
		return channels;
	}

	public int getResamplingFactor() {
		return resamplingFactor;
	}

	public int getThreshold() {
		return threshold;
	}

	public int getTimepoint() {
		return timepoint;
	}

	public String getName() {
		return name;
	}

	public int getType() {
		return type;
	}

	File openFileOrDir(final String title) {
		Java2.setSystemLookAndFeel();
		try {
			JFileChooser chooser = new JFileChooser();
			chooser.setCurrentDirectory(new File(OpenDialog.getDefaultDirectory()));
			chooser.setDialogTitle(title);
			chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
			chooser.setApproveButtonText("Select");
			if(chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
				return null;

			File dir = chooser.getCurrentDirectory();
			File file = chooser.getSelectedFile();
			String directory = dir.getPath();
			if(directory != null)
				OpenDialog.setDefaultDirectory(directory);
			return file;
		} catch (Exception e) {}
		return null;
	}
}
