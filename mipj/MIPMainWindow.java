package mipj;
import java.awt.*;
import java.awt.event.*;
import ij.plugin.frame.*;
import ij.ImagePlus;
import ij.WindowManager;
import java.util.HashMap;
import ij.IJ;
import ij.process.ImageProcessor;
import java.net.*;
import java.io.*;

public class MIPMainWindow
extends PlugInFrame
implements ActionListener, ItemListener
{

	Discard disc;
	RealTimeMIP rtmip[];
	private boolean generating;
	MIPMainWindow copy;
	private int rtx, rty;

	public static boolean running = false;

	 Display disp = null;
	 Matrix4f rot;
	 boolean rotset;

     Label labelStack;
 	 Label labelFilename;
 	 Label labelZScale;

	 Choice choiceStack;
	 HashMap stackIDs;

	 TextField textFilename;
	 TextField textZScale;
	 Choice choiceType;
	 Choice choiceInterp;
	 TextField textInc;
	 Checkbox chkDMIP;
	 TextField textThreshold;
	 TextField textResFac;
	 TextField textNumFrames;
	 TextField textStart[];
	 TextField textEnd[];
	 Button buttonStart;
	 Button buttonRender;
	 Checkbox chkResize;
	 Checkbox chkOne;
	 Button buttonRT;

	 String m_url;
	 private boolean m_isApplet;

	 public MIPMainWindow(String url)
	 {

		super("MipJ");

		if ( url != null && url.length() > 1 )
			m_isApplet = true;
		else
			m_isApplet = false;

		Panel pRow[] = new Panel[16]; // make every row a panel

		for( int i = 0 ; i < pRow.length ; ++i )
			pRow[i] = new Panel();

		copy = this;
		running = true;

		addWindowListener( new Terminator(this) );


		setLayout(new GridLayout(16, 1) );
		setSize(460,460); // Size the outercontainer

		labelStack = new Label("Select stack: ");


		choiceStack = new Choice();

		stackIDs = new HashMap();

		populateStackChoice();

		labelFilename = new Label("Filename prefix: ");
		textFilename = new TextField( "projection", 32 );
		labelZScale = new Label("ZScale: ");
		textZScale = new TextField( "1.0" , 5 );

		choiceInterp = new Choice();
		choiceInterp.add("Trilinear");
		choiceInterp.add("Nearest Neighbour");
		textInc = new TextField( "1.0", 5 );
		chkDMIP = new Checkbox( "", false );
		textThreshold = new TextField( "255", 5 );
		textResFac = new TextField( "1.0", 5 );

		textStart = new TextField[3];
		textEnd = new TextField[3];
		textNumFrames = new TextField( "1", 4 );
		for(int i = 0 ; i < 3 ; ++i )
		{
			textStart[i] = new TextField("0.0",3);
			textEnd[i] = new TextField("0.0",3);
		}
		buttonStart = new Button("RT Lock");
		buttonStart.addActionListener( this );
		buttonStart.setEnabled( false );

		buttonRender = new Button( "Render" );
		buttonRender.addActionListener( this );

		chkResize = new Checkbox( "", true );
		chkOne = new Checkbox( "", true );
		buttonRT = new Button( "Real-Time View" );
		buttonRT.addActionListener( this );

		// Add the stuff to the panels

		if ( !m_isApplet )
		{
			pRow[0].setLayout(new GridLayout(1,2)); pRow[0].add(labelStack); pRow[0].add(choiceStack);
			pRow[1].setLayout(new GridLayout(1,2)); pRow[1].add(labelFilename); pRow[1].add(textFilename);
			pRow[2].setLayout(new GridLayout(1,2)); pRow[2].add(labelZScale); pRow[2].add(textZScale);
		}

		choiceType = new Choice();
		choiceType.add("Splatting");
		choiceType.add("Raycasting");
		pRow[3].setLayout(new GridLayout(1,2)); pRow[3].add(new Label("Projection Type")); pRow[3].add( choiceType );

		pRow[4].setLayout(new GridLayout(1,2)); pRow[4].add( new Label("Interpolation") ); pRow[4].add( choiceInterp );
		pRow[5].setLayout(new GridLayout(1,2)); pRow[5].add( new Label("Ray Increment") ); pRow[5].add( textInc );
		pRow[6].setLayout(new GridLayout(1,2)); pRow[6].add( new Label("Depth-MIP") ); pRow[6].add( chkDMIP );
		pRow[7].setLayout(new GridLayout(1,2)); pRow[7].add( new Label("Termination Threshold") ); 	pRow[7].add( textThreshold );
		pRow[8].setLayout(new GridLayout(1,2)); pRow[8].add( new Label("Resolution Factor") ); 	pRow[8].add( textResFac );


		pRow[9].setLayout(new GridLayout(1,2)); pRow[9].add( new Label("No. frames") );		pRow[9].add( textNumFrames );
		pRow[10].setLayout(new GridLayout(1,5));pRow[10].add( new Label("Initial Rotation (xyz)") );
		for( int i = 0 ; i < 3 ; ++i )
			pRow[10].add( textStart[i] );

		if ( !m_isApplet )
			pRow[10].add( buttonStart );

		pRow[11].setLayout(new GridLayout(1,5)); pRow[11].add( new Label("Rotate By (xyz)") );
		for( int i = 0 ; i < 3 ; ++i )
			pRow[11].add( textEnd[i] );
		pRow[11].add( new Label("") );

		pRow[12].setLayout(new GridLayout(1,1));pRow[12].add( buttonRender );

		if ( !m_isApplet )
		{

			pRow[13].setLayout(new GridLayout(1,2)); pRow[13].add( new Label("Resize to 256 (recommended)") );	pRow[13].add( chkResize );
			pRow[14].setLayout(new GridLayout(1,2)); pRow[14].add( new Label("Use 1/24th dataset") ); pRow[14].add( chkOne );
			chkOne.setEnabled(false);
			pRow[15].setLayout(new GridLayout(1,1)); pRow[15].add( buttonRT );

		}

		for(int i = 0 ; i < pRow.length ; ++i )
			add( pRow[i] );

		raycasting( false );

		choiceType.addItemListener( this );

		setVisible(true);

		if ( m_isApplet )
		{
			m_url = url;
			createRealTimeAppletWindow();
		}



     }

     private void raycasting(boolean b)
     {
		 choiceInterp.setEnabled( b );
		 textInc.setEnabled( b );
		 chkDMIP.setEnabled( b );
		 textThreshold.setEnabled( b );
	 }

     public synchronized void closeRT()
     {

		 if (disp != null)
		 {
			disp.dispose();
		 }

			System.gc();
			buttonRT.setLabel("Real-Time View");
			disp = null;
			for(int i = 0 ; i < 3 ; ++i )
				textStart[i].setEnabled(true);
			buttonStart.setEnabled(false);
			rotset = false;
			buttonStart.setLabel("RT Lock");


	 }

     /** Populates the listbox containing all of the stacks */

     private void populateStackChoice()
     {

		choiceStack.removeAll();

		int[] idList = WindowManager.getIDList(); // find all windows

		if ( idList != null ) // populate the stack chooser
		{

			for (int i = 0; i < idList.length; i++)
			{
				ImagePlus imp = WindowManager.getImage(idList[i]);

				if (imp instanceof ImagePlus && imp.getStackSize() > 1)
				{
					choiceStack.add(imp.getTitle());
					stackIDs.put( imp.getTitle(), new Integer(idList[i]) );
				}
			}

			// set to the currently selected stack

			if ( stackIDs.containsValue( new Integer(WindowManager.getCurrentImage().getID()) ) )
			{
				choiceStack.select( WindowManager.getCurrentImage().getTitle() );
			}

		}

	 }


	/** Create real time MIP window */

	private synchronized void createRealTimeAppletWindow()
	{

		generating = true;

		try
		{
			Thread hack = new Thread(new Runnable()
			{
				public void run()
				{

					try
					{

						URL dataLoc = new URL( m_url );

						IJ.showStatus("Loading real-time dataset...");

						java.io.ObjectInputStream ois = new java.io.ObjectInputStream( dataLoc.openStream() );

						IJ.showStatus("");

						RealTimeMIP rtmip[] = new RealTimeMIP[1];

						rtmip[0] = (RealTimeMIP) ois.readObject();

						ois.close();

						disp = new Display( rtmip[0].getX(), rtmip[0].getY(), rtmip, copy );

						buttonStart.setEnabled(false);
						buttonRT.setEnabled( false );


					}
					catch(Exception ioe)
					{
						closeRT();
						IJ.error("Could not open url " + m_url );
						ioe.printStackTrace();
					}

					generating = false;

				}
			});

			hack.start();

		}
		catch(OutOfMemoryError ooe)
		{
			closeRT();
			generating = false;
			IJ.error("Out of memory: try java -mxNm where N is MB of memory");
		}
		catch(Exception e)
		{
			e.printStackTrace();
			closeRT();
			generating = false;
			IJ.error("Failed to create real-time window");
		}

	}

	private synchronized void createRealTime()
	{


		try{

		if ( generating ) return;

		generating = true;

		// create appropriate realtime mip objects
		// create a Display object with an array to these
		// keep a reference to it here, to monitor what goes on!

		String st = getStackName();

		if (st == null)
			throw new Exception();

		ImagePlus img = getImage( st );

		if ( img == null )
			throw new Exception();

		ImageStack ist = getImageStack(img);

		if ( ist == null )
			throw new Exception();

		rtx = img.getWidth();
		rty = img.getHeight();

		float max = (float) Math.max( img.getWidth(), img.getWidth() );

		float zscale = getZScale();

		if ( chkResize.getState() && max != 256.0f )
		{
			float ratio = 256.0f / max;
			IJ.showStatus("Scaling");
			ist.scale( ratio );
			IJ.showStatus("");
			rtx = ist.getWidth();
			rty = ist.getHeight();

			zscale *= (256.0f / max); // change the zscale to reflect any resizing
		}

		if ( zscale != 1.0f)
		{
			IJ.showStatus("ZScaling");
			ist.zScale( zscale );
			IJ.showStatus("");
		}

		Discard.plugin = true;

		disc = new Discard( ist );

		rtmip = new RealTimeMIP[24];

		Thread hack = new Thread(new Runnable()
		{
			public void run()
			{
				disc.discardNN();

				rtmip[0] = disc.calculate(Discard.FRONTMAIN, Discard.LOWER);

				disp = new Display( rtx, rty, rtmip, copy );

				buttonStart.setEnabled(true);

				buttonRT.setLabel("Close Real-Time View");

				generating = false;

			}
		});

		hack.start();

		}
		catch(OutOfMemoryError ooe)
		{
			closeRT();
			generating = false;
			IJ.error("Out of memory: try java -mxNm where N is MB of memory");
		}
		catch(Exception e)
		{
			e.printStackTrace();
			closeRT();
			generating = false;
			IJ.error("Failed to create real-time window");
		}

	}

	private String getStackName()
	{

		String thing = choiceStack.getSelectedItem();

		if ( thing == null )
		{
			IJ.error("No stack selected");
			return null;
		}

		return thing;

	}

	private ImagePlus getImage(String s)
	{

		Integer id = (Integer) stackIDs.get( s );

		ImagePlus img = WindowManager.getImage(id.intValue());

		if ( img == null )
		{
			IJ.error("Stack selected no longer exists!");
			return null;
		}

		return img;

	}

	private ImageStack getImageStack( ImagePlus img )
	{

		// Create a stack for mipj

		ImageStack ist;

		try
		{
			ist = new ImageStack( img.getStack() );
		}
		catch(ClassCastException cce)
		{
			IJ.error("Stack must be greyscale");
			return null;
		}

		return ist;

	}

	private float getZScale() throws NumberFormatException
	{

		float zscale;

		try
		{
			zscale = Float.parseFloat( textZScale.getText() );
			if (zscale <= 0.0f)
				throw new NumberFormatException();
		}
		catch( NumberFormatException nfe )
		{
			IJ.error("ZScale invalid");
			throw new NumberFormatException();
		}

		return zscale;

	}

     private synchronized void render()
     {

		try
		{

		int type = MIPDriver.SPLATTING;

		// projection type

		if ( choiceType.getSelectedItem().equals("Splatting") )
			type = MIPDriver.SPLATTING;
		else
			type = MIPDriver.RAYCASTING;

		// Obtain the stack to be processed

		String thing = getStackName();

		if ( thing == null )
			return;

		ImagePlus img = getImage( thing );

		if (img == null)
			return;

		ImageStack ist = getImageStack(img);

		if ( ist == null )
			return;

		// The resolution scale

		float scale = 1.0f;
		int resx = img.getWidth();
		int resy = img.getHeight();

		try
		{
			scale = Float.parseFloat( textResFac.getText() );
			resx = Math.round(resx * scale);
			resy = Math.round(resy * scale);

			if ( resx < 1 || resy < 1 )
				throw new NumberFormatException();

		}
		catch( NumberFormatException nfe )
		{
			IJ.error("Resolution scale invalid");
			return;
		}

		// if splatting, then scale the actual stack (memory expensive)

		if ( type == MIPDriver.SPLATTING && scale != 1.0f )
		{
			IJ.showStatus("Scaling");
			ist.scale( scale );
			IJ.showStatus("");
		}

		// Get the ZScale

		float zscale;

		try
		{
			zscale = getZScale();
		}
		catch( NumberFormatException nfe )
		{
			return;
		}

		// Get the ray-cast increment

		float rayinc = 1.0f;

		try
		{
			rayinc = Float.parseFloat( textInc.getText() );
			if ( type == MIPDriver.RAYCASTING && rayinc <= 0.0f )
				throw new NumberFormatException();

		}
		catch( NumberFormatException nfe )
		{
			IJ.error("Ray increment invalid");
			return;
		}

		// Get the threshold

		int threshold = 255;

		try
		{
			threshold = Integer.parseInt( textThreshold.getText() );
			if ( type == MIPDriver.RAYCASTING && (threshold < 1 || threshold > 255) )
				throw new NumberFormatException();
		}
		catch( NumberFormatException nfe )
		{
			IJ.error("Threshold invalid");
			return;
		}

		// Get the number of frames

		int nframes = 1;

		try
		{
			nframes = Integer.parseInt( textNumFrames.getText() );
			if ( nframes < 1 )
				throw new NumberFormatException();
		}
		catch( NumberFormatException nfe )
		{
			IJ.error("Number of frames invalid");
			return;
		}

		// Initial rotation

		Matrix4f rotation;

		if (rotset)
		{
			rotation = new Matrix4f(rot); // it's the real-time matrix
		}
		else
		{

			rotation = new Matrix4f();

			try
			{
				rotation.rotByX( Float.parseFloat( textStart[0].getText() ) * MIP.ONEDEGREE );
				rotation.rotByY( Float.parseFloat( textStart[1].getText() ) * MIP.ONEDEGREE );
				rotation.rotByZ( Float.parseFloat( textStart[2].getText() ) * MIP.ONEDEGREE );
			}
			catch( NumberFormatException nfe )
			{
				IJ.error("Start position invalid");
				return;
			}

		}

		// Amount to rotate

		float anim[] = new float[3];

		try
		{

			for(int i = 0 ; i < 3 ; ++i )
			{
				anim[i] = Float.parseFloat( textEnd[i].getText() );
			}

		}
		catch( NumberFormatException nfe )
		{
			IJ.error("Rotation invalid");
			return;
		}

		// Type of interpolation

		int interp = MIP.TRILINEAR;

		if ( choiceInterp.getSelectedItem().equals( "Trilinear" ) )
		{
			interp = MIP.TRILINEAR;
		}
		else
			interp = MIP.NEARESTNEIGHBOUR;

		MIPDriver md = new MIPDriver(
			type,
			resx,
			resy,
			interp,
			scale,
			zscale,
			rayinc,
			chkDMIP.getState(),
			threshold,
			nframes,
			ist,
			rotation,
			anim,
			textFilename.getText());

		md.start();

		}
		catch(OutOfMemoryError oom)
		{
			IJ.error("Insufficient memory: try java -mxNm where N is MB of memory");
		}

	 }

	 private void requestByURL()
	 {

		 try
		 {

			java.applet.Applet ap = IJ.getApplet();
			String strUrl = new String(ap.getDocumentBase().toString());

			float angle[] = disp.getRot().getEuler();

			strUrl = strUrl + "mipj.php?stck=" + ap.getParameter("MIPJSTACK") + "&s=" + ap.getParameter("MIPJZSCALE") +
			"&x=" + Math.round(angle[0]) + "&y=" + Math.round(angle[1]) + "&z=" + Math.round(angle[2]);

			ap.getAppletContext().showDocument( new URL(strUrl) , "_blank" );

		}
		catch(Exception e)
		{
			e.printStackTrace();
			IJ.error("Unexpected error creating URL");
		}
	 }

	public void actionPerformed(ActionEvent ae)
	{

		Object reason = ae.getSource();

		if ( reason.equals( buttonStart ) )
		{

			if ( buttonStart.getLabel().equals("RT Lock") && disp != null )
			{
				rotset = true;
				rot = disp.getRot();
				buttonStart.setLabel("Unlock");
				for(int i = 0 ; i < 3 ; ++i )
					textStart[i].setEnabled(false);
			}
			else
			{
				rotset = false;
				buttonStart.setLabel("RT Lock");
				for(int i = 0 ; i < 3 ; ++i )
					textStart[i].setEnabled(true);
			}

		}
		else if ( reason.equals( buttonRender ) )
		{
			if ( !m_isApplet )
				render();
			else
				requestByURL();
		}
		else if ( reason.equals( buttonRT ) )
		{

			if ( disp == null )
			{

				createRealTime();

			}
			else
			{
				closeRT();
			}

		}

	}

	public void itemStateChanged( ItemEvent reason )
	{

		if ( choiceType.getSelectedItem().equals("Raycasting") )
		{
			raycasting(true);
		}
		else
			raycasting(false);

	}

}
