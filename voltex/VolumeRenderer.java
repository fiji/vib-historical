package voltex;

import java.awt.*;
import java.awt.image.*;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.io.*;
import com.sun.j3d.utils.behaviors.mouse.*;
import ij.IJ;
import ij.ImagePlus;

public class VolumeRenderer extends Renderer {

	protected float transparency;
	protected Color3f color;

	private int curAxis = Z_AXIS;
	private int curDir = FRONT;

	private BranchGroup root;

	protected AppearanceCreator appCreator;

	protected GeometryCreator geomCreator;

	protected Switch axisSwitch;
	protected int[][] axisIndex = new int[3][2];


	public VolumeRenderer(ImagePlus img, Color3f color,
					float tr, boolean[] channels) {
		super(img);
		volume.setTransparencyType(Volume.TRANSLUCENT);
		this.transparency = tr;
		this.color = color;
		appCreator = new AppearanceCreator(
				volume, color, tr, channels);
		geomCreator = new GeometryCreator(volume);

		axisIndex[X_AXIS][FRONT] = 0;
		axisIndex[X_AXIS][BACK] = 1;
		axisIndex[Y_AXIS][FRONT] = 2;
		axisIndex[Y_AXIS][BACK] = 3;
		axisIndex[Z_AXIS][FRONT] = 4;
		axisIndex[Z_AXIS][BACK] = 5;

		axisSwitch = new Switch();
		axisSwitch.setCapability(Switch.ALLOW_SWITCH_READ);
		axisSwitch.setCapability(Switch.ALLOW_SWITCH_WRITE);
		axisSwitch.setCapability(Group.ALLOW_CHILDREN_READ);
		axisSwitch.setCapability(Group.ALLOW_CHILDREN_WRITE);
		for(int i = 0; i < 6; i++)
			axisSwitch.addChild(getOrderedGroup());

		root = new BranchGroup();
		root.addChild(axisSwitch);
		root.setCapability(BranchGroup.ALLOW_DETACH);
		root.setCapability(BranchGroup.ALLOW_LOCAL_TO_VWORLD_READ);
	}

	public BranchGroup getVolumeNode() {
		return root;
	}

	private Group getOrderedGroup() {
		OrderedGroup og = new OrderedGroup();
		og.setCapability(Group.ALLOW_CHILDREN_READ);
		og.setCapability(Group.ALLOW_CHILDREN_WRITE);
		og.setCapability(Group.ALLOW_CHILDREN_EXTEND);
		return og;
	}

	private Vector3d eyeVec = new Vector3d();
	public void eyePtChanged(View view) {

		Point3d eyePt = getViewPosInLocal(view, root);
		if (eyePt != null) {
			Point3d  volRefPt = volume.volRefPt;
			eyeVec.sub(eyePt, volRefPt);

			// compensate for different xyz resolution/scale
//			eyeVec.x /= volume.xSpace;
//			eyeVec.y /= volume.ySpace;
//			eyeVec.z /= volume.zSpace;

			// select the axis with the greatest magnitude
			int axis = X_AXIS;
			double value = eyeVec.x;
			double max = Math.abs(eyeVec.x);
			if (Math.abs(eyeVec.y) > max) {
				axis = Y_AXIS;
				value = eyeVec.y;
				max = Math.abs(eyeVec.y);
			}
			if (Math.abs(eyeVec.z) > max) {
				axis = Z_AXIS;
				value = eyeVec.z;
				max = Math.abs(eyeVec.z);
			}

			// select the direction based on the sign of the magnitude
			int dir = value > 0.0 ? FRONT : BACK;

			if ((axis != curAxis) || (dir != curDir)) {
				curAxis = axis;
				curDir = dir;
				axisSwitch.setWhichChild(
						axisIndex[curAxis][curDir]);
			}
		}
	}

	public void fullReload() {
		for(int i = 0; i < axisSwitch.numChildren(); i++) {
			((Group)axisSwitch.getChild(i)).removeAllChildren();
		}
		if (volume.hasData()) {
			loadQuads();
		}
		axisSwitch.setWhichChild(axisIndex[curAxis][curDir]);
	}

	public void setThreshold(int threshold) {
		float value = threshold/255f;
		value = Math.min(1f, value);
		value = Math.max(0.1f, value);
		this.threshold = (int)Math.round(value * 255);
		appCreator.setThreshold(value);
	}

	public void setTransparency(float transparency) {
		this.transparency = transparency;
		appCreator.setTransparency(transparency);
	}

	public void setChannels(boolean[] channels) {
		if(volume.setChannels(channels))
			fullReload();
	}

	public void setColor(Color3f color) {
		this.color = color;
		if(volume.setAverage(color != null))
			fullReload();
		Color3f c = color != null ? color : new Color3f(1f, 1f, 1f);
		appCreator.setColor(c);
	}

	private void loadQuads() {
		loadAxis(Z_AXIS);
		loadAxis(Y_AXIS);
		loadAxis(X_AXIS);
	}

	private void loadAxis(int axis) {
		int rSize = 0;		// number of tex maps to create
		Group frontGroup = null;
		Group backGroup = null;

		frontGroup = (Group)axisSwitch.getChild(axisIndex[axis][FRONT]);
		backGroup  = (Group)axisSwitch.getChild(axisIndex[axis][BACK]);
		String m = "Loading ";

		switch (axis) {
			case Z_AXIS: rSize = volume.zDim; m += "x axis"; break;
			case Y_AXIS: rSize = volume.yDim; m += "y axis"; break;
			case X_AXIS: rSize = volume.xDim; m += "z axis"; break;
		}

		IJ.showStatus("Loading " + m);
		for (int i=0; i < rSize; i++) {
			IJ.showProgress(i+1, rSize);

			GeometryArray quadArray = geomCreator.getQuad(axis, i);
			Appearance a = appCreator.getAppearance(axis, i);

			Shape3D frontShape = new Shape3D(quadArray, a);
			frontShape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);

			BranchGroup frontShapeGroup = new BranchGroup();
			frontShapeGroup.setCapability(BranchGroup.ALLOW_DETACH);
			frontShapeGroup.setCapability(BranchGroup.ALLOW_CHILDREN_READ);
			frontShapeGroup.addChild(frontShape);
			frontGroup.addChild(frontShapeGroup);

			Shape3D backShape = new Shape3D(quadArray, a);
			backShape.setCapability(Shape3D.ALLOW_APPEARANCE_READ);

			BranchGroup backShapeGroup = new BranchGroup();
			backShapeGroup.setCapability(BranchGroup.ALLOW_DETACH);
			backShapeGroup.setCapability(BranchGroup.ALLOW_CHILDREN_READ);
			backShapeGroup.addChild(backShape);
			backGroup.insertChild(backShapeGroup, 0);
		}
	}
}
