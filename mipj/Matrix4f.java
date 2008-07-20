package mipj;
import java.util.Arrays;

public class Matrix4f
{

    public float m[];
    private final float HPI = 1.570796f;

    /** Creates a matrix set to the 4x4 identity matrix */

    public Matrix4f()
    {
        m = new float[16];
        setIdentity();
    }

    /** Creates a matrix with values equal to another */

	public Matrix4f( Matrix4f mat )
	{
		m = new float[16];
		System.arraycopy( mat.m, 0, m, 0, 16 );
	}

	/** Sets the matrix to the identity matrix */

    public void setIdentity()
    {

        Arrays.fill( m, 0.0f );

        m[0] = 1.0f;
        m[5] = 1.0f;
        m[10] = 1.0f;
        m[15] = 1.0f;

    }

	/** Mutiply by argument matrix */

    public void mul( Matrix4f m2 )
    {

        mul( m2.m );

    }


	/** Multiply matrix by matrix defined as float[16]*/

    private void mul( float[] y )
    {

        float tmp[] = new float[16];

		mul( m, y, tmp );

        System.arraycopy( tmp, 0, m, 0, 16 );

    }

	/** Multiply float arrays a*b into c */

    private void mul( float[] a, float[] b, float[] c )
    {

        /*for(int i = 0 ; i < 4 ; ++i)
            for(int j = 0 ; j < 16 ; j += 4)
                c[i+j] = a[j]*b[0+i] + a[j+1]*b[4+i] + a[j+2]*b[8+i] + a[j+3]*b[12+i];*/

          // might be a wee bit more efficient like this!

          c[0] = a[0]*b[0] + a[1]*b[4] + a[2]*b[8] + a[3]*b[12];
          c[1] = a[0]*b[1] + a[1]*b[5] + a[2]*b[9] + a[3]*b[13];
          c[2] = a[0]*b[2] + a[1]*b[6] + a[2]*b[10] + a[3]*b[14];
          c[3] = a[0]*b[3] + a[1]*b[7] + a[2]*b[11] + a[3]*b[15];
          c[4] = a[4]*b[0] + a[5]*b[4] + a[6]*b[8] + a[7]*b[12];
          c[5] = a[4]*b[1] + a[5]*b[5] + a[6]*b[9] + a[7]*b[13];
          c[6] = a[4]*b[2] + a[5]*b[6] + a[6]*b[10] + a[7]*b[14];
          c[7] = a[4]*b[3] + a[5]*b[7] + a[6]*b[11] + a[7]*b[15];
          c[8] = a[8]*b[0] + a[9]*b[4] + a[10]*b[8] + a[11]*b[12];
          c[9] = a[8]*b[1] + a[9]*b[5] + a[10]*b[9] + a[11]*b[13];
          c[10] = a[8]*b[2] + a[9]*b[6] + a[10]*b[10] + a[11]*b[14];
          c[11] = a[8]*b[3] + a[9]*b[7] + a[10]*b[11] + a[11]*b[15];
          c[12] = a[12]*b[0] + a[13]*b[4] + a[14]*b[8] + a[15]*b[12];
          c[13] = a[12]*b[1] + a[13]*b[5] + a[14]*b[9] + a[15]*b[13];
          c[14] = a[12]*b[2] + a[13]*b[6] + a[14]*b[10] + a[15]*b[14];
          c[15] = a[12]*b[3] + a[13]*b[7] + a[14]*b[11] + a[15]*b[15];

	}

	/** Transform given vector by matrix. */

    public void transform( Vector3f v )
    {

		float x = v.x;
		float y = v.y;
		float z = v.z;

		v.x = m[0]*x + m[1]*y + m[2]*z + m[3];
		v.y = m[4]*x + m[5]*y + m[6]*z + m[7];
		v.z = m[8]*x + m[9]*y + m[10]*z + m[11];

	}

	/** Translate matrix by vector (x,y,z) */

	public void translateBy( float x, float y, float z )
	{

		float t[] = new float[16];
		t[0] = t[5] = t[10] = t[15] = 1.0f;

		t[3] = x;
		t[7] = y;
		t[11] = z;

		float tmp[] = new float[16];

		System.arraycopy( m, 0, tmp, 0, 16 );

		mul(t, tmp, m);

	}

	/** Inverts the matrix  */

	public void invert()
	{

		float[] inverted = new float[16];

		// compute adj matrix

		for (int i = 0 ; i < 3 ; ++i)
		{
			 for (int j = 0 ; j < 3 ; ++j)
			 {
				int a = ((i + 1) % 3) << 2;
				int b = ((i + 2) % 3) << 2;
				int c = (j + 1) % 3;
				int d = (j + 2) % 3;

				inverted[(j<<2)+i] = (m[a+c] * m[b+d]) - (m[a+d] * m[b+c]);
			 }
		}

		// get det

		float det = m[0] * inverted[0]
				  + m[4] * inverted[1]
				  + m[8] * inverted[2];

		// normalise

		float invdet = 1.0f / det;

		for (int i = 0 ; i < 3 ; ++i)
		{

			int a = i << 2;

			for (int j = 0 ; j < 3 ; ++j)
			{
				inverted[a+j] *= invdet;
			}
		}

		// invert

		for (int i = 0 ; i < 3 ; i++)
		{
			int a = i << 2;

			inverted[(i<<2)+3] = - inverted[a+0] * m[3]
							- inverted[a+1] * m[7]
							- inverted[a+2] * m[11] ;
		}

		m = inverted;

	}

	/** Rotate the matrix around the X axis by angle (radians) */

    public void rotByX( float angle )
    {

		float x[] = new float[16];

		x[0] = 1.0f;
        x[5] = (float) Math.cos( angle );
        x[6] = (float) -Math.sin( angle );
        x[9] = (float) Math.sin(angle);
        x[10] = (float) Math.cos(angle);
        x[15] = 1.0f;

		float tmp[] = new float[16];

		System.arraycopy( m, 0, tmp, 0, 16 );

        mul( x, tmp, m );

    }

    /** Rotate the matrix around the Y axis by angle (radians) */

    public void rotByY( float angle )
    {

        float y[] = new float[16];


        y[0] = (float) Math.cos( angle );
        y[2] = (float) Math.sin( angle );
        y[5] = 1.0f;
        y[8] = (float) -Math.sin(angle);
        y[10] = (float) Math.cos(angle);
        y[15] = 1.0f;

		float tmp[] = new float[16];

		System.arraycopy( m, 0, tmp, 0, 16 );

        mul( y, tmp, m );

    }

	/** Rotate the matrix around the Z axis by angle (radians) */

    public void rotByZ( float angle )
    {

        float z[] = new float[16];


        z[0] = (float) Math.cos( angle );
        z[1] = (float) -Math.sin( angle );
        z[10] = 1.0f;
        z[4] = (float) Math.sin(angle);
        z[5] = (float) Math.cos(angle);
       	z[15] = 1.0f;

		float tmp[] = new float[16];

		System.arraycopy( m, 0, tmp, 0, 16 );

        mul( z, tmp, m );

    }

	/** Sets the matrix to the rotation around the X axis by angle (radians) */

    public void rotX( float angle )
    {

		setIdentity();

        m[5] = (float) Math.cos( angle );
        m[6] = (float) -Math.sin( angle );
        m[9] = (float) Math.sin(angle);
        m[10] = (float) Math.cos(angle);


    }

    /** Sets the matrix to the rotation around the Y axis by angle (radians) */

    public void rotY( float angle )
    {

		setIdentity();

        m[0] = (float) Math.cos( angle );
        m[2] = (float) Math.sin( angle );
        m[8] = (float) -Math.sin(angle);
        m[10] = (float) Math.cos(angle);


    }

    /** Sets the matrix to the rotation around the Z axis by angle (radians) */

    public void rotZ( float angle )
    {

        setIdentity();

        m[0] = (float) Math.cos( angle );
        m[1] = (float) -Math.sin( angle );
        m[4] = (float) Math.sin(angle);
        m[5] = (float) Math.cos(angle);

    }

    /** Displays the matrix */

    public void show()
    {

		for(int i = 0 ; i < 16 ; ++i )
			System.out.println(m[i]);

	}

	/** Obtains euler angles from the matrix as float[3] */

	public float[] getEuler()
	{

		float[] pos = new float[3];

		if ( m[0] != 0.0f )
		{
			pos[2] = (float) Math.atan2( (double)m[4], (double)m[0] );
			pos[2] = (pos[2] / (float)Math.PI) * 180.0f;
		}
		else
		{
			pos[2] = 180.0f;
		}

		if ( m[10] != 0.0f )
		{
			pos[0] = (float) Math.atan2( (double)m[9], (double)m[10] );
			pos[0] = (pos[0] / (float)Math.PI) * 180.0f;
		}
		else
		{
			pos[0] = -180.0f;
		}

		pos[1] = (float) Math.asin( -m[8] );
		pos[1] = (pos[1] / HPI) * 90.0f;

		if ( pos[1] < -90.0f )
			pos[1] = -90.0f;
		else if ( pos[1] > 90.0f )
			pos[1] = 90.0f;

		for(int i = 0 ; i < 3 ; i+=2 )
		{

			if ( pos[i] < -180.0f )
				pos[i] = -180.0f;
			else if ( pos[i] > 180.0f )
				pos[i] = 180.0f;

		}

		return pos;

	}

	public void setEuler( float[] euler )
	{

		Matrix4f tmp = new Matrix4f();

		setIdentity();

		rotByX( euler[0] );

		rotByY( euler[1] );

		rotByZ( euler[2] );

	}



}
