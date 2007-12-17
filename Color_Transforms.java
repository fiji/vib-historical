import ij.*;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.gui.*;
import java.awt.*;

/** Color_Transforms.java
  * Converts an RGB colour image into a colour space represented by a stack
  *
  * @author Maria E. Barilla-Perez
  * @author Birmingham University, United Kingdom
  * @author barillame@yahoo.co.uk and perezm@eee.bham.ac.uk
  * @version 1.0
  *
  */

public class Color_Transforms implements PlugInFilter{
    private ImagePlus  imp;             // Original image
    private ImageStack sstack;          // Stack result
    private int        width;           // Width of the original image
    private int        height;          // Height of the original image
    private int        size;            // Total number of pixels
    private float[]    c1, c2, c3;      // Colour space values
    private float[]    rf, gf, bf;      // r, g, b values
    private String     colourspace;     // Colour space chosen
    private String     title;           // Name of the original image
    private String     n1, n2, n3;      // Names for every layer on the stack
    private boolean    inverse;

    final public int XYZ = 0;
    final public int Yxy = 1;
    final public int YIQ = 2;
    final public int Luv = 3;
    final public int Lab = 4;
    final public int AC1C2 = 5;
    final public int I1I2I3 = 6;
    final public int Yuv = 7;
    final public int YUV = 8;
    final public int YQ1Q2 = 9;
    final public int HSI = 10;
    final public int HSV = 11;
    final public int HSL = 12;
    final public int LCHLuv = 13;
    final public int LSHLuv = 14;
    final public int LSHLab = 15;
    final public String[] colourspaces = {"XYZ",
                                 "Yxy",
                                 "YIQ",
                                 "Luv",
                                 "Lab",
                                 "AC1C2",
                                 "I1I2I3",
                                 "Yuv",
                                 "YUV",
                                 "YQ1Q2",
                                 "HSI",
                                 "HSV",
                                 "HSL",
                                 "LCHLuv",
                                 "LSHLuv",
                                 "LSHLab"};

    public int setup(String arg, ImagePlus imp){
        this.imp = imp;
        if (arg.equals("about")) {
		showAbout();
		return DONE;
	}
        return DOES_RGB | DOES_32;
    }

    public void run(ImageProcessor ip) {
        GenericDialog gd = new GenericDialog("Colour Transform");
        gd.addChoice("Colour space:", colourspaces, colourspaces[0]);
        gd.showDialog();
        if (gd.wasCanceled())
            return;
        int colourspace = gd.getNextChoiceIndex();
	run(imp, colourspace);
    }

    public void run(ImagePlus imp, int colourspace) {
        int offset, i;
	ImageProcessor ip = imp.getProcessor();
        width = ip.getWidth();
        height = ip.getHeight();
        size = width * height;
        c1 = new float[size];
        c2 = new float[size];
        c3 = new float[size];
	if (imp.getStack().getSize() == 1) {
		inverse = false;
		if (!(ip instanceof ColorProcessor)) {
			IJ.error("Need a color image or a stack of 3 gray images!");
			return;
		}
		rf = new float[size];
		gf = new float[size];
		bf = new float[size];
		for (int row = 0; row < height; row++){
			offset = row*width;
			for (int col = 0; col < width; col++) {
				i = offset + col;
				int c = ip.getPixel(col, row);
				rf[i] = ((c&0xff0000)>>16)/255f;    //R 0..1
				gf[i] = ((c&0x00ff00)>>8)/255f;     //G 0..1
				bf[i] =  (c&0x0000ff)/255f;         //B 0..1
			}
		}
	} else {
		inverse = true;
		if (imp.getStack().getSize() != 3 ||
				!(ip instanceof FloatProcessor)) {
			IJ.error("Need a color image or a stack of 3 gray images!");
			return;
		}
		rf = (float[])imp.getStack().getProcessor(1).getPixelsCopy();
		gf = (float[])imp.getStack().getProcessor(2).getPixelsCopy();
		bf = (float[])imp.getStack().getProcessor(3).getPixelsCopy();
	}

	Transform transform = null;
        switch (colourspace) {
	case XYZ:
            n1 = "X";
            n2 = "Y";
            n3 = "Z";
            transform = new XYZ();
	    break;
        case Yxy:
            n1 = "Y";
            n2 = "x";
            n3 = "y";
            transform = new Yxy();
	    break;
        case YUV:
            n1 = "Y";
            n2 = "U";
            n3 = "V";
            transform = new YUV();
	    break;
        case YIQ:
            n1 = "Y";
            n2 = "I";
            n3 = "Q";
            transform = new YIQ();
	    break;
        case AC1C2:
            n1 = "A";
            n2 = "C1";
            n3 = "C2";
            transform = new AC1C2();
	    break;
        case Luv:
            n1 = "L";
            n2 = "u";
            n3 = "v";
            transform = new Luv();
	    break;
        case Lab:
            n1 = "L";
            n2 = "a";
            n3 = "b";
            transform = new Lab();
	    break;
        case I1I2I3:
            n1 = "I1";
            n2 = "I2";
            n3 = "I3";
            transform = new I1I2I3();
	    break;
        case Yuv:
            n1 = "Y";
            n2 = "u";
            n3 = "v";
            transform = new Yuv();
	    break;
        case YQ1Q2:
            n1 = "Y";
            n2 = "Q1";
            n3 = "Q2";
            transform = new YQ1Q2();
	    break;
        case HSI:
            n1 = "I";
            n2 = "H";
            n3 = "S";
            transform = new HSI();
	    break;
        case HSV:
            n1 = "V";
            n2 = "H";
            n3 = "S";
            transform = new HSV();
	    break;
        case HSL:
            n1 = "L";
            n2 = "H";
            n3 = "S";
            transform = new HSL();
	    break;
        case LCHLuv:
            n1 = "L";
            n2 = "H";
            n3 = "C";
            transform = new LCHLuv();
	    break;
        case LSHLuv:
            n1 = "L";
            n2 = "H";
            n3 = "S";
            transform = new LSHLuv();
	    break;
        case LSHLab:
            n1 = "L";
            n2 = "H";
            n3 = "S";
            transform = new LCHLab();
	    break;
        }

	if (transform == null) {
		IJ.error("Cannot transform " + (inverse ? "inverse " : "")
			+ colourspaces[colourspace]);
		return;
	}
	transform.transform();

        title = imp.getTitle();
        sstack=new ImageStack(width,height); // Create new float stack for output

        ip = new FloatProcessor(width, height, c1, null);
        sstack.addSlice(n1, ip);
        ip = new FloatProcessor(width, height, c2, null);
        sstack.addSlice(n2, ip);
        ip = new FloatProcessor(width, height, c3, null);
        sstack.addSlice(n3, ip);

        ImagePlus imluv=new ImagePlus(colourspaces[colourspace],sstack);
        imluv.show();
        IJ.resetMinAndMax();

    }


    abstract class Transform {
	abstract void transform(int q);

	void transform() {
		for (int q = 0; q < size; q++)
			transform(q);
	}
    }

    class XYZ extends Transform {
	void transform(int q) {
            rf[q] = (rf[q] > 0.04045f)?(new Double(Math.exp(Math.log((rf[q]+0.055)/1.055)*2.4))).floatValue():rf[q]/12.92f;
            gf[q] = (gf[q] > 0.04045f)?(new Double(Math.exp(Math.log((gf[q]+0.055)/1.055)*2.4))).floatValue():gf[q]/12.92f;
            bf[q] = (bf[q] > 0.04045f)?(new Double(Math.exp(Math.log((bf[q]+0.055)/1.055)*2.4))).floatValue():bf[q]/12.92f;

            rf[q] = rf[q] * 100f;
            gf[q] = gf[q] * 100f;
            bf[q] = bf[q] * 100f;

            float X = 0.4124f * rf[q] + 0.3576f * gf[q] + 0.1805f * bf[q];
            float Y = 0.2126f * rf[q] + 0.7152f * gf[q] + 0.0722f * bf[q];
            float Z = 0.0193f * rf[q] + 0.1192f * gf[q] + 0.9505f * bf[q];

            c1[q] = X;
            c2[q] = Y;
            c3[q] = Z;
        }
    }

    class Yxy extends Transform {
	void transform(int q) {
            rf[q] = (rf[q] > 0.04045f)?(new Double(Math.exp(Math.log((rf[q]+0.055)/1.055)*2.4))).floatValue():rf[q]/12.92f;
            gf[q] = (gf[q] > 0.04045f)?(new Double(Math.exp(Math.log((gf[q]+0.055)/1.055)*2.4))).floatValue():gf[q]/12.92f;
            bf[q] = (bf[q] > 0.04045f)?(new Double(Math.exp(Math.log((bf[q]+0.055)/1.055)*2.4))).floatValue():bf[q]/12.92f;

            rf[q] = rf[q] * 100f;
            gf[q] = gf[q] * 100f;
            bf[q] = bf[q] * 100f;

            float X = 0.4124f * rf[q] + 0.3576f * gf[q] + 0.1805f * bf[q];
            float Y = 0.2126f * rf[q] + 0.7152f * gf[q] + 0.0722f * bf[q];
            float Z = 0.0193f * rf[q] + 0.1192f * gf[q] + 0.9505f * bf[q];

            float xx = (X == 0f && Y == 0f && Z == 0f)? 0f : (X / ((X + Y + Z)));
            float yy = (X == 0f && Y == 0f && Z == 0f)? 0f : (Y / ((X + Y + Z)));

            c1[q] = Y;
            c2[q] = xx;
            c3[q] = yy;
        }
    }

    class YUV extends Transform {
        void transform(int q) {
            float Y =  0.299f * rf[q] + 0.587f * gf[q] + 0.114f * bf[q];
            float U = -0.147f * rf[q] - 0.289f * gf[q] + 0.437f * bf[q];
            float V =  0.615f * rf[q] - 0.515f * gf[q] - 0.100f * bf[q];

            c1[q] = Y;
            c2[q] = U;
            c3[q] = V;
        }
    }

    class YIQ extends Transform {
	void transform(int q) {
            float Y = 0.299f * rf[q] + 0.587f * gf[q] + 0.114f * bf[q];
            float I = 0.596f * rf[q] - 0.274f * gf[q] - 0.322f * bf[q];
            float Q = 0.211f * rf[q] - 0.253f * gf[q] - 0.312f * bf[q];

            c1[q] = Y;
            c2[q] = I;
            c3[q] = Q;
        }
    }

    class AC1C2 extends Transform {
        void transform(int q) {
            float L, M, S;
            float A, C1, C2;
            L = 0.3634f * rf[q] + 0.6102f * gf[q] + 0.0264f * bf[q];
            M = 0.1246f * rf[q] + 0.8138f * gf[q] + 0.0616f * bf[q];
            S = 0.0009f * rf[q] + 0.0602f * gf[q] + 0.9389f * bf[q];

            L = new Double(Math.log(L)).floatValue();
            M = new Double(Math.log(M)).floatValue();
            S = new Double(Math.log(S)).floatValue();

            A  = 13.8312f * L + 8.3394f * M + 0.4294f * S;
            C1 = 64.0000f * L - 64.000f * M + 0.0000f * S;
            C2 = 10.0000f * L + 0.0000f * M - 10.000f * S;

            c1[q] = A;
            c2[q] = C1;
            c3[q] = C2;
        }
    }

    class Luv extends Transform {
        /** Illuminant = D65 */
        //X = 95.047, Y = 100.0, Z = 108.883;
        float yn = 1f;

        /** un' corresponding to Yn */
        //  un' = 4X/(X + 15Y + 3Z)
        float unp = 0.19784f;

        /** vn' corresponding to Yn */
        //  vn = 9Y/(X + 15Y + 3Z)
        float vnp = 0.4683f;

        void transform(int q) {
            rf[q] = (rf[q] > 0.04045f)?(new Double(Math.exp(Math.log((rf[q]+0.055)/1.055)*2.4))).floatValue():rf[q]/12.92f;
            gf[q] = (gf[q] > 0.04045f)?(new Double(Math.exp(Math.log((gf[q]+0.055)/1.055)*2.4))).floatValue():gf[q]/12.92f;
            bf[q] = (bf[q] > 0.04045f)?(new Double(Math.exp(Math.log((bf[q]+0.055)/1.055)*2.4))).floatValue():bf[q]/12.92f;

            rf[q] = rf[q] * 100f;
            gf[q] = gf[q] * 100f;
            bf[q] = bf[q] * 100f;

            float X = 0.4124f * rf[q] + 0.3576f * gf[q] + 0.1805f * bf[q];
            float Y = 0.2126f * rf[q] + 0.7152f * gf[q] + 0.0722f * bf[q];
            float Z = 0.0193f * rf[q] + 0.1192f * gf[q] + 0.9505f * bf[q];

            // As yn = 1.0, we will just consider Y value as yyn
            //yyn = (Y/yn);
            float f_yyn = Y / 100f;

            if (f_yyn > 0.008856f) {
            f_yyn=new Double(Math.exp(Math.log(f_yyn)/3f)).floatValue();
            } else {
            f_yyn = ((7.787f * f_yyn) + (16f/116f));
            }

            float up = (X == 0f && Y == 0f && Z == 0f)? 0f : (4f*X / ((X + 15f*Y + 3f*Z)));
            float vp = (X == 0f && Y == 0f && Z == 0f)? 0f : (9f*Y / ((X + 15f*Y + 3f*Z)));

            float l = (116f *f_yyn)-16f;
            float u = 13f  * l * (up - unp);
            float v = 13f  * l * (vp - vnp);

            c1[q] = l;
            c2[q] = u;
            c3[q] = v;
        }
    }

    class Lab extends Transform {
        void transform(int q) {
            float l, a, b;
            rf[q] = (rf[q] > 0.04045f)?(new Double(Math.exp(Math.log((rf[q]+0.055f)/1.055f)*2.4f))).floatValue():rf[q]/12.92f;
            gf[q] = (gf[q] > 0.04045f)?(new Double(Math.exp(Math.log((gf[q]+0.055f)/1.055f)*2.4f))).floatValue():gf[q]/12.92f;
            bf[q] = (bf[q] > 0.04045f)?(new Double(Math.exp(Math.log((bf[q]+0.055f)/1.055f)*2.4f))).floatValue():bf[q]/12.92f;

            rf[q] = rf[q] * 100f;
            gf[q] = gf[q] * 100f;
            bf[q] = bf[q] * 100f;

            float X = 0.4124f * rf[q] + 0.3576f * gf[q] + 0.1805f * bf[q];
            float Y = 0.2126f * rf[q] + 0.7152f * gf[q] + 0.0722f * bf[q];
            float Z = 0.0193f * rf[q] + 0.1192f * gf[q] + 0.9505f * bf[q];

            float fX, fY, fZ;
            float La, aa, bb;

            // XYZ to Lab
            if ( X > 0.008856f )
                fX = (new Double(Math.exp(Math.log(X)/3f))).floatValue();
            else
                fX = ((7.787f * X) + (16f/116f));

            if ( Y > 0.008856f )
            fY = (new Double(Math.exp(Math.log(Y)/3f))).floatValue();
            else
            fY = ((7.787f * Y) + (16f/116f));

            if ( Z > 0.008856f )
            fZ =  (new Double(Math.exp(Math.log(Z)/3f))).floatValue();
            else
            fZ = ((7.787f * Z) + (16f/116f));

            l = ( 116f * fY ) - 16f;
            a = 500f * ( fX - fY );
            b = 200f * ( fY - fZ );

            c1[q] = l;
            c2[q] = a;
            c3[q] = b;
        }
    }

    class I1I2I3 extends Transform {
        void transform(int q) {
            float I1 =  (rf[q] + gf[q] + bf[q])/3f;
            float I2 =  (rf[q] - bf[q])/2f;
            float I3 =  (2f * gf[q] - rf[q] - bf[q])/4f;
            c1[q] = I1;
            c2[q] = I2;
            c3[q] = I3;
        }
    }

    class Yuv extends Transform {
        void transform(int q) {
            float u, v;
            float Y =  (rf[q] + gf[q] + bf[q])/3f;
            if(Y != 0f){
              u =  3f*(rf[q] - bf[q])/2f*Y;
              v =  (new Double(Math.sqrt(3.0)).floatValue())*(2f * gf[q] - rf[q] - bf[q])/2f*Y;
            }
            else{
              u =  0f;
              v =  0f;
            }
            c1[q] = Y;
            c2[q] = u;
            c3[q] = v;
        }
    }

    class YQ1Q2 extends Transform {
        void transform(int q) {
            float Y =  (rf[q] + gf[q] + bf[q])/3f;
            float Q1 = ((rf[q] + gf[q]) != 0f)?rf[q]/(rf[q] + gf[q]):0f;
            float Q2 = ((rf[q] + bf[q]) != 0f)?rf[q]/(rf[q] + bf[q]):0f;
            c1[q] = Y;
            c2[q] = Q1;
            c3[q] = Q2;
        }
    }

    class HSI extends Transform {
        void transform(int q) {
            float var_Min = Math.min(rf[q], gf[q]); //Min. value of RGB
            var_Min = Math.min(var_Min, bf[q]);
            float var_Max = Math.max(rf[q], gf[q]); //Max. value of RGB
            var_Max = Math.max(var_Max, bf[q]);
            float del_Max = var_Max - var_Min; //Delta RGB value

            c3[q] =  (rf[q] + gf[q] + bf[q])/3f;

            if ( del_Max == 0f ){                //This is a gray, no chroma...
                c1[q] =  0f;                   //HSL results = 0 ÷ 1
                c2[q] =  0f;
            }
            else{                               //Chromatic data...
               c2[q] = 1 - (var_Min / c3[q]);

               float del_R = ( ( ( var_Max - rf[q] ) / 6f ) + ( del_Max / 2f ) ) / del_Max;
               float del_G = ( ( ( var_Max - gf[q] ) / 6f ) + ( del_Max / 2f ) ) / del_Max;
               float del_B = ( ( ( var_Max - bf[q] ) / 6f ) + ( del_Max / 2f ) ) / del_Max;

               if      ( rf[q] == var_Max ) c1[q] = del_B - del_G;
               else if ( gf[q] == var_Max ) c1[q] = ( 1 / 3 ) + del_R - del_B;
               else if ( bf[q] == var_Max ) c1[q] = ( 2 / 3 ) + del_G - del_R;

               if ( c1[q] < 0 )  c1[q] += 1;
               if ( c1[q] > 1 )  c1[q] -= 1;
            }
        }
    }

    class HSL extends Transform {
        void transform(int q) {
            float H=0, S=0, L=0;
            float var_Min = Math.min(rf[q], gf[q]); //Min. value of RGB
            var_Min = Math.min(var_Min, bf[q]);
            float var_Max = Math.max(rf[q], gf[q]); //Max. value of RGB
            var_Max = Math.max(var_Max, bf[q]);
            float del_Max = var_Max - var_Min; //Delta RGB value

            L =  (var_Max + var_Min)/2;

            if ( del_Max == 0f){                //This is a gray, no chroma...
                H =  0f;                   //HSL results = 0 ÷ 1
                S =  0f;
            }
            else{                               //Chromatic data...
               if ( L < 0.5f )  S = del_Max / ( var_Max + var_Min );
               else             S = del_Max / ( 2f - var_Max - var_Min );

               float del_R = ( ( ( var_Max - rf[q] ) / 6f ) + ( del_Max / 2f ) ) / del_Max;
               float del_G = ( ( ( var_Max - gf[q] ) / 6f ) + ( del_Max / 2f ) ) / del_Max;
               float del_B = ( ( ( var_Max - bf[q] ) / 6f ) + ( del_Max / 2f ) ) / del_Max;

               if      ( rf[q] == var_Max ) H = del_B - del_G;
               else if ( gf[q] == var_Max ) H = ( 1f / 3f ) + del_R - del_B;
               else if ( bf[q] == var_Max ) H = ( 2f / 3f ) + del_G - del_R;

               if ( H < 0f )  H += 1f;
               if ( H > 1f )  H -= 1f;
            }
            c1[q] = H;
            c2[q] = S;
            c3[q] = L;
        }
    }

    class HSV extends Transform {                   // HSV_Stack Plugin (HSV colour space is also known as HSB where B means brightness)
        void transform(int q) {          // http://www.easyrgb.com/
            float H=0, S=0, V=0;
            float var_Min = Math.min(rf[q], gf[q]); //Min. value of RGB
            var_Min = Math.min(var_Min, bf[q]);
            float var_Max = Math.max(rf[q], gf[q]); //Max. value of RGB
            var_Max = Math.max(var_Max, bf[q]);
            float del_Max = var_Max - var_Min;      //Delta RGB value

            V =  var_Max*1f;
            if ( del_Max == 0 ){                    //This is a gray, no chroma...
                H =  0f;                        //HSV results = 0 ÷ 1
                S =  0f;
            }
            else{                                   //Chromatic data...
               S = del_Max / var_Max;
               float del_R = ( ( ( var_Max - rf[q] ) / 6f ) + ( del_Max / 2f ) ) / del_Max;
               float del_G = ( ( ( var_Max - gf[q] ) / 6f ) + ( del_Max / 2f ) ) / del_Max;
               float del_B = ( ( ( var_Max - bf[q] ) / 6f ) + ( del_Max / 2f ) ) / del_Max;

               if      ( rf[q] == var_Max ) H = del_B - del_G;
               else if ( gf[q] == var_Max ) H = ( 1f / 3f ) + del_R - del_B;
               else if ( bf[q] == var_Max ) H = ( 2f / 3f ) + del_G - del_R;

               if ( H < 0 )  H += 1;
               if ( H > 1 )  H -= 1;
            }
            c1[q] = H;
            c2[q] = S;
            c3[q] = V;
        }

  }

    class LCHLuv extends Transform {
        /** Illuminant = D65 */
        //X = 95.047, Y = 100.0, Z = 108.883;
        float yn = 1f;

        /** un' corresponding to Yn */
        //  un' = 4X/(X + 15Y + 3Z)
        float unp = 0.19784f;

        /** vn' corresponding to Yn */
        //  vn = 9Y/(X + 15Y + 3Z)
        float vnp = 0.4683f;

        void transform(int q) {
            float L=0f, C=0f, H=0f;
            rf[q] = (rf[q] > 0.04045f)?(new Double(Math.exp(Math.log((rf[q]+0.055)/1.055)*2.4))).floatValue():rf[q]/12.92f;
            gf[q] = (gf[q] > 0.04045f)?(new Double(Math.exp(Math.log((gf[q]+0.055)/1.055)*2.4))).floatValue():gf[q]/12.92f;
            bf[q] = (bf[q] > 0.04045f)?(new Double(Math.exp(Math.log((bf[q]+0.055)/1.055)*2.4))).floatValue():bf[q]/12.92f;

            rf[q] = rf[q] * 100f;
            gf[q] = gf[q] * 100f;
            bf[q] = bf[q] * 100f;

            float X = 0.4124f * rf[q] + 0.3576f * gf[q] + 0.1805f * bf[q];
            float Y = 0.2126f * rf[q] + 0.7152f * gf[q] + 0.0722f * bf[q];
            float Z = 0.0193f * rf[q] + 0.1192f * gf[q] + 0.9505f * bf[q];

            // As yn = 1.0, we will just consider Y value as yyn
            //yyn = (Y/yn);
            float f_yyn = Y / 100f;

            if (f_yyn > 0.008856f) {
            f_yyn=new Double(Math.exp(Math.log(f_yyn)/3f)).floatValue();
            } else {
            f_yyn = ((7.787f * f_yyn) + (16f/116f));
            }

            float up = (X == 0f && Y == 0f && Z == 0f)? 0f : (4f*X / ((X + 15f*Y + 3f*Z)));
            float vp = (X == 0f && Y == 0f && Z == 0f)? 0f : (9f*Y / ((X + 15f*Y + 3f*Z)));

            float l = (116f *f_yyn)-16f;
            float u = 13f  * l * (up - unp);
            float v = 13f  * l * (vp - vnp);

            L = l;
            C = new Double(Math.sqrt((u*u)+(v*v))).floatValue();

            if ( u == 0 ){                //This is a gray, no chroma...
                H =  0f;                   //LCH results = 0 ÷ 1
            }
            else{
              if (u>=0f && v>=0f) H = new Double(Math.atan(v/u)).floatValue();
              else if (u>=0f && v<0f) H = new Double(Math.atan(v/u) + Math.PI).floatValue();
              else if (u<0f && v<0f) H = new Double(Math.atan(v/u) + Math.PI).floatValue();
              else if (u<0f && v>=0f) H = new Double(Math.atan(v/u) + (2*Math.PI)).floatValue();
            }
            //IJ.write("u " + u + " v " + v + " atan " + H);
            c1[q] = L;
            c2[q] = C;
            c3[q] = H;
        }
    }

    class LSHLuv extends Transform {
        /** Illuminant = D65 */
        //X = 95.047, Y = 100.0, Z = 108.883;
        float yn = 1f;

        /** un' corresponding to Yn */
        //  un' = 4X/(X + 15Y + 3Z)
        float unp = 0.19784f;

        /** vn' corresponding to Yn */
        //  vn = 9Y/(X + 15Y + 3Z)
        float vnp = 0.4683f;

        void transform(int q) {
            float L=0f, S=0f, H=0f;
            rf[q] = (rf[q] > 0.04045f)?(new Double(Math.exp(Math.log((rf[q]+0.055)/1.055)*2.4))).floatValue():rf[q]/12.92f;
            gf[q] = (gf[q] > 0.04045f)?(new Double(Math.exp(Math.log((gf[q]+0.055)/1.055)*2.4))).floatValue():gf[q]/12.92f;
            bf[q] = (bf[q] > 0.04045f)?(new Double(Math.exp(Math.log((bf[q]+0.055)/1.055)*2.4))).floatValue():bf[q]/12.92f;

            rf[q] = rf[q] * 100f;
            gf[q] = gf[q] * 100f;
            bf[q] = bf[q] * 100f;

            float X = 0.4124f * rf[q] + 0.3576f * gf[q] + 0.1805f * bf[q];
            float Y = 0.2126f * rf[q] + 0.7152f * gf[q] + 0.0722f * bf[q];
            float Z = 0.0193f * rf[q] + 0.1192f * gf[q] + 0.9505f * bf[q];

            // As yn = 1.0, we will just consider Y value as yyn
            //yyn = (Y/yn);
            float f_yyn = Y / 100f;

            if (f_yyn > 0.008856f) {
            f_yyn=new Double(Math.exp(Math.log(f_yyn)/3f)).floatValue();
            } else {
            f_yyn = ((7.787f * f_yyn) + (16f/116f));
            }

            float up = (X == 0f && Y == 0f && Z == 0f)? 0f : (4f*X / ((X + 15f*Y + 3f*Z)));
            float vp = (X == 0f && Y == 0f && Z == 0f)? 0f : (9f*Y / ((X + 15f*Y + 3f*Z)));

            float l = (116f *f_yyn)-16f;
            float u = 13f  * l * (up - unp);
            float v = 13f  * l * (vp - vnp);

            L = l;
            S = (new Double(13*Math.sqrt(((u-up)*(u-up))+((v-vp)*(v-vp))))).floatValue();

            if ( u == 0 ){                //This is a gray, no chroma...
                H =  0f;                   //LCH results = 0 ÷ 1
            }
            else{
              if (u>=0f && v>=0f) H = new Double(Math.atan(v/u)).floatValue();
              else if (u>=0f && v<0f) H = new Double(Math.atan(v/u) + Math.PI).floatValue();
              else if (u<0f && v<0f) H = new Double(Math.atan(v/u) + Math.PI).floatValue();
              else if (u<0f && v>=0f) H = new Double(Math.atan(v/u) + (2*Math.PI)).floatValue();
            }
            c1[q] = L;
            c2[q] = S;
            c3[q] = H;
        }
    }

    class LCHLab extends Transform {
        void transform(int q) {
            float L=0f, C=0f, H=0f;
            rf[q] = (rf[q] > 0.04045f)?(new Double(Math.exp(Math.log((rf[q]+0.055f)/1.055f)*2.4f))).floatValue():rf[q]/12.92f;
            gf[q] = (gf[q] > 0.04045f)?(new Double(Math.exp(Math.log((gf[q]+0.055f)/1.055f)*2.4f))).floatValue():gf[q]/12.92f;
            bf[q] = (bf[q] > 0.04045f)?(new Double(Math.exp(Math.log((bf[q]+0.055f)/1.055f)*2.4f))).floatValue():bf[q]/12.92f;

            rf[q] = rf[q] * 100f;
            gf[q] = gf[q] * 100f;
            bf[q] = bf[q] * 100f;

            float X = 0.4124f * rf[q] + 0.3576f * gf[q] + 0.1805f * bf[q];
            float Y = 0.2126f * rf[q] + 0.7152f * gf[q] + 0.0722f * bf[q];
            float Z = 0.0193f * rf[q] + 0.1192f * gf[q] + 0.9505f * bf[q];

            float fX, fY, fZ;

            // XYZ to Lab
            if ( X > 0.008856f )
                fX = (new Double(Math.exp(Math.log(X)/3f))).floatValue();
            else
                fX = ((7.787f * X) + (16f/116f));

            if ( Y > 0.008856f )
            fY = (new Double(Math.exp(Math.log(Y)/3f))).floatValue();
            else
            fY = ((7.787f * Y) + (16f/116f));

            if ( Z > 0.008856f )
            fZ =  (new Double(Math.exp(Math.log(Z)/3f))).floatValue();
            else
            fZ = ((7.787f * Z) + (16f/116f));

            float l = ( 116f * fY ) - 16f;
            float a = 500f * ( fX - fY );
            float b = 200f * ( fY - fZ );

            L = l;
            C = new Double(Math.sqrt((a*a)+(b*b))).floatValue();

            if ( a == 0f ){                //This is a gray, no chroma...
                H =  0f;                   //LCH results = 0 ÷ 1
            }
            else{
              if (a>=0f && b>=0f) H = new Double(Math.atan(b/a)).floatValue();
              else if (a>=0f && b<0f) H = new Double(Math.atan(b/a) + Math.PI).floatValue();
              else if (a<0f && b<0f) H = new Double(Math.atan(b/a) + Math.PI).floatValue();
              else if (a<0f && b>=0f) H = new Double(Math.atan(b/a) + (2*Math.PI)).floatValue();
            }

            c1[q] = L;
            c2[q] = C;
            c3[q] = H;
        }
    }

  void showAbout() {
    IJ.showMessage("About Colour_transform...",
                   "It converts an RGB image into a colour space stack.\n"
                   );
  }

  void error() {
		IJ.showMessage("Colour_transform", "This plugin converts an RGB image into \n"
                   +"a colour space stack.");
  }

}

