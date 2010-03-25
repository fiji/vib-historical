package ij3d;

import ij3d.pointlist.PointListDialog;
import ij.ImagePlus;

import vib.PointList;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Switch;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.View;

import javax.vecmath.Color3f;
import javax.vecmath.Point3d;

import java.util.HashMap;

public class Content extends BranchGroup implements UniverseListener, ContentConstants {

	private HashMap<Integer, ContentInstant> contents;
	private int currentTimePoint;
	private Switch contentSwitch;
	private final String name;


	public Content(String name) {
		this.name = name;
		contents = new HashMap<Integer, ContentInstant>();
		ContentInstant ci = new ContentInstant(name + "_#0");
		contents.put(0, ci);
		contentSwitch = new Switch();
		contentSwitch.setWhichChild(Switch.CHILD_ALL);
		contentSwitch.addChild(ci);
		addChild(contentSwitch);
	}

	public Content(String name, HashMap<Integer, ContentInstant> contents) {
		this.name = name;
		this.contents = contents;
		contentSwitch = new Switch();
		contentSwitch.setWhichChild(Switch.CHILD_ALL);
		for(int i : contents.keySet()) {
			ContentInstant c = contents.get(i);
			c.name = name + "_#" + i;
			contentSwitch.addChild(c);
			addChild(contentSwitch);
		}
	}

	public ContentInstant getCurrent() {
		return contents.get(currentTimePoint);
	}

	public ContentInstant getInstant(int i) {
		return contents.get(i);
	}

	public HashMap<Integer, ContentInstant> getInstants() {
		return contents;
	}

	public void displayAs(int type) {
		for(ContentInstant c : contents.values())
			c.displayAs(type);
	}

	public static int getDefaultThreshold(ImagePlus imp, int type) {
		return ContentInstant.getDefaultThreshold(imp, type);
	}

	public static int getDefaultResamplingFactor(ImagePlus imp, int type) {
		return ContentInstant.getDefaultResamplingFactor(imp, type);
	}

	public void display(ContentNode node) {
		for(ContentInstant c : contents.values())
			c.display(node);
	}

	/* ************************************************************
	 * setters - visibility flags
	 *
	 * ***********************************************************/

	public void setVisible(boolean b) {
		for(ContentInstant c : contents.values())
			c.setVisible(b);
	}

	public void showBoundingBox(boolean b) {
		for(ContentInstant c : contents.values())
			c.showBoundingBox(b);
	}


	public void showCoordinateSystem(boolean b) {
		for(ContentInstant c : contents.values())
			c.showCoordinateSystem(b);
	}

	public void setSelected(boolean selected) {
		// TODO really all?
		for(ContentInstant c : contents.values())
			c.setSelected(selected);
	}

	/* ************************************************************
	 * point list
	 *
	 * ***********************************************************/
	public void setPointListDialog(PointListDialog p) {
		for(ContentInstant c : contents.values())
			c.setPointListDialog(p);
	}

	public void showPointList(boolean b) {
		for(ContentInstant c : contents.values())
			c.showPointList(b);
	}

	// TODO only for current point, makes this sense?
	public void loadPointList() {
		getCurrent().loadPointList();
	}

	// TODO only for current point, makes this sense?
	public void savePointList() {
		getCurrent().savePointList();
	}

	/**
	 * @deprecated
	 * @param p
	 */
	public void addPointListPoint(Point3d p) {
		getCurrent().addPointListPoint(p);
	}

	/**
	 * @deprecated
	 * @param i
	 * @param pos
	 */
	public void setListPointPos(int i, Point3d pos) {
		getCurrent().setListPointPos(i, pos);
	}

	public float getLandmarkPointSize() {
		return getCurrent().getLandmarkPointSize();
	}

	public void setLandmarkPointSize(float r) {
		for(ContentInstant c : contents.values())
			c.setLandmarkPointSize(r);
	}

	public PointList getPointList() {
		return getCurrent().getPointList();
	}

	/**
	 * @deprecated
	 * @param i
	 */
	public void deletePointListPoint(int i) {
		getCurrent().deletePointListPoint(i);
	}

	/* ************************************************************
	 * setters - transform
	 *
	 **************************************************************/
	public void toggleLock() {
		for(ContentInstant c : contents.values())
			c.toggleLock();
	}

	public void setLocked(boolean b) {
		for(ContentInstant c : contents.values())
			c.setLocked(b);
	}

	public void applyTransform(double[] matrix) {
		applyTransform(new Transform3D(matrix));
	}

	public void applyTransform(Transform3D transform) {
		for(ContentInstant c : contents.values())
			c.applyTransform(transform);
	}

	public void setTransform(double[] matrix) {
		setTransform(new Transform3D(matrix));
	}

	public void setTransform(Transform3D transform) {
		for(ContentInstant c : contents.values())
			c.setTransform(transform);
	}

	/* ************************************************************
	 * setters - attributes
	 *
	 * ***********************************************************/

	public void setChannels(boolean[] channels) {
		for(ContentInstant c : contents.values())
			c.setChannels(channels);
	}

	public void setThreshold(int th) {
		for(ContentInstant c : contents.values())
			c.setThreshold(th);
	}

	public void setShaded(boolean b) {
		for(ContentInstant c : contents.values())
			c.setShaded(b);
	}

	public void setColor(Color3f color) {
		for(ContentInstant c : contents.values())
			c.setColor(color);
	}

	public synchronized void setTransparency(float transparency) {
		for(ContentInstant c : contents.values())
			c.setTransparency(transparency);
	}

	/* ************************************************************
	 * UniverseListener interface
	 *
	 *************************************************************/
	public void transformationStarted(View view) {}
	public void contentAdded(Content c) {}
	public void contentRemoved(Content c) {
		for(ContentInstant co : contents.values()) {
			co.contentRemoved(c);
		}
	}
	public void canvasResized() {}
	public void contentSelected(Content c) {}
	public void contentChanged(Content c) {}

	public void universeClosed() {
		for(ContentInstant c : contents.values()) {
			c.universeClosed();
		}
	}

	public void transformationUpdated(View view) {
		eyePtChanged(view);
	}

	public void transformationFinished(View view) {
		eyePtChanged(view);
	}

	public void eyePtChanged(View view) {
		for(ContentInstant c : contents.values())
			c.eyePtChanged(view);
	}

	/* *************************************************************
	 * getters
	 *
	 **************************************************************/
	@Override
	public String getName() {
		return name;
	}

	public int getType() {
		return getCurrent().getType();
	}

	public ContentNode getContent() {
		return getCurrent().getContent();
	}

	public ImagePlus getImage() {
		return getCurrent().getImage();
	}

	public boolean[] getChannels() {
		return getCurrent().getChannels();
	}

	public Color3f getColor() {
		return getCurrent().getColor();
	}

	public boolean isShaded() {
		return getCurrent().isShaded();
	}

	public int getThreshold() {
		return getCurrent().getThreshold();
	}

	public float getTransparency() {
		return getCurrent().getTransparency();
	}

	public int getResamplingFactor() {
		return getCurrent().getResamplingFactor();
	}

	public TransformGroup getLocalRotate() {
		return getCurrent().getLocalRotate();
	}

	public TransformGroup getLocalTranslate() {
		return getCurrent().getLocalTranslate();
	}

	public void getLocalRotate(Transform3D t) {
		getCurrent().getLocalRotate(t);
	}

	public void getLocalTranslate(Transform3D t) {
		getCurrent().getLocalTranslate(t);
	}

	public boolean isLocked() {
		return getCurrent().isLocked();
	}

	public boolean isVisible() {
		return getCurrent().isVisible();
	}

	public boolean hasCoord() {
		return getCurrent().hasCoord();
	}

	public boolean hasBoundingBox() {
		return getCurrent().hasBoundingBox();
	}

	public boolean isPLVisible() {
		return getCurrent().isPLVisible();
	}
}

