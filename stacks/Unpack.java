/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

/* Copyright 2006, 2007 Mark Longair */

/*
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License as
    published by the Free Software Foundation; either version 3 of the
    License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package stacks;

import util.BatchOpener;
import util.Limits;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.process.ImageProcessor;
import ij.process.ByteProcessor;
import ij.gui.ImageWindow;
import ij.Macro;
import ij.LookUpTable;
import ij.plugin.PlugIn;
import ij.plugin.Thresholder;
import ij.plugin.Scaler;
import ij.plugin.filter.ThresholdToSelection;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.PolygonRoi;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.text.DecimalFormat;
import javax.imageio.ImageIO;

import java.util.HashSet;
import java.util.Iterator;
import java.util.ArrayList;

import amira.AmiraParameters;

public class Unpack {

	public static void unpack( ImagePlus imagePlus,
				   int channel,
				   LookUpTable lut,
				   float scale,
				   int sliceMin,
				   int sliceMax,
				   String directory ) throws IOException {

		int imageType = imagePlus.getType();
		float [] limits = null;
		if( imageType != ImagePlus.COLOR_RGB )
			limits = Limits.getStackLimits( imagePlus );

		ImageStack stack = imagePlus.getStack();

		int width = imagePlus.getWidth();
		int height = imagePlus.getHeight();
		int depth = imagePlus.getStackSize();

		if( sliceMin < 0 )
			sliceMin = 0;
		if( sliceMax < 0 )
			sliceMax = depth - 1;

		for( int z = sliceMin; z <= sliceMax; ++z ) {

			DecimalFormat f2 = new DecimalFormat("00");
			DecimalFormat f5 = new DecimalFormat("00000");

			String outputFileName = f2.format(channel) + "-" +
				f5.format(z)+".png";

			outputFileName = directory +
				File.separator + outputFileName;

			BufferedImage bi;

			ImageProcessor unscaled = stack.getProcessor(z+1);
			ImageProcessor ip;

			int toWriteWidth = width;
			int toWriteHeight = height;

			if( scale == 1 ) {
				ip = unscaled;
			} else {
				int newWidth = (int)( width * scale );
				ip = unscaled.resize( newWidth );
				toWriteWidth = ip.getWidth();
				toWriteHeight = ip.getHeight();
			}

			if( imageType == ImagePlus.GRAY16 || imageType == ImagePlus.GRAY32 )
				ip.setMinAndMax( limits[0], limits[1] );

			int bitDepth = imagePlus.getBitDepth();

			if( bitDepth == 8 ) {

				if( lut == null ) {
					bi = new BufferedImage(toWriteWidth, toWriteHeight, BufferedImage.TYPE_BYTE_GRAY);
				} else {
					int size = lut.getMapSize();
					byte [] reds = lut.getReds();
					byte [] greens = lut.getGreens();
					byte [] blues = lut.getBlues();
					IndexColorModel cm = null;
					cm = new IndexColorModel(8,size,reds,greens,blues);
					bi = new BufferedImage(toWriteWidth, toWriteHeight, BufferedImage.TYPE_BYTE_INDEXED, cm);
				}

			} else {

				bi = new BufferedImage(toWriteWidth, toWriteHeight, BufferedImage.TYPE_INT_RGB );

			}

			Graphics2D g = (Graphics2D)bi.getGraphics();
			Image imageToDraw = ip.createImage();
			g.drawImage(imageToDraw, 0, 0, null);
			File f = new File(outputFileName);
			ImageIO.write(bi, "png", f);
		}
	}
}
