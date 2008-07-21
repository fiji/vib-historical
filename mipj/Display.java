/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package mipj;

import java.awt.*;
import java.awt.event.*;
import ij.IJ;

public class Display
	extends Frame
	implements MouseMotionListener, MouseListener
{

	RealTimeMIP mip[];
	Panel pan;
	Matrix4f mat;

	float rotx, roty;
	MouseEvent from;

	int topLeftX, topLeftY;

	public Display(int x, int y, RealTimeMIP mip[], MIPMainWindow mmw ) {

		super("Real-Time View");

		addWindowListener( new Terminate(this, mmw) );

		this.mip = mip;

		mat = new Matrix4f();

		addMouseMotionListener(this);
		addMouseListener(this);

		setVisible( true );

		Insets in = getInsets();

		topLeftX = in.left;
		topLeftY = in.top;

		int ylen = (in.bottom + in.top);
		int xlen = (in.left + in.right);

		setSize( x + xlen, y + ylen );

	}

	public Matrix4f getRot() {
		return mat;
	}

	public void update(Graphics g) {
		paint(g);
	}

	public void paint(Graphics gfx) {

		mat.rotByY( MIP.ONEDEGREE * -roty );

		mat.rotByX( MIP.ONEDEGREE * rotx );

		//float[] f = m.getEuler();

		//System.out.print( f[0] + " " + f[1] + " " + f[2] + "            \r");

		mip[0].drawImage( gfx, mat, 0, topLeftX, topLeftY );

		roty = 0.0f;
		rotx = 0.0f;

	}

	// MOUSE HANDLERS

	public void mouseMoved(MouseEvent e) {
	}

	public void mouseDragged(MouseEvent e) {
		// change the picture to display

		rotx = (e.getY() - from.getY());

		roty = (e.getX() - from.getX());

		from = e;

		repaint();

	}

	public void mousePressed(MouseEvent e) {
		from = e;//System.out.println(e);
	}

	public void mouseReleased(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {//System.out.println(e);
	}

	public void mouseExited(MouseEvent e) {//System.out.println(e);
	}

	public void mouseClicked(MouseEvent e) {//System.out.println(e);
	}

	public static void main(String[] args) {
		//Display gui = new Display();
	}

}

class Terminate extends WindowAdapter
{

	private Window w;
	private MIPMainWindow mmw;

	public Terminate( Window w, MIPMainWindow mmw) {
		this.w = w;
		this.mmw = mmw;
	}

	public void windowClosing(WindowEvent e) {
		if (IJ.getApplet() == null ) {
			mmw.closeRT();
			w.dispose();
		}
		//System.exit(0); // a bit excessive!
	}
}
