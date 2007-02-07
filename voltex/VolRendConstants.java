package voltex;

import java.awt.*;
import java.awt.image.*;
import java.awt.color.ColorSpace;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.io.*;
import com.sun.j3d.utils.behaviors.mouse.*;

abstract public interface VolRendConstants {

    static final int 	X_AXIS = 0;
    static final int 	Y_AXIS = 1;
    static final int 	Z_AXIS = 2;

    static final int 	FRONT = 0;
    static final int 	BACK = 1;

    static final int 	PLUS_X = 0;
    static final int 	PLUS_Y = 1;
    static final int 	PLUS_Z = 2;
    static final int 	MINUS_X = 3;
    static final int 	MINUS_Y = 4;
    static final int 	MINUS_Z = 5;

}
