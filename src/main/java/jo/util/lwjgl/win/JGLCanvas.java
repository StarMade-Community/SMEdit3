package jo.util.lwjgl.win;

import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jo.util.jgl.enm.JGLColorMaterialFace;
import jo.util.jgl.enm.JGLColorMaterialMode;
import jo.util.jgl.enm.JGLFogMode;
import jo.util.jgl.obj.JGLScene;
import jo.vecmath.Point3f;
import jo.vecmath.logic.Color4fLogic;
import jo.vecmath.logic.Matrix4fLogic;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.*;

@SuppressWarnings("serial")
public class JGLCanvas extends Canvas {

    private JGLScene mScene;
    private Point3f mEyeRay;

    private final IntBuffer mIB16;
    //private int mViewportX;
    //private int mViewportBottom;
    private int mWidth;
    private int mHeight;

    private boolean mCloseRequested = false;
    private AtomicReference<Dimension> mNewCanvasSize;

    private boolean[] mMouseState;
    private final List<MouseListener> mMouseListeners;
    private final List<MouseMotionListener> mMouseMotionListeners;
    private final List<MouseWheelListener> mMouseWheelListeners;
    private final List<KeyListener> mKeyListeners;
    
    private long windowHandle = 0;
    private int mMouseX = 0;
    private int mMouseY = 0;
    
    private static final int MAX_MOUSE_BUTTONS = 8;

    public JGLCanvas() {
        this.mNewCanvasSize = new AtomicReference<>();
        this.mMouseListeners = new ArrayList<>();
        this.mMouseWheelListeners = new ArrayList<>();
        this.mMouseMotionListeners = new ArrayList<>();
        this.mKeyListeners = new ArrayList<>();
        mIB16 = BufferUtils.createIntBuffer(16);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                mNewCanvasSize.set(getSize());
            }
        });
    }

    /**
     * <p>
     * Queries the current view port size & position and updates all related
     * internal state.</p>
     *
     * <p>
     * It is important that the internal state matches the OpenGL viewport or
     * clipping won't work correctly.</p>
     *
     * <p>
     * This method should only be called when the viewport size has changed. It
     * can have negative impact on performance to call every frame.</p>
     *
     * @see #getWidth()
     * @see #getHeight()
     */
    public void syncViewportSize() {
        mIB16.clear();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, mIB16);
        //mViewportX = mIB16.get(0);
        mWidth = mIB16.get(2);
        mHeight = mIB16.get(3);
        //mViewportBottom = mIB16.get(1) + mHeight;
    }

    private void init() {
        Thread t = new Thread("Render Thread") {
            @Override
            public void run() {
                doRenderLoop();
            }
        };
        t.start();
    }

    private void initFog() {
        GL11.glEnable(GL11.GL_FOG);
        GL11.glFogi(GL11.GL_FOG_MODE, conv(mScene.getFogMode()));
        if (!Matrix4fLogic.epsilonEquals(mScene.getFogDensity(), 1)) {
            GL11.glFogf(GL11.GL_FOG_DENSITY, mScene.getFogDensity());
        }
        if (!Matrix4fLogic.epsilonEquals(mScene.getFogStart(), 0)) {
            GL11.glFogf(GL11.GL_FOG_START, mScene.getFogStart());
        }
        if (!Matrix4fLogic.epsilonEquals(mScene.getFogEnd(), 1)) {
            GL11.glFogf(GL11.GL_FOG_END, mScene.getFogEnd());
        }
        if (!Matrix4fLogic.epsilonEquals(mScene.getFogIndex(), 0)) {
            GL11.glFogf(GL11.GL_FOG_INDEX, mScene.getFogIndex());
        }
        if (mScene.getFogColor() != null) {
            GL11.glFogfv(GL11.GL_FOG_COLOR, Color4fLogic.toFloatBuffer(mScene.getFogColor()));
        }
    }

    private void initMaterial() {
        int face = conv(mScene.getColorMaterialFace());
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        if (mScene.getColorMaterialFace() != JGLColorMaterialFace.UNSET) {
            GL11.glColorMaterial(face, conv(mScene.getColorMaterialMode()));
        }
        if (mScene.getMaterialAmbient() != null) {
            GL11.glMaterialfv(face, GL11.GL_AMBIENT, Color4fLogic.toFloatBuffer(mScene.getMaterialAmbient()));
        }
        if (mScene.getMaterialDiffuse() != null) {
            GL11.glMaterialfv(face, GL11.GL_DIFFUSE, Color4fLogic.toFloatBuffer(mScene.getMaterialDiffuse()));
        }
        if (mScene.getMaterialSpecular() != null) {
            GL11.glMaterialfv(face, GL11.GL_SPECULAR, Color4fLogic.toFloatBuffer(mScene.getMaterialSpecular()));
        }
        if (mScene.getMaterialEmission() != null) {
            GL11.glMaterialfv(face, GL11.GL_EMISSION, Color4fLogic.toFloatBuffer(mScene.getMaterialEmission()));
        }
        if (mScene.getMaterialShininess() >= 0) {
            GL11.glMaterialf(face, GL11.GL_SHININESS, mScene.getMaterialShininess());
        }
    }

    private int conv(JGLFogMode fogMode) {
        switch (fogMode) {
            case UNSET:
                return -1;
            case LINEAR:
                return GL11.GL_LINEAR;
            case EXP:
                return GL11.GL_EXP;
            case EXP2:
                return GL11.GL_EXP2;
        }
        return -1;
    }

    private int conv(JGLColorMaterialFace colorMaterialFace) {
        switch (colorMaterialFace) {
            case UNSET:
                return -1;
            case FRONT:
                return GL11.GL_FRONT;
            case BACK:
                return GL11.GL_BACK;
            case FRONT_AND_BACK:
                return GL11.GL_FRONT_AND_BACK;
        }
        return -1;
    }

    private int conv(JGLColorMaterialMode colorMaterialMode) {
        switch (colorMaterialMode) {
            case UNSET:
                return -1;
            case EMISSION:
                return GL11.GL_EMISSION;
            case AMBIENT:
                return GL11.GL_AMBIENT;
            case DIFFUSE:
                return GL11.GL_DIFFUSE;
            case SPECULAR:
                return GL11.GL_SPECULAR;
            case AMBIENT_AND_DIFFUSE:
                return GL11.GL_AMBIENT_AND_DIFFUSE;
        }
        return -1;
    }

    private void doRenderLoop() {
        try {
            while (!isDisplayable()) {
                Thread.sleep(50);
            }
            
            // Initialize GLFW
            GLFWErrorCallback.createPrint(System.err).set();
            if (!glfwInit()) {
                throw new IllegalStateException("Unable to initialize GLFW");
            }
            
            // Configure GLFW
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
            
            // Get the initial canvas size
            Dimension size = getSize();
            if (size.width <= 0 || size.height <= 0) {
                size = new Dimension(800, 600); // Default size
            }
            
            // Create the window
            windowHandle = glfwCreateWindow(size.width, size.height, "SMEdit3", NULL, NULL);
            if (windowHandle == NULL) {
                throw new RuntimeException("Failed to create the GLFW window");
            }
            
            // Setup mouse button callback
            glfwSetMouseButtonCallback(windowHandle, (window, button, action, mods) -> {
                // Validate button index
                if (button < 0 || button >= MAX_MOUSE_BUTTONS) {
                    return;
                }
                
                int jButton = (button == GLFW_MOUSE_BUTTON_LEFT) ? MouseEvent.BUTTON1 
                            : (button == GLFW_MOUSE_BUTTON_RIGHT) ? MouseEvent.BUTTON2 
                            : MouseEvent.BUTTON3;
                boolean buttonState = (action == GLFW_PRESS);
                
                if (mMouseState[button] != buttonState) {
                    long nanoseconds = System.nanoTime();
                    if (buttonState) {
                        MouseEvent event = new MouseEvent(this, MouseEvent.MOUSE_PRESSED, nanoseconds, 0, mMouseX, mMouseY, 1, false, jButton);
                        fireMouseEvent(event);
                    } else {
                        MouseEvent event = new MouseEvent(this, MouseEvent.MOUSE_RELEASED, nanoseconds, 0, mMouseX, mMouseY, 1, false, jButton);
                        fireMouseEvent(event);
                    }
                    mMouseState[button] = buttonState;
                }
            });
            
            // Setup cursor position callback
            glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
                mMouseX = (int) xpos;
                mMouseY = (int) ypos;
                
                long nanoseconds = System.nanoTime();
                int jButton = 0;
                if (GLFW_MOUSE_BUTTON_LEFT < MAX_MOUSE_BUTTONS && mMouseState[GLFW_MOUSE_BUTTON_LEFT]) {
                    jButton |= MouseEvent.BUTTON1;
                }
                if (GLFW_MOUSE_BUTTON_RIGHT < MAX_MOUSE_BUTTONS && mMouseState[GLFW_MOUSE_BUTTON_RIGHT]) {
                    jButton |= MouseEvent.BUTTON2;
                }
                if (GLFW_MOUSE_BUTTON_MIDDLE < MAX_MOUSE_BUTTONS && mMouseState[GLFW_MOUSE_BUTTON_MIDDLE]) {
                    jButton |= MouseEvent.BUTTON3;
                }
                
                if (jButton != 0) {
                    MouseEvent event = new MouseEvent(this, MouseEvent.MOUSE_DRAGGED, nanoseconds, 0, mMouseX, mMouseY, 1, false, jButton);
                    fireMouseMoveEvent(event);
                } else {
                    MouseEvent event = new MouseEvent(this, MouseEvent.MOUSE_MOVED, nanoseconds, 0, mMouseX, mMouseY, 1, false, jButton);
                    fireMouseMoveEvent(event);
                }
            });
            
            // Setup scroll callback
            glfwSetScrollCallback(windowHandle, (window, xoffset, yoffset) -> {
                long nanoseconds = System.nanoTime();
                int dWheel = (int) (yoffset * 120);
                MouseWheelEvent event = new MouseWheelEvent(this, MouseEvent.MOUSE_WHEEL, nanoseconds, 0,
                        mMouseX, mMouseY, 1, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, dWheel, (int) yoffset);
                fireMouseWheelEvent(event);
            });
            
            // Setup key callback
            glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
                if (action == GLFW_REPEAT) {
                    return; // Skip repeat events for simplicity
                }
                
                boolean eventState = (action == GLFW_PRESS);
                long eventTick = System.nanoTime();
                
                // Update modifiers
                if (key == GLFW_KEY_LEFT_SHIFT || key == GLFW_KEY_RIGHT_SHIFT) {
                    if (eventState) {
                        mModifiers |= KeyEvent.VK_SHIFT;
                    } else {
                        mModifiers &= ~KeyEvent.VK_SHIFT;
                    }
                } else if (key == GLFW_KEY_LEFT_CONTROL || key == GLFW_KEY_RIGHT_CONTROL) {
                    if (eventState) {
                        mModifiers |= KeyEvent.VK_CONTROL;
                    } else {
                        mModifiers &= ~KeyEvent.VK_CONTROL;
                    }
                } else if (key == GLFW_KEY_LEFT_ALT || key == GLFW_KEY_RIGHT_ALT) {
                    if (eventState) {
                        mModifiers |= KeyEvent.VK_ALT;
                    } else {
                        mModifiers &= ~KeyEvent.VK_ALT;
                    }
                }
                
                int awtKey = key;
                if (KEY_GLFW_TO_AWT.containsKey(key)) {
                    awtKey = KEY_GLFW_TO_AWT.get(key);
                }
                
                KeyEvent e = new KeyEvent(this, eventState ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                        eventTick, mModifiers, awtKey, KeyEvent.CHAR_UNDEFINED);
                fireKeyEvent(e);
            });
            
            // Make the OpenGL context current
            glfwMakeContextCurrent(windowHandle);
            // Enable v-sync
            glfwSwapInterval(1);
            
            // This line is critical for LWJGL's interoperation with GLFW's
            // OpenGL context, or any context that is managed externally.
            // LWJGL detects the context that is current in the current thread,
            // creates the GLCapabilities instance and makes the OpenGL
            // bindings available for use.
            GL.createCapabilities();
            
            // Initialize mouse state
            mMouseState = new boolean[MAX_MOUSE_BUTTONS];
            for (int i = 0; i < mMouseState.length; i++) {
                mMouseState[i] = false;
            }

            // Initialize OpenGL
            GL11.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
            GL11.glHint(GL11.GL_PERSPECTIVE_CORRECTION_HINT, GL11.GL_NICEST);
            GL11.glClearDepth(1.0);
            GL11.glLineWidth(2);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            if (mScene.getAmbientLight() != null) {
                GL11.glEnable(GL11.GL_LIGHTING);
                GL11.glLightModelfv(GL11.GL_LIGHT_MODEL_AMBIENT, Color4fLogic.toFloatBuffer(mScene.getAmbientLight()));
            }
            if (mScene.getColorMaterialFace() != JGLColorMaterialFace.UNSET) {
                initMaterial();
            }
            if (mScene.getFogMode() != JGLFogMode.UNSET) {
                initFog();
            }

            Dimension newDim;

            // Run the rendering loop until the user closes the window or close is requested
            while (!glfwWindowShouldClose(windowHandle) && !mCloseRequested) {
                newDim = mNewCanvasSize.getAndSet(null);
                if (newDim != null) {
                    glfwSetWindowSize(windowHandle, newDim.width, newDim.height);
                    GL11.glViewport(0, 0, newDim.width, newDim.height);
                    syncViewportSize();
                }
                
                doRender();
                doEye();
                
                glfwSwapBuffers(windowHandle);
                glfwPollEvents();
            }

            // Free the window callbacks and destroy the window
            glfwDestroyWindow(windowHandle);
            
            // Terminate GLFW and free the error callback
            glfwTerminate();
            glfwSetErrorCallback(null).free();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static final Map<Integer, Integer> KEY_GLFW_TO_AWT = new HashMap<>();

    static {
        KEY_GLFW_TO_AWT.put(GLFW_KEY_0, KeyEvent.VK_0);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_1, KeyEvent.VK_1);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_2, KeyEvent.VK_2);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_3, KeyEvent.VK_3);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_4, KeyEvent.VK_4);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_5, KeyEvent.VK_5);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_6, KeyEvent.VK_6);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_7, KeyEvent.VK_7);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_8, KeyEvent.VK_8);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_9, KeyEvent.VK_9);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_A, KeyEvent.VK_A);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_B, KeyEvent.VK_B);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_C, KeyEvent.VK_C);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_D, KeyEvent.VK_D);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_E, KeyEvent.VK_E);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_F, KeyEvent.VK_F);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_G, KeyEvent.VK_G);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_H, KeyEvent.VK_H);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_I, KeyEvent.VK_I);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_J, KeyEvent.VK_J);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_K, KeyEvent.VK_K);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_L, KeyEvent.VK_L);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_M, KeyEvent.VK_M);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_N, KeyEvent.VK_N);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_O, KeyEvent.VK_O);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_P, KeyEvent.VK_P);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_Q, KeyEvent.VK_Q);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_R, KeyEvent.VK_R);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_S, KeyEvent.VK_S);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_T, KeyEvent.VK_T);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_U, KeyEvent.VK_U);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_V, KeyEvent.VK_V);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_W, KeyEvent.VK_W);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_X, KeyEvent.VK_X);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_Y, KeyEvent.VK_Y);
        KEY_GLFW_TO_AWT.put(GLFW_KEY_Z, KeyEvent.VK_Z);
    }

    private int mModifiers = 0;

    private void doEye() {
        FloatBuffer modelview = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, modelview);
        FloatBuffer projection = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX, projection);
        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        float winX = mMouseX;
        float winY = viewport.get(3) - mMouseY;
        FloatBuffer winZBuffer = BufferUtils.createFloatBuffer(1);
        GL11.glReadPixels(mMouseX, mMouseY, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, winZBuffer);
        float winZ = winZBuffer.get(0);
        FloatBuffer pos = BufferUtils.createFloatBuffer(3);
        GLUHelper.gluUnProject(winX, winY, winZ, modelview, projection, viewport, pos);
        mEyeRay = new Point3f(pos.get(0), pos.get(1), pos.get(2));
    }

    private void doRender() {
        for (Runnable r : mScene.getBetweenRenderers()) {
            r.run();
        }
        DrawLogic.draw(mWidth, mHeight, System.currentTimeMillis(), mScene);
    }

    public JGLScene getScene() {
        return mScene;
    }

    public void setScene(JGLScene scene) {
        if ((mScene != null) && (mScene != scene)) {
            throw new IllegalArgumentException("Cannot set a new scene!");
        }
        mScene = scene;
        if (mScene != null) {
            init();
        }
    }

    public boolean isCloseRequested() {
        return mCloseRequested;
    }

    public void setCloseRequested(boolean closeRequested) {
        mCloseRequested = closeRequested;
    }

    @Override
    public synchronized void addMouseListener(MouseListener l) {
        if (System.getProperty("os.name").contains("Mac")) {
            super.addMouseListener(l);
        } else {
            mMouseListeners.add(l);
        }
    }

    @Override
    public synchronized void addMouseMotionListener(MouseMotionListener l) {
        if (System.getProperty("os.name").contains("Mac")) {
            super.addMouseMotionListener(l);
        } else {
            mMouseMotionListeners.add(l);
        }
    }

    @Override
    public synchronized void addMouseWheelListener(MouseWheelListener l) {
        if (System.getProperty("os.name").contains("Mac")) {
            super.addMouseWheelListener(l);
        } else {
            mMouseWheelListeners.add(l);
        }
    }

    @Override
    public synchronized void removeMouseListener(MouseListener l) {
        mMouseListeners.remove(l);
    }

    @Override
    public synchronized void removeMouseMotionListener(MouseMotionListener l) {
        mMouseMotionListeners.remove(l);
    }

    @Override
    public synchronized void removeMouseWheelListener(MouseWheelListener l) {
        mMouseWheelListeners.remove(l);
    }

    @Override
    public synchronized void addKeyListener(KeyListener l) {
        mKeyListeners.add(l);
        super.addKeyListener(l);
    }

    @Override
    public synchronized void removeKeyListener(KeyListener l) {
        mKeyListeners.remove(l);
    }

    private void fireMouseEvent(MouseEvent e) {
        for (MouseListener l : mMouseListeners) {
            if (e.getID() == MouseEvent.MOUSE_PRESSED) {
                l.mousePressed(e);
            } else if (e.getID() == MouseEvent.MOUSE_RELEASED) {
                l.mouseReleased(e);
            }
        }
    }

    private void fireMouseMoveEvent(MouseEvent e) {
        for (MouseMotionListener l : mMouseMotionListeners) {
            if (e.getID() == MouseEvent.MOUSE_MOVED) {
                l.mouseMoved(e);
            } else if (e.getID() == MouseEvent.MOUSE_DRAGGED) {
                l.mouseDragged(e);
            }
        }
    }

    private void fireMouseWheelEvent(MouseWheelEvent e) {
        for (MouseWheelListener l : mMouseWheelListeners) {
            if (e.getID() == MouseEvent.MOUSE_WHEEL) {
                l.mouseWheelMoved(e);
            }
        }
    }

    private void fireKeyEvent(KeyEvent e) {
        for (KeyListener l : mKeyListeners) {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                l.keyPressed(e);
            } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                l.keyReleased(e);
            } else if (e.getID() == KeyEvent.KEY_TYPED) {
                l.keyTyped(e);
            }
        }
    }

    public Point3f getEyeRay() {
        return mEyeRay;
    }

    public void setEyeRay(Point3f eyeRay) {
        mEyeRay = eyeRay;
    }
}
