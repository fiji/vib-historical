/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package util;

import ij.plugin.PlugIn;

import ij.Macro;

public class Just_Sleep implements PlugIn {
	
	public void run(String arg0) {

		String macroArguments = Macro.getOptions();

		String client = Macro.getValue(
			macroArguments,
			"client",
			null );

		System.out.println("Got client: "+client);	

		System.out.println("About to sleep for 20 seconds.");
		try {
			Thread.sleep(20000);
		} catch( InterruptedException e ) {
		}
		System.out.println("Done sleeping.");
	}	
}
