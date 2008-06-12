package ij3d;

import com.sun.j3d.utils.geometry.ColorCube;
import com.sun.j3d.utils.universe.*;
import com.sun.j3d.utils.geometry.*;
import com.sun.j3d.utils.behaviors.mouse.*;
import javax.media.j3d.*;
import javax.vecmath.*;
import mops.Feature;

public class FeatureShape extends BranchGroup {

	private Feature feature;
	private Color3f color = new Color3f(1, 0, 0);
	private Color3f leftupper = new Color3f(0, 1, 0);
	private Shape3D shape;
	private Geometry geometry;

	private Point3d[] vertices;

	public FeatureShape() {
		shape = new Shape3D();
		shape.setCapability(Shape3D.ALLOW_GEOMETRY_WRITE);
		shape.setAppearance(initAppearance());
		initGeom();
		shape.setGeometry(geometry);
		addChild(shape);
	}

	public void setFeature(Feature f) {
		vertices = f.vertices;
		initGeom();
		shape.setGeometry(geometry);
	}

	private Appearance initAppearance() {
		Appearance appearance = new Appearance();
		PolygonAttributes pa = new PolygonAttributes();
		pa.setPolygonMode(PolygonAttributes.POLYGON_LINE);
		pa.setCullFace(PolygonAttributes.CULL_NONE);
		appearance.setPolygonAttributes(pa);
		return appearance;
	}

	private void initGeom() {
		if(vertices == null) {
			geometry = null;
			return;
		}
		Point3d[] coords = new Point3d[24];
		coords[0] = vertices[0];
		coords[1] = vertices[1];
		coords[2] = vertices[2];
		coords[3] = vertices[3];
		
		coords[4] = vertices[1];
		coords[5] = vertices[5];
		coords[6] = vertices[6];
		coords[7] = vertices[2];
		
		coords[8] = vertices[5];
		coords[9] = vertices[4];
		coords[10] = vertices[7];
		coords[11] = vertices[6];
		
		coords[12] = vertices[4];
		coords[13] = vertices[0];
		coords[14] = vertices[3];
		coords[15] = vertices[7];
		
		coords[16] = vertices[1];
		coords[17] = vertices[0];
		coords[18] = vertices[4];
		coords[19] = vertices[5];
		
		coords[20] = vertices[3];
		coords[21] = vertices[2];
		coords[22] = vertices[6];
		coords[23] = vertices[7];

		Color3f[] col = new Color3f[24];
		col[0] = leftupper;
		for(int i = 1; i < 24; i++) 
			col[i] = color;

		QuadArray ga = new QuadArray(24, 
				QuadArray.COORDINATES |
				QuadArray.COLOR_3 |
				QuadArray.NORMALS);
		ga.setCoordinates(0, coords);
		ga.setColors(0, col);

		geometry = ga;
	}
} 

