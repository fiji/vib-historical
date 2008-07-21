/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

import mipj.*;

import ij.plugin.PlugIn;

public class MipJ_ implements PlugIn
{
	public void run(String arg) {
		new MIPMainWindow(arg);
        }
}
