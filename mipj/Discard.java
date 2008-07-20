package mipj;
import java.util.*;
import java.io.*;

import ij.IJ;

public class Discard
{

    private ImageStack stack;
    private byte[][] data;

    private int width, height, depth;

    private final int XYZ = 0;
    private final int ZYX = 1;
    private final int XZY = 2;

    // describe part to compute

    public static final int FRONTMAIN = 0;
    public static final int REARMAIN = 1;
    public static final int LEFTMAIN = 2;
    public static final int RIGHTMAIN = 3;
    public static final int TOPMAIN = 4;
    public static final int LOWERMAIN = 5;
    public static final int LOWER = 0;
    public static final int UPPER = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;

    public static boolean plugin = false;

    /** Produce a discard class from an image stack */

    public Discard(ImageStack is)
    {
        stack = is;

        data = is.getStackCopy();

        width = stack.getWidth();
        height = stack.getHeight();
        depth = stack.getDepth();

    }

    /** Discards (by setting to zero) all voxels in which every neighbour is greater than itself. This tends
    to remove a small amount (<10%). */

    public int discardNN()
    {

        int count = 0;

        // for every voxel

        System.out.println( "Discarding wasted voxels..." );

        for(int z = 0 ; z < depth ; ++z)
        {

            if ( plugin )
            {
                double pc = (((100*z)/depth) / 100.0);
                IJ.showProgress( pc );
            }
            else
                ; // System.out.print("Discarding wasted voxels..." + (100*z)/depth + "%\r");

            for(int y = 0 ; y < height ; ++y)
            {

                for(int x = 0 ; x < width ; ++x)
                {

                    // assume it's the smallest unless proven otherwise

                    boolean smallest = true;

                    // for every surrounding voxel

                    for( int zi = -1 ; zi <= 1 ; ++zi )
                    {
                        for (int yi = -1 ; yi <= 1 ; ++yi )
                        {
                            for( int xi = -1 ; xi <= 1 ; ++xi)
                            {

                                int xtest, ytest, ztest;

                                xtest = x+xi;
                                ytest = y+yi;
                                ztest = z+zi;

                                // if it isn't outside the dataset

                                if ( (ytest) >= height ||
                                     (ztest) >= depth ||
                                     (ztest) < 0 ||
                                     (xtest) >= width ||
                                     (ytest) < 0 ||
                                     (xtest) < 0)
                                         continue;

                                // if proved not to be smallest, then quit loop

                                if ( (0xff & data[z][x + (y*width)]) > (0xff & data[ztest][(xtest) + ((ytest)*width)]) )
                                {
                                    smallest = false;
                                    break;
                                }

                            }   // zi

                            if ( !smallest )
                                break;

                        } // yi

                        if ( !smallest )
                            break;

                    } // xi

                    if (smallest)
                    {

                        // if not already zero, count it and set it to zero

                        if ( data[z][x + (width*y)] != 0 )
                        {
                            count++;
                            data[z][x + (width*y)] = 0;
                        }

                    }

                } // z

            } // y

        } // x

        if ( plugin )
        {
            IJ.showProgress( 1.0 );
        }
        else
            System.out.println("Discarding wasted voxels...DONE (" + (100*count)/(depth*height*width) + "% discarded)");

        return count;

    }

    /** Check from left to right */

    private void checkLeft( byte[] front, byte[] newfront )
    {

        for( int y = height - 1 ; y >= 0 ; --y )
        {

            for( int x = 0 ; x < width ; ++x )
            {

                // check from bottom, then from horizontal

                int maxMin;
                int position = x+(width*y);

                if ( x == 0 ) // special case for being at left of volume
                    maxMin = 0;
                else
                    maxMin = (front[position-1] & 0xff);

                // get the minimum of         below             horizontal

                newfront[position] = (byte) Math.min( maxMin, (front[position] & 0xff) );

            }

        }

    }

    /** Check from right to left */

    private void checkRight( byte[] front, byte[] newfront )
    {

        for( int y = height - 1 ; y >= 0 ; --y )
        {

            for( int x = 0 ; x < width ; ++x )
            {

                // check from bottom, then from horizontal

                int maxMin;
                int position = x+(width*y);

                if ( x == width-1 ) // special case for being at right of volume
                    maxMin = 0;
                else
                    maxMin = (front[position+1] & 0xff);

                // get the minimum of         below             horizontal

                newfront[position] = (byte) Math.min( maxMin, (front[position] & 0xff) );

            }

        }

    }

    /** Check from bottom to top */

    private void checkLow( byte[] front, byte[] newfront )
    {

        for( int y = height - 1 ; y >= 0 ; --y )
        {

            for( int x = 0 ; x < width ; ++x )
            {

                // check from bottom, then from horizontal

                int maxMin;
                int position = x+(width*y);

                if ( y == 0 ) // special case for being at bottom of volume
                    maxMin = 0;
                else
                    maxMin = (front[x + (width*(y-1))] & 0xff);

                // get the minimum of         below             horizontal

                newfront[position] = (byte) Math.min( maxMin, (front[position] & 0xff) );

            }

        }

    }

    /** Check from top to bottom */

    private void checkHigh( byte[] front, byte[] newfront )
    {

        for( int y = height - 1 ; y >= 0 ; --y )
        {

            for( int x = 0 ; x < width ; ++x )
            {

                // check from bottom, then from horizontal

                int maxMin;
                int position = x+(width*y);

                if ( y == height-1 ) // special case for being at top of volume
                    maxMin = 0;
                else
                    maxMin = (front[x + (width*(y+1))] & 0xff);

                // get the minimum of         below             horizontal

                newfront[position] = (byte) Math.min( maxMin, (front[position] & 0xff) );

            }

        }

    }

    /** Removes data which is less than the values of the progressing front */

    private void exclude(byte[][] datain, byte[] front, int z )
    {

        for( int y = 0 ; y < height ; ++y )
        {

            for( int x = 0 ; x < width ; ++x )
            {

                int position = x+(width*y);

                if ( (front[position] & 0xff) >= (datain[z][position] & 0xff) )
                {
                    if ( datain[z][position] != 0 )
                        datain[z][position] = 0;
                }
                else
                    front[position] = datain[z][position];

            }

        }

    }

    /** Sends a "front" through the dataset, maintaining the minimum value possible
    for the maximum intensity as it goes, to calculate a dataset which is used for
    real-time projection.. */

    public RealTimeMIP calculate(int main, int sub)
    {

        byte[][] dataout;

        if ( main == FRONTMAIN || main == REARMAIN )
        {

            width = stack.getWidth();
            height = stack.getHeight();
            depth = stack.getDepth();

            dataout = new byte[data.length][];

            System.out.println("Creating RealTime Dataset... 20%");

            for(int i = 0 ; i < data.length ; ++i )
            {

                if (plugin)
                {

                    double pc = ((20*i)/(data.length))/100.0;
                    IJ.showProgress( pc );

                }
                else
                    ; // System.out.print("Creating RealTime Dataset..." + (20*i)/(data.length) + "%\r");

                dataout[i] = new byte[data[i].length];
                System.arraycopy( data[i], 0, dataout[i], 0, data[i].length );
            }

        }
        else if ( main == LEFTMAIN || main == RIGHTMAIN )
        {
            width = stack.getDepth(); // this is "wrong" deliberately!
            height = stack.getHeight();
            depth = stack.getWidth();

            // translate the actual data around, so I can use the same old routines

            dataout = new byte[depth][];

            for( int i = 0 ; i < depth ; ++i )
            {
                dataout[i] = new byte[width*height];
            }

            System.out.print( "Creating RealTime Dataset... 20%" );

            for( int i = 0 ; i < depth ; ++i )
            {

                if (plugin) {

                    double pc = ((20*i)/(depth))/100.0;
                    IJ.showProgress( pc );

                } else
                    ; // System.out.print("Creating RealTime Dataset..." + (20*i)/(depth) + "%\r");

                for( int y = 0 ; y < height ; ++y ) {
                    for( int x = 0 ; x < width  ; ++x ) {
                        dataout[i][x+(width*y)] = data[x][i + (depth*y)];
                    }
                }

            }
        }
        else //if ( TOPMAIN || LOWERMAIN )
        {

            height = stack.getDepth(); // this is "wrong" deliberately!
            depth = stack.getHeight();
            width = stack.getWidth();

            // translate the actual data around, so I can use the same old routines

            dataout = new byte[depth][];

            for( int i = 0 ; i < depth ; ++i )
            {
                dataout[i] = new byte[width*height];
            }

            System.out.println("Creating RealTime Dataset... 20%");

            for( int i = 0 ; i < depth ; ++i )
            {

                if (plugin)
                {
                    double pc = ((20*i)/(depth))/100.0;
                    IJ.showProgress( pc );

                }
                else
                    ; // System.out.print("Creating RealTime Dataset..." + (20*i)/(depth) + "%\r");

                for( int y = 0 ; y < height ; ++y )
                {
                    for( int x = 0 ; x < width  ; ++x )
                    {
                        dataout[i][x+(width*y)] = data[y][x + (width*i)];
                    }
                }

            }

        }

        // store the current "minimum maximums"

        byte[] front = new byte[height*width];
        byte[] newfront = new byte[height*width];

        if ( main == FRONTMAIN || main == LEFTMAIN || main == TOPMAIN )
        {

            System.out.println("Creating RealTime Dataset... 20%");

            for( int z = 0 ; z < depth ; ++z )
            {
                if (plugin)
                {

                    double pc = (20+(20*z)/(depth))/100.0;
                    IJ.showProgress( pc );

                }
                else
                    ; // System.out.print("Creating RealTime Dataset..." + (20+((20*z)/(depth))) + "%\r");

                if (sub == LOWER)
                    checkLow(front, newfront);
                else if (sub == UPPER)
                    checkHigh(front, newfront );
                else if (sub == LEFT)
                    checkLeft(front, newfront);
                else if (sub == RIGHT)
                    checkRight( front, newfront );
                byte[] tmp;
                tmp = front;
                front = newfront;
                newfront = tmp;

                // exclude anything which is lower than the "minimum maximum"

                exclude( dataout , front, z);

            } // z

        }
        else //if (REARMAIN || RIGHTMAIN || LOWERMAIN )
        {

            System.out.print("Creating RealTime Dataset... 20%");

            for( int z = depth-1 ; z >=0 ; --z )
            {

                if (plugin)
                {

                    double pc = (20+(20*(depth-z))/(depth))/100.0;
                    IJ.showProgress( pc );

                }
                else
                    ; // System.out.print("Creating RealTime Dataset..." + (20+((20*(depth-z))/(depth))) + "%\r");

                if (sub == LOWER)
                    checkLow(front, newfront);
                else if (sub == UPPER)
                    checkHigh(front, newfront );
                else if (sub == LEFT)
                    checkLeft(front, newfront);
                else if (sub == RIGHT)
                    checkRight( front, newfront );
                byte[] tmp;
                tmp = front;
                front = newfront;
                newfront = tmp;

                // exclude anything which is lower than the "minimum maximum"

                exclude( dataout , front, z);

            } // z

        }
        // now do the same again from the reverse!

        Arrays.fill( front, (byte)0 ); // clear the arrays
        Arrays.fill( newfront, (byte)0 );

        if ( main == FRONTMAIN || main == LEFTMAIN || main == TOPMAIN )
        {

            System.out.println("Creating RealTime Dataset... 40%");

            for( int z = depth-1 ; z >= 0 ; --z )
            {

                if (plugin)
                {

                    double pc = (40+((20*(depth-z))/(depth)))/100.0;
                    IJ.showProgress( pc );

                }
                else
                    ; // System.out.print("Creating RealTime Dataset..." + (40+((20*(depth-z))/(depth))) + "%\r");

                if (sub == LOWER)
                    checkHigh( front, newfront );
                else if (sub == UPPER)
                    checkLow(front, newfront );
                else if (sub == LEFT)
                    checkRight(front, newfront);
                else if (sub == RIGHT)
                    checkLeft( front, newfront );

                byte[] tmp;
                tmp = front;
                front = newfront;
                newfront = tmp;

                // exclude anything which is lower than the "minimum maximum"

                exclude( dataout , front, z);


            } // z

        }
        else //if (REARMAIN || RIGHTMAIN || LOWERMAIN )
        {

            System.out.println ("Creating RealTime Dataset... 40%");

            for( int z = 0 ; z < depth ; ++z )
            {

                if (plugin)
                {

                    double pc = ((40+(20*z))/depth)/100.0;
                    IJ.showProgress( pc );

                }
                else
                    ; // System.out.print("Creating RealTime Dataset..." + (40+((20*z)/(depth))) + "%\r");

                if (sub == LOWER)
                    checkHigh( front, newfront );
                else if (sub == UPPER)
                    checkLow(front, newfront );
                else if (sub == LEFT)
                    checkRight(front, newfront);
                else if (sub == RIGHT)
                    checkLeft( front, newfront );

                byte[] tmp;
                tmp = front;
                front = newfront;
                newfront = tmp;

                // exclude anything which is lower than the "minimum maximum"

                exclude( dataout , front, z);


            } // z
        }

        if ( main == FRONTMAIN || main == REARMAIN)
            return createStructure(dataout, XYZ);
        else if ( main == LEFTMAIN || main == RIGHTMAIN )
            return createStructure(dataout, ZYX);
        else //if ( TOPMAIN || LOWERMAIN )
            return createStructure(dataout, XZY);

    }

    /** Creates a real-time data structure from the remaining data. */

    private RealTimeMIP createStructure(byte[][] datain, int encodeType)
    {

        ArrayList[] tmp = new ArrayList[256];

        for(int i = 0 ; i < 256 ; ++i)
            tmp[i] = new ArrayList();

        System.out.println ("Creating RealTime Dataset... 60%");

        for( int z = 0 ; z < depth ; ++z )
        {

            if ( plugin )
            {

                double pc = (60+((20*z)/(depth)))/100.0;
                IJ.showProgress(pc);
            }
            else
                ; // System.out.print("Creating RealTime Dataset..." + (60+((20*z)/(depth))) + "%\r");

            for( int y = 0 ; y < height ; ++y )
            {
                for( int x = 0 ; x < width ; ++x )
                {
                    if ( datain[z][x+(y*width)] != 0 )
                    {
                        switch ( encodeType )
                        {
                            case 0:
                                tmp[ (datain[z][x+(y*width)] & 0xff) ].add( new Integer( RealTimeMIP.encode(x, y, z) ) );
                                break;
                            case 1:
                                tmp[ (datain[z][x+(y*width)] & 0xff) ].add( new Integer( RealTimeMIP.encode(z, y, x) ) );
                                break;
                            case 2:
                                tmp[ (datain[z][x+(y*width)] & 0xff) ].add( new Integer( RealTimeMIP.encode(x, z, y) ) );
                                break;

                        }
                    }
                }
            }
        }

        // count the number of voxels

        int count = 0;

        for( int i = 0 ; i < 256 ; ++i )
        {
            count += tmp[i].size();
        }

        int total = width * height * depth;

        int[] pos = new int[count];
        int[] vals = new int[256];

        count = 0;

        System.out.println("Creating RealTime Dataset... 80%");

        for( int i = 1 ; i < 256 ; ++i )
        {

            if ( plugin )
            {

                double pc = (80+((20*i)/(256)))/100.0;
                IJ.showProgress( pc );
            }
            else
                ; // System.out.print("Creating RealTime Dataset..." + (80+((20*i)/(256))) + "%\r");

            vals[i-1] = count;

            for( int element = 0 ; element < tmp[i].size() ; ++element )
            {
                Integer value = (Integer) tmp[i].get(element);
                pos[count++] = value.intValue();
            }
        }

        if ( plugin )
        {
            IJ.showProgress( 1.0 );
        }
        else
            System.out.println("Creating RealTime Dataset...DONE (" + (100 - ((100*count)/total) ) + "% discarded)" );

        vals[255] = count;

        switch ( encodeType )
        {
            case 0:
                return new RealTimeMIP( vals, pos, width, height, depth );
            case 1:
                return new RealTimeMIP( vals, pos, depth, height, width );
            default:
                return new RealTimeMIP( vals, pos, width, depth, height );
        }

    }


}
