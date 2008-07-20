package mipj;

public class IntegerRayCast
{

	private int pos[];
	private int dx, dy, dz;
	private int incx, incy, incz, fracy, fracx, fracz;
	private int traceby;

	public IntegerRayCast( int start[], int end[] )
	{

		pos = new int[3];

		pos[0] = start[0];
		pos[1] = start[1];
		pos[2] = start[2];

		// set up all the bresenham-type stuff here

		dx = end[0] - start[0];    // steps across
		dy = end[1] - start[1];    // steps vertically
		dz = end[2] - start[2];    // steps into

		if (dy < 0)
		{
			dy = -dy;    // then the line's going down the way
			incy = -1;
		}
		else
			incy = 1;  // going upwards

		if (dx < 0)
		{
			dx = -dx;  // line going left
			incx = -1;
		}
		else
			incx = 1;  // line going right

		if (dz < 0)
		{
			dz = -dz;
			incz = -1;
		}
		else
			incz = 1;

		if (dx > dy && dx > dz) // tracing along x axis
		{
			fracy = (dy<<1) - (dx);
			fracz = (dz<<1) - (dx);
			traceby = 0;
		}
		else if ( dy > dx && dy > dz)
		{

			fracx = (dx<<1) - (dy);
			fracz = (dz<<1) - (dy);
			traceby = 1;

		}
		else
		{
			fracx = (dx<<1) - (dz);
			fracy = (dy<<1) - (dz);
			traceby = 2;
		}

		dy <<= 1;
		dx <<= 1;
		dz <<= 1;

	}

	public int[] next()
	{

		if ( traceby == 0 ) // x
		{
			if (fracy >= 0)
			{
				pos[1] += incy;
				fracy -= dx;
			}

			if (fracz >= 0)
			{
				pos[2] += incz;
				fracz -= dx;
			}

			fracy += dy;
			fracz += dz;
			pos[0] += incx;

		}
		else if ( traceby == 1 ) //y
		{
			if (fracx >= 0)
			{
				pos[0] += incx;
				fracx -= dy;
			}

			if (fracz >= 0)
			{
				pos[2] += incz;
				fracz -= dy;
			}

			fracx += dx;
			fracz += dz;
			pos[1] += incy;
		}
		else // z
		{
			if (fracx >= 0)
			{
				pos[0] += incx;
				fracx -= dz;
			}

			if (fracy >= 0)
			{
				pos[1] += incy;
				fracz -= dz;
			}

			fracx += dx;
			fracy += dy;
			pos[2] += incz;
		}

		return pos;

	}

	public int[] getSteps()
	{
		int[] answer = new int[3];
		answer[0] = incx;
		answer[1] = incy;
		answer[2] = incz;
		return answer;
	}

	public byte[] createTemplate()
	{

		int length;


		if ( traceby == 0 )
		{
			length = dx >> 1;
		}
		else if ( traceby == 1 )
		{
			length = dy >> 1;
		}
		else
		{
			length = dz >> 1;
		}

		//System.out.println(length);

		byte[] answer = new byte[length];

		int x,y,z;

		for( int i = 0 ; i < length ; ++i )
		{

			x = pos[0];
			y = pos[1];
			z = pos[2];

			int[] step = next();

			if ( (step[0] - x) != 0 )
			{
				answer[i] |= 0x01;
				//System.out.println("x step");
			}
			if ( (step[1] - y) != 0 )
			{
				answer[i] |= 0x02;
				//System.out.println("y step");
			}
			if ( (step[2] - z) != 0 )
			{
				answer[i] |= 0x04;
				//System.out.println("z step");
			}

		}


		return answer;


	}

	public static void main(String[] args)
	{

		int[] a = new int[3];
		int[] b = new int[3];

		a[0] = a[1] = a[2] = 0;
		b[0] = 50;
		b[1] = 25;
		b[2] = -4;

		IntegerRayCast irc = new IntegerRayCast( a, b );

		for (int i = 0 ; i < 50 ; ++i)
		{
			int[] pos = irc.next();
			System.out.println( pos[0] + " " + pos[1] + " " + pos[2] );
		}

	}

}