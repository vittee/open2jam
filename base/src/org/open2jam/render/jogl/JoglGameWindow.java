package org.open2jam.render.jogl;

import com.jogamp.gluegen.runtime.NativeLibLoader;
import java.awt.event.WindowEvent;

import javax.media.opengl.GL;
import javax.media.opengl.GLAutoDrawable;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.newt.event.WindowAdapter;
import java.io.File;
import javax.media.opengl.GL2;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLDrawable;
import javax.media.opengl.GLEventListener;

import javax.media.opengl.GLProfile;
import org.open2jam.render.GameWindow;
import org.open2jam.render.GameWindowCallback;

/**
 * An implementation of GameWindow that will use OPENGL (JOGL) to 
 * render the scene. Its also responsible for monitoring the keyboard
 * using AWT.
 * 
 * @author Kevin Glass
 */
public class JoglGameWindow implements GLEventListener,GameWindow {

	/** The callback which should be notified of window events */
	private GameWindowCallback callback;
	/** The width of the game display area */
	private int width;
	/** The height of the game display area */
	private int height;
	/** The canvas which gives us access to OpenGL */
	private GLWindow window;

	/** The loader responsible for converting images into OpenGL textures */
	private TextureLoader textureLoader;

        private GL2 gl;
        private boolean vsync = false;

        static {
            NativeLibLoader.disableLoading();
            File[] libs = new File("jogl_lib/linux64").listFiles();
            for(File f : libs)System.load(f.getAbsolutePath());
            System.out.println("libs loaded");
        }
	
	/**
	 * Create a new game window that will use OpenGL to 
	 * render our game.
	 */
	public JoglGameWindow() {
	}
	
	/**
	 * Retrieve access to the texture loader that converts images
	 * into OpenGL textures. Note, this has been made package level
	 * since only other parts of the JOGL implementations need to access
	 * it.
	 * 
	 * @return The texture loader that can be used to load images into
	 * OpenGL textures.
	 */
	TextureLoader getTextureLoader() {
		return textureLoader;
	}
	
	/**
	 * Get access to the GL context that can be used in JOGL to
	 * call OpenGL commands.
	 * 
	 * @return The GL context which can be used for this window
	 */
	GL2 getGL() {
		return gl;
	}
	
	/**
	 * Set the title of this window.
	 *
	 * @param title The title to set on this window
	 */
	public void setTitle(String title) {
		// TODO
	}

	/**
	 * Set the resolution of the game display area.
	 *
	 * @param x The width of the game display area
	 * @param y The height of the game display area
	 */
	public void setResolution(int x, int y) {
		width = x;
		height = y;
	}

	/**
	 * Start the rendering process. This method will cause the 
	 * display to redraw as fast as possible.
	 */
	public void startRendering() {
            GLProfile.initSingleton(true);
            GLProfile glp = GLProfile.getDefault();
            
            GLCapabilities caps = new GLCapabilities(glp);
            
            window = GLWindow.create(caps);
            window.setSize(width, height);
            window.setVisible(true);
            window.addGLEventListener(this);
		
		// add a listener to respond to the user closing the window. If they
		// do we'd like to exit the game
		window.addWindowListener(new WindowAdapter() {
			public void windowDestroyNotify(WindowEvent e) {
				if (callback != null) {
					callback.windowClosed();
				} else {
					System.exit(0);
				}
			}
		});
	}

	/**
	 * Register a callback that will be notified of game window
	 * events.
	 *
	 * @param callback The callback that should be notified of game
	 * window events. 
	 */
	public void setGameWindowCallback(GameWindowCallback callback) {
		this.callback = callback;
	}


	/**
	 * Called by the JOGL rendering process at initialisation. This method
	 * is responsible for setting up the GL context.
	 *
	 * @param drawable The GL context which is being initialised
	 */
	public void init(GLAutoDrawable drawable) {
                gl = drawable.getGL().getGL2();

		// enable textures since we're going to use these for our sprites
		gl.glEnable(GL.GL_TEXTURE_2D);
		
		// set the background colour of the display to black
		gl.glClearColor(0, 0, 0, 0);
		// set the area being rendered
		gl.glViewport(0, 0, width, height);
		// disable the OpenGL depth test since we're rendering 2D graphics
		gl.glDisable(GL.GL_DEPTH_TEST);
		
		textureLoader = new TextureLoader(gl);

                if(vsync)gl.setSwapInterval(1);
		
		if (callback != null) {
			callback.initialise();
		}
	}

	/**
	 * Called by the JOGL rendering process to display a frame. In this
	 * case its responsible for blanking the display and then notifing
	 * any registered callback that the screen requires rendering.
	 * 
	 * @param drawable The GL context component being drawn
	 */
	public void display(GLAutoDrawable drawable) {

                gl = drawable.getGL().getGL2();
		// clear the screen and setup for rendering
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();
		
		// if a callback has been registered notify it that the
		// screen is being rendered
		if (callback != null) {
			callback.frameRendering();
		}
		
		// flush the graphics commands to the card
		gl.glFlush();
	}

	/**
	 * Called by the JOGL rendering process if and when the display is 
	 * resized.
	 *
	 * @param drawable The GL content component being resized
	 * @param x The new x location of the component
	 * @param y The new y location of the component
	 * @param width The width of the component
	 * @param height The height of the component 
	 */
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {

                gl = drawable.getGL().getGL2();
		// at reshape we're going to tell OPENGL that we'd like to 
		// treat the screen on a pixel by pixel basis by telling
		// it to use Orthographic projection.
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadIdentity();

		gl.glOrtho(0, width, height, 0, -1, 1);
	}

	/**
	 * Called by the JOGL rendering process if/when the display mode
	 * is changed.
	 *
	 * @param drawable The GL context which has changed
	 * @param modeChanged True if the display mode has changed
	 * @param deviceChanged True if the device in use has changed 
	 */
	public void displayChanged(GLDrawable drawable, boolean modeChanged, boolean deviceChanged) {
		// we're not going to do anything here, we could react to the display 
		// mode changing but for the tutorial there's not much point.
	}



    public int getResolutionHeight() {
        return height;
    }

    public void destroy() {
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    public void dispose(GLAutoDrawable arg0) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    public void setDisplay(int width, int height, boolean vsync, boolean fs) throws Exception {
        this.width = width;
        this.height = height;
        this.vsync = vsync;
        // TODO: vync, fullscreen
    }
}