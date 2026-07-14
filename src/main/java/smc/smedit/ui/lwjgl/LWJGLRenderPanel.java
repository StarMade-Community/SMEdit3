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
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseListener;
import java.util.ArrayList;

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
        mCanvas = new JGLCanvas();
        mCanvas.setScene(mScene);
        setLayout(new BorderLayout());
        add("Center", mCanvas);
        MouseAdapter ma = new LWJGLMouseAdapter(this);
        mCanvas.addMouseListener(ma);
        mCanvas.addMouseMotionListener(ma);
        mCanvas.addMouseWheelListener(ma);
//        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(
//        		new LWJGLKeyEventDispatcher(this));
        mCanvas.addKeyListener(new LWJGLKeyEventDispatcher(this));
        StarMadeLogic.getInstance().addPropertyChangeListener("model", ev -> setLookAt(new Point3f(0, 0, -1)));
        // Refresh the viewport highlight whenever the selection changes.
        StarMadeLogic.getInstance().getSelection().addListener(this::updateSelectionHighlight);
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

    /** Rebuilds the empty box frame around the current selection's extent. */
    public void updateSelectionHighlight() {
        java.util.List<Point3i> selected = StarMadeLogic.getInstance().getSelection().getSelected();
        MeshInfo info = new MeshInfo();
        info.verts = new ArrayList<>();
        info.indexes = new ArrayList<>();
        info.colors = new ArrayList<>(); // plain-coloured, not textured
        if (!selected.isEmpty()) {
            // One frame around the whole selection's bounding extent, inflated a
            // touch so its edges don't z-fight the enclosed blocks.
            int minx = Integer.MAX_VALUE, miny = Integer.MAX_VALUE, minz = Integer.MAX_VALUE;
            int maxx = Integer.MIN_VALUE, maxy = Integer.MIN_VALUE, maxz = Integer.MIN_VALUE;
            for (Point3i p : selected) {
                minx = Math.min(minx, p.x); miny = Math.min(miny, p.y); minz = Math.min(minz, p.z);
                maxx = Math.max(maxx, p.x); maxy = Math.max(maxy, p.y); maxz = Math.max(maxz, p.z);
            }
            final float e = 0.03f;
            LWJGLRenderLogic.addBox(info,
                    new Point3f(minx - e, miny - e, minz - e),
                    new Point3f(maxx + e, maxy + e, maxz + e),
                    SELECT_FACE_COLORS);
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

    public void updateAxis() {
        mAxis.getChildren().clear();
        MeshInfo info = new MeshInfo();
        info.verts = new ArrayList<>();
        info.indexes = new ArrayList<>();
        //info.colors = new ArrayList<Color3f>();
        info.uv = new ArrayList<>();
        System.out.println("Adding axis");
//        LWJGLRenderLogic.addBox(info, new Point3f(9,8,8), new Point3f(256+8,8,8), new short[] { BlockTypes.SPECIAL_SELECT_XP });
//        LWJGLRenderLogic.addBox(info, new Point3f(8-256,8,8), new Point3f(7,8,8), new short[] { BlockTypes.SPECIAL_SELECT_XM });
//        LWJGLRenderLogic.addBox(info, new Point3f(8,9,8), new Point3f(8,256+8,8), new short[] { BlockTypes.SPECIAL_SELECT_YP });
//        LWJGLRenderLogic.addBox(info, new Point3f(8,8-256,8), new Point3f(8,7,8), new short[] { BlockTypes.SPECIAL_SELECT_YM });
//        LWJGLRenderLogic.addBox(info, new Point3f(8,8,9), new Point3f(8,8,256+8), new short[] { BlockTypes.SPECIAL_SELECT_ZP });
//        LWJGLRenderLogic.addBox(info, new Point3f(8,8,8-256), new Point3f(8,8,7), new short[] { BlockTypes.SPECIAL_SELECT_ZM });
        LWJGLRenderLogic.addBox(info, new Point3f(9, 8, 8), new Point3f(256 + 8, 8, 8), new short[]{BlockTypes.LIGHT_RED});
        LWJGLRenderLogic.addBox(info, new Point3f(8 - 256, 8, 8), new Point3f(7, 8, 8), new short[]{BlockTypes.LIGHT_RED});
        LWJGLRenderLogic.addBox(info, new Point3f(8, 9, 8), new Point3f(8, 256 + 8, 8), new short[]{BlockTypes.LIGHT_GREEN});
        LWJGLRenderLogic.addBox(info, new Point3f(8, 8 - 256, 8), new Point3f(8, 7, 8), new short[]{BlockTypes.LIGHT_GREEN});
        LWJGLRenderLogic.addBox(info, new Point3f(8, 8, 9), new Point3f(8, 8, 256 + 8), new short[]{BlockTypes.LIGHT_BLUE});
        LWJGLRenderLogic.addBox(info, new Point3f(8, 8, 8 - 256), new Point3f(8, 8, 7), new short[]{BlockTypes.LIGHT_BLUE});
        JGLObj obj = LWJGLRenderLogic.infoToObj(info);
//        for (int i = 0; i < obj.getColorBuffer().limit(); i++)
//            System.out.print(" "+obj.getColorBuffer().get(i));
//        System.out.println();
        mAxis.add(obj);
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
    public UndoBuffer getUndoer() {
        return mUndoer;
    }

    @Override
    public void setUndoer(UndoBuffer undoer) {
        mUndoer = undoer;
    }

    @Override
    public void undo() {
        SparseMatrix<Block> grid = mUndoer.undo();
        if (grid != null) {
            StarMadeLogic.setModel(grid);
        }
    }

    @Override
    public void redo() {
        SparseMatrix<Block> grid = mUndoer.redo();
        if (grid != null) {
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
