package mipj;
import java.applet.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import java.util.Vector;

 public class DisplayApplet
 extends Applet
 implements ItemListener, ActionListener
 {

	private RTMipPanel upper;
	private Panel lower;
	private Frame outer;

	private Label statusLabel;
	private Choice stackChoice;
	private Choice algChoice;
	private Choice interpChoice;
	private Button renderButton;
	private TextField rayIncText;
	private CheckboxGroup scaleGroup;

	private Vector paramVec;
	private int currentIndex = -1;

	private final static int PARAMSPERDATASET = 4;

	private void parseParameters()
	{

		paramVec = new Vector();

		int i = 0;
		String s = getParameter( new Integer(i).toString() );

		while ( s != null )
		{

			String[] split = splitString(s, ",");

			if ( split.length != PARAMSPERDATASET )
			{
				System.out.println( "Parameter " + i + " invalid. " + s );
				continue;
			}

			for( int j = 0 ; j < PARAMSPERDATASET-1 ; ++j )
			{
				paramVec.add( split[j].trim() );
			}

			try
			{
				paramVec.add( new Float(Float.parseFloat(split[PARAMSPERDATASET-1])) );
			}
			catch (NumberFormatException nfe)
			{
				System.out.println("Ignoring value " + split[PARAMSPERDATASET-1] + " in parameter " + i );
				paramVec.add( new Float(1.0f) );
			}

			s = getParameter( new Integer(++i).toString() );

		}

	}

	private void requestUrl()
	{

		if ( currentIndex == -1 ) return;

		try
		{

			String strUrl = getParameter("SCRIPT");

			String strType;
			String strRaySpec = "";

			if (algChoice.getSelectedItem().equals("Splatting"))
			{
				strType = "splat";
			}
			else
			{

				strType = "ray";

				Float fInc;

				try
				{
					fInc = new Float( Float.parseFloat(rayIncText.getText()) );
					if ( fInc.floatValue() < 0.1f )
					{
						fInc = new Float( 0.1f );
						rayIncText.setText(fInc.toString());
					}
				}
				catch( NumberFormatException nfe )
				{
					fInc = new Float( 1.0f );
					rayIncText.setText(fInc.toString());
				}


				if ( interpChoice.getSelectedItem().equals("Trilinear") )
				{
					strRaySpec = "&t=tri&i=" + fInc.toString();
				}
				else
				{
					strRaySpec = "&t=nn&i=" + fInc.toString();
				}

			}

			String strScale = "";
			float scale = 1.0f;

			if ( scaleGroup.getSelectedCheckbox().getLabel().equals("0.25") )
			{
				strScale = "&o=0.25";
				scale = 0.25f;
			}
			else if ( scaleGroup.getSelectedCheckbox().getLabel().equals("0.50") )
			{
				strScale = "&o=0.5";
				scale = 0.5f;
			}

			Float objZScale = (Float)paramVec.get(currentIndex+3);

			float zScale = objZScale.floatValue();

			zScale *= scale;

			float angle[] = upper.getRot().getEuler();

			strUrl = strUrl + "?stck=" + (String)paramVec.get(currentIndex+1) + "&s=" + zScale +
			"&x=" + Math.round(angle[0]) + "&y=" + Math.round(angle[1]) + "&z=" + Math.round(angle[2]) + "&m=" + strType + strRaySpec
			+ strScale;

			getAppletContext().showDocument( new URL(strUrl) , "_blank" );

		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

	}

     private void setStatus(String s)
     {
		 statusLabel.setText(s);
	 }

	 private String[] splitString( String str, String around )
	 {

		 String string = new String(str);

		Vector vec = new Vector();

		do
		{

			int i = string.indexOf( around );
			int j;

			if ( i == -1 )
			{
				i = string.length();
				j = i;
			}
			else
			{
				j = i + around.length();
			}


			vec.add( string.substring( 0, i ).trim() );
			string = string.substring( j );

		}
		while ( string.length() > 0 );

		String[] ret = new String[vec.size()];

		for( int i = 0 ; i < vec.size() ; ++i )
		{
			ret[i] = (String) vec.get(i);
		}

		return ret;

	 }

	 public void init()
	 {

		parseParameters();

		stackChoice = new Choice(); // populate the choices
		stackChoice.addItemListener(this);

		for( int i = 0 ; i < paramVec.size() ; i += PARAMSPERDATASET ) // first is the "friendly" name
		{
			stackChoice.add( (String) paramVec.get(i) );
		}

		setLayout( new GridLayout(2,1) );

		lower = new Panel();
		upper = new RTMipPanel(this);

		setSize( 512, 512 );

		lower.setLayout(new GridLayout(7,1));
		statusLabel = new Label("Select a stack from the Choice Box below");

		Panel tmpPanel = new Panel();
		tmpPanel.setLayout( new GridLayout( 1, 1 ) );
		tmpPanel.add( statusLabel );

		lower.add( tmpPanel );

		tmpPanel = new Panel();
		tmpPanel.setLayout( new GridLayout( 1, 2 ) );
		tmpPanel.add( new Label("Stack:") );
		tmpPanel.add( stackChoice );

		lower.add( tmpPanel );

		tmpPanel = new Panel();
		tmpPanel.setLayout( new GridLayout( 1, 2 ) );
		tmpPanel.add( new Label("Projection Type:"));
		algChoice = new Choice();
		algChoice.add( "Splatting" );
		algChoice.add( "Raycasting" );
		tmpPanel.add( algChoice );

		lower.add( tmpPanel );

		tmpPanel = new Panel();
		tmpPanel.setLayout( new GridLayout( 1, 2 ) );
		tmpPanel.add( new Label("Interpolation (Raycasting only):"));
		interpChoice = new Choice();
		interpChoice.add( "Trilinear" );
		interpChoice.add( "Nearest Neighbour" );
		tmpPanel.add( interpChoice );

		lower.add( tmpPanel );

		tmpPanel = new Panel();
		tmpPanel.setLayout( new GridLayout( 1, 2 ) );
		tmpPanel.add( new Label("Ray Increment (Raycasting only):"));
		rayIncText = new TextField("1.0", 5);
		tmpPanel.add( rayIncText );

		lower.add( tmpPanel );

		tmpPanel = new Panel();
		tmpPanel.setLayout( new GridLayout( 1, 4 ) );
		tmpPanel.add( new Label("Size:") );

		scaleGroup = new CheckboxGroup();

		tmpPanel.add( new Checkbox("0.25", scaleGroup, false) );
		tmpPanel.add( new Checkbox("0.50", scaleGroup, true) );
		tmpPanel.add( new Checkbox("1.0", scaleGroup, false) );

		lower.add( tmpPanel );

		tmpPanel = new Panel();
		tmpPanel.setLayout( new GridLayout( 1, 1 ) );
		renderButton = new Button("Render");
		renderButton.addActionListener(this);
		tmpPanel.add( renderButton );

		lower.add( tmpPanel );


		add( upper );
		add( lower );

     }

     public void start()
     {
	 }

	 public void stop()
	 {
	 }

	public void actionPerformed(ActionEvent ae)
	{

		Object reason = ae.getSource();

		if ( reason.equals( renderButton ) )
		{
			// ask for the image from the magical server script
			requestUrl();
		}

	}


	public void itemStateChanged( ItemEvent reason )
	{

		// only used for the stackChoice thing to indicate selection or de-selection

		if ( reason.getStateChange() == ItemEvent.SELECTED )
		{

			int index = stackChoice.getSelectedIndex() * PARAMSPERDATASET;

			if ( currentIndex != index )
			{
				// load new selection

				currentIndex = index;

				// get the full URL

				 String url = (String) paramVec.get(currentIndex + 2);

				 try
				 {
					setStatus( "Loading from " + url );
					upper.loadDataset( new URL( url ) );
					setStatus("");
				 }
				 catch( Exception mue )
				 {
					 mue.printStackTrace();
					 setStatus("Failed to load from " + url );
				 }

			}

		}

	}

 }


