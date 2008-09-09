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

     make fastpngtest
*/

package fastpng;

import ij.ImagePlus;
import ij.ImageStack;

import util.BatchOpener;

public class Test_Native_PNG_Writer {

	public static void main( String [] arguments ) {

		ImagePlus [] images = BatchOpener.open("test-images/tidied-mhl-62yxUAS-lacZ0-reduced.tif");
		ImageStack stack = images[0].getStack();

		byte [] pixelData8Bit = (byte[])stack.getPixels(stack.getSize()/2);

		Native_PNG_Writer writer = new Native_PNG_Writer();

		writer.write8BitPNG( pixelData8Bit, stack.getWidth(), stack.getHeight(), null, null, null, "test-grey.png");

		byte [] reds   = new byte[256];
		byte [] greens = new byte[256];
		byte [] blues  = new byte[256];

		for( int i = 0; i < 256; ++i ) {
			reds[i] = blues[i] = (byte)i;
			greens[i] = 0;
		}

		writer.write8BitPNG( pixelData8Bit, stack.getWidth(), stack.getHeight(), reds, greens, blues, "test-colour.png");

		// ------------------------------------------------------------------------
		// Now write an RGB image (hopefully):

		images = BatchOpener.open("test-images/71yAAeastmost-RGB.tif");
		stack = images[0].getStack();

		int [] pixelDataRGB = (int[])stack.getPixels(stack.getSize()/2);

		writer.writeFullColourPNG( pixelDataRGB, stack.getWidth(), stack.getHeight(), "test-RGB.png");

	}
}
