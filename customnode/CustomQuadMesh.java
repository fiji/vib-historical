package customnode;

import java.util.List;
import java.util.Arrays;

import javax.media.j3d.Geometry;
import javax.media.j3d.GeometryArray;
import javax.media.j3d.QuadArray;
import javax.vecmath.Color3f;
import javax.vecmath.Point3f;

import com.sun.j3d.utils.geometry.GeometryInfo;
import com.sun.j3d.utils.geometry.NormalGenerator;
import com.sun.j3d.utils.geometry.Stripifier;

public class CustomQuadMesh extends CustomTriangleMesh {

	public CustomQuadMesh(List<Point3f> mesh) {
		super(mesh);
	}

	public CustomQuadMesh(List<Point3f> mesh, Color3f color, float trans) {
		super(mesh, color, trans);
	}

	@Override
	protected GeometryArray createGeometry() {
		if(mesh.size() < 4)
			return null;
		List<Point3f> tri = mesh;
		int nValid = tri.size();
		int nAll = 2 * nValid;

		Point3f[] coords = new Point3f[nValid];
		tri.toArray(coords);

		Color3f colors[] = new Color3f[nValid];
		Arrays.fill(colors, color);

		GeometryArray ta = new QuadArray(nAll,
						QuadArray.COORDINATES |
						QuadArray.COLOR_3 |
						QuadArray.NORMALS);

		ta.setCoordinates(0, coords);
		ta.setColors(0, colors);

		GeometryInfo gi = new GeometryInfo(ta);
		gi.recomputeIndices();
		// generate normals
		NormalGenerator ng = new NormalGenerator();
		ng.generateNormals(gi);
		// stripify
		Stripifier st = new Stripifier();
		st.stripify(gi);
		GeometryArray result = gi.getGeometryArray();
		result.setCapability(GeometryArray.ALLOW_COLOR_WRITE);
		result.setCapability(GeometryArray.ALLOW_COUNT_READ);
		result.setCapability(GeometryArray.ALLOW_INTERSECT);

		return result;
	}
}
