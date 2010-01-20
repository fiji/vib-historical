package ij3d;

import javax.media.j3d.Transform3D;
import javax.media.j3d.Canvas3D;

import javax.vecmath.Vector3d;
import javax.vecmath.Point3d;

class ViewAdjuster {

	private Canvas3D canvas;
	private Image3DUniverse univ;

	private final Point3d eye = new Point3d();
	private final Point3d oldEye = new Point3d();

	private final Transform3D toCamera = new Transform3D();
	private final Transform3D toCameraInverse = new Transform3D();
	
	private final double e = 1.0;
	private final double w = 2 * Math.tan(Math.PI/8);

	public ViewAdjuster(Image3DUniverse univ) {
		this.univ = univ;
		this.canvas = univ.getCanvas();
	}

	public void init() {
		// get eye in image plate
		canvas.getCenterEyeInImagePlate(eye);
		// transform eye to vworld
		canvas.getImagePlateToVworld(toCamera);
		toCamera.transform(eye);
		// transform eye to camera
		univ.getVworldToCamera(toCamera);
		toCamera.transform(eye);

		// save the old eye pos
		oldEye.set(eye);

		univ.getVworldToCameraInverse(toCameraInverse);
	}

	public void apply() {
System.out.println("eye = " + eye);
System.out.println("oldEye = " + oldEye);
		Transform3D t3d = new Transform3D();
		Vector3d transl = new Vector3d();

		// adjust zoom
		univ.getZoomTG().getTransform(t3d);
		t3d.get(transl);
		transl.z += eye.z - oldEye.z;
		t3d.set(transl);
		univ.getZoomTG().setTransform(t3d);

		// adjust center
		Transform3D tmp = new Transform3D();
		univ.getCenterTG().getTransform(t3d);
		univ.getTranslateTG().getTransform(tmp);
		t3d.mul(tmp);
		univ.getRotationTG().getTransform(tmp);
		t3d.mul(tmp);
		transl.set(eye.x - oldEye.x, eye.y - oldEye.y, 0d);
		tmp.set(transl);
		t3d.mul(tmp);
		univ.getRotationTG().getTransform(tmp);
		t3d.mulInverse(tmp);
		univ.getTranslateTG().getTransform(tmp);
		t3d.mulInverse(tmp);
		univ.getCenterTG().setTransform(t3d);
	}

	/*
	 * Assumes p to be in vworld coordinates
	 */
	public void adjustViewXZ(Point3d point) {
		Point3d p = new Point3d(point);
		toCamera.transform(p);

		double s1 = (p.x - eye.x) / w;
		double s2 = (eye.z - p.z) / (2 * e);

		double m1 = s1 + s2;
		double m2 = -s1 + s2;

		if(m1 > m2) {
			if(m1 > 0) {
				eye.x += m1 * w / 2;
				eye.z -= m1 * e;
			}
		} else {
			if(m2 > 0) {
				eye.x -= m2 * w / 2;
				eye.z -= m2 * e;
			}
		}
System.out.println("eye = " + eye);
	}
}

