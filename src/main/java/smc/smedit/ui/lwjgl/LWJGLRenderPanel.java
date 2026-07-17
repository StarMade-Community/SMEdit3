/**
 * Copyright 2014 
 * SMEdit https://github.com/StarMade/SMEdit
 * SMTools https://github.com/StarMade/SMTools
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 **/
package smc.smedit.ui.lwjgl;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseListener;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.glu.GLU;

import smc.smedit.data.Blocks;
import smc.smedit.data.BlockGroups;
import smc.smedit.data.RenderPoly;
import smc.smedit.data.SparseMatrix;
import smc.smedit.data.UndoBuffer;
import smc.smedit.logic.StarMadeLogic;
import smc.smedit.ship.data.Block;
import smc.smedit.ui.RenderPanel;
import smc.smedit.util.jgl.obj.JGLCamera;
import smc.smedit.scene.Scene;
import smc.smedit.logic.SelectionModel;
import smc.smedit.scene.SceneModel;
import smc.smedit.scene.SceneObject;
import smc.smedit.util.jgl.obj.JGLGroup;
import smc.smedit.vecmath.Matrix4f;
import smc.smedit.util.jgl.obj.JGLNode;
import smc.smedit.util.jgl.obj.JGLScene;
import smc.smedit.util.jgl.obj.tri.JGLObj;
import smc.smedit.util.lwjgl.win.JGLCanvas;
import smc.smedit.vecmath.Color3f;
import smc.smedit.vecmath.Color4f;
import smc.smedit.vecmath.Point3f;
import smc.smedit.vecmath.Point3i;
import smc.smedit.vecmath.Vector3f;

@SuppressWarnings("serial")
public class LWJGLRenderPanel extends RenderPanel {

    private final JGLCanvas mCanvas;
    private final JGLScene mScene;
    public JGLCamera mUniverse;
    private final JGLGroup mBlocks;
    /** Read-only geometry for the scene's other (non-active) visible objects, each at its transform. */
    private final JGLGroup mContext;
    private final JGLGroup mSelection;
    private final JGLGroup mAxis;
    private final JGLGroup mGrid;
    /** The Move tool's manipulator (axis + plane handles); empty for every other tool. */
    private final JGLGroup mGizmo;

    /** Where the axis/grid guide is centred. */
    private AxisAnchor mAxisAnchor = AxisAnchor.SCENE;
    private boolean mShowGrid;

    private CameraMode mCameraMode = CameraMode.ORBIT;

    private static final Color3f AXIS_X = new Color3f(0.90f, 0.25f, 0.25f);
    private static final Color3f AXIS_Y = new Color3f(0.30f, 0.85f, 0.30f);
    private static final Color3f AXIS_Z = new Color3f(0.35f, 0.45f, 1.00f);
    private static final Color3f GRID_COLOR = new Color3f(0.32f, 0.32f, 0.35f);

    private SparseMatrix<Block> mFilteredGrid;
    private boolean mPlainGraphics;
    private boolean mDontDraw;
    private UndoBuffer mUndoer;

    Vector3f mPOVTranslate;

    /** The point the camera orbits around (the ship centre); set by {@link #setLookAt}. */
    Point3f mOrbitCenter = new Point3f();

    /** Camera position at the last transparent-mesh depth sort (skip re-sorting when it barely moved). */
    private Point3f mLastSortCam;
    /** GL matrices of the block group, captured each frame for screen->world picking. */
    private final PickMatrices mPickMatrices = new PickMatrices();

    /** True while a model swap is a mere edit-target change (click-to-focus): don't re-frame. */
    private boolean mSuppressReframe;

    public LWJGLRenderPanel() {
        mUndoer = new UndoBuffer();
        mPOVTranslate = new Vector3f();
        mScene = new JGLScene();
        mScene.setBackground(new Color4f());
        mUniverse = new JGLCamera();
        mScene.setNode(mUniverse);
        mBlocks = new JGLGroup();
        // Render thread stashes the block group's GL matrices here for picking.
        mBlocks.setData("pickCapture", mPickMatrices);
        mUniverse.getChildren().add(mBlocks);
        // Non-active scene objects render here as read-only context (no picking).
        mContext = new JGLGroup();
        mUniverse.getChildren().add(mContext);
        mSelection = new JGLGroup();
        mUniverse.getChildren().add(mSelection);
        mAxis = new JGLGroup();
        mUniverse.getChildren().add(mAxis);
        mGrid = new JGLGroup();
        mUniverse.getChildren().add(mGrid);
        // Drawn last and depth-test-off, so the Move gizmo sits over everything.
        mGizmo = new JGLGroup();
        mUniverse.getChildren().add(mGizmo);
        mCanvas = new JGLCanvas();
        mCanvas.setScene(mScene);
        setLayout(new BorderLayout());
        add("Center", mCanvas);
        // The GL canvas is a heavyweight java.awt.Canvas, which reports its current
        // size as its minimum — so a docking split divider can't shrink the viewport
        // and neighbouring panels (e.g. the Brush palette) can't be grown by dragging
        // it. Pin a small minimum so the viewport yields space on demand.
        Dimension minView = new Dimension(80, 80);
        mCanvas.setMinimumSize(minView);
        setMinimumSize(minView);
        MouseAdapter ma = new LWJGLMouseAdapter(this);
        mCanvas.addMouseListener(ma);
        mCanvas.addMouseMotionListener(ma);
        mCanvas.addMouseWheelListener(ma);
        // Fly-camera keys. JGLCanvas polls the LWJGL keyboard on its render thread
        // (the embedded LWJGL Display captures input, so AWT's KeyboardFocusManager
        // never sees these keys) and forwards them — plus any AWT key events — to its
        // KeyListeners. So register here as a listener. LWJGL only reports keys while
        // the Display has focus, which we grab on a viewport click (see requestViewportFocus).
        // Also a focus listener, so held keys are cleared when the viewport loses focus.
        LWJGLKeyEventDispatcher keys = new LWJGLKeyEventDispatcher(this);
        mCanvas.addKeyListener(keys);
        mCanvas.addFocusListener(keys);
        // Single-key tool shortcuts (V/B/P/…) via the same canvas key path.
        mCanvas.addKeyListener(new smc.smedit.ui.tool.ToolKeyListener());
        // A model swap normally re-frames the camera (open / undo). But when the swap
        // is just re-targeting which entity we edit (click-to-focus), the world-space
        // scene is unchanged and re-framing would jarringly snap the view — so only
        // refresh the geometry in that case.
        StarMadeLogic.getInstance().addPropertyChangeListener("model", ev -> {
            if (mSuppressReframe) {
                updateTiles();
            } else {
                setLookAt(new Point3f(0, 0, -1));
            }
        });
        // Refresh the viewport highlight whenever the selection changes (and the
        // axis/grid too, when they're anchored to the selection).
        StarMadeLogic.getInstance().getSelection().addListener(this::onSelectionChanged);
        // The Move gizmo only shows for the Move tool, so rebuild it when the tool changes.
        smc.smedit.ui.tool.ToolController.get().addListener(
                new smc.smedit.ui.tool.ToolController.Listener() {
                    @Override
                    public void toolChanged(smc.smedit.ui.tool.EditorTool tool) {
                        updateGizmo();
                    }
                });
    }

    @Override
    public CameraMode getCameraMode() {
        return mCameraMode;
    }

    @Override
    public void setCameraMode(CameraMode mode) {
        if (mode != null) {
            mCameraMode = mode;
        }
    }

    /** Flips between orbit (turntable) and first-person (free-look) control. */
    @Override
    public void toggleCameraMode() {
        mCameraMode = (mCameraMode == CameraMode.ORBIT)
                ? CameraMode.FIRST_PERSON : CameraMode.ORBIT;
        System.out.println("Camera mode: " + mCameraMode);
    }

    @Override
    public void updateTransform() {
        // Keep the orthographic view box sized to match the perspective framing at
        // the current orbit distance, so zoom behaves the same in both modes.
        Point3f loc = mUniverse.getCamera().getLocation();
        float dx = loc.x - mOrbitCenter.x;
        float dy = loc.y - mOrbitCenter.y;
        float dz = loc.z - mOrbitCenter.z;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        mScene.setOrthoHalfHeight((float) (dist * Math.tan(Math.toRadians(mScene.getFieldOfView() / 2.0))));
        sortTransparent(loc);
        updateGizmo();   // keep the move gizmo screen-sized and centred as the view moves
    }

    /**
     * Re-sorts the glass/transparent mesh back-to-front for the current camera so
     * it blends correctly. Skipped when the camera has barely moved since the last
     * sort (keeps dragging cheap); the order only needs updating as the view swings.
     */
    private void sortTransparent(Point3f cam) {
        // Each visible object's mesh is a child group of mBlocks now, so the glass
        // meshes sit one level down. Collect them all and sort each.
        List<JGLObj> glass = new ArrayList<>();
        for (JGLNode c : mBlocks.getChildren()) {
            if (c instanceof JGLObj && ((JGLObj) c).isTransparent()) {
                glass.add((JGLObj) c);
            } else if (c instanceof JGLGroup) {
                for (JGLNode g : ((JGLGroup) c).getChildren()) {
                    if (g instanceof JGLObj && ((JGLObj) g).isTransparent()) {
                        glass.add((JGLObj) g);
                    }
                }
            }
        }
        if (glass.isEmpty()) {
            mLastSortCam = null;
            return;
        }
        if (mLastSortCam != null) {
            float mx = cam.x - mLastSortCam.x, my = cam.y - mLastSortCam.y, mz = cam.z - mLastSortCam.z;
            if (mx * mx + my * my + mz * mz < 0.25f) { // moved < 0.5 world units
                return;
            }
        }
        for (JGLObj o : glass) {
            LWJGLRenderLogic.sortTransparentQuads(o, cam);
        }
        mLastSortCam = new Point3f(cam);
    }

    @Override
    public void setOrthographic(boolean orthographic) {
        mScene.setOrthographic(orthographic);
        updateTransform();
    }

    @Override
    public boolean isOrthographic() {
        return mScene.isOrthographic();
    }

    @Override
    public void resetCamera() {
        // Look down at the ship from the top-left (a 3/4 view): camera up (+Y),
        // left (-X) and back (-Z) of the model centre. setLookAt scales the axis
        // by the model size, so normalize it for a consistent distance.
        Point3f axis = new Point3f(-1f, 1f, 1f);
        float len = (float) Math.sqrt(axis.x * axis.x + axis.y * axis.y + axis.z * axis.z);
        if (len > 0f) {
            axis.scale(1f / len);
        }
        setLookAt(axis);
    }

    public void setLookAt(Point3f axis) {
        // Frame the WHOLE scene in world space (all visible objects at their world
        // transforms), so the camera stays put when the active object changes — only
        // the scene's overall bounds drive framing, not which entity is "current".
        Point3f lo = new Point3f();
        Point3f hi = new Point3f();
        Point3f center;
        float maxModel;
        if (sceneWorldBounds(lo, hi)) {
            center = new Point3f((lo.x + hi.x) / 2f, (lo.y + hi.y) / 2f, (lo.z + hi.z) / 2f);
            maxModel = Math.max(hi.x - lo.x, Math.max(hi.y - lo.y, hi.z - lo.z)) + 1;
        } else {
            center = new Point3f(0, 0, 0);
            maxModel = 16f;
        }
        mOrbitCenter = new Point3f(center);
        Point3f standHere = new Point3f(axis);
        standHere.scale(maxModel * 2);
        standHere.add(center);
        mUniverse.getCamera().lookAt(standHere, center);
        updateTiles();
    }

    /**
     * World-space AABB over every visible scene object (each grid's local bounds
     * transformed by its world matrix), or the legacy active grid at the origin when
     * there is no scene. Returns {@code false} (leaving lo/hi untouched) if empty.
     */
    private boolean sceneWorldBounds(Point3f lo, Point3f hi) {
        boolean[] any = {false};
        SceneModel sm = StarMadeLogic.getInstance().getSceneModel();
        Scene scene = sm != null ? sm.getScene() : null;
        if (scene != null && !scene.getObjects().isEmpty()) {
            for (SceneObject o : scene.getObjects()) {
                if (o.isVisible() && o.getGrid() != null && o.getGrid().size() > 0) {
                    accumulateBounds(o.getGrid(), o.getTransform(), lo, hi, any);
                }
            }
        }
        if (!any[0]) {
            SparseMatrix<Block> g = StarMadeLogic.getModel();
            if (g != null && g.size() > 0) {
                Matrix4f id = new Matrix4f();
                id.setIdentity();
                accumulateBounds(g, id, lo, hi, any);
            }
        }
        return any[0];
    }

    /** Expands lo/hi to enclose a grid's local AABB transformed into world space. */
    private static void accumulateBounds(SparseMatrix<Block> grid, Matrix4f world,
            Point3f lo, Point3f hi, boolean[] any) {
        Point3i gl = new Point3i();
        Point3i gh = new Point3i();
        grid.getBounds(gl, gh);
        for (int i = 0; i < 8; i++) {
            Point3f c = new Point3f((i & 1) == 0 ? gl.x : gh.x,
                    (i & 2) == 0 ? gl.y : gh.y, (i & 4) == 0 ? gl.z : gh.z);
            world.transform(c);
            if (!any[0]) {
                lo.set(c);
                hi.set(c);
                any[0] = true;
            } else {
                lo.x = Math.min(lo.x, c.x);
                lo.y = Math.min(lo.y, c.y);
                lo.z = Math.min(lo.z, c.z);
                hi.x = Math.max(hi.x, c.x);
                hi.y = Math.max(hi.y, c.y);
                hi.z = Math.max(hi.z, c.z);
            }
        }
    }

    /** Applies the view filter + layer visibility to a grid (for the active document). */
    private SparseMatrix<Block> filtered(SparseMatrix<Block> grid) {
        if (grid == null) {
            return new SparseMatrix<>();
        }
        SparseMatrix<Block> g = grid;
        if (StarMadeLogic.getInstance().getViewFilter() != null) {
            g = StarMadeLogic.getInstance().getViewFilter().modify(g, null, StarMadeLogic.getInstance(), null);
        }
        return StarMadeLogic.getInstance().getLayers().applyVisibility(g);
    }

    /**
     * The pickable objects, each with its world transform. {@code activeOnly} limits
     * it to the active object (used by drag continuation / cursor readout so a stroke
     * stays on the object it started on); otherwise every visible object is included.
     */
    private List<RaycastPicker.Target> sceneTargets(boolean activeOnly) {
        List<RaycastPicker.Target> out = new ArrayList<>();
        SceneModel sm = StarMadeLogic.getInstance().getSceneModel();
        Scene scene = sm != null ? sm.getScene() : null;
        SceneObject active = sm != null ? sm.getActiveObject() : null;
        if (scene != null && !scene.getObjects().isEmpty()) {
            for (SceneObject o : scene.getObjects()) {
                if (activeOnly && o != active) {
                    continue;
                }
                if (!o.isVisible() || o.getGrid() == null) {
                    continue;
                }
                Matrix4f w = new Matrix4f();
                w.set(o.getTransform());
                out.add(new RaycastPicker.Target(o, w, o.getGrid()));
            }
        } else {
            SparseMatrix<Block> g = StarMadeLogic.getModel();
            if (g != null) {
                Matrix4f id = new Matrix4f();
                id.setIdentity();
                out.add(new RaycastPicker.Target(null, id, g));
            }
        }
        return out;
    }

    @Override
    public void updateTiles() {
        updateAxis();
        updateGrid();
        mBlocks.getChildren().clear();
        mContext.getChildren().clear(); // retired: every object now renders under mBlocks
        if (mDontDraw) {
            mFilteredGrid = new SparseMatrix<>();
            mLastSortCam = null;
            updateSelectionBox();
            updateTransform();
            return;
        }
        // World-space render: every visible object as a child group of mBlocks at its
        // own world transform. mBlocks stays at identity, so the captured pick matrices
        // remain world→eye. The active object also gets the view filter / layer masks.
        SceneModel sm = StarMadeLogic.getInstance().getSceneModel();
        Scene scene = sm != null ? sm.getScene() : null;
        SceneObject active = sm != null ? sm.getActiveObject() : null;
        if (scene == null || scene.getObjects().isEmpty()) {
            mFilteredGrid = filtered(StarMadeLogic.getModel());
            LWJGLRenderLogic.addBlocks(mBlocks, mFilteredGrid, mPlainGraphics);
        } else {
            for (SceneObject o : new ArrayList<>(scene.getObjects())) {
                if (!o.isVisible() || o.getGrid() == null) {
                    continue;
                }
                boolean isActive = (o == active);
                SparseMatrix<Block> g = isActive ? filtered(o.getGrid()) : o.getGrid();
                if (isActive) {
                    mFilteredGrid = g;
                }
                JGLGroup grp = new JGLGroup();
                Matrix4f m = new Matrix4f();
                m.set(o.getTransform());
                grp.setTransform(m);
                LWJGLRenderLogic.addBlocks(grp, g, mPlainGraphics);
                mBlocks.getChildren().add(grp);
            }
        }
        // A freshly rebuilt mesh must be re-sorted even from the same camera.
        mLastSortCam = null;
        updateSelectionBox();
        updateTransform();
    }

    public void updateSelectionBox() {
        updateSelectionHighlight();
    }

    /** Local-cell offset the Move tool is previewing for a block selection (null = none). */
    private Point3i mMoveCellPreview;
    /** World-space offset the Move tool is previewing for a whole entity (null = none). */
    private Vector3f mMoveWorldPreview;

    /**
     * Previews a pending block-selection move by drawing the selection outline shifted
     * by {@code offset} (local cells). Null clears the preview. The blocks themselves
     * don't move until the gesture commits.
     */
    public void setBlockMovePreview(Point3i offset) {
        mMoveCellPreview = offset;
        mMoveWorldPreview = null;
        updateSelectionHighlight();
    }

    /** Previews a pending whole-entity move by shifting its box by {@code worldOffset}. */
    public void setEntityMovePreview(Vector3f worldOffset) {
        mMoveWorldPreview = worldOffset;
        mMoveCellPreview = null;
        updateSelectionHighlight();
    }

    /** Clears any in-progress move preview. */
    public void clearMovePreview() {
        mMoveCellPreview = null;
        mMoveWorldPreview = null;
        updateSelectionHighlight();
    }

    /** The camera's forward (view) direction in world space — the Move drag plane normal. */
    public Vector3f cameraForward() {
        return mUniverse.getCamera().getForward();
    }

    /**
     * Intersects the world ray through screen pixel (sx, sy) with the plane defined by
     * {@code planePt} and unit {@code normal}. Returns the world hit point, or null if
     * nothing has been rendered yet or the ray runs parallel to the plane.
     */
    public Point3f pickPlane(double sx, double sy, Point3f planePt, Vector3f normal) {
        float[] r = RaycastPicker.worldRay((float) sx, (float) sy, mPickMatrices.snapshot());
        if (r == null) {
            return null;
        }
        float dx = r[3] - r[0], dy = r[4] - r[1], dz = r[5] - r[2];
        float denom = dx * normal.x + dy * normal.y + dz * normal.z;
        if (Math.abs(denom) < 1e-6f) {
            return null;
        }
        float tt = ((planePt.x - r[0]) * normal.x + (planePt.y - r[1]) * normal.y
                + (planePt.z - r[2]) * normal.z) / denom;
        return new Point3f(r[0] + tt * dx, r[1] + tt * dy, r[2] + tt * dz);
    }

    // ------------------------------------------------------------------
    // Move gizmo (axis / plane manipulator for the Move tool)
    // ------------------------------------------------------------------

    /** The frame the gizmo was last drawn at, or null when it isn't shown. */
    private MoveGizmo mGizmoFrame;
    /** World length of each axis shaft (kept ~constant on screen), and plane-handle offset. */
    private float mGizmoLen;
    private float mGizmoPlaneOff;
    /** The handle under the cursor (hover) and the one being dragged (active); highlighted when drawn. */
    private MoveGizmo.Handle mGizmoHover;
    private MoveGizmo.Handle mGizmoActive;
    /** Fraction of the axis length used as the plane handle's inset and square size. */
    private static final float GIZMO_PLANE_INSET = 0.32f;
    private static final float GIZMO_PLANE_SIZE = 0.16f;

    private static final Color3f[] GIZMO_AXIS_COLORS = {AXIS_X, AXIS_Y, AXIS_Z};
    /** The colour a hovered / grabbed handle is drawn in. */
    private static final Color3f GIZMO_HIGHLIGHT = new Color3f(1.00f, 0.85f, 0.20f);

    /** The world ray through screen pixel (sx, sy): {nearXYZ, farXYZ}, or null. */
    public float[] worldRay(double sx, double sy) {
        return RaycastPicker.worldRay((float) sx, (float) sy, mPickMatrices.snapshot());
    }

    /** Sets the handle the cursor is hovering (null = none); rebuilds the gizmo if it changed. */
    public void setGizmoHover(MoveGizmo.Handle hover) {
        if (hover != mGizmoHover) {
            mGizmoHover = hover;
            updateGizmo();
        }
    }

    /** Sets the handle currently being dragged (null = none) so it stays highlighted. */
    public void setGizmoActive(MoveGizmo.Handle active) {
        if (active != mGizmoActive) {
            mGizmoActive = active;
            updateGizmo();
        }
    }

    /** The axis colour, or the highlight colour when {@code h} is the hovered/grabbed handle. */
    private Color3f gizmoColor(MoveGizmo.Handle h, Color3f base) {
        MoveGizmo.Handle lit = mGizmoActive != null ? mGizmoActive : mGizmoHover;
        return h == lit ? GIZMO_HIGHLIGHT : base;
    }

    /** The current gizmo frame (centre + world axes), or null when the gizmo isn't shown. */
    public MoveGizmo getGizmoFrame() {
        return mGizmoFrame;
    }

    /**
     * Rebuilds the Move gizmo: three colour-coded axis arrows and three plane handles
     * at the move target's centre, drawn on top of the scene and scaled with camera
     * distance so it holds a roughly constant on-screen size. Empty for every tool but
     * Move, or when there's nothing to move.
     */
    public void updateGizmo() {
        MoveGizmo g = smc.smedit.ui.tool.ToolController.get().getActive() == smc.smedit.ui.tool.EditorTool.MOVE
                ? MoveGizmo.forCurrent() : null;
        if (g == null) {
            mGizmoFrame = null;
            synchronized (mGizmo) {
                mGizmo.getChildren().clear();
            }
            return;
        }
        Point3f c = g.center();
        Point3f cam = mUniverse.getCamera().getLocation();
        float dist = (float) Math.sqrt((cam.x - c.x) * (cam.x - c.x)
                + (cam.y - c.y) * (cam.y - c.y) + (cam.z - c.z) * (cam.z - c.z));
        float len = Math.min(256f, Math.max(2f, dist * 0.14f));
        mGizmoFrame = g;
        mGizmoLen = len;
        mGizmoPlaneOff = len * GIZMO_PLANE_INSET;

        List<Point3f> verts = new ArrayList<>();
        List<Color3f> cols = new ArrayList<>();
        float head = len * 0.16f;
        MoveGizmo.Handle[] axisHandles = {MoveGizmo.Handle.X, MoveGizmo.Handle.Y, MoveGizmo.Handle.Z};
        MoveGizmo.Handle[] planeHandles = {MoveGizmo.Handle.PLANE_X, MoveGizmo.Handle.PLANE_Y, MoveGizmo.Handle.PLANE_Z};
        for (int i = 0; i < 3; i++) {
            Vector3f a = g.axis(i);
            Color3f col = gizmoColor(axisHandles[i], GIZMO_AXIS_COLORS[i]);
            Point3f tip = new Point3f(c.x + a.x * len, c.y + a.y * len, c.z + a.z * len);
            LWJGLRenderLogic.addLine(verts, cols, new Point3f(c), tip, col);
            // A little arrowhead: two short back-swept lines in the plane of the other axes.
            Vector3f b = g.axis((i + 1) % 3);
            Point3f base = new Point3f(tip.x - a.x * head, tip.y - a.y * head, tip.z - a.z * head);
            LWJGLRenderLogic.addLine(verts, cols, tip,
                    new Point3f(base.x + b.x * head * 0.5f, base.y + b.y * head * 0.5f, base.z + b.z * head * 0.5f), col);
            LWJGLRenderLogic.addLine(verts, cols, tip,
                    new Point3f(base.x - b.x * head * 0.5f, base.y - b.y * head * 0.5f, base.z - b.z * head * 0.5f), col);
        }
        // Plane handles: a small square outline in each axis plane, coloured by the
        // plane's normal axis, inset from the centre along the two in-plane axes.
        float off = mGizmoPlaneOff;
        float sz = len * GIZMO_PLANE_SIZE;
        for (int n = 0; n < 3; n++) {
            Vector3f u = g.axis((n + 1) % 3);
            Vector3f v = g.axis((n + 2) % 3);
            Color3f col = gizmoColor(planeHandles[n], GIZMO_AXIS_COLORS[n]);
            Point3f p00 = planePoint(c, u, v, off, off);
            Point3f p10 = planePoint(c, u, v, off + sz, off);
            Point3f p11 = planePoint(c, u, v, off + sz, off + sz);
            Point3f p01 = planePoint(c, u, v, off, off + sz);
            LWJGLRenderLogic.addLine(verts, cols, p00, p10, col);
            LWJGLRenderLogic.addLine(verts, cols, p10, p11, col);
            LWJGLRenderLogic.addLine(verts, cols, p11, p01, col);
            LWJGLRenderLogic.addLine(verts, cols, p01, p00, col);
        }
        JGLObj obj = LWJGLRenderLogic.linesToObj(verts, cols);
        synchronized (mGizmo) {
            mGizmo.getChildren().clear();
            if (obj != null) {
                obj.setOnTop(true);   // never occluded, so handles stay grabbable
                mGizmo.add(obj);
            }
        }
    }

    private static Point3f planePoint(Point3f c, Vector3f u, Vector3f v, float du, float dv) {
        return new Point3f(c.x + u.x * du + v.x * dv,
                c.y + u.y * du + v.y * dv, c.z + u.z * du + v.z * dv);
    }

    /**
     * Which gizmo handle the pixel (sx, sy) is over, or null for none. Axis shafts are
     * tested as screen-space line segments; plane handles as their projected centre.
     * Planes win ties (they sit near the centre where the axes also cross).
     */
    public MoveGizmo.Handle pickMoveHandle(double sx, double sy) {
        MoveGizmo g = mGizmoFrame;
        PickMatrices.Snapshot snap = mPickMatrices.snapshot();
        if (g == null || snap == null) {
            return null;
        }
        Point3f c = g.center();
        float[] pc = RaycastPicker.project(c.x, c.y, c.z, snap);
        if (pc == null || pc[2] > 1f) {
            return null;
        }
        // Plane handles first: their projected centre within a small radius.
        float off = mGizmoPlaneOff + mGizmoLen * GIZMO_PLANE_SIZE * 0.5f;
        MoveGizmo.Handle bestPlane = null;
        double bestPlaneD = 14.0;   // px
        MoveGizmo.Handle[] planes = {MoveGizmo.Handle.PLANE_X, MoveGizmo.Handle.PLANE_Y, MoveGizmo.Handle.PLANE_Z};
        for (int n = 0; n < 3; n++) {
            Vector3f u = g.axis((n + 1) % 3);
            Vector3f v = g.axis((n + 2) % 3);
            Point3f h = planePoint(c, u, v, off, off);
            float[] ph = RaycastPicker.project(h.x, h.y, h.z, snap);
            if (ph == null || ph[2] > 1f) {
                continue;
            }
            double d = Math.hypot(ph[0] - sx, ph[1] - sy);
            if (d < bestPlaneD) {
                bestPlaneD = d;
                bestPlane = planes[n];
            }
        }
        if (bestPlane != null) {
            return bestPlane;
        }
        // Axis shafts: nearest projected segment within the pick threshold.
        MoveGizmo.Handle[] axes = {MoveGizmo.Handle.X, MoveGizmo.Handle.Y, MoveGizmo.Handle.Z};
        MoveGizmo.Handle best = null;
        double bestD = 9.0;   // px
        for (int i = 0; i < 3; i++) {
            Vector3f a = g.axis(i);
            Point3f tip = new Point3f(c.x + a.x * mGizmoLen, c.y + a.y * mGizmoLen, c.z + a.z * mGizmoLen);
            float[] pt = RaycastPicker.project(tip.x, tip.y, tip.z, snap);
            if (pt == null || pt[2] > 1f) {
                continue;
            }
            double d = segmentDist(sx, sy, pc[0], pc[1], pt[0], pt[1]);
            if (d < bestD) {
                bestD = d;
                best = axes[i];
            }
        }
        return best;
    }

    /** Distance from point (px,py) to the segment (ax,ay)-(bx,by), in pixels. */
    private static double segmentDist(double px, double py, double ax, double ay, double bx, double by) {
        double vx = bx - ax, vy = by - ay;
        double wx = px - ax, wy = py - ay;
        double len2 = vx * vx + vy * vy;
        double t = len2 <= 1e-9 ? 0 : (wx * vx + wy * vy) / len2;
        t = Math.max(0, Math.min(1, t));
        double cx = ax + t * vx, cy = ay + t * vy;
        return Math.hypot(px - cx, py - cy);
    }

    private static final short[] SELECT_FACE_COLORS = {
        BlockGroups.SPECIAL_SELECT_XP, BlockGroups.SPECIAL_SELECT_XM,
        BlockGroups.SPECIAL_SELECT_YP, BlockGroups.SPECIAL_SELECT_YM,
        BlockGroups.SPECIAL_SELECT_ZP, BlockGroups.SPECIAL_SELECT_ZM,
    };

    /** Rebuilds the wireframe outline tracing the exact selected cells. */
    public void updateSelectionHighlight() {
        SelectionModel selection = StarMadeLogic.getInstance().getSelection();
        java.util.List<Point3i> selected = selection.getSelected();
        SceneModel sm = StarMadeLogic.getInstance().getSceneModel();
        SceneObject active = sm != null ? sm.getActiveObject() : null;
        MeshInfo info = new MeshInfo();
        info.verts = new ArrayList<>();
        info.indexes = new ArrayList<>();
        info.colors = new ArrayList<>(); // plain-coloured, not textured
        if (selection.getMode() == SelectionModel.Mode.ENTITY && active != null
                && active.getGrid().size() > 0) {
            // Whole entity selected: a single frame around the entity's extent reads
            // cleaner than tracing every block, so draw one box from the grid bounds.
            Point3i lo = new Point3i();
            Point3i hi = new Point3i();
            active.getGrid().getBounds(lo, hi);
            LWJGLRenderLogic.addSelectionBox(info, lo, hi, SELECT_FACE_COLORS, 0.03f);
        } else if (!selected.isEmpty()) {
            // Outline the true shape of the selection: every cell's outward-facing
            // faces (culling shared interior faces), inflated a touch so the edges
            // don't z-fight the enclosed blocks. Irregular selections (flood-fill,
            // scattered multi-pick) now trace exactly, not a min/max bounding box.
            java.util.Collection<Point3i> cells = selected;
            if (mMoveCellPreview != null) {
                // Move tool drag: draw the outline at the destination the blocks would
                // land on, so the frame previews the move before it commits on release.
                java.util.List<Point3i> shifted = new ArrayList<>(selected.size());
                for (Point3i p : selected) {
                    shifted.add(new Point3i(p.x + mMoveCellPreview.x,
                            p.y + mMoveCellPreview.y, p.z + mMoveCellPreview.z));
                }
                cells = shifted;
            }
            LWJGLRenderLogic.addSelectionCells(info, cells, SELECT_FACE_COLORS, 0.03f);
        }
        JGLObj obj = null;
        if (!info.verts.isEmpty()) {
            obj = LWJGLRenderLogic.infoToObj(info);
            // Wireframe so it reads as an empty frame and never hides the blocks
            // (the axis-coloured edges also hint at orientation).
            obj.setWireframe(true);
        }
        // Selected cells are in the ACTIVE object's local grid space, so the highlight
        // group carries that object's world transform — otherwise it would draw at the
        // origin while a translated/rotated entity sits elsewhere.
        Matrix4f t = new Matrix4f();
        if (active != null) {
            t.set(active.getTransform());
        } else {
            t.setIdentity();
        }
        if (mMoveWorldPreview != null) {
            // Whole-entity move drag: shift the box in world space to preview where the
            // entity will sit, while its blocks stay put until the move commits.
            t.m03 += mMoveWorldPreview.x;
            t.m13 += mMoveWorldPreview.y;
            t.m23 += mMoveWorldPreview.z;
        }
        synchronized (mSelection) {
            mSelection.setTransform(t);
            mSelection.getChildren().clear();
            if (obj != null) {
                mSelection.add(obj);
            }
        }
    }

    /**
     * Rebuilds the axis guide as three thin coloured lines (X red, Y green, Z blue)
     * crossing the current anchor point. Cheap 2D-line geometry, not boxes.
     */
    public void updateAxis() {
        Point3f o = guideOrigin();
        float r = guideReach();
        List<Point3f> verts = new ArrayList<>();
        List<Color3f> cols = new ArrayList<>();
        LWJGLRenderLogic.addLine(verts, cols,
                new Point3f(o.x - r, o.y, o.z), new Point3f(o.x + r, o.y, o.z), AXIS_X);
        LWJGLRenderLogic.addLine(verts, cols,
                new Point3f(o.x, o.y - r, o.z), new Point3f(o.x, o.y + r, o.z), AXIS_Y);
        LWJGLRenderLogic.addLine(verts, cols,
                new Point3f(o.x, o.y, o.z - r), new Point3f(o.x, o.y, o.z + r), AXIS_Z);
        JGLObj obj = LWJGLRenderLogic.linesToObj(verts, cols);
        synchronized (mAxis) {
            mAxis.getChildren().clear();
            if (obj != null) {
                mAxis.add(obj);
            }
        }
    }

    /**
     * Rebuilds the ground grid as 2D lines on the horizontal (Y) plane through the
     * anchor point, spanning the model extent. Empty (nothing drawn) when the grid
     * is toggled off. Spacing coarsens for very large models to cap the line count.
     */
    public void updateGrid() {
        List<Point3f> verts = new ArrayList<>();
        List<Color3f> cols = new ArrayList<>();
        if (mShowGrid) {
            Point3f o = guideOrigin();
            float r = guideReach();
            int minX = (int) Math.floor(o.x - r), maxX = (int) Math.ceil(o.x + r);
            int minZ = (int) Math.floor(o.z - r), maxZ = (int) Math.ceil(o.z + r);
            int span = Math.max(maxX - minX, maxZ - minZ);
            int step = Math.max(1, (int) Math.ceil(span / 100.0)); // ≤ ~100 lines per direction
            float y = o.y;
            for (int x = minX; x <= maxX; x += step) {
                LWJGLRenderLogic.addLine(verts, cols,
                        new Point3f(x, y, minZ), new Point3f(x, y, maxZ), GRID_COLOR);
            }
            for (int z = minZ; z <= maxZ; z += step) {
                LWJGLRenderLogic.addLine(verts, cols,
                        new Point3f(minX, y, z), new Point3f(maxX, y, z), GRID_COLOR);
            }
        }
        JGLObj obj = LWJGLRenderLogic.linesToObj(verts, cols);
        synchronized (mGrid) {
            mGrid.getChildren().clear();
            if (obj != null) {
                mGrid.add(obj);
            }
        }
    }

    /** The point the axis/grid guide is centred on, per the current anchor. */
    private Point3f guideOrigin() {
        if (mAxisAnchor == AxisAnchor.SELECTION) {
            List<Point3i> sel = StarMadeLogic.getInstance().getSelection().getSelected();
            if (!sel.isEmpty()) {
                return centerOf(sel);
            }
        }
        Point3f lo = new Point3f();
        Point3f hi = new Point3f();
        if (sceneWorldBounds(lo, hi)) {
            return new Point3f((lo.x + hi.x) / 2f, (lo.y + hi.y) / 2f, (lo.z + hi.z) / 2f);
        }
        return new Point3f(0, 0, 0);
    }

    /** Half-length of the axis lines / grid, scaled to the whole scene with sane clamps. */
    private float guideReach() {
        Point3f lo = new Point3f();
        Point3f hi = new Point3f();
        if (sceneWorldBounds(lo, hi)) {
            float extent = Math.max(hi.x - lo.x, Math.max(hi.y - lo.y, hi.z - lo.z));
            return Math.min(512f, Math.max(16f, extent / 2f + 8f));
        }
        return 16f;
    }

    private static Point3f centerOf(List<Point3i> cells) {
        int minx = Integer.MAX_VALUE, miny = Integer.MAX_VALUE, minz = Integer.MAX_VALUE;
        int maxx = Integer.MIN_VALUE, maxy = Integer.MIN_VALUE, maxz = Integer.MIN_VALUE;
        for (Point3i p : cells) {
            minx = Math.min(minx, p.x); miny = Math.min(miny, p.y); minz = Math.min(minz, p.z);
            maxx = Math.max(maxx, p.x); maxy = Math.max(maxy, p.y); maxz = Math.max(maxz, p.z);
        }
        return new Point3f((minx + maxx) / 2f, (miny + maxy) / 2f, (minz + maxz) / 2f);
    }

    /** Selection change: refresh the highlight, and the guide too if it follows the selection. */
    private void onSelectionChanged() {
        updateSelectionHighlight();
        updateGizmo();   // the move gizmo centres on the selection, so follow it
        if (mAxisAnchor == AxisAnchor.SELECTION) {
            updateAxis();
            updateGrid();
        }
    }

    @Override
    public RenderPoly getTileAt(double x, double y) {
        Point3i p = getPointAt(x, y);
        if (p == null) {
            return null;
        }
        Block b = StarMadeLogic.getModel().get(p);
        if (b == null) {
            return null;
        }
        RenderPoly tile = new RenderPoly();
        tile.setBlock(b);
        tile.setPosition(p);
        return tile;
    }

    @Override
    public Block getBlockAt(double x, double y) {
        Point3i p = getPointAt(x, y);
        if (p == null) {
            return null;
        }
        return StarMadeLogic.getModel().get(p);
    }

    public Point3i getPointAt(double x, double y) {
        // (x, y) are LWJGL window coords (origin bottom-left). Ray-cast the ACTIVE
        // object only, in its own local space — this drives drag continuation and the
        // cursor readout, which must stay on the object a stroke started on. Use
        // {@link #pickScene} to resolve a click against every entity.
        PickResult pr = RaycastPicker.pickScene((float) x, (float) y,
                mPickMatrices.snapshot(), sceneTargets(true));
        return pr == null ? null : pr.cell;
    }

    /** Resolves a click against every visible entity: which object + local cell it hit. */
    public PickResult pickScene(double x, double y) {
        return RaycastPicker.pickScene((float) x, (float) y,
                mPickMatrices.snapshot(), sceneTargets(false));
    }

    /**
     * Makes the picked object the edit target (active document) if it isn't already.
     * Until the unified scene-level undo lands, switching entities clears the undo
     * history so a Ctrl+Z can't restore one object's snapshot onto another.
     */
    public void focusPickedObject(PickResult pr) {
        if (pr == null || pr.object == null) {
            return;
        }
        SceneModel sm = StarMadeLogic.getInstance().getSceneModel();
        if (sm == null || sm.isActive(pr.object)) {
            return;
        }
        mSuppressReframe = true;
        try {
            sm.setActive(pr.object);
        } finally {
            mSuppressReframe = false;
        }
        if (mUndoer != null) {
            mUndoer.clear();
        }
    }

    // ------------------------------------------------------------------
    // Marquee (drag-box) selection
    // ------------------------------------------------------------------

    /** The selection captured when an additive drag-box began; empty otherwise. */
    private List<Point3i> mMarqueeBase = Collections.emptyList();

    /**
     * Begins a drag-box selection. An additive drag (Shift/Ctrl) unions with the
     * selection that existed when the drag started; a plain drag replaces it.
     */
    public void beginMarquee(boolean additive) {
        mMarqueeBase = additive
                ? StarMadeLogic.getInstance().getSelection().getSelected()
                : Collections.emptyList();
    }

    /**
     * Selects every block whose centre projects inside the screen rectangle spanned
     * by (x0,y0)-(x1,y1) — LWJGL window coords (origin bottom-left, matching the
     * synthesized mouse events). Selects through occluders, so the marquee grabs the
     * whole framed region, not just the visible skin.
     */
    public void updateMarquee(int x0, int y0, int x1, int y1) {
        PickMatrices.Snapshot snap = mPickMatrices.snapshot();
        SparseMatrix<Block> grid = StarMadeLogic.getModel();
        if (snap == null || grid == null) {
            return;
        }
        int minX = Math.min(x0, x1), maxX = Math.max(x0, x1);
        int minY = Math.min(y0, y1), maxY = Math.max(y0, y1);
        FloatBuffer mv = wrap16(snap.modelview);
        FloatBuffer proj = wrap16(snap.projection);
        IntBuffer vp = BufferUtils.createIntBuffer(16);
        vp.put(snap.viewport).flip();
        FloatBuffer win = BufferUtils.createFloatBuffer(3);
        LinkedHashSet<Point3i> inside = new LinkedHashSet<>(mMarqueeBase);
        for (Iterator<Point3i> it = grid.iteratorNonNull(); it.hasNext();) {
            Point3i p = it.next();
            win.clear();
            if (!GLU.gluProject(p.x, p.y, p.z, mv, proj, vp, win)) {
                continue;
            }
            float wz = win.get(2);
            if (wz < 0f || wz > 1f) {
                continue; // behind the eye or past the far plane
            }
            float wx = win.get(0), wy = win.get(1);
            if (wx >= minX && wx <= maxX && wy >= minY && wy <= maxY) {
                inside.add(new Point3i(p));
            }
        }
        StarMadeLogic.getInstance().getSelection().select(inside);
    }

    /** Ends a drag-box selection, releasing the captured base selection. */
    public void endMarquee() {
        mMarqueeBase = Collections.emptyList();
    }

    private static FloatBuffer wrap16(float[] a) {
        FloatBuffer b = BufferUtils.createFloatBuffer(16);
        b.put(a).flip();
        return b;
    }

    /**
     * Gives the GL canvas keyboard focus so the LWJGL keyboard (fly-camera keys)
     * starts reporting events. Called on a viewport click — clicking to interact
     * is the natural point to claim focus.
     */
    public void requestViewportFocus() {
        mCanvas.requestFocusInWindow();
    }

    @Override
    public Point3i getPlacementAt(double x, double y) {
        // Placement cell for Build, in the ACTIVE object's local space (see getPointAt).
        PickResult pr = RaycastPicker.pickScene((float) x, (float) y,
                mPickMatrices.snapshot(), sceneTargets(true));
        return pr == null ? null : pr.place;
    }

    @Override
    public boolean isPlainGraphics() {
        return mPlainGraphics;
    }

    @Override
    public void setPlainGraphics(boolean plainGraphics) {
        mPlainGraphics = plainGraphics;
    }

    @Override
    public boolean isAxis() {
        return !mAxis.isCull();
    }

    @Override
    public void setAxis(boolean axis) {
        mAxis.setCull(!axis);
        updateAxis();
    }

    @Override
    public AxisAnchor getAxisAnchor() {
        return mAxisAnchor;
    }

    @Override
    public void setAxisAnchor(AxisAnchor anchor) {
        if (anchor != null && anchor != mAxisAnchor) {
            mAxisAnchor = anchor;
            updateAxis();
            updateGrid();
        }
    }

    @Override
    public boolean isGrid() {
        return mShowGrid;
    }

    @Override
    public void setGrid(boolean grid) {
        mShowGrid = grid;
        updateGrid();
    }

    @Override
    public UndoBuffer getUndoer() {
        return mUndoer;
    }

    @Override
    public void setUndoer(UndoBuffer undoer) {
        mUndoer = undoer;
    }

    @Override
    public void undo() {
        restore(mUndoer.undo(StarMadeLogic.getModel()));
    }

    @Override
    public void redo() {
        restore(mUndoer.redo(StarMadeLogic.getModel()));
    }

    /**
     * Installs a grid restored by undo/redo. Replaces the live grid's contents
     * <em>in place</em> and rebuilds the mesh directly, rather than going through
     * {@link StarMadeLogic#setModel} — that fires the "model" change that re-frames
     * the camera (right for loading a file, wrong for an undo, which must leave the
     * view exactly where it is).
     */
    private void restore(SparseMatrix<Block> grid) {
        if (grid == null) {
            return;
        }
        SparseMatrix<Block> current = StarMadeLogic.getModel();
        if (current != null) {
            current.set(grid);
            updateTiles();
        } else {
            StarMadeLogic.setModel(grid);
        }
    }

    @Override
    public void setCloseRequested(boolean pleaseClose) {
        mCanvas.setCloseRequested(pleaseClose);
    }

    public void moveCamera(Point3i delta) {
        mUniverse.getCamera().moveRight(delta.x);
        mUniverse.getCamera().moveUp(delta.y);
        mUniverse.getCamera().moveForward(delta.z);
        //System.out.println("After moveCamera=\n"+mUniverse.getCamera());
    }

    public void rotateCamera(Point3i delta) {
        //System.out.println("Before rotate:\n"+mUniverse.getCamera());
        mUniverse.getCamera().yaw(delta.x * LWJGLMouseAdapter.PIXEL_TO_RADIANS);
        mUniverse.getCamera().pitch(delta.y * LWJGLMouseAdapter.PIXEL_TO_RADIANS);
        mUniverse.getCamera().roll(delta.z * LWJGLMouseAdapter.PIXEL_TO_RADIANS);
        //System.out.println("After rotate "+delta+":\n"+mUniverse.getCamera());
    }

    @Override
    public boolean isDontDraw() {
        return mDontDraw;
    }

    @Override
    public void setDontDraw(boolean dontDraw) {
        mDontDraw = dontDraw;
        updateTiles();
    }

    @Override
    public synchronized void addMouseListener(MouseListener l) {
        mCanvas.addMouseListener(l);
    }

    @Override
    public synchronized void removeMouseListener(MouseListener l) {
        mCanvas.removeMouseListener(l);
    }
}
