package org.open2jam.render.jogl;



import javax.media.opengl.GL2;
import org.open2jam.render.Sprite;
import org.open2jam.render.SpriteID;

/**
 * Implementation of sprite that uses an OpenGL quad and a texture
 * to render a given image to the screen.
 * 
 * @author Kevin Glass
 */
public class JoglSprite implements Sprite {
	/** The texture that stores the image for this sprite */
	private Texture texture;
	/** The window that this sprite can be drawn in */
	private JoglGameWindow window;
	/** The width in pixels of this sprite */
	private int width;
	/** The height in pixels of this sprite */
	private int height;

        /** the position inside the texture of the sprite */
	private int x, y;

        /** the coordinates for the texture */
	private float u, v, w, z;

	/**
	 * Create a new sprite from a specified image.
	 * 
	 * @param window The window in which the sprite will be displayed
	 * @param ref A reference to the image on which this sprite should be based
	 */
	public JoglSprite(JoglGameWindow window,SpriteID ref) {
		try {
			this.window = window;
			texture = window.getTextureLoader().getTexture(ref.getURL());

			if(ref.getSlice() == null){
				x = 0;
				y = 0;
				width = texture.getWidth();
				height = texture.getHeight();
			}else{
				x = ref.getSlice().x;
				y = ref.getSlice().y;
				width = ref.getSlice().width;
				height = ref.getSlice().height;
			}
			u = ((float)x/texture.getWidth()); // top-left x
			v = ((float)y/texture.getHeight()); // top-left y

			w = ((float)(x+width)/texture.getWidth()); // bottom-right x
			z = ((float)(y+height)/texture.getHeight()); // bottom-right y
		} catch (Exception e) {
			// a tad abrupt, but our purposes if you can't find a 
			// sprite's image you might as well give up.
			System.err.println("Unable to load texture: "+ref);
			System.exit(0);
		}
	}
	
	/**
	 * Get the width of this sprite in pixels
	 * 
	 * @return The width of this sprite in pixels
	 */
	public int getWidth() {
		return width;
	}

	/**
	 * Get the height of this sprite in pixels
	 * 
	 * @return The height of this sprite in pixels
	 */
	public int getHeight() {
		return height;
	}

	/**
	 * Draw the sprite at the specified location
	 * 
	 * @param x The x location at which to draw this sprite
	 * @param y The y location at which to draw this sprite
	 */
	public void draw(int x, int y) {
		// get hold of the GL content from the window in which we're drawning
		GL2 gl = window.getGL();
		
		// store the current model matrix
		gl.glPushMatrix();
		
		// bind to the appropriate texture for this sprite
		texture.bind(gl);		
		// translate to the right location and prepare to draw
		gl.glTranslatef(x, y, 0);
		gl.glColor3f(1,1,1);
		
		// draw a quad textured to match the sprite
		gl.glBegin(GL2.GL_QUADS);
		{
			gl.glTexCoord2f(0, 0);
			gl.glVertex2f(0, 0);
			gl.glTexCoord2f(0, texture.getHeight());
			gl.glVertex2f(0, height);
			gl.glTexCoord2f(texture.getWidth(), texture.getHeight());
			gl.glVertex2f(width,height);
			gl.glTexCoord2f(texture.getWidth(), 0);
			gl.glVertex2f(width,0);
		}
		gl.glEnd();
		
		// restore the model view matrix to prevent contamination
		gl.glPopMatrix();
	}

    public SpriteID getID() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void draw(double x, double y) {
        draw((int)Math.round(x),(int)Math.round(y));
    }
	
}