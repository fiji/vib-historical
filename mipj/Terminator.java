package mipj;

import java.awt.*;
import java.awt.event.*;

public class Terminator extends WindowAdapter
{

	private MIPMainWindow w;

	public Terminator( MIPMainWindow w )
	{
		this.w = w;
	}

	public void windowClosing(WindowEvent e)
	{
		w.closeRT();
		w.dispose();
	}
}