package ij3d;

import ij3d.shapes.CoordinateSystem;
import ij3d.shapes.BoundingBox;
import ij3d.pointlist.PointListPanel;
import ij3d.pointlist.PointListShape;
import ij3d.pointlist.PointListDialog;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileInfo;
import ij.io.OpenDialog;

import vib.PointList;
import isosurface.MeshGroup;
import voltex.VoltexGroup;
import orthoslice.OrthoGroup;
import surfaceplot.SurfacePlotGroup;

import java.util.BitSet;

import javax.media.j3d.BranchGroup;
import javax.media.j3d.Switch;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.media.j3d.View;

import javax.vecmath.Color3f;
import javax.vecmath.Matrix3f;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

public class Content extends BranchGroup implements UniverseListener, ContentConstants {

	private ContentInstant[] contents;
	private int currentTimePoint;
	private Switch contentSwitch;


	public Content(String name) {
		contents = new ContentInstant[1];
		contents[0] = new ContentInstant(name);

		contentSwitch = new Switch();
		contentSwitch.addChild(contents[0]);
		addChild(contentSwitch);
	}

	public Content(ContentInstant[] contents) {
		this.contents = contents;
		for(ContentInstant c : contents) {
			contentSwitch = new Switch();
			contentSwitch.addChild(c);
			addChild(contentSwitch);
		}
	}

	public ContentInstant getCurrent() {
		return contents[currentTimePoint];
	}

	public ContentInstant getInstant(int i) {
		return contents[i];
	}

	public ContentInstant[] getInstants() {
		return contents;
	}

	public void displayAs(int type) {
		for(ContentInstant c : contents)
			c.displayAs(type);
	}

	public static int getDefaultThreshold(ImagePlus imp, int type) {
		return ContentInstant.getDefaultThreshold(imp, type);
	}

	public static int getDefaultResamplingFactor(ImagePlus imp, int type) {
		return ContentInstant.getDefaultResamplingFactor(imp, type);
	}

	public void display(ContentNode node) {
		for(ContentInstant c : contents)
			c.display(node);
	}

	/* ************************************************************
	 * setters - visibility flags
	 *
	 * ***********************************************************/

	public void setVisible(boolean b) {
		for(ContentInstant c : contents)
			c.setVisible(b);
	}

	public void showBoundingBox(boolean b) {
		for(ContentInstant c : contents)
			c.showBoundingBox(b);
	}


	public void showCoordinateSystem(boolean b) {
		for(ContentInstant c : contents)
			c.showCoordinateSystem(b);
	}

	public void setSelected(boolean selected) {
		// TODO really all?
		for(ContentInstant c : contents)
			c.setSelected(selected);
	}

	/* ************************************************************
	 * point list
	 *
	 * ***********************************************************/
	public void setPointListDialog(PointListDialog p) {
		for(ContentInstant c : contents)
			c.setPointListDialog(p);
	}

	public void showPointList(boolean b) {
		for(ContentInstant c : contents)
			c.showPointList(b);
	}

	// TODO only for current point, makes this sense?
	public void loadPointList() {
		contents[currentTimePoint].loadPointList();
	}

	// TODO only for current point, makes this sense?
	public void savePointList() {
		contents[currentTimePoint].savePointList();
	}

	/**
	 * @deprecated
	 * @param p
	 */
	public void addPointListPoint(Point3d p) {
		contents[currentTimePoint].addPointListPoint(p);
	}

	/**
	 * @deprecated
	 * @param i
	 * @param pos
	 */
	public void setListPointPos(int i, Point3d pos) {
		contents[currentTimePoint].setListPointPos(i, pos);
	}

	public float getLandmarkPointSize() {
		return contents[currentTimePoint].getLandmarkPointSize();
	}

	public void setLandmarkPointSize(float r) {
		for(ContentInstant c : contents)
			c.setLandmarkPointSize(r);
	}

	public PointList getPointList() {
		return contents[currentTimePoint].getPointList();
	}

	/**
	 * @deprecated
	 * @param i
	 */
	public void deletePointListPoint(int i) {
		contents[currentTimePoint].deletePointListPoint(i);
	}

	/* ************************************************************
	 * setters - transform
	 *
	 **************************************************************/
	public void toggleLock() {
		for(ContentInstant c : contents)
			c.toggleLock();
	}

	public void setLocked(boolean b) {
		for(ContentInstant c : contents)
			c.setLocked(b);
	}

	public void applyTransform(double[] matrix) {
		applyTransform(new Transform3D(matrix));
	}

	public void applyTransform(Transform3D transform) {
		for(ContentInstant c : contents)
			c.applyTransform(transform);
	}

	public void setTransform(double[] matrix) {
		setTransform(new Transform3D(matrix));
	}

	public void setTransform(Transform3D transform) {
		for(ContentInstant c : contents)
			c.setTransform(transform);
	}

	/* ************************************************************
	 * setters - attributes
	 *
	 * ***********************************************************/

	public void setChannels(boolean[] channels) {
		for(ContentInstant c : contents)
			c.setChannels(channels);
	}

	public void setThreshold(int th) {
		for(ContentInstant c : contents)
			c.setThreshold(th);
	}

	public void setShaded(boolean b) {
		for(ContentInstant c : contents)
			c.setShaded(b);
	}

	public void setColor(Color3f color) {
		for(ContentInstant c : contents)
			c.setColor(color);
	}

	public synchronized void setTransparency(float transparency) {
		for(ContentInstant c : contents)
			c.setTransparency(transparency);
	}

	/* ************************************************************
	 * UniverseListener interface
	 *
	 *************************************************************/
	public void transformationStarted(View view) {}
	public void contentAdded(Content c) {}
	public void contentRemoved(Content c) {
		for(ContentInstant co : contents) {
			co.contentRemoved(c);
		}
	}
	public void canvasResized() {}
	public void contentSelected(Content c) {}
	public void contentChanged(Content c) {}

	public void universeClosed() {
		for(ContentInstant c : contents) {
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
		for(ContentInstant c : contents)
			c.eyePtChanged(view);
	}

	/* *************************************************************
	 * getters
	 *
	 **************************************************************/
	@Override
	public String getName() {
		return contents[currentTimePoint].getName();
	}

	public int getType() {
		return contents[currentTimePoint].getType();
	}

	public ContentNode getContent() {
		return contents[currentTimePoint].getContent();
	}

	public ImagePlus getImage() {
		return contents[currentTimePoint].getImage();
	}

	public boolean[] getChannels() {
		return contents[currentTimePoint].getChannels();
	}

	public Color3f getColor() {
		return contents[currentTimePoint].getColor();
	}

	public boolean isShaded() {
		return contents[currentTimePoint].isShaded();
	}

	public int getThreshold() {
		return contents[currentTimePoint].getThreshold();
	}

	public float getTransparency() {
		return contents[currentTimePoint].getTransparency();
	}

	public int getResamplingFactor() {
		return contents[currentTimePoint].getResamplingFactor();
	}

	public TransformGroup getLocalRotate() {
		return contents[currentTimePoint].getLocalRotate();
	}

	public TransformGroup getLocalTranslate() {
		return contents[currentTimePoint].getLocalTranslate();
	}

	public void getLocalRotate(Transform3D t) {
		contents[currentTimePoint].getLocalRotate(t);
	}

	public void getLocalTranslate(Transform3D t) {
		contents[currentTimePoint].getLocalTranslate(t);
	}

	public boolean isLocked() {
		return contents[currentTimePoint].isLocked();
	}

	public boolean isVisible() {
		return contents[currentTimePoint].isVisible();
	}

	public boolean hasCoord() {
		return contents[currentTimePoint].hasCoord();
	}

	public boolean hasBoundingBox() {
		return contents[currentTimePoint].hasBoundingBox();
	}

	public boolean isPLVisible() {
		return contents[currentTimePoint].isPLVisible();
	}
}

