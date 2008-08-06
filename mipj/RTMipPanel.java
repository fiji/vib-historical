/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package mipj;
import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;


public class RTMipPanel extends Panel implements MouseMotionListener, MouseListener
{
	private Applet applet;
	private RealTimeMIP r = null;
	private Matrix4f m;
	private float rotx, roty;
	private MouseEvent from;
	private int paintcount = 0;

	public Matrix4f getRot() {
		return new Matrix4f(m);
	}

	public RTMipPanel( Applet a ) {

		try {

			applet = a;

			m = new Matrix4f();
			m.setIdentity();

			rotx = roty = 0.0f;

			addMouseMotionListener(this);
			addMouseListener(this);

		} catch(Exception e) {
			e.printStackTrace();
		}

	}

	public void clearDataset( ) {
		r = null;
	}

	public void loadDataset( URL loc ) throws Exception {

		m.setIdentity();

		URLConnection uc = loc.openConnection();

		ObjectInputStream ois = new ObjectInputStream( uc.getInputStream() );

		r = (RealTimeMIP) ois.readObject();

		ois.close();

		repaint();

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

	public void update(Graphics g) {
		paint(g);
	}

	public void paint(Graphics g) {

		if ( r != null ) {


			m.rotByY( MIP.ONEDEGREE * -roty );

			m.rotByX( MIP.ONEDEGREE * rotx );

				 /*float[] f = m.getEuler();

				 System.out.print( f[0] + " " + f[1] + " " + f[2] + "            \r");*/

			r.drawImage( g, m, 0, 0, 0);

			roty = 0.0f;
			rotx = 0.0f;

		}

	}

}
