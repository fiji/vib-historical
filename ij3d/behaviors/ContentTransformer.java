package ij3d.behaviors;

import ij3d.Content;
import ij3d.DefaultUniverse;
import ij3d.ImageCanvas3D;
import java.awt.event.MouseEvent;
import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.AxisAngle4d;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

public class ContentTransformer {

	private Initializer initializer;

	private DefaultUniverse univ;
	private ImageCanvas3D canvas;
	private BehaviorCallback callback;

	private Vector3d axisPerDx = new Vector3d();
	private Vector3d axisPerDy = new Vector3d();
	private double anglePerPix;
	

	private AxisAngle4d aaX = new AxisAngle4d();
	private AxisAngle4d aaY = new AxisAngle4d();
	private Transform3D transX = new Transform3D();
	private Transform3D transY = new Transform3D();

	private Transform3D transl = new Transform3D();
	private Transform3D transl_inv = new Transform3D();

	private Vector3d translationPerDx = new Vector3d();
	private Vector3d translationPerDy = new Vector3d();

	private TransformGroup translateTG, rotateTG;
		
	private int xLast, yLast;

	public ContentTransformer(DefaultUniverse univ, BehaviorCallback callback) {
		this.univ = univ;
		this.canvas = (ImageCanvas3D)univ.getCanvas();
		this.callback = callback;
		this.initializer = new Initializer();
	}

	public void init(Content c, int x, int y) {
		initializer.init(c, x, y);
	}

	public void translate(MouseEvent e) {
		translate(e.getX(), e.getY());
	}

	public void rotate(MouseEvent e) {
		rotate(e.getX(), e.getY());
	}

	private Transform3D translateNew = new Transform3D();
	private Transform3D translateOld = new Transform3D();
	private Vector3d translation = new Vector3d();
	private Point3d v1 = new Point3d();
	private Point3d v2 = new Point3d();

	public void translate(int xNew, int yNew) {
		int dx = xNew - xLast;
		int dy = yNew - yLast;
		translateTG.getTransform(translateOld);
		v1.scale(dx, translationPerDx);
		v2.scale(-dy, translationPerDy);
		translation.add(v1, v2);
		translateNew.set(translation);
		translateNew.mul(translateOld);

		translateTG.setTransform(translateNew);
		transformChanged(BehaviorCallback.TRANSLATE, translateNew);	

		xLast = xNew;
		yLast = yNew;
	}

	private Transform3D rotateNew = new Transform3D();
	private Transform3D rotateOld = new Transform3D();
	public void rotate(int xNew, int yNew) {
		int dx = xNew - xLast;
		int dy = yNew - yLast;

		aaX.set(axisPerDx, dx * anglePerPix);
		aaY.set(axisPerDy, dy * anglePerPix);

		transX.set(aaX);
		transY.set(aaY);

		rotateTG.getTransform(rotateOld);

		rotateNew.set(transl_inv);
		rotateNew.mul(transY);
		rotateNew.mul(transX);
		rotateNew.mul(transl);
		rotateNew.mul(rotateOld);

		rotateTG.setTransform(rotateNew);
		xLast = xNew;
		yLast = yNew;

		transformChanged(BehaviorCallback.ROTATE, rotateNew);	
	}

	private void transformChanged(int type, Transform3D t) {
		if(callback != null)
			callback.transformChanged(type, t);
	}

	private class Initializer {
		private Point3d centerInVWorld = new Point3d();
		
		private Transform3D ipToVWorld           = new Transform3D();
		private Transform3D ipToVWorldInverse    = new Transform3D();
		private Transform3D localToVWorld        = new Transform3D();
		private Transform3D localToVWorldInverse = new Transform3D();

		private Point3d eyePtInVWorld  = new Point3d();
		private Point3d pickPtInVWorld = new Point3d();

		private Point3d p1 = new Point3d();
		private Point3d p2 = new Point3d();
		private Point3d p3 = new Point3d();

		private Vector3d vec = new Vector3d();

		private void init(Content c, int x, int y) {
			xLast = x;
			yLast = y;

			// some transforms
			c.getLocalToVworld(localToVWorld);
			localToVWorldInverse.invert(localToVWorld);
			canvas.getImagePlateToVworld(ipToVWorld);
			ipToVWorldInverse.invert(ipToVWorld);

			// calculate the canvas position in world coords
			c.getContent().getCenter(centerInVWorld);
			localToVWorld.transform(centerInVWorld);

			// get the eye point in world coordinates
			canvas.getCenterEyeInImagePlate(eyePtInVWorld);
			ipToVWorld.transform(eyePtInVWorld);

			// use picking to infer the radius of the virtual sphere which is rotated
			pickPtInVWorld = univ.getPicker().getPickPointGeometry(c, x, y);
			if(pickPtInVWorld == null)
				return;
			localToVWorld.transform(pickPtInVWorld);
			float r = (float)pickPtInVWorld.distance(centerInVWorld);
			float dD = (float)pickPtInVWorld.distance(eyePtInVWorld);

			// calculate distance between eye and canvas point
			canvas.getPixelLocationInImagePlate(x, y, p1);
			ipToVWorld.transform(p1);
			float dd = (float)p1.distance(eyePtInVWorld);

			// calculate the virtual distance between two neighboring pixels
			canvas.getPixelLocationInImagePlate(x+1, y, p2);
			ipToVWorld.transform(p2);
			float dx = (float)p1.distance(p2);

			// calculate the virtual distance between two neighboring pixels
			canvas.getPixelLocationInImagePlate(x, y+1, p3);
			ipToVWorld.transform(p3);
			float dy = (float)p1.distance(p3);

			float dX = dD / dd * dx;
			float dY = dD / dd * dy;

			anglePerPix = Math.atan2(dX, r);
			
			univ.getViewPlatformTransformer().getYDir(axisPerDx, ipToVWorld);
			univ.getViewPlatformTransformer().getXDir(axisPerDy, ipToVWorld);

			translationPerDx.set(axisPerDy);
			translationPerDx.scale(dX);

			translationPerDy.set(axisPerDx);
			translationPerDy.scale(dY);

			rotateTG = c.getLocalRotate();
			translateTG = c.getLocalTranslate();
			c.getContent().getCenter(vec);
			transl_inv.set(vec);
			vec.set(-vec.x, -vec.y, -vec.z);
			transl.set(vec);
		}
	}
}
