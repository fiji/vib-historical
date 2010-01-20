package ij3d;

import javax.media.j3d.Transform3D;
import javax.media.j3d.Canvas3D;

import javax.vecmath.Vector3d;
import javax.vecmath.Point3d;

public class ViewAdjuster {

	/** Fit the points horizontally */
	public static final int ADJUST_HORIZONTAL = 0;

	/** Fit the points vertically */
	public static final int ADJUST_VERTICAL   = 1;

	/** Fit the points both horizontally and vertically */
	public static final int ADJUST_BOTH       = 2;

	private Canvas3D canvas;
	private Image3DUniverse univ;

	private final Point3d eye = new Point3d();
	private final Point3d oldEye = new Point3d();

	private final Transform3D toCamera = new Transform3D();
	private final Transform3D toCameraInverse = new Transform3D();

	private boolean firstPoint = true;

	private double e = 1.0;
	private double w = 2 * Math.tan(Math.PI / 8);
	private double h = 2 * Math.tan(Math.PI / 8);

	private final Adjuster adjuster;

	private interface Adjuster {
		public void add(Point3d p);
	}

	/**
	 * Create a new ViewAdjuster.
	 * @param dir One of ADJUST_HEIGHT, ADJUST_WIDTH or ADJUST_BOTH.
	 *        The former adjusts the view so that the added
	 *        points fit in width, the latter so that they
	 *        fit in height in the canvas.
	 */
	public ViewAdjuster(Image3DUniverse univ, int dir) {
		this.univ = univ;
		this.canvas = univ.getCanvas();

		switch(dir) {
			case ADJUST_HORIZONTAL:
				adjuster = new HorizontalAdjuster();
				break;
			case ADJUST_VERTICAL:
				adjuster = new VerticalAdjuster();
				break;
			case ADJUST_BOTH:
				adjuster = new BothAdjuster();
				break;
			default:
				throw new IllegalArgumentException();
		}

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

		Transform3D toIpInverse = new Transform3D();
		canvas.getImagePlateToVworld(toIpInverse);

		// get the upper left canvas corner in camera coordinates
		Point3d lu = new Point3d();
		canvas.getPixelLocationInImagePlate(0, 0, lu);
		toIpInverse.transform(lu);
		toCamera.transform(lu);

		// get the lower right canvas corner in camera coordinates
		Point3d rl = new Point3d();
		canvas.getPixelLocationInImagePlate(
			canvas.getWidth(), canvas.getHeight(), rl);
		toIpInverse.transform(rl);
		toCamera.transform(rl);

		w = rl.x - lu.x;
		h = rl.y - lu.y;

		e = -rl.z;

		univ.getVworldToCameraInverse(toCameraInverse);
	}

	/**
	 * After all points/contents were added, apply() finally
	 * adjusts center and zoom transformations of the view.
	 */
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

	/**
	 * Add another Content which should be completely visible
	 * in the canvas.
	 */
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

	/**
	 * Add another point which should be visible in the canvas;
	 * the point is expected to be in local coordinates, with
	 * the given local-to-vworld transformation.
	 */
	public void add(Transform3D localToVworld, Point3d local) {
		localToVworld.transform(local);
		add(local);
	}

	/**
	 * Add another point which should be visible in the canvas;
	 * the point is expected to be in vworld coordinates.
	 */
	public void add(Point3d p) {
		adjuster.add(p);
	}

	private final class BothAdjuster implements Adjuster {
		private Adjuster hAdj, vAdj;

		public BothAdjuster() {
			hAdj = new HorizontalAdjuster();
			vAdj = new VerticalAdjuster();
		}

		public void add(Point3d point) {
			Point3d p = new Point3d(point);
			toCamera.transform(p);

			if(firstPoint) {
				eye.set(p.x, p.y, p.z);
				firstPoint = false;
				return;
			}
			vAdj.add(point);
			hAdj.add(point);
		}
	}

	private final class HorizontalAdjuster implements Adjuster {

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

	private final class VerticalAdjuster implements Adjuster {
		public void add(Point3d point) {
			Point3d p = new Point3d(point);
			toCamera.transform(p);

			if(firstPoint) {
				eye.set(eye.x, p.y, p.z);
				firstPoint = false;
				return;
			}

			double s1 = (p.y - eye.y) / h;
			double s2 = (eye.z - p.z) / (2 * e);

			double m1 = s1 - s2;
			double m2 = -s1 - s2;

			if(m1 > m2) {
				if(m1 > 0) {
					eye.y += m1 * h / 2;
					eye.z += m1 * e;
				}
			} else {
				if(m2 > 0) {
					eye.y -= m2 * h / 2;
					eye.z += m2 * e;
				}
			}
		}
	}
}

