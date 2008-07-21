/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

package mipj;

public class Vector3f
{

	public float x,y,z;

	/** Constructs a vector with values (x, y, z) */

	public Vector3f( float x, float y, float z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/** Constructs a vector with values of argument vector */

	public Vector3f( Vector3f v ) {
		this.x = v.x;
		this.y = v.y;
		this.z = v.z;
	}

	/** Constructs a vector with values (0,0,0) */

	public Vector3f() {
	}

	/** Add the Vector to this Vector */

	public void add( Vector3f v ) {
		x += v.x;
		y += v.y;
		z += v.z;
	}

	/** Scale the vector by scale */

	public void scale( float s ) {

		x *= s;
		y *= s;
		z *= s;

	}

	/** Return the length of the vector */

	public float length() {

		return (float) Math.sqrt(  (double) ( x*x + y*y + z*z ) );

	}

}
