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

	private boolean firstPoint = true;

	private final double e = 1.0;
	private final double w = 2 * Math.tan(Math.PI / 8);
	private final double h = 2 * Math.tan(Math.PI / 8);

	public ViewAdjuster(Image3DUniverse univ) {
		this.univ = univ;
		this.canvas = univ.getCanvas();

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
		/* The camera to vworld transformation is given by
		 * C * T * R * Z, where
		 *
		 * C: center transformation
		 * T: user defined translation
		 * R: rotation
		 * Z: zoom translation
		 *
		 * the calculated eye coordinate can be thought of as
		 * an additional transformation A, so that we have
		 * C * T * R * Z * A.
		 *
		 * A should be split into A_z, the z translation, which
		 * is incorporated into the Zoom translation Z, and
		 * A_xy, which should be incorporated into the center
		 * transformation C.
		 *
		 * C * T * R * A * Z_new = C_new * T * R * Z_new
		 *
		 * where Z_new = A_z * Z.
		 *
		 * C_new is then C * T * R * A_xy * R^-1 * T^-1
		 */
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

	public void add(Content c) {
		Transform3D localToVworld = new Transform3D();
		c.getContent().getLocalToVworld(localToVworld);

		Point3d min = new Point3d();
		c.getContent().getMin(min);

		Point3d max = new Point3d();
		c.getContent().getMax(max);

		Point3d tmp = new Point3d();

		// transform each of the 8 corners to vworld
		// coordinates and feed it to add(Point3d).
		add(localToVworld, new Point3d(min.x, min.y, min.z));
		add(localToVworld, new Point3d(max.x, min.y, min.z));
		add(localToVworld, new Point3d(min.x, max.y, min.z));
		add(localToVworld, new Point3d(max.x, max.y, min.z));
		add(localToVworld, new Point3d(min.x, min.y, max.z));
		add(localToVworld, new Point3d(max.x, min.y, max.z));
		add(localToVworld, new Point3d(min.x, max.y, max.z));
		add(localToVworld, new Point3d(max.x, max.y, max.z));
	}

	/*
	 * Helper function.
	 */
	public void add(Transform3D localToVworld, Point3d local) {
		localToVworld.transform(local);
		add(local);
	}

	/*
	 * Assumes p to be in vworld coordinates
	 */
	public void add(Point3d point) {
		Point3d p = new Point3d(point);
		toCamera.transform(p);

		if(firstPoint) {
			eye.set(p.x, eye.y, p.z);
			firstPoint = false;
			return;
		}

		double s1 = (p.x - eye.x) / w;
		double s2 = (eye.z - p.z) / (2 * e);

		double m1 = s1 - s2;
		double m2 = -s1 - s2;

		if(m1 > m2) {
			if(m1 > 0) {
				eye.x += m1 * w / 2;
				eye.z += m1 * e;
			}
		} else {
			if(m2 > 0) {
				eye.x -= m2 * w / 2;
				eye.z += m2 * e;
			}
		}
	}
}

