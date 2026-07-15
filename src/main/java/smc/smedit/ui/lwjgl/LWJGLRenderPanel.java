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
import java.util.ArrayList;
import java.util.List;

import smc.smedit.data.BlockTypes;
import smc.smedit.data.RenderPoly;
import smc.smedit.data.SparseMatrix;
import smc.smedit.data.UndoBuffer;
import smc.smedit.logic.StarMadeLogic;
import smc.smedit.ship.data.Block;
import smc.smedit.ui.RenderPanel;
import smc.smedit.util.jgl.obj.JGLCamera;
import smc.smedit.util.jgl.obj.JGLGroup;
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
    private final JGLGroup mSelection;
    private final JGLGroup mAxis;
    private final JGLGroup mGrid;

    /** Where the axis/grid guide is centred. */
    private AxisAnchor mAxisAnchor = AxisAnchor.SCENE;
    private boolean mShowGrid;

    /** How the mouse drives the camera. */
    public enum CameraMode {
        /** Turntable: right-drag swings around the ship centre. */
        ORBIT,
        /** Free-look: right-drag turns the camera in place (WASD to fly). */
        FIRST_PERSON
    }

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
    /** The transparent mesh last sorted, so a rebuilt mesh is always re-sorted even from the same camera. */
    private JGLObj mLastSortedObj;
    /** GL matrices of the block group, captured each frame for screen->world picking. */
    private final PickMatrices mPickMatrices = new PickMatrices();

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
        mSelection = new JGLGroup();
        mUniverse.getChildren().add(mSelection);
        mAxis = new JGLGroup();
        mUniverse.getChildren().add(mAxis);
        mGrid = new JGLGroup();
        mUniverse.getChildren().add(mGrid);
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
        StarMadeLogic.getInstance().addPropertyChangeListener("model", ev -> setLookAt(new Point3f(0, 0, -1)));
        // Refresh the viewport highlight whenever the selection changes (and the
        // axis/grid too, when they're anchored to the selection).
        StarMadeLogic.getInstance().getSelection().addListener(this::onSelectionChanged);
    }

    public CameraMode getCameraMode() {
        return mCameraMode;
    }

    public void setCameraMode(CameraMode mode) {
        if (mode != null) {
            mCameraMode = mode;
        }
    }

    /** Flips between orbit (turntable) and first-person (free-look) control. */
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
    }

    /**
     * Re-sorts the glass/transparent mesh back-to-front for the current camera so
     * it blends correctly. Skipped when the camera has barely moved since the last
     * sort (keeps dragging cheap); the order only needs updating as the view swings.
     */
    private void sortTransparent(Point3f cam) {
        JGLObj transparent = null;
        for (JGLNode c : mBlocks.getChildren()) {
            if (c instanceof JGLObj && ((JGLObj) c).isTransparent()) {
                transparent = (JGLObj) c;
                break;
            }
        }
        if (transparent == null) {
            mLastSortCam = null;
            mLastSortedObj = null;
            return;
        }
        if (transparent == mLastSortedObj && mLastSortCam != null) {
            float mx = cam.x - mLastSortCam.x, my = cam.y - mLastSortCam.y, mz = cam.z - mLastSortCam.z;
            if (mx * mx + my * my + mz * mz < 0.25f) { // moved < 0.5 world units
                return;
            }
        }
        LWJGLRenderLogic.sortTransparentQuads(transparent, cam);
        mLastSortedObj = transparent;
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

    /** Average of all block positions; falls back to the bounding-box centre if empty. */
    private Point3f modelCentroid(Point3i lower, Point3i upper) {
        SparseMatrix<Block> grid = StarMadeLogic.getModel();
        double sx = 0, sy = 0, sz = 0;
        long n = 0;
        for (java.util.Iterator<Point3i> it = grid.iteratorNonNull(); it.hasNext();) {
            Point3i p = it.next();
            sx += p.x;
            sy += p.y;
            sz += p.z;
            n++;
        }
        if (n == 0) {
            return new Point3f((upper.x + lower.x) / 2f, (upper.y + lower.y) / 2f, (upper.z + lower.z) / 2f);
        }
        return new Point3f((float) (sx / n), (float) (sy / n), (float) (sz / n));
    }

    public void setLookAt(Point3f axis) {
        Point3i lower = new Point3i();
        Point3i upper = new Point3i();
        StarMadeLogic.getModel().getBounds(lower, upper);
        // Orbit/look at the ship's CENTROID (average block position), not the
        // bounding-box centre — a long thin protrusion (nose, antenna) skews the
        // box centre well off the ship's actual mass, which makes orbit feel
        // off-centre.
        Point3f lookAtThis = modelCentroid(lower, upper);
        mOrbitCenter = new Point3f(lookAtThis);
        float maxModel = Math.max(Math.max(upper.x - lower.x, upper.y - lower.y), upper.z - lower.z) + 1;
        Point3f standHere = new Point3f(axis);
        standHere.scale(maxModel * 2);
        standHere.add(lookAtThis);
        //mUniverse.getCamera().setLocation(lookAtThis);
        mUniverse.getCamera().lookAt(standHere, lookAtThis);
        //mUniverse.getCamera().scale(mScale);
        System.out.println("Standing at " + standHere + ", looking at " + lookAtThis);
        //mUniverse.setTransformer(new SpinningTransformer(new Point3f(0, 20, 0)));
        updateTiles();
    }

    @Override
    public void updateTiles() {
        if (mDontDraw) {
            mFilteredGrid = new SparseMatrix<>();
        } else if (StarMadeLogic.getInstance().getViewFilter() == null) {
            mFilteredGrid = StarMadeLogic.getModel();
        } else {
            mFilteredGrid = StarMadeLogic.getInstance().getViewFilter().modify(StarMadeLogic.getModel(), null, StarMadeLogic.getInstance(), null);
        }
        updateAxis();
        updateGrid();
        mBlocks.getChildren().clear();
        LWJGLRenderLogic.addBlocks(mBlocks, mFilteredGrid, mPlainGraphics);
        System.out.println("Quads:" + mBlocks.getChildren().size());
        updateSelectionBox();
        // Depth-sort the freshly built glass mesh for the current camera.
        updateTransform();
    }

    public void updateSelectionBox() {
        updateSelectionHighlight();
    }

    private static final short[] SELECT_FACE_COLORS = {
        BlockTypes.SPECIAL_SELECT_XP, BlockTypes.SPECIAL_SELECT_XM,
        BlockTypes.SPECIAL_SELECT_YP, BlockTypes.SPECIAL_SELECT_YM,
        BlockTypes.SPECIAL_SELECT_ZP, BlockTypes.SPECIAL_SELECT_ZM,
    };

    /** Rebuilds the wireframe outline tracing the exact selected cells. */
    public void updateSelectionHighlight() {
        java.util.List<Point3i> selected = StarMadeLogic.getInstance().getSelection().getSelected();
        MeshInfo info = new MeshInfo();
        info.verts = new ArrayList<>();
        info.indexes = new ArrayList<>();
        info.colors = new ArrayList<>(); // plain-coloured, not textured
        if (!selected.isEmpty()) {
            // Outline the true shape of the selection: every cell's outward-facing
            // faces (culling shared interior faces), inflated a touch so the edges
            // don't z-fight the enclosed blocks. Irregular selections (flood-fill,
            // scattered multi-pick) now trace exactly, not a min/max bounding box.
            LWJGLRenderLogic.addSelectionCells(info, selected, SELECT_FACE_COLORS, 0.03f);
        }
        JGLObj obj = null;
        if (!info.verts.isEmpty()) {
            obj = LWJGLRenderLogic.infoToObj(info);
            // Wireframe so it reads as an empty frame and never hides the blocks
            // (the axis-coloured edges also hint at orientation).
            obj.setWireframe(true);
        }
        synchronized (mSelection) {
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
        SparseMatrix<Block> grid = StarMadeLogic.getModel();
        if (grid != null && grid.size() > 0) {
            Point3i lo = new Point3i();
            Point3i hi = new Point3i();
            grid.getBounds(lo, hi);
            return new Point3f((lo.x + hi.x) / 2f, (lo.y + hi.y) / 2f, (lo.z + hi.z) / 2f);
        }
        return new Point3f(0, 0, 0);
    }

    /** Half-length of the axis lines / grid, scaled to the model with sane clamps. */
    private float guideReach() {
        SparseMatrix<Block> grid = StarMadeLogic.getModel();
        if (grid != null && grid.size() > 0) {
            Point3i lo = new Point3i();
            Point3i hi = new Point3i();
            grid.getBounds(lo, hi);
            int extent = Math.max(hi.x - lo.x, Math.max(hi.y - lo.y, hi.z - lo.z));
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
        // (x, y) are LWJGL window coords (origin bottom-left), matching what the
        // GL viewport/gluUnProject expect. Ray-cast into the voxel grid.
        return RaycastPicker.pick((float) x, (float) y, mPickMatrices.snapshot(), StarMadeLogic.getModel());
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
        RaycastPicker.Hit hit = RaycastPicker.pickHit((float) x, (float) y,
                mPickMatrices.snapshot(), StarMadeLogic.getModel());
        return hit == null ? null : hit.place;
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
