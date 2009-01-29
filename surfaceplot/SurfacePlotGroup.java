package surfaceplot;

import ij.ImagePlus;
import ij.gui.StackWindow;
import ij3d.Content;
import ij3d.ContentNode;

import java.awt.Component;
import java.awt.Scrollbar;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

import javax.media.j3d.View;
import javax.vecmath.Point3f;

import vib.Resample_;
import voltex.Volume;

public class SurfacePlotGroup extends ContentNode implements AdjustmentListener{

	SurfacePlot surfacep;
	Content c;

	public SurfacePlotGroup (Content c) {
		super();
		this.c = c;
		int res = c.getResamplingFactor();
		ImagePlus imp = res == 1 ? c.getImage() 
			: Resample_.resample(c.getImage(), res, res, 1);
		Volume volume = new Volume(imp);
		volume.setAverage(true);
		volume.setChannels(c.getChannels());
		surfacep = new SurfacePlot(volume, c.getColor(),
				c.getTransparency(), c.getImage().getSlice());

		addChild(surfacep);
		if(c.getImage().getStackSize() == 1)
			return;
		StackWindow win = (StackWindow)c.getImage().getWindow();
		if(win == null)
			return;
		Component[] co = win.getComponents();
		for(int i = 0; i < co.length; i++) {
			if(co[i] instanceof Scrollbar) {
				((Scrollbar)co[i]).addAdjustmentListener(this);
			}
		}
	}

	public void adjustmentValueChanged(AdjustmentEvent e) {
		surfacep.setSlice(((Scrollbar)e.getSource()).getValue());
	}

	public void eyePtChanged(View view) {
		// do nothing
	}

	public void thresholdUpdated() {
		// TODO
	}

	public void channelsUpdated() {
		surfacep.setChannels(c.getChannels());
	}

	public void calculateMinMaxCenterPoint() {
		min = new Point3f(); max = new Point3f();
		center = new Point3f();
		if(surfacep != null) {
			surfacep.calculateMinMaxCenterPoint(min, max, center);
		}
	}

	public float getVolume() {
		if(surfacep == null)
			return -1;
		// TODO
		return 0f;
	}

	public void colorUpdated() {
		if(c.getColor() != surfacep.getColor())
			surfacep.setColor(c.getColor());
	}

	public void transparencyUpdated() {
		if(c.getTransparency() != surfacep.getTransparency())
			surfacep.setTransparency(c.getTransparency());
	}

	public void shadeUpdated() {
		if(c.isShaded() != surfacep.isShaded())
			surfacep.setShaded(c.isShaded());
	}
}

