package ij3d;

import ij3d.Image3DUniverse;


/**
 * Implements the functionality for the 4D viewer, like loading
 * and animation.
 *
 * @author Benjamin Schmid
 */
public class Timeline {

	private Image3DUniverse univ;
	private boolean playing = false;

	/**
	 * Initialize the timeline
	 * @param univ
	 */
	public Timeline(Image3DUniverse univ) {
		this.univ = univ;
	}

	/**
	 * Returns the number of time points.
	 * @return
	 */
	public int size() {
		if(univ.getContents().size() == 0)
			return 0;
		return univ.getEndTime() - univ.getStartTime();
	}

	/**
	 * Speed up the animation.
	 */
	public void faster() {
		if(delay >= 100)
			delay -= 100;
	}

	/**
	 * Slows the animation down.
	 */
	public void slower() {
		delay += 100;
	}

	private boolean shouldPause = false;
	private int delay = 1000;

	/**
	 * Start animation.
	 */
	public synchronized void play() {
		if(size() == 0)
			return;
		new Thread(new Runnable() {
			public void run() {
				playing = true;
				while(!shouldPause) {
					if(univ.getCurrentTimepoint() < univ.getEndTime())
						next();
					else
						first();
					try {
						Thread.sleep(delay);
					} catch(Exception e) {
						shouldPause = true;
					}
				}
				playing = false;
				shouldPause = false;
			}
		}).start();
	}

	/**
	 * Stop/pause animation
	 */
	public synchronized void pause() {
		shouldPause = true;
	}

	/**
	 * Display next timepoint.
	 */
	public void next() {
		if(univ.getContents().size() == 0)
			return;
		int curr = univ.getCurrentTimepoint();
		if(curr == univ.getEndTime())
			return;
		univ.showTimepoint(curr + 1);
	}

	/**
	 * Display previous timepoint.
	 */
	public void previous() {
		if(univ.getContents().size() == 0)
			return;
		int curr = univ.getCurrentTimepoint();
		if(curr == univ.getStartTime())
			return;
		univ.showTimepoint(curr - 1);
	}

	/**
	 * Display first timepoint.
	 */
	public void first() {
		if(univ.getContents().size() == 0)
			return;
		int first = univ.getStartTime();
		if(univ.getCurrentTimepoint() == first)
			return;
		univ.showTimepoint(first);
	}

	/**
	 * Display last timepoint.
	 */
	public void last() {
		if(univ.getContents().size() == 0)
			return;
		int last = univ.getEndTime();
		if(univ.getCurrentTimepoint() == last)
			return;
		univ.showTimepoint(last);
	}
}

