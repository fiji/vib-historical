package view4d;

import ij.gui.GenericDialog;

import java.awt.*;
import java.awt.event.*;

import java.net.URL;
import java.awt.image.ImageProducer;

import vib.segment.ImageButton;

/**
 * This class implements the window with the controls for the 4D viewer.
 *
 * @author Benjamin Schmid
 */
public class TimelineGUI implements ActionListener {

	private final Panel p;
	private boolean visible = false;

	final String nbbFile = "icons/nobounceback.png";
	final String bbFile = "icons/bounceback.png";
	final int bbIndex = 2;
	final Image bbImage, nbbImage;

	final String playFile = "icons/play.png";
	final String pauseFile = "icons/pause.png";
	final int playIndex = 3;
	final Image playImage, pauseImage;

	private static final String[] FILES = new String[] {
				"icons/first.png",
				"icons/last.png",
				"icons/nobounceback.png",
				"icons/play.png",
				"icons/record.png",
				"icons/faster.png",
				"icons/slower.png"};

	private static final String[] COMMANDS = new String[] {
			"FIRST",
			"LAST", "NOBOUNCEBACK",
			"PLAY", "RECORD", "FASTER", "SLOWER"};


	private ImageButton[] buttons = new ImageButton[FILES.length];
	private final Timeline timeline;
	private final Scrollbar scroll;
	private final TextField tf;

	/**
	 * Initializes a new Viewer4DController;
	 * opens a new new window with the control buttons for the 4D viewer.
	 * @param viewer
	 */
	public TimelineGUI(Timeline tl) {
		this.timeline = tl;

		bbImage = loadIcon(bbFile);
		nbbImage = loadIcon(nbbFile);
		playImage = loadIcon(playFile);
		pauseImage = loadIcon(pauseFile);

		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();

		p = new Panel(gridbag);
		c.gridx = c.gridy = 0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.EAST;
		c.weightx = c.weighty = 0.0;

		for(int i = 0; i < FILES.length; i++) {
			buttons[i] = new ImageButton(loadIcon(FILES[i]));
			buttons[i].setUnarmedBorder(new Border(false));
			buttons[i].setArmedBorder(new Border(true));
			buttons[i].setOverBorder(new Border(true));
			buttons[i].setDisabledBorder(new Border(true));
			buttons[i].addActionListener(this);
			buttons[i].setActionCommand(COMMANDS[i]);
			gridbag.setConstraints(buttons[i], c);
			p.add(buttons[i]);
			c.gridx++;
		}
		// set up scroll bar
		int min = timeline.getUniverse().getStartTime();
		int max = timeline.getUniverse().getEndTime() + 1;
		int cur = timeline.getUniverse().getCurrentTimepoint();
		
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		scroll = new Scrollbar(Scrollbar.HORIZONTAL, cur, 1, min, max);
		scroll.addAdjustmentListener(new AdjustmentListener() {
			public void adjustmentValueChanged(AdjustmentEvent e) {
				timeline.pause();
				showTimepoint(scroll.getValue());
			}
		});
		gridbag.setConstraints(scroll, c);
		p.add(scroll);

		// set up text field
		tf = new TextField(2);
		tf.setText(Integer.toString(cur));
		tf.addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				if(e.getKeyCode() == KeyEvent.VK_ENTER) {
					int v = 0;
					try {
						v = Integer.parseInt(
							tf.getText());
						showTimepoint(v);
						tf.selectAll();
					} catch(Exception ex) {}
				}
			}
		});
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.EAST;
		c.weightx = c.weighty = 0.0;
		c.gridx++;
		gridbag.setConstraints(tf, c);
		p.add(tf);
	}

	private void showTimepoint(int v) {
		timeline.getUniverse().showTimepoint(v);
	}

	public Panel getPanel() {
		return p;
	}

	public void updateTimepoint(int val) {
		scroll.setValue(val);
		tf.setText(Integer.toString(val));
	}

	public void updateStartAndEnd(int start, int end) {
		scroll.setMinimum(start);
		scroll.setMaximum(end + 1);
	}

	private Image loadIcon(String name) {
		URL url;
		Image img = null;
		try {
			url = getClass().getResource(name);
			img = Toolkit.getDefaultToolkit()
				.createImage((ImageProducer)url.getContent());
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (img == null)
			throw new RuntimeException("Image not found: " + name);
		return img;
	}

	public void actionPerformed(ActionEvent e) {
		for(int i = 0; i < buttons.length; i++)
			buttons[i].repaint();

		String command = e.getActionCommand();
		if(command.equals("BOUNCEBACK")) {
			buttons[bbIndex].setUnarmedImage(nbbImage);
			buttons[bbIndex].setActionCommand("NOBOUNCEBACK");
			buttons[bbIndex].repaint();
			timeline.setBounceBack(true);
		} else if(command.equals("NOBOUNCEBACK")) {
			buttons[bbIndex].setUnarmedImage(bbImage);
			buttons[bbIndex].setActionCommand("BOUNCEBACK");
			buttons[bbIndex].repaint();
			timeline.setBounceBack(false);
		} else if(command.equals("PLAY")) {
			buttons[playIndex].setUnarmedImage(pauseImage);
			buttons[playIndex].setActionCommand("PAUSE");
			buttons[playIndex].repaint();
			timeline.play();
		} else if(command.equals("PAUSE")) {
			buttons[playIndex].setUnarmedImage(playImage);
			buttons[playIndex].setActionCommand("PLAY");
			buttons[playIndex].repaint();
			timeline.pause();
		} else if(command.equals("RECORD")) {
			timeline.record().show();
		} else if(command.equals("FIRST")) {
			timeline.first();
		} else if(command.equals("LAST")) {
			timeline.last();
		} else if(command.equals("FASTER")) {
			timeline.faster();
		} else if(command.equals("SLOWER")) {
			timeline.slower();
		}
	}

	static class Border extends vib.segment.Border {
		public Border(boolean armed) {
// 			setBorderThickness(2);
			setBorderThickness(0);
			if (armed) {
				setType(THREED_IN);
// 				setMargins(4, 4, 2, 2);
				setMargins(0, 0, 0, 0);
			}
			else {
				setType( THREED_OUT );
// 				setMargins(3);
				setMargins(0);
			}
		}
	}
}

