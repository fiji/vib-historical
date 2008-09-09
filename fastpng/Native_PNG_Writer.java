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

public class Native_PNG_Writer {

	public native boolean write8BitPNG( byte [] pixelData, int width, int height, byte [] reds, byte [] greens, byte [] blues, String outputFilename );

	public native boolean writeFullColourPNG( int [] pixelData, int width, int height, String outputFilename );

	static {
		System.loadLibrary("fastpng");
	}

}
