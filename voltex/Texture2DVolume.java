package voltex;

import ij.IJ;

import java.awt.*;
import java.awt.image.*;
import java.awt.color.ColorSpace;
import javax.media.j3d.*;
import javax.vecmath.*;
import java.io.*;
import java.text.NumberFormat;

public class Texture2DVolume implements VolRendConstants {

	protected static final int RELOAD_NONE = 0;
	protected static final int RELOAD_VOLUME = 1;

    protected Texture2D[] xTextures;	
    protected Texture2D[] yTextures;	
    protected Texture2D[] zTextures;	

    protected TexCoordGeneration xTg = new TexCoordGeneration();
    protected TexCoordGeneration yTg = new TexCoordGeneration();
    protected TexCoordGeneration zTg = new TexCoordGeneration();

    private WritableRaster raster;
    private int	volEditId = -1;
    private boolean	volumeReloadNeeded = true;

	protected Volume volume;

	int[][] texColorMap;

    public Texture2DVolume(Volume volume) {
		this.volume = volume;
    }

    int update() {
		int newVolEditId = -1;
		if ((newVolEditId = volume.update()) != volEditId) {
			volEditId = newVolEditId;
			volumeReloadNeeded = true;
		}
		if (volumeReloadNeeded) {
			volumeReload();
			return RELOAD_VOLUME;
		} else {
			return RELOAD_NONE;
		}
    }

    void volumeReload() {
		if (volume.hasData()) {
			loadTexture();
		}
		volumeReloadNeeded = false;
    }

    void loadTexture() {

		IJ.showStatus("Loading Z axis texture maps");
		loadAxis(Z_AXIS);
		IJ.showStatus("Loading Y axis texture maps");
		loadAxis(Y_AXIS);
		IJ.showStatus("Loading X axis texture maps");
		loadAxis(X_AXIS);
    }

    private void loadAxis(int axis) {
		int	rSize = 0;    // number of tex maps to create
		int	sSize = 0; 	  // s,t = size of texture map to create
		int tSize = 0; 
		Texture2D[] textures = null;

		switch (axis) {
		  case Z_AXIS:
			rSize = volume.zDim;
			sSize = volume.xTexSize;
			tSize = volume.yTexSize;
			textures = zTextures = new Texture2D[rSize];
			zTg = new TexCoordGeneration();
			zTg.setPlaneS(new Vector4f(volume.xTexGenScale, 0.0f, 0.0f, 0.0f));
			zTg.setPlaneT(new Vector4f(0.0f, volume.yTexGenScale, 0.0f, 0.0f));
			break;
		  case Y_AXIS:
			rSize = volume.yDim;
			sSize = volume.xTexSize;
			tSize = volume.zTexSize;
			textures = yTextures = new Texture2D[rSize];
			yTg = new TexCoordGeneration();
			yTg.setPlaneS(new Vector4f(volume.xTexGenScale, 0.0f, 0.0f, 0.0f));
			yTg.setPlaneT(new Vector4f(0.0f, 0.0f, volume.zTexGenScale, 0.0f));
			break;
		  case X_AXIS:
			rSize = volume.xDim;
			sSize = volume.yTexSize;
			tSize = volume.zTexSize;
			textures = xTextures = new Texture2D[rSize];
			xTg = new TexCoordGeneration();
			xTg.setPlaneS(new Vector4f(0.0f, volume.yTexGenScale, 0.0f, 0.0f));
			xTg.setPlaneT(new Vector4f(0.0f, 0.0f, volume.zTexGenScale, 0.0f));
			break;
		}

		ColorModel colorModel = null;
		if (volume.is8C) {
			colorModel = volume.cmodel;
		} else {
			ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
			int[] nBits = {8};
			colorModel = new ComponentColorModel(cs, nBits, false, false, 
							Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
		}

		raster = colorModel.createCompatibleWritableRaster(sSize, tSize); 
		byte[] data = ((DataBufferByte)raster.getDataBuffer()).getData();

		BufferedImage bImage = 
			new BufferedImage(colorModel, raster, false, null); 

		int textureMode, componentType; 
		if (volume.is8C) {
			textureMode = Texture.RGBA;
			componentType = ImageComponent.FORMAT_RGBA;
		} else {
			textureMode = Texture.INTENSITY;
			componentType = ImageComponent.FORMAT_CHANNEL8;
		}

		for (int i=0; i < rSize; i ++) { 
			switch (axis) {
			  case Z_AXIS: volume.loadZ(i, data); break;
			  case Y_AXIS: volume.loadY(i, data); break;
			  case X_AXIS: volume.loadX(i, data); break;
			}
			IJ.showProgress(i, rSize);

			Texture2D tex;
			ImageComponent2D pArray;
			tex = new Texture2D(Texture.BASE_LEVEL, textureMode, sSize, tSize);
			pArray = new ImageComponent2D(componentType, sSize, tSize);
			pArray.set(bImage);
		
			tex.setImage(0, pArray);
			tex.setEnable(true);
			tex.setMinFilter(Texture.BASE_LEVEL_LINEAR);
			tex.setMagFilter(Texture.BASE_LEVEL_LINEAR);
			
			tex.setBoundaryModeS(Texture.CLAMP);
			tex.setBoundaryModeT(Texture.CLAMP);

			textures[i] = tex;
		} 
    } 
}
