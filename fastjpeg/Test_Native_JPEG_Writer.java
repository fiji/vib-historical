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

     make fastjpegtest
*/

package fastjpeg;

import ij.ImagePlus;
import ij.ImageStack;

import util.BatchOpener;

public class Test_Native_JPEG_Writer {

	public static void main( String [] arguments ) {

		Native_JPEG_Writer writer = new Native_JPEG_Writer();

		// ------------------------------------------------------------------------
		// Write an RGB image (hopefully):

		ImagePlus [] images = BatchOpener.open("test-images/71yAAeastmost-RGB.tif");
	        ImageStack stack = images[0].getStack();

		int [] pixelDataRGB = (int[])stack.getPixels(stack.getSize()/2);

		writer.writeFullColourJPEG( pixelDataRGB, stack.getWidth(), stack.getHeight(), "test-RGB.jpeg");

	}
}
