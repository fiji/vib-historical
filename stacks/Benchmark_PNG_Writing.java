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

/*
   Test this with:

     ./fiji -eval 'run("Benchmark PNG Writing");'

*/

package stacks;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.LookUpTable;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.ColorProcessor;

import java.text.DecimalFormat;
import java.io.*;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.IndexColorModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.Iterator;

import javax.imageio.ImageIO;

import util.BatchOpener;
import util.Limits;
import server.Job_Server;

public class Benchmark_PNG_Writing implements PlugIn {

	String testFilenames[] = {
		"/home/mark/scans/71yAAeastmost.lsm",
		"/home/mark/scans/71yAAeastmost-RGB.tif"
	};

	String outputDirectory = "/tmp/bored/";

	long lastBenchmarkedTime = System.currentTimeMillis();

	void v() {
		b("No message supplied");
	}

	static final DecimalFormat f5dot5 = new DecimalFormat("00000.00000");
	static final DecimalFormat i3 = new DecimalFormat("000");

	void b(String message) {
		long now = System.currentTimeMillis();
		float difference = (now - lastBenchmarkedTime) / 1000.0f;
		System.out.println(f5dot5.format(difference)+" ["+message+"]");
		lastBenchmarkedTime = now;
	}

	public void run( String ignored ) {

		try {
			Thread.sleep(2500);
		} catch ( InterruptedException e ) { }

		File outputDirectoryFile = new File(outputDirectory);
		outputDirectoryFile.mkdir();

		for( int tfi = 0; tfi < testFilenames.length; ++tfi ) {

			b("Trying filename: "+testFilenames[tfi]);

			// First load the image to an array of ImagePlus objects:
			ImagePlus [] images = BatchOpener.open( testFilenames[tfi] );
			if( images == null || images.length == 0 )
				throw new RuntimeException("Failed to load: "+testFilenames[tfi]);

			File testFile = new File(testFilenames[tfi]);

			for( int c = 0; c < images.length; ++c ) {

				int imageType = images[c].getType();
				if( imageType == ImagePlus.GRAY16 || imageType == ImagePlus.GRAY32 ) {
					float [] limits = Limits.getStackLimits(images[c]);
					ImageProcessor p = images[c].getProcessor();
					p.setMinAndMax(limits[0],limits[1]);
				}
				int bitDepth = images[c].getBitDepth();

				String channelString = i3.format(c);

				ImagePlus imagePlus = images[c];

				int newWidth = imagePlus.getWidth();
				int newHeight = imagePlus.getHeight();
				int depth = imagePlus.getStackSize();

				ImageStack stack = imagePlus.getStack();

				for( int z = 0; z < depth; ++z ) {

					ImageProcessor ip = stack.getProcessor(z+1);

					String sliceString = i3.format(z);
					String outputFileName =
						outputDirectory +
						"/" +
						channelString +
						"-" + sliceString + "-" +
						testFile.getName() + ".png";

					LookUpTable lut = imagePlus.createLut();
					IndexColorModel cm = null;
					if( lut != null ) {
						int size = lut.getMapSize();
						if( size > 0 ) {
							byte [] reds = lut.getReds();
							byte [] greens = lut.getGreens();
							byte [] blues = lut.getBlues();
							cm = new IndexColorModel(8,size,reds,greens,blues);
						}
					}
					BufferedImage bi;
					if( bitDepth == 8 || bitDepth == 16 ) {
						if( cm == null ) {
							bi = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_BYTE_GRAY);
						} else {
							bi = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_BYTE_INDEXED, cm);
						}
					} else {
						bi = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
					}
					Graphics2D g = (Graphics2D)bi.getGraphics();
					Image imageToDraw = ip.createImage();
					g.drawImage(imageToDraw, 0, 0, null);
					File f = new File(outputFileName);
					try {
						ImageIO.write(bi, "png", f);
					} catch( IOException e ) {
						throw new RuntimeException(e.toString());
					}	
					b("Done writing PNG files");
				}
			}
		}
	}
}
