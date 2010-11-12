package org.open2jam.render.jogl;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.net.URL;
import java.util.Properties;
import javax.imageio.ImageIO;

import javax.media.opengl.GL2;

/**
 * A utility class to load textures for JOGL. This source is based
 * on a texture that can be found in the Java Gaming (www.javagaming.org)
 * Wiki. It has been simplified slightly for explicit 2D graphics use.
 * 
 * OpenGL uses a particular image format. Since the images that are 
 * loaded from disk may not match this format this loader introduces
 * a intermediate image which the source image is copied into. In turn,
 * this image is used as source for the OpenGL texture.
 *
 * @author Kevin Glass
 * @author Brian Matzon
 * @author fox
 */
public class TextureLoader {
    /** The table of textures that have been loaded in this loader */
    private HashMap<URL,Texture> table = new HashMap<URL,Texture>();

    /** The colour model including alpha for the GL image */
    private ColorModel glAlphaColorModel;
    
    /** The colour model for the GL image */
    private ColorModel glColorModel;
    private final GL2 gl;
    
    /** 
     * Create a new texture loader based on the game panel
     *
     * @param gl The GL content in which the textures should be loaded
     */
    public TextureLoader(GL2 gl) {
        this.gl = gl;
        glAlphaColorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                                            new int[] {8,8,8,8},
                                            true,
                                            false,
                                            ComponentColorModel.TRANSLUCENT,
                                            DataBuffer.TYPE_BYTE);
                                            
        glColorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                                            new int[] {8,8,8,0},
                                            false,
                                            false,
                                            ComponentColorModel.OPAQUE,
                                            DataBuffer.TYPE_BYTE);
    }
    
    /**
     * Create a new texture ID 
     *
     * @return A new texture ID
     */
    private int createTextureID() 
    { 
       IntBuffer tmp = createIntBuffer(1); 
       gl.glGenTextures(1, tmp);
       return tmp.get(0);
    }
    
    /**
     * Load a texture
     *
     * @param resourceName The location of the resource to load
     * @return The loaded texture
     * @throws IOException Indicates a failure to access the resource
     */
    public Texture getTexture(URL resource) throws IOException {
        Texture tex = table.get(resource);
        
        if (tex != null)return tex;
        
        tex = createTexture(resource,
                         GL2.GL_TEXTURE_2D, // target
                         GL2.GL_RGBA,     // dst pixel format
                         GL2.GL_LINEAR, // min filter (unused)
                         GL2.GL_LINEAR);
        
        table.put(resource,tex);
        
        return tex;
    }
    
    /**
     * Load a texture into OpenGL from a image reference on
     * disk.
     *
     * @param resourceName The location of the resource to load
     * @param target The GL target to load the texture against
     * @param dstPixelFormat The pixel format of the screen
     * @param minFilter The minimising filter
     * @param magFilter The magnification filter
     * @return The loaded texture
     * @throws IOException Indicates a failure to access the resource
     */
    public Texture createTexture(URL resource, 
                              int target, 
                              int dstPixelFormat, 
                              int minFilter, 
                              int magFilter) throws IOException 
    {
        int srcPixelFormat = 0;

        BufferedImage image = loadImage(resource);

	int texw = getNextPOT(image.getWidth(null));
	int texh = getNextPOT(image.getHeight(null));
        
        // create the texture ID for this texture 
        int textureID = createTextureID(); 
        Texture texture = new Texture(target,textureID, texw, texh); 
        
        // bind this texture 
        gl.glBindTexture(target, textureID);


        if (image.getColorModel().hasAlpha()) {
            srcPixelFormat = GL2.GL_RGBA;
        } else {
            srcPixelFormat = GL2.GL_RGB;
        }

        // convert that image into a byte buffer of texture data 
        ByteBuffer textureBuffer = convertImageData(image);
        
        if (target == GL2.GL_TEXTURE_2D)
        {
            gl.glTexParameteri(target, GL2.GL_TEXTURE_MIN_FILTER, minFilter);
            gl.glTexParameteri(target, GL2.GL_TEXTURE_MAG_FILTER, magFilter);
        }
 
        // produce a texture from the byte buffer
        gl.glTexImage2D(target,
                      0, 
                      dstPixelFormat, 
                      texw, 
                      texh,
                      0, 
                      srcPixelFormat, 
                      GL2.GL_UNSIGNED_BYTE,
                      textureBuffer ); 
        
        return texture; 
    }
    
    /**
     * Get the closest greater power of 2 to the fold number
     * 
     * @param fold The target number
     * @return The power of 2
     */
    private int getNextPOT(int fold) {
        int ret = 2;
        while (ret < fold) {
            ret *= 2;
        }
        return ret;
    }
    
	/**
	* Convert the buffered image to a texture
	*
	* @param bufferedImage The image to convert to a texture
	* @param texture The texture to store the data into
	* @param slice specify only a part of the source image, can be null
	* @return A buffer containing the data
	*/
	private ByteBuffer convertImageData(BufferedImage image)
	{
		int texWidth = getNextPOT(image.getWidth(null));
		int texHeight = getNextPOT(image.getHeight(null));

		// create a raster that can be used by OpenGL as a source
		// for a texture
		BufferedImage texImage;
		if (image.getColorModel().hasAlpha()) {
			texImage = new BufferedImage(glAlphaColorModel,
			Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,texWidth,texHeight,4,null),
			false,new Properties());
		}
		else {
			texImage = new BufferedImage(glColorModel,
			Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,texWidth,texHeight,3,null),
			false,new Properties());
		}
		
		// copy the source image into the produced image
		Graphics g = texImage.getGraphics();
		g.setColor(new Color(0f,0f,0f,0f));
		g.fillRect(0,0,texWidth,texHeight);
		g.drawImage(image,0,0,null);
		
		// build a byte buffer from the temporary image 
		// that be used by OpenGL to produce a texture.
		byte[] data = ((DataBufferByte) texImage.getRaster().getDataBuffer()).getData(); 

		ByteBuffer imageBuffer = imageBuffer = ByteBuffer.allocateDirect(data.length); 
		imageBuffer.order(ByteOrder.nativeOrder()); 
		imageBuffer.put(data, 0, data.length); 
		imageBuffer.flip();
		g.dispose();

		return imageBuffer; 
	}
    
	/** 
	* Load a given resource as a buffered image
	* 
	* @param url The location of the resource to load
	* @return The loaded image
	*/
	private BufferedImage loadImage(URL url) throws IOException
	{
		return ImageIO.read(url);
	}
    
    /**
     * Creates an integer buffer to hold specified ints
     * - strictly a utility method
     *
     * @param size how many int to contain
     * @return created IntBuffer
     */
    protected IntBuffer createIntBuffer(int size) {
      ByteBuffer temp = ByteBuffer.allocateDirect(4 * size);
      temp.order(ByteOrder.nativeOrder());

      return temp.asIntBuffer();
    }
}
