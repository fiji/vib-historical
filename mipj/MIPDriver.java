package mipj;

import java.io.*;
import ij.util.StringSorter;
import ij.ImagePlus;
import ij.IJ;
import java.text.*;
import java.util.Locale;


public class MIPDriver extends Thread
{

    private String outputFileName;
    private String currentParse;
    private int mipType;
    private int resx, resy;
    private int interpolationType;
    private float scale, zScale;
    private float rayCastInc;
    private boolean dmip;
    private int threshold;
    private int nImages;
    private String inputDir;
    private Matrix4f rot;
    float animx, animy, animz;
    ImageStack ist = null;
    private boolean plugin = false;
    private boolean forceWrite = false;
    private static boolean outputJpeg = false;

    public final static int SPLATTING = 0;
    public final static int RAYCASTING = 1;
    public final static int REALTIME = 2;


    /** Creates a MIPDriver from a lot of arguments */

    public MIPDriver(
        int type,
        int resx,
        int resy,
        int interpolationType,
        float scale,
        float zscale,
        float rayinc,
        boolean dmip,
        int threshold,
        int n,
        ImageStack ist,
        Matrix4f rotangle,
        float[] rotresult,
        String name
        )
    {

        outputFileName = new String(name);
        mipType = type;
        this.resx = resx;
        this.resy = resy;
        this.interpolationType = interpolationType;
        this.scale = scale;
        this.zScale = zscale;
        rayCastInc = rayinc;
        this.dmip = dmip;
        this.threshold = threshold;
        nImages = n;
        animx = rotresult[0];
        animy = rotresult[1];
        animz = rotresult[2];
        rot = rotangle;
        this.ist = ist;
        plugin = true;

    }


    /** Creates a MIPDriver class from a set of command-line options */

    public MIPDriver( String[] args ) throws Exception
    {

        outputFileName = new String("projection");
        mipType = SPLATTING;
        resx = resy = 0; // (no change)
        interpolationType = MIP.TRILINEAR;
        scale = zScale = 1.0f;
        rayCastInc = 0.5f;
        dmip = false;
        threshold = 255;
        nImages = 1;
        animx = animy = animz = 0.0f;

        plugin = false;

        rot = new Matrix4f();


        if ( args.length < 1 )
        {
            System.out.println("Usage: java MIPDriver inputdir ...");
            throw new Exception();
        }
        else
            inputDir = new String(args[0]);


        try
        {

            if (args.length > 0)
            {

                int i = 1; // missing out inputdir

                if ( args.length > 1 )
                {
                    if ( !args[1].startsWith("-") )
                    {
                        i = 2;
                        outputFileName = new String( args[1] );
                        // spot directories
                        if ( new File(outputFileName).isDirectory() )
                        {
                            outputFileName = new String( outputFileName + File.separatorChar + "projection" );
                        }
                    }
                }

                while (i < args.length)
                {

                    currentParse = new String ( args[i] );

                    args[i] = args[i].toLowerCase();

                    if (args[i].equals("-m") || args[i].equals("--mip") )
                    {
                        ++i;
                        args[i] = args[i].toLowerCase();
                        if (args[i].equals("ray"))
                            mipType = RAYCASTING;
                        else if ( args[i].equals("splat") )
                            mipType = SPLATTING;
                        else if ( args[i].equals("rt") )
                            mipType = REALTIME;
                        else
                        {
                            System.out.println("Unrecognised argument after -m/--mip: " + args[i]);
                            throw new Exception();
                        }
                    }
                    else if (args[i].equals("-r") || args[i].equals("--res") )
                    {
                        ++i;
                        resx = Integer.parseInt( args[i++] );
                        resy = Integer.parseInt( args[i] );
                    }
                    else if (args[i].equals("-t") || args[i].equals("--raytype") )
                    {
                        ++i;
                        args[i] = args[i].toLowerCase();
                        if (args[i].equals("nn"))
                            interpolationType =  MIP.NEARESTNEIGHBOUR;
                        else if ( args[i].equals("tri") )
                            interpolationType = MIP.TRILINEAR;
                        else
                        {
                            System.out.println("Unrecognised argument after -t/--raytype: " + args[i]);
                            throw new Exception();
                        }
                    }
                    else if ( args[i].equals("-s") || args[i].equals("--zscale") )
                    {
                        ++i;
                        zScale = Float.parseFloat( args[i] );
                    }
                    else if ( args[i].equals("-o") || args[i].equals("--scale") )
                    {
                        ++i;
                        scale = Float.parseFloat( args[i] );
                    }
                    else if ( args[i].equals("-i") || args[i].equals("--rayinc") )
                    {
                        ++i;
                        rayCastInc = Float.parseFloat( args[i] );
                    }
                    else if ( args[i].equals("-d") || args[i].equals("--dmip") )
                    {
                        dmip = true;
                    }
                    else if ( args[i].equals("-h") || args[i].equals("--maxt") )
                    {
                        ++i;
                        int t = Integer.parseInt( args[i] );
                        if ( t < 1 || t > 255)
                        {
                            System.out.println("Max threshold out of bounds. Must be between 1 and 255. Ignoring...");
                        }
                        else
                            threshold = t;
                    }
                    else if ( args[i].equals("-n") || args[i].equals("--nimages") )
                    {
                        ++i;
                        int t = Integer.parseInt( args[i] );
                        if ( t < 1 || t > 1000)
                        {
                            System.out.println("No. Images out of bounds. Must be between 1 and 1000. Ignoring...");
                        }
                        else
                            nImages = t;
                    }
                    else if ( args[i].equals("-x") || args[i].equals("--rotx") )
                    {
                        ++i;
                        float f = Float.parseFloat( args[i] );
                        rot.rotByX( f * MIP.ONEDEGREE );
                    }
                    else if ( args[i].equals("-y") || args[i].equals("--roty") )
                    {
                        ++i;
                        float f = Float.parseFloat( args[i] );
                        rot.rotByY( f * MIP.ONEDEGREE );
                    }
                    else if ( args[i].equals("-z") || args[i].equals("--rotz") )
                    {
                        ++i;
                        float f = Float.parseFloat( args[i] );
                        rot.rotByZ( f * MIP.ONEDEGREE );
                    }
                    else if ( args[i].equals("-ax") || args[i].equals("--animx") )
                    {
                        ++i;
                        animx = Float.parseFloat( args[i] );
                    }
                    else if ( args[i].equals("-ay") || args[i].equals("--animy") )
                    {
                        ++i;
                        animy = Float.parseFloat( args[i] );
                    }
                    else if ( args[i].equals("-az") || args[i].equals("--animz") )
                    {
                        ++i;
                        animz = Float.parseFloat( args[i] );
                    }
                    else if ( args[i].equals("-f") || args[i].equals("--forcewrite") )
                    {
                        forceWrite = true;
                    }
                    else if ( args[i].equals("-j") || args[i].equals("--jpeg") )
                    {
                        outputJpeg = true;
                    }
                    else
                    {
                        System.out.println("Unrecognised command line option: " + args[i] );
                        throw new Exception();
                    }

                    ++i;

                } // end while

            }
            else
            {
                System.out.println("Usage: java MIPDriver inputdir [output file] [options]");
            }

        }
        catch(ArrayIndexOutOfBoundsException aob)
        {
            System.out.println("Command line parse error at " + currentParse);
            aob.printStackTrace();
            throw new Exception();
        }
        catch ( NumberFormatException nfe )
        {
            System.out.println("Command line number parse error at " + currentParse);
            throw new Exception();
        }



    }

    public static boolean outputJpeg()
    {
        return outputJpeg;
    }

    /** Creates the images requested from the command-line */

    public void run()
    {

        try
        {

            if ( mipType == REALTIME )
            {

                if ( ist == null )
                    ist = new ImageStack( inputDir, 256 );

                if ( zScale != 1.0f )
                    ist.zScale(zScale);

                Discard d = new Discard( ist );
                d.discardNN();

                RealTimeMIP r = d.calculate(Discard.FRONTMAIN, Discard.UPPER);

                System.out.print("Writing file:\n" + outputFileName + ".rt");
                ObjectOutputStream oos = new ObjectOutputStream( new FileOutputStream( outputFileName + ".rt" ) );
                System.out.println(" : DONE");
                oos.writeObject( r );
                oos.close();
                return;

            }

            if ( ist == null )
            {
                ist = new ImageStack( inputDir, scale );
            }

            if (mipType == SPLATTING && zScale != 1.0f) // need to resample to do zscaling for splatting
            {
                if (plugin)
                    IJ.showStatus("ZScaling");
                ist.zScale( zScale );
            }

            MIP m = new MIP( ist, !plugin );

            m.setPlugin(plugin);
            m.setForceWrite( forceWrite );

            FileNameGenerator fng = new FileNameGenerator( outputFileName, plugin, nImages );

            if ( resx != 0 && resy != 0 )
                m.setResolution( resx, resy );

            m.setZScale( zScale );

            m.setRayCastType( interpolationType );
            m.setThreshold( threshold );
            m.setRayCastIncrement( rayCastInc );
            m.setDMIP( dmip );

            float incX, incY, incZ;

            incX = incY = incZ = 0.0f;

            if (nImages > 1 )
            {
                float f = 1.0f / (nImages-1);

                incX = animx * f * MIP.ONEDEGREE;
                incY = animy * f * MIP.ONEDEGREE;
                incZ = animz * f * MIP.ONEDEGREE;

            }


            ij.ImageStack buildStack = null;
            ImagePlus buildImg = null;

            System.out.print( "Processing Projection: " );

            for( int i = 0 ; i < nImages ; ++i )
            {

                m.setOutputFile( new File( fng.next() ) );

                if (!plugin)
                    System.out.print( "." );

                if ( mipType == SPLATTING )
                {
                    if ( !plugin )
                        m.projectByMatrix( rot );
                    else
                    {
                        ImagePlus img =  m.projectByMatrix( rot );

                        if ( i == 0 && nImages > 1 ) // set up the stack for adding to
                        {
                             buildStack = new ij.ImageStack( img.getWidth(), img.getHeight(), img.getProcessor().getColorModel() );
                             byte[] ba = (byte[]) img.getProcessor().getPixels();

                             buildStack.addSlice( null, ba );

                             for( int n = 1 ; n < nImages ; ++n )
                             {
                                 buildStack.addSlice( null , new byte[ba.length] );
                             }

                             buildImg = new ImagePlus( m.getOutputFile().getName(), buildStack );
                             buildImg.show();
                             buildImg.setSlice(1);
                        }
                        else if ( nImages > 1 )
                        {
                            buildStack.setPixels( img.getProcessor().getPixels() , buildImg.getCurrentSlice()+1 );
                            if (buildImg == null) return;
                            buildImg.setSlice( buildImg.getCurrentSlice()+1 );
                        }
                        else
                        {
                            img.setTitle( m.getOutputFile().getName() );
                            img.show();
                        }
                    }
                }
                else if ( mipType == RAYCASTING )
                {
                    if ( !plugin )
                        m.rayCastByMatrix( rot );
                    else
                    {
                        ImagePlus img =  m.rayCastByMatrix( rot );

                        if ( i == 0 && nImages > 1 ) // set up the stack for adding to
                        {
                             buildStack = new ij.ImageStack( img.getWidth(), img.getHeight(), img.getProcessor().getColorModel() );
                             byte[] ba = (byte[]) img.getProcessor().getPixels();

                             buildStack.addSlice( null, ba );

                             for( int n = 1 ; n < nImages ; ++n )
                             {
                                 buildStack.addSlice( null , new byte[ba.length] );
                             }

                             buildImg = new ImagePlus( m.getOutputFile().getName(), buildStack );
                             buildImg.show();
                             buildImg.setSlice(1);
                        }
                        else if ( nImages > 1 )
                        {
                            buildStack.setPixels( img.getProcessor().getPixels() , buildImg.getCurrentSlice()+1 );
                            if (buildImg == null) return;
                            buildImg.setSlice( buildImg.getCurrentSlice()+1 );
                        }
                        else
                        {
                            img.setTitle( m.getOutputFile().getName() );
                            img.show();
                        }
                    }
                }

                rot.rotByX( incX );
                rot.rotByY( incY );
                rot.rotByZ( incZ );

            }
            System.out.println( "" );

        }
        catch(OutOfMemoryError oom)
        {
            if ( plugin ) IJ.error("Insufficient memory, try java -mxNm where N is MB of memory");
            System.out.println("Insufficient memory: try java -mxNm where N is MB of memory");
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }


    }


    public static void main(String args[])
    {

        try
        {

            MIPDriver mipd = new MIPDriver( args );
            mipd.start();


        }
        catch(Exception io)
        {
            io.printStackTrace();
        }

    }


}

class FileNameGenerator
{

    private String stub;
    private String append;
    private NumberFormat nf;
    private int pos;
    boolean onlyStub = true;

    public FileNameGenerator( String filename, boolean notif, int n)
    {
        stub = filename;
        if ( !notif )
            append = ".tif";
        else
            append = "";
        pos = 0;
        if ( n > 1 )
            onlyStub = false;
        nf = NumberFormat.getIntegerInstance(Locale.UK);
        nf.setGroupingUsed(false);
        nf.setMinimumIntegerDigits( 3 );
        nf.setMaximumIntegerDigits( 3 );
    }

    /** Obtains the next filename */

    public String next()
    {
        if (onlyStub) return new String( stub + append );
        else return new String( stub + nf.format( pos++ ) + append );
    }


}
