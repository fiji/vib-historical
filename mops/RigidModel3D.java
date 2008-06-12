package mops;

import java.util.Collection;
import java.util.Iterator;

import mpicbg.models.Model;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.NoninvertibleModelException;
import mpicbg.models.NotEnoughDataPointsException;

import vib.FastMatrix;
import math3d.Point3d;

/**
 * @author Bene
 *
 */
public class RigidModel3D extends Model
{

	private FastMatrix matrix;

	/* (non-Javadoc)
	 * @see mpicbg.models.Model#apply(float[])
	 */
	@Override
	public float[] apply( float[] point )
	{
		matrix.apply(point[0], point[1], point[2]);
		return new float[] {
			(float)matrix.x, (float)matrix.y, (float)matrix.z};
	}

	/* (non-Javadoc)
	 * @see mpicbg.models.Model#applyInPlace(float[])
	 */
	@Override
	public void applyInPlace( float[] point )
	{
		matrix.apply(point[0], point[1], point[2]);
		point[0] = (float)matrix.x;
		point[1] = (float)matrix.y;
		point[2] = (float)matrix.z;
	}

	/* (non-Javadoc)
	 * @see mpicbg.models.Model#applyInverse(float[])
	 */
	@Override
	public float[] applyInverse( float[] point ) throws NoninvertibleModelException
	{
		FastMatrix inverse = matrix.inverse();
		inverse.apply(point[0], point[1], point[2]);
		return new float[] {
			(float)inverse.x, (float)inverse.y, (float)inverse.z};
	}

	/* (non-Javadoc)
	 * @see mpicbg.models.Model#applyInverseInPlace(float[])
	 */
	@Override
	public void applyInverseInPlace( float[] point ) throws NoninvertibleModelException
	{
		FastMatrix inverse = matrix.inverse();
		inverse.apply(point[0], point[1], point[2]);
		point[0] = (float)inverse.x;
		point[1] = (float)inverse.y;
		point[2] = (float)inverse.z;
	}

	/* (non-Javadoc)
	 * @see mpicbg.models.Model#clone()
	 */
	@Override
	public Model clone()
	{
		RigidModel3D model = new RigidModel3D();
		model.matrix = new FastMatrix(matrix);
		return model;
	}

	/* (non-Javadoc)
	 * @see mpicbg.models.Model#fit(java.util.Collection)
	 * p1 in PointMatch should be transformed to match p2
	 */
	@Override
	public void fit( Collection< PointMatch > matches ) throws NotEnoughDataPointsException
	{
		int n = matches.size();
		Point3d[] model = new Point3d[n];
		Point3d[] templ = new Point3d[n];
		Iterator<PointMatch> it = matches.iterator();
		int i = 0;
		while(it.hasNext()) {
			PointMatch match = it.next();
			float[] m = match.getP1().getL();
			float[] t = match.getP2().getW();
			model[i] = new Point3d(m[0], m[1], m[2]);
			templ[i] = new Point3d(t[0], t[1], t[2]);
			i++;
		}
		matrix = FastMatrix.bestRigid(model, templ);
	}

	/* (non-Javadoc)
	 * @see mpicbg.models.Model#getMinSetSize()
	 */
	@Override
	public int getMinSetSize()
	{
		return 5;
	}

	/* (non-Javadoc)
	 * @see mpicbg.models.Model#shake(java.util.Collection, float, float[])
	 */
	@Override
	public void shake( Collection< PointMatch > matches, float scale, float[] center )
	{
		// do nth
	}

	/* (non-Javadoc)
	 * @see mpicbg.models.Model#toString()
	 */
	@Override
	public String toString()
	{
		return matrix.toString();
	}
}

