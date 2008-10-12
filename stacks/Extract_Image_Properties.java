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

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageWindow;
import ij.Macro;
import ij.LookUpTable;
import ij.plugin.PlugIn;
import ij.measure.Calibration;

import java.util.Hashtable;
import java.util.Enumeration;
import java.io.*;

import amira.AmiraParameters;

import org.jvyamlb.YAML;
import org.jvyamlb.YAMLFactory;
import org.jvyamlb.DefaultYAMLFactory;
import org.jvyamlb.Serializer;
import org.jvyamlb.YAMLConfig;
import org.jvyamlb.Representer;
import org.jvyamlb.SafeRepresenterImpl;
import org.jruby.util.ByteList;

import server.Job_Server;

import java.io.File;
import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.Set;
import org.imagearchive.lsm.toolbox.Reader;
import org.imagearchive.lsm.toolbox.info.CZ_LSMInfo;
import org.imagearchive.lsm.toolbox.info.scaninfo.Track;
import org.imagearchive.lsm.toolbox.info.scaninfo.Recording;
import org.imagearchive.lsm.toolbox.info.scaninfo.DetectionChannel;
import org.imagearchive.lsm.toolbox.info.scaninfo.IlluminationChannel;

import util.ChannelDataLSM;

public class Extract_Image_Properties implements PlugIn {

	static String typeToString(int type) {
		switch(type) {
		case ImagePlus.GRAY8:
			return "GRAY8";
		case ImagePlus.GRAY16:
			return "GRAY16";
		case ImagePlus.GRAY32:
			return "GRAY32";
		case ImagePlus.COLOR_256:
			return "COLOR_256";
		case ImagePlus.COLOR_RGB:
			return "COLOR_RGB";
		default:
			return "Unknown";
		}
	}

	public static String getStringValueSafe( AbstractMap m, String key ) {
		Object result = m.get(key);
		if( result == null )
			return "";
		else
			return (String)result;
	}

	public void run( String pluginArguments ) {

		String realArguments = null;
		String macroArguments = Macro.getOptions();

		if( (macroArguments == null) || (macroArguments.equals("")) ) {

			if( (pluginArguments == null) || (pluginArguments.equals("")) ) {
				throw new RuntimeException("No parameters supplied either as macro options or a plugin argument.");
			} else {
				realArguments = pluginArguments;
			}

		} else {
			realArguments = macroArguments;
		}

		String dataRoot = Macro.getValue(
			realArguments,
			"dataroot",
			"");

		if( dataRoot.equals("") )
			throw new RuntimeException("No dataroot parameter was supplied");

		String relativeFilename = Macro.getValue(
			realArguments,
			"filename",
			"");

		if( relativeFilename.equals("") )
			throw new RuntimeException("No macro parameter 'filename' supplied");

		String filename = dataRoot + File.separator + relativeFilename;
		File filenameFile = new File( filename );
		if( ! filenameFile.exists() )
			throw new RuntimeException( "The uploaded and copied filename '"+
						    filename+"' doesn't seem to exist. "+
						    "(The absolute version is '"+
						    filenameFile.getAbsolutePath()+"'" );

		String original_filename = Macro.getValue(
			realArguments,
			"original",
			"");

		String destinationDirectory = Macro.getValue(
			macroArguments,
			"directory",
			"");


		if( destinationDirectory.equals("") )
			throw new RuntimeException("No macro parameter 'directory' supplied");

		Hashtable<String,Object> properties=new Hashtable<String,Object>();

		String loaderUsed = null;

		BatchOpener.ChannelsAndLoader cal = null;
		try {
			cal = BatchOpener.openToChannelsAndLoader( filename );
		} catch( BatchOpener.ImageLoaderException e ) {
		}

		if( cal == null )
			throw new RuntimeException("Couldn't open the file: "+filename);

		loaderUsed = cal.loaderUsed;
		properties.put( "loader", cal.loaderUsed );

		ImagePlus [] imps = cal.channels;

		properties.put( "channels",  imps.length);

		int width  = imps[0].getWidth();
		int height = imps[0].getHeight();
		int depth  = imps[0].getStackSize();

		properties.put( "width",     width);
		properties.put( "height",    height);
		properties.put( "depth",     depth);
		properties.put( "imagej-type", typeToString(imps[0].getType()) );
		properties.put( "original-filename", original_filename );

		Calibration c = imps[0].getCalibration();

		if( c != null ) {
			properties.put( "sample-spacing-x",      c.pixelWidth );
			properties.put( "sample-spacing-y",      c.pixelHeight );
			properties.put( "sample-spacing-z",      c.pixelDepth );
			properties.put( "sample-spacing-unit",   c.getUnit() );
			properties.put( "sample-spacing-units",  c.getUnits() );
		}

		// Now write out the properties we've discovered as YAML:

		File outputDirectoryFile = new File(destinationDirectory);

		if( ! outputDirectoryFile.exists() ) {
			throw new RuntimeException("The output directory ('"+destinationDirectory+"') didn't exist");
		}

		String outputFilename = outputDirectoryFile.getAbsolutePath() + File.separator + "properties-generic";

		PrintWriter pw = null;
		try {
			pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputFilename),"UTF-8"));
		} catch( Exception e ) {
			throw new RuntimeException("Couldn't open the file: '"+outputFilename+"' for writing; exception was: "+e);
		}

		YAMLFactory y=new SafeYAMLFactory();
		ByteList bl=YAML.dump(properties,y);
		String d=bl.toString();

		pw.print(d);
		pw.close();

		// Now extract some thumbnails:

		int maxThumbnailDimension = 180;

		float scale;
		if( width > height ) {
			scale = (float)maxThumbnailDimension / width;
		} else {
			scale = (float)maxThumbnailDimension / height;
		}

		int midSlice = depth / 2;

		File thumbnailDirectory = new File( destinationDirectory +
						    File.separator +
						    "thumbnails" );

		try {

			thumbnailDirectory.mkdir();

			for( int i = 0; i < imps.length; ++i ) {
				Unpack.unpack( imps[i],
					       i,
					       imps[i].createLut(),
					       scale,
					       midSlice,
					       midSlice,
					       thumbnailDirectory.getAbsolutePath() );
			}
		} catch( IOException e ) {
			throw new RuntimeException("Writing thumbnails failed: "+e);
		}

		// close all ImagePlus objects

		for( int i = 0; i < imps.length; ++i ) {
			imps[i].close();
		}

		boolean verbose = false;

		// Now some special cases for different file types:

		if(verbose) System.out.println("Considering loader-specific properties...");

		if( loaderUsed.equals("LSM_Toolbox") ) {
			if(verbose) System.out.println("LSM_Toolbox specific case:");

			// Then we should write out some additional information:
			String outputFilenameLSM = outputDirectoryFile.getAbsolutePath() + File.separator + "properties-LSM";

			properties=new Hashtable<String,Object>();

			for( int i = 0; i < 1; ++i ) {

				File f = new File(filename);
				Reader reader = new Reader();
				CZ_LSMInfo cz = reader.readCz(f);

				if (cz.scanInfo.recordings.size() == 0) {
					properties.put("Error","No recordings found in the file.");
					break;
				}
				if (cz.scanInfo.recordings.size() > 1) {
					properties.put ("Error","We don't currently support multiple recordings in a single file.");
					break;
				}

				// Go through the scan info:
				Recording r = (Recording) cz.scanInfo.recordings.get(0);

				properties.put( "objective",   getStringValueSafe( r.records, "ENTRY_OBJECTIVE" ) );
				properties.put( "notes",       getStringValueSafe( r.records, "ENTRY_NOTES" ) );
				properties.put( "description", getStringValueSafe( r.records, "ENTRY_DESCRIPTION" ) );
				properties.put( "name",        getStringValueSafe( r.records, "ENTRY_NAME" ) );
				properties.put( "user",        getStringValueSafe( r.records, "USER" ) );

				ChannelDataLSM channels[] = ChannelDataLSM.getChannelsData(f);

				Hashtable[] channelsAsArrayOfHashes = new Hashtable[channels.length];
				for( int ci = 0; ci < channels.length; ++ci )
					channelsAsArrayOfHashes[ci] = channels[ci].toHash();

				properties.put( "channels", channelsAsArrayOfHashes );
			}

			pw = null;
			try {
				pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputFilenameLSM),"UTF-8"));
			} catch( Exception e ) {
				throw new RuntimeException("Couldn't open the file: '"+outputFilename+"' for writing; exception was: "+e);
			}

			bl=YAML.dump(properties,y);
			d=bl.toString();

			pw.print(d);
			pw.close();

		}

		Job_Server.finishDirectory(destinationDirectory);
	}
}
