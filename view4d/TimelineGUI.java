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

	private static final String[] FILES = new String[] {
				"icons/first.png",
				"icons/previous.png",
				"icons/next.png",
				"icons/last.png",
				"icons/play.png",
				"icons/pause.png",
				"icons/faster.png",
				"icons/slower.png"};

	private static final String[] COMMANDS = new String[] {
			"FIRST", "PREV", "NEXT", "LAST",
			"PLAY", "PAUSE", "FASTER", "SLOWER"};


	private ImageButton[] buttons = new ImageButton[FILES.length];
	private final Timeline timeline;
	private final Scrollbar scroll;

	/**
	 * Initializes a new Viewer4DController;
	 * opens a new new window with the control buttons for the 4D viewer.
	 * @param viewer
	 */
	public TimelineGUI(Timeline timeline) {
		this.timeline = timeline;

		p = new Panel(new FlowLayout());
		for(int i = 0; i < FILES.length; i++) {
			buttons[i] = new ImageButton(loadIcon(FILES[i]));
			buttons[i].addActionListener(this);
			buttons[i].setActionCommand(COMMANDS[i]);
			p.add(buttons[i]);
		}
		// set up scroll bar
		int min = timeline.getUniverse().getStartTime();
		int max = timeline.getUniverse().getEndTime() + 1;
		scroll = new Scrollbar(Scrollbar.HORIZONTAL, 0, 1, min, max);
		p.add(scroll);
	}

	public Panel getPanel() {
		return p;
	}

	public void updateTimepoint(int val) {
		scroll.setValue(val);
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
		if(command.equals("NEXT")) {
			timeline.next();
		} else if(command.equals("PREV")) {
			timeline.previous();
		} else if(command.equals("PLAY")) {
			timeline.play();
		} else if(command.equals("FIRST")) {
			timeline.first();
		} else if(command.equals("LAST")) {
			timeline.last();
		} else if(command.equals("PAUSE")) {
			timeline.pause();
		} else if(command.equals("FASTER")) {
			timeline.faster();
		} else if(command.equals("SLOWER")) {
			timeline.slower();
		}
	}
}

