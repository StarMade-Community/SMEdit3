/**
 * Copyright 2014 SMEdit https://github.com/StarMade/SMEdit SMTools
 * https://github.com/StarMade/SMTools
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
 */
package smc.smedit.ui.tool;

import java.awt.event.InputEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import smc.smedit.data.Blocks;
import smc.smedit.data.SparseMatrix;
import smc.smedit.ui.BlockShapes;
import smc.smedit.ui.BlockSlabs;
import smc.smedit.ui.BlockTypeColors;
import smc.smedit.logic.SelectionModel;
import smc.smedit.logic.StarMadeLogic;
import smc.smedit.scene.SceneModel;
import smc.smedit.scene.SceneObject;
import smc.smedit.ship.data.Block;
import smc.smedit.ui.RenderPanel;
import smc.smedit.ui.lwjgl.LWJGLRenderPanel;
import smc.smedit.ui.lwjgl.MoveGizmo;
import smc.smedit.vecmath.Matrix4f;
import smc.smedit.vecmath.Point3f;
import smc.smedit.vecmath.Point3i;
import smc.smedit.vecmath.Vector3f;

/**
 * Owns the single <em>active tool</em> and the brush settings, and routes
 * viewport clicks to the right behaviour. This is the seam that decouples
 * <em>selecting</em> from <em>painting</em>: the {@link EditorTool#SELECT} tool
 * builds a region in the {@link SelectionModel}, and the paint-like tools
 * (PAINT, ERASE, …) treat that region as a <em>mask</em> — when a selection
 * exists, edits are restricted to it; otherwise they apply freely under the
 * cursor. A left-click is the active tool's action, never an implicit select.
 *
 * <p>A singleton so the viewport mouse adapter, the tool rail and (later) the
 * brush panel / command console all share one source of truth. All access is on
 * the EDT / AWT event thread, matching the rest of the editor UI.
 */
public final class ToolController {

    private static final Logger log = Logger.getLogger(ToolController.class.getName());

    /** StarMade block grids centre symmetry on 8 (a 16-cell segment); mirror = 16 - c. */
    private static final int SYM_CENTER = 16;
    private static final int SYM_AXIS = 8;

    /** The footprint the brush stamps around the cursor. */
    public enum BrushShape {
        POINT("Point"), BOX("Box"), SPHERE("Sphere"), DISC("Disc");

        private final String label;

        BrushShape(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Which cells of the footprint an edit actually touches. */
    public enum FillMode {
        SOLID("Solid"),         // every cell in the footprint
        HOLLOW("Hollow"),       // only the footprint's outer shell
        SURFACE("Surface");     // only footprint cells on the model's skin (exposed to space)

        private final String label;

        FillMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Notified when the active tool or the active block changes, so the rail /
     * brush panel / status can re-sync. {@code activeBlockChanged} has a default
     * so listeners that only care about the tool can stay lambdas.
     */
    public interface Listener {
        void toolChanged(EditorTool tool);

        default void activeBlockChanged(short blockId) {
        }

        /**
         * The grid was changed by an edit stroke (build / erase / paint / fill), so
         * aggregate readouts (block count, type count, dimensions) may be stale.
         * Fired once per completed edit, not per touched cell.
         */
        default void modelEdited() {
        }
    }

    private static final ToolController INSTANCE = new ToolController();

    public static ToolController get() {
        return INSTANCE;
    }

    private EditorTool active = EditorTool.PAINT;
    private int brushSize = 1;              // radius in cells; 1 = a single block
    private BrushShape brushShape = BrushShape.BOX;
    private FillMode fillMode = FillMode.SOLID;
    private int blockStyle = BlockTypeColors.STYLE_NORMAL; // sub-shape for shape-family blocks
    private int slabLevel;                                 // 0 = full; 1=¾ 2=½ 3=¼ (mutually exclusive with a shape)
    private boolean convertShapes = true;                 // paint keeps the target's shape/slab in the new material
    private final List<Short> materials = new ArrayList<>(); // brush material set; first = primary
    private final java.util.Random random = new java.util.Random();
    private boolean symX;
    private boolean symY;
    private boolean symZ;
    private boolean floodDiagonals = true;         // double-click flood spreads through edge/corner touches too
    private boolean stroking;               // between press and release of an edit stroke

    // --- Move gesture (Move tool drag) ----------------------------------
    private boolean moving;                  // between beginMove and endMove
    private SelectionModel.Mode moveMode;    // whether we're sliding blocks or the whole entity
    private SceneObject moveObject;          // the entity being moved / whose blocks move
    private Point3f moveAnchorWorld;         // the world point grabbed at press
    private Vector3f movePlaneNormal;        // camera forward at press — the drag plane's normal
    private Point3f moveLocalAnchor;         // the anchor in the object's local grid space (blocks)
    private List<Point3i> moveCells;         // the selected cells captured at press (blocks)
    private Point3i moveOffset;              // last previewed integer cell offset (blocks)
    private Vector3f moveWorldOffset;        // last previewed snapped world offset (entity)
    // Gizmo constraint for the drag (null = free camera-plane drag).
    private MoveGizmo.Handle moveHandle;
    private Point3f moveCenterWorld;         // gizmo centre (world) for constrained drags
    private Vector3f[] moveAxes;             // gizmo world axes for constrained drags
    private double moveGrabParam;            // grabbed distance along the axis (AXIS_*)
    private Point3f moveGrabPlanePt;         // grabbed point on the plane (PLANE_*)

    private final List<Listener> listeners = new ArrayList<>();

    private ToolController() {
    }

    // ------------------------------------------------------------------
    // Active tool + brush settings
    // ------------------------------------------------------------------

    public EditorTool getActive() {
        return active;
    }

    public void setActive(EditorTool tool) {
        if (tool != null && tool != active) {
            active = tool;
            for (Listener l : new ArrayList<>(listeners)) {
                l.toolChanged(active);
            }
        }
    }

    public int getBrushSize() {
        return brushSize;
    }

    public void setBrushSize(int size) {
        brushSize = Math.max(1, size);
    }

    public BrushShape getBrushShape() {
        return brushShape;
    }

    public void setBrushShape(BrushShape shape) {
        if (shape != null) {
            brushShape = shape;
        }
    }

    public FillMode getFillMode() {
        return fillMode;
    }

    public void setFillMode(FillMode mode) {
        if (mode != null) {
            fillMode = mode;
        }
    }

    /** The primary base block type — the first of the material set (a shape-family cube for hull/armor). */
    public short getActiveBlockType() {
        return materials.isEmpty() ? -1 : materials.get(0);
    }

    /** The full brush material set (first = primary). Build/Paint pick from it. */
    public List<Short> getMaterials() {
        return java.util.Collections.unmodifiableList(materials);
    }

    /** Replaces the material set with a single block (plain palette click, eyedropper). */
    public void setActiveBlockType(short blockId) {
        materials.clear();
        if (blockId >= 0) {
            materials.add(blockId);
        }
        materialsChanged();
    }

    /** Adds a block to the material set (Shift-click in the palette). */
    public void addMaterial(short blockId) {
        if (blockId >= 0 && !materials.contains(blockId)) {
            materials.add(blockId);
            materialsChanged();
        }
    }

    /** Removes a block from the material set (Ctrl-click); keeps at least one. */
    public void removeMaterial(short blockId) {
        if (materials.size() > 1 && materials.remove((Short) blockId)) {
            materialsChanged();
        }
    }

    private void materialsChanged() {
        short primary = getActiveBlockType();
        StarMadeLogic.getInstance().setSelectedBlockType(primary);
        for (Listener l : new ArrayList<>(listeners)) {
            l.activeBlockChanged(primary);
        }
    }

    /** A block chosen from the material set — random when there's more than one. */
    public short pickMaterial() {
        if (materials.isEmpty()) {
            return -1;
        }
        return materials.size() == 1 ? materials.get(0) : materials.get(random.nextInt(materials.size()));
    }

    /** Like {@link #pickMaterial()} but resolved to the chosen sub-shape / slab (for Build). */
    private short pickEffectiveBlockType() {
        short m = pickMaterial();
        if (m < 0) {
            return -1;
        }
        return slabLevel > 0 ? BlockSlabs.slabVariant(m, slabLevel) : BlockShapes.variant(m, blockStyle);
    }

    /** The chosen sub-shape ({@code STYLE_*}) applied when placing a shape-family block. */
    public int getBlockStyle() {
        return blockStyle;
    }

    public void setBlockStyle(int style) {
        blockStyle = style;
    }

    /** The chosen slab level (0 = full block; 1=¾ 2=½ 3=¼). Mutually exclusive with a shape. */
    public int getSlabLevel() {
        return slabLevel;
    }

    public void setSlabLevel(int level) {
        slabLevel = level;
    }

    /** Whether Paint keeps the target block's shape/slab in the new material where possible. */
    public boolean isConvertShapes() {
        return convertShapes;
    }

    public void setConvertShapes(boolean on) {
        convertShapes = on;
    }

    /** The concrete block placed: the active block resolved to the chosen sub-shape or slab. */
    public short getEffectiveBlockType() {
        short base = getActiveBlockType();
        if (slabLevel > 0) {
            return BlockSlabs.slabVariant(base, slabLevel);
        }
        return BlockShapes.variant(base, blockStyle);
    }

    public boolean isSymX() {
        return symX;
    }

    public void setSymX(boolean on) {
        symX = on;
    }

    public boolean isSymY() {
        return symY;
    }

    public void setSymY(boolean on) {
        symY = on;
    }

    public boolean isSymZ() {
        return symZ;
    }

    public void setSymZ(boolean on) {
        symZ = on;
    }

    /** Whether the double-click flood spreads through diagonal (edge/corner) touches, not just faces. */
    public boolean isFloodDiagonals() {
        return floodDiagonals;
    }

    public void setFloodDiagonals(boolean on) {
        floodDiagonals = on;
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    // ------------------------------------------------------------------
    // Viewport routing (called by the renderer's mouse adapter)
    // ------------------------------------------------------------------

    /**
     * A left-button press. {@code hit} is the block cell under the cursor (null on
     * empty space); {@code place} is the adjacent empty cell for building (null if
     * unavailable). {@code modifiers} are the AWT event modifiers — Alt = temporary
     * eyedropper from any tool; Shift/Ctrl = additive select.
     */
    public void onPress(Point3i hit, Point3i place, RenderPanel renderer, int modifiers) {
        if ((modifiers & InputEvent.ALT_MASK) != 0) {
            pick(hit);
            return;
        }
        switch (active) {
            case SELECT:
                boolean additive = (modifiers
                        & (InputEvent.SHIFT_MASK | InputEvent.CTRL_MASK)) != 0;
                StarMadeLogic.getInstance().getSelection()
                        .applyPick(hit, StarMadeLogic.getModel(), additive);
                break;
            case PAINT:
            case ERASE:
                if (hit != null) {
                    // Checkpoint once at the start of the stroke, then edit freely
                    // without flooding the undo buffer on every dragged cell.
                    renderer.getUndoer().checkpoint(StarMadeLogic.getModel());
                    stroking = true;
                    applyStroke(hit, renderer);
                }
                break;
            case BUILD:
                if (place != null) {
                    renderer.getUndoer().checkpoint(StarMadeLogic.getModel());
                    stroking = true;
                    buildAt(place, renderer);
                }
                break;
            case FILL:
                fillAt(hit, renderer);
                fireEdited();
                break;
            case PICKER:
                pick(hit);
                break;
            default:
                log.info(active.getDisplayName()
                        + " tool isn't wired up yet — coming in the next pass.");
                break;
        }
    }

    /**
     * A double-click. For the Select tool it flood-selects every contiguous block of
     * the same type (any shape / slab of the same family) — the basis for a bucket fill.
     * Plain replaces the selection; Shift adds the region, Ctrl removes it.
     */
    public void onDoubleClick(Point3i hit, int modifiers) {
        if (hit == null || active != EditorTool.SELECT) {
            return;
        }
        Set<Point3i> region = contiguousByType(hit, StarMadeLogic.getModel(), floodDiagonals);
        SelectionModel sel = StarMadeLogic.getInstance().getSelection();
        if ((modifiers & InputEvent.CTRL_MASK) != 0) {
            sel.removeAll(region);
        } else if ((modifiers & InputEvent.SHIFT_MASK) != 0) {
            sel.addAll(region);
        } else {
            sel.select(region);
        }
    }

    /** Left-button drag: continues a paint/erase/build stroke (no new undo checkpoint). */
    public void onDrag(Point3i hit, Point3i place, RenderPanel renderer) {
        if (!stroking) {
            return;
        }
        if (active == EditorTool.PAINT || active == EditorTool.ERASE) {
            applyStroke(hit, renderer);
        } else if (active == EditorTool.BUILD) {
            buildAt(place, renderer);
        }
    }

    /** Left-button release: ends any in-progress stroke. */
    public void onRelease() {
        boolean wasStroke = stroking;
        stroking = false;
        if (wasStroke) {
            fireEdited();
        }
    }

    /** Notifies listeners that the grid changed, so status readouts can refresh. */
    private void fireEdited() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.modelEdited();
        }
    }

    // ------------------------------------------------------------------
    // Move tool — drag the whole entity, or the block selection within it
    // ------------------------------------------------------------------

    /**
     * Begins a Move drag. {@code hit} is the cell grabbed under the cursor (the pick
     * has already made its object the active edit target). The drag slides on a plane
     * through the grab point facing the camera, so orbiting first lets you push along
     * different axes; the result snaps to whole cells. In {@link SelectionModel.Mode#ENTITY}
     * the whole entity translates; in {@code BLOCKS} the current selection relocates.
     */
    public void beginMove(LWJGLRenderPanel panel, double sx, double sy, Point3i hit) {
        moving = false;
        moveHandle = null;
        SceneModel sm = StarMadeLogic.getInstance().getSceneModel();
        SceneObject obj = sm != null ? sm.getActiveObject() : null;
        if (panel == null || obj == null) {
            return;
        }
        SelectionModel sel = StarMadeLogic.getInstance().getSelection();
        SelectionModel.Mode mode = sel.getMode();
        Matrix4f world = new Matrix4f();
        world.set(obj.getTransform());
        if (mode != SelectionModel.Mode.ENTITY) {
            List<Point3i> cells = sel.getSelected();
            if (cells.isEmpty()) {
                return;   // nothing selected to move
            }
            moveCells = cells;
        }
        moveMode = mode;
        moveObject = obj;
        moveOffset = new Point3i(0, 0, 0);
        moveWorldOffset = new Vector3f();

        // Prefer a gizmo handle under the cursor: it constrains the drag to one axis
        // or plane. Falling through to a free camera-plane drag when nothing's grabbed
        // keeps "grab the body and slide" working.
        MoveGizmo frame = panel.getGizmoFrame();
        MoveGizmo.Handle handle = panel.pickMoveHandle(sx, sy);
        if (handle != null && frame != null) {
            float[] ray = panel.worldRay(sx, sy);
            if (ray == null) {
                return;
            }
            moveHandle = handle;
            moveCenterWorld = frame.center();
            moveAxes = new Vector3f[]{frame.axis(0), frame.axis(1), frame.axis(2)};
            if (handle.isPlane()) {
                moveGrabPlanePt = planeIntersect(ray, moveCenterWorld, moveAxes[planeNormalIndex(handle)]);
                if (moveGrabPlanePt == null) {
                    return;   // ray parallel to the handle plane — can't start
                }
            } else {
                moveGrabParam = closestParam(ray, moveCenterWorld, moveAxes[axisIndex(handle)]);
            }
            panel.setGizmoActive(handle);   // keep the grabbed handle highlighted during the drag
            moving = true;
            return;
        }

        // Free drag: a plane through the grab point facing the camera.
        Point3f anchorWorld;
        if (mode == SelectionModel.Mode.ENTITY) {
            anchorWorld = hit != null ? localToWorld(world, hit.x, hit.y, hit.z)
                    : new Point3f(world.m03, world.m13, world.m23);
        } else {
            List<Point3i> cells = moveCells;
            if (hit != null && cells.contains(hit)) {
                anchorWorld = localToWorld(world, hit.x, hit.y, hit.z);
            } else {
                double cx = 0, cy = 0, cz = 0;
                for (Point3i p : cells) {
                    cx += p.x;
                    cy += p.y;
                    cz += p.z;
                }
                int n = cells.size();
                anchorWorld = localToWorld(world, cx / n, cy / n, cz / n);
            }
            Matrix4f inv = new Matrix4f();
            inv.set(world);
            try {
                inv.invert();
            } catch (RuntimeException e) {
                return;   // singular transform — can't map back to local space
            }
            moveLocalAnchor = new Point3f(anchorWorld);
            inv.transform(moveLocalAnchor);
        }
        Vector3f normal = panel.cameraForward();
        normal.normalize();
        moveAnchorWorld = anchorWorld;
        movePlaneNormal = normal;
        moving = true;
    }

    /** Continues a Move drag: turns the cursor into a world delta and previews the result. */
    public void updateMove(LWJGLRenderPanel panel, double sx, double sy) {
        if (!moving) {
            return;
        }
        Vector3f raw = dragWorldDelta(panel, sx, sy);
        if (raw == null) {
            return;   // ray parallel / nothing rendered yet — keep the last preview
        }
        if (moveMode == SelectionModel.Mode.ENTITY) {
            Vector3f off = snapEntityDelta(raw);
            if (!off.equals(moveWorldOffset)) {
                moveWorldOffset = off;
                panel.setEntityMovePreview(off);
            }
        } else {
            Point3i off = blockOffset(raw);
            if (off != null && !off.equals(moveOffset)) {
                moveOffset = off;
                panel.setBlockMovePreview(off);
            }
        }
    }

    /**
     * The raw (unsnapped) world-space translation the current cursor implies, per the
     * active constraint: along one axis, within one plane, or on the free camera plane.
     * Null when the cursor ray can't resolve a point (parallel / no frame yet).
     */
    private Vector3f dragWorldDelta(LWJGLRenderPanel panel, double sx, double sy) {
        if (moveHandle == null) {
            Point3f world = panel.pickPlane(sx, sy, moveAnchorWorld, movePlaneNormal);
            if (world == null) {
                return null;
            }
            return new Vector3f(world.x - moveAnchorWorld.x,
                    world.y - moveAnchorWorld.y, world.z - moveAnchorWorld.z);
        }
        float[] ray = panel.worldRay(sx, sy);
        if (ray == null) {
            return null;
        }
        if (moveHandle.isPlane()) {
            Point3f p = planeIntersect(ray, moveCenterWorld, moveAxes[planeNormalIndex(moveHandle)]);
            if (p == null) {
                return null;
            }
            return new Vector3f(p.x - moveGrabPlanePt.x,
                    p.y - moveGrabPlanePt.y, p.z - moveGrabPlanePt.z);
        }
        Vector3f a = moveAxes[axisIndex(moveHandle)];
        double d = closestParam(ray, moveCenterWorld, a) - moveGrabParam;
        return new Vector3f((float) (a.x * d), (float) (a.y * d), (float) (a.z * d));
    }

    /** Snaps a world delta to whole cells along whichever axes the constraint allows. */
    private Vector3f snapEntityDelta(Vector3f raw) {
        if (moveHandle == null) {
            return new Vector3f(Math.round(raw.x), Math.round(raw.y), Math.round(raw.z));
        }
        if (moveHandle.isPlane()) {
            int n = planeNormalIndex(moveHandle);
            Vector3f u = moveAxes[(n + 1) % 3];
            Vector3f v = moveAxes[(n + 2) % 3];
            float cu = Math.round(dot(raw, u));
            float cv = Math.round(dot(raw, v));
            return new Vector3f(u.x * cu + v.x * cv, u.y * cu + v.y * cv, u.z * cu + v.z * cv);
        }
        Vector3f a = moveAxes[axisIndex(moveHandle)];
        float s = Math.round(dot(raw, a));
        return new Vector3f(a.x * s, a.y * s, a.z * s);
    }

    /** Converts a world delta into an integer local-cell offset for the active object. */
    private Point3i blockOffset(Vector3f raw) {
        Matrix4f inv = new Matrix4f();
        inv.set(moveObject.getTransform());
        try {
            inv.invert();
        } catch (RuntimeException e) {
            return null;
        }
        Vector3f local = new Vector3f(raw);
        inv.transform(local);   // rotate the delta into local space (3x3 part only)
        return new Point3i(Math.round(local.x), Math.round(local.y), Math.round(local.z));
    }

    private static float dot(Vector3f a, Vector3f b) {
        return a.x * b.x + a.y * b.y + a.z * b.z;
    }

    private static int axisIndex(MoveGizmo.Handle h) {
        switch (h) {
            case X: return 0;
            case Y: return 1;
            default: return 2;
        }
    }

    private static int planeNormalIndex(MoveGizmo.Handle h) {
        switch (h) {
            case PLANE_X: return 0;
            case PLANE_Y: return 1;
            default: return 2;
        }
    }

    /** Distance along unit axis {@code a} (from {@code c}) of the point on it nearest the ray. */
    private static double closestParam(float[] ray, Point3f c, Vector3f a) {
        double ux = ray[3] - ray[0], uy = ray[4] - ray[1], uz = ray[5] - ray[2]; // ray dir
        double wx = ray[0] - c.x, wy = ray[1] - c.y, wz = ray[2] - c.z;          // near - c
        double uu = ux * ux + uy * uy + uz * uz;
        double uv = ux * a.x + uy * a.y + uz * a.z;
        double uw = ux * wx + uy * wy + uz * wz;
        double vw = a.x * wx + a.y * wy + a.z * wz;   // a is unit, so v·v = 1
        double denom = uu - uv * uv;
        if (Math.abs(denom) < 1e-9) {
            return -vw;   // ray parallel to axis — fall back to plain projection
        }
        return (uu * vw - uv * uw) / denom;
    }

    /** Intersection of the ray with the plane (point {@code c}, unit {@code normal}), or null. */
    private static Point3f planeIntersect(float[] ray, Point3f c, Vector3f normal) {
        double dx = ray[3] - ray[0], dy = ray[4] - ray[1], dz = ray[5] - ray[2];
        double denom = dx * normal.x + dy * normal.y + dz * normal.z;
        if (Math.abs(denom) < 1e-9) {
            return null;
        }
        double t = ((c.x - ray[0]) * normal.x + (c.y - ray[1]) * normal.y
                + (c.z - ray[2]) * normal.z) / denom;
        return new Point3f((float) (ray[0] + t * dx),
                (float) (ray[1] + t * dy), (float) (ray[2] + t * dz));
    }

    /** Ends a Move drag: commits the whole-entity translation or the block relocation. */
    public void endMove(LWJGLRenderPanel panel) {
        panel.setGizmoActive(null);   // drop the grabbed-handle highlight
        if (!moving) {
            return;
        }
        moving = false;
        if (moveMode == SelectionModel.Mode.ENTITY) {
            Vector3f off = moveWorldOffset;
            boolean changed = off != null && (off.x != 0 || off.y != 0 || off.z != 0);
            if (changed) {
                Matrix4f tr = moveObject.getTransform();
                tr.m03 += off.x;
                tr.m13 += off.y;
                tr.m23 += off.z;
            }
            panel.clearMovePreview();
            if (changed) {
                panel.updateTiles();
                fireEdited();
            }
        } else {
            commitBlockMove(panel);
        }
        moveObject = null;
        moveCells = null;
        moveHandle = null;
        moveAxes = null;
    }

    /**
     * Relocates the selected blocks by {@link #moveOffset}. Blocks that would land on
     * cells occupied by <em>other</em> (non-moving) blocks are a collision: the user is
     * asked to Replace them or Cancel — clipping two blocks into one cell is never
     * allowed. On commit the move is a single undoable edit and the selection follows.
     */
    private void commitBlockMove(LWJGLRenderPanel panel) {
        Point3i off = moveOffset;
        SparseMatrix<Block> grid = moveObject != null ? moveObject.getGrid() : null;
        if (grid == null || off == null || (off.x == 0 && off.y == 0 && off.z == 0)
                || moveCells == null || moveCells.isEmpty()) {
            panel.clearMovePreview();
            return;
        }
        // A selection can include empty cells; only cells that actually hold a block move.
        java.util.LinkedHashMap<Point3i, Block> lifted = new java.util.LinkedHashMap<>();
        for (Point3i c : moveCells) {
            Block b = grid.get(c);
            if (b != null) {
                lifted.put(c, b);
            }
        }
        if (lifted.isEmpty()) {
            panel.clearMovePreview();
            return;
        }
        Set<Point3i> movingSet = lifted.keySet();
        int overlaps = 0;
        int coreOverlaps = 0;
        for (Point3i c : movingSet) {
            Point3i dest = new Point3i(c.x + off.x, c.y + off.y, c.z + off.z);
            if (!movingSet.contains(dest)) {
                Block d = grid.get(dest);
                if (d != null) {
                    if (isProtectedCore(d)) {
                        coreOverlaps++;
                    } else {
                        overlaps++;
                    }
                }
            }
        }
        if (coreOverlaps > 0) {
            // A ship core is never replaceable — refuse the whole move rather than
            // destroy it (partially skipping cells would clip or lose blocks).
            javax.swing.JOptionPane.showMessageDialog(panel,
                    "That move would land on the ship core, which can't be replaced.\n"
                            + "Reposition the selection so it clears the core.",
                    "Ship core protected", javax.swing.JOptionPane.WARNING_MESSAGE);
            panel.clearMovePreview();
            return;
        }
        if (overlaps > 0) {
            int choice = javax.swing.JOptionPane.showOptionDialog(panel,
                    "Moving the selection would overlap " + overlaps
                            + " existing block(s).\nReplace them, or cancel the move?",
                    "Blocks overlap", javax.swing.JOptionPane.YES_NO_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE, null,
                    new Object[]{"Replace", "Cancel"}, "Cancel");
            if (choice != 0) {   // Cancel, or dialog closed
                panel.clearMovePreview();
                return;
            }
        }
        panel.getUndoer().checkpoint(grid);
        // Lift every moving block out first (so the source cells are free), then drop
        // them at the destination — overwriting any non-moving block there (Replace).
        for (Point3i c : movingSet) {
            grid.set(c, null);
        }
        List<Point3i> moved = new ArrayList<>(lifted.size());
        for (java.util.Map.Entry<Point3i, Block> e : lifted.entrySet()) {
            Point3i c = e.getKey();
            Point3i dest = new Point3i(c.x + off.x, c.y + off.y, c.z + off.z);
            grid.set(dest, e.getValue());
            moved.add(dest);
        }
        StarMadeLogic.getInstance().getSelection().select(moved);
        panel.clearMovePreview();
        panel.updateTiles();
        fireEdited();
    }

    /** The ship core is load-bearing for the whole entity, so it's never overwritten. */
    private static boolean isProtectedCore(Block b) {
        return b != null && b.getBlockID() == Blocks.SHIP_CORE.getId();
    }

    /** A local grid point transformed into world space by {@code world}. */
    private static Point3f localToWorld(Matrix4f world, double x, double y, double z) {
        Point3f p = new Point3f((float) x, (float) y, (float) z);
        world.transform(p);
        return p;
    }

    // ------------------------------------------------------------------
    // Tool behaviours
    // ------------------------------------------------------------------

    private void pick(Point3i hit) {
        if (hit == null) {
            return;
        }
        SparseMatrix<Block> grid = StarMadeLogic.getModel();
        Block b = grid != null ? grid.get(hit) : null;
        if (b != null) {
            short picked = b.getBlockID();
            // Sample into the base block + its sub-form (shape or slab), so the brush
            // reflects exactly what was under the cursor.
            if (BlockSlabs.isSlab(picked)) {
                blockStyle = BlockTypeColors.STYLE_NORMAL;
                slabLevel = BlockSlabs.levelOf(picked);
                setActiveBlockType(BlockSlabs.baseOf(picked));
            } else {
                slabLevel = 0;
                blockStyle = BlockShapes.styleOf(picked);
                setActiveBlockType(BlockShapes.baseCube(picked));
            }
            log.info("Picked block type " + picked);
        }
    }

    /**
     * Applies the active edit tool over the brush footprint around {@code center}:
     * shape → fill-mode → selection mask → symmetry, then paint / erase.
     */
    private void applyStroke(Point3i center, RenderPanel renderer) {
        if (center == null) {
            return;
        }
        SparseMatrix<Block> grid = StarMadeLogic.getModel();
        if (grid == null) {
            return;
        }
        Set<Point3i> cells = applyFillMode(shapeFootprint(center), grid);

        List<Point3i> selected = StarMadeLogic.getInstance().getSelection().getSelected();
        if (!selected.isEmpty()) {
            cells.retainAll(new HashSet<>(selected));   // selection-as-mask
        }
        cells = withSymmetry(cells);

        boolean changed = false;
        for (Point3i p : cells) {
            if (active == EditorTool.PAINT) {
                Block b = grid.get(p);
                if (b == null) {
                    continue;
                }
                short material = pickMaterial();   // random from the material set (per cell)
                if (material < 0) {
                    continue;
                }
                // "Convert equivalent shapes" (default): keep the target's shape/slab
                // in the new material's family where it exists; otherwise replace the
                // block with the material's base form.
                short newID = convertShapes ? equivalentForm(material, b.getBlockID()) : material;
                if (newID != -1 && newID != b.getBlockID()) {
                    b.setBlockID(newID);
                    changed = true;
                }
            } else if (active == EditorTool.ERASE) {
                if (grid.get(p) != null) {
                    grid.set(p, null);
                    changed = true;
                }
            }
        }
        if (changed) {
            // Rebuild the mesh so the recolour / removal is visible (colours are
            // baked into the geometry at build time, so a plain repaint won't do).
            renderer.updateTiles();
        }
    }

    /**
     * Places the active (shaped/slabbed) block across the brush footprint around the
     * placement cell, filling only empty cells (never overwriting), clipped to the
     * selection mask and mirrored per symmetry.
     */
    private void buildAt(Point3i place, RenderPanel renderer) {
        if (place == null) {
            return;
        }
        SparseMatrix<Block> grid = StarMadeLogic.getModel();
        if (grid == null) {
            return;
        }
        if (getActiveBlockType() < 0) {
            return;   // no material selected
        }
        // HOLLOW hollows the footprint; SOLID/SURFACE fill it (SURFACE is meaningless
        // when placing into empty space, so it behaves as solid here).
        Set<Point3i> cells = shapeFootprint(place);
        if (fillMode == FillMode.HOLLOW) {
            cells = shellOf(cells);
        }
        List<Point3i> selected = StarMadeLogic.getInstance().getSelection().getSelected();
        if (!selected.isEmpty()) {
            cells.retainAll(new HashSet<>(selected));
        }
        cells = withSymmetry(cells);

        boolean changed = false;
        for (Point3i p : cells) {
            if (grid.get(p) == null) {     // add into empty space only
                short eff = pickEffectiveBlockType();   // random from the material set (per cell)
                if (eff >= 0) {
                    grid.set(p, new Block(eff));
                    changed = true;
                }
            }
        }
        if (changed) {
            renderer.updateTiles();
        }
    }

    /**
     * Bucket fill: repaints the current selection (or, if none, the contiguous
     * same-type region under the cursor) with the active material(s), preserving
     * each block's shape/slab like Paint.
     */
    private void fillAt(Point3i hit, RenderPanel renderer) {
        SparseMatrix<Block> grid = StarMadeLogic.getModel();
        if (grid == null) {
            return;
        }
        List<Point3i> selected = StarMadeLogic.getInstance().getSelection().getSelected();
        Collection<Point3i> target = selected.isEmpty() ? contiguousByType(hit, grid, floodDiagonals) : selected;
        if (target.isEmpty() || getActiveBlockType() < 0) {
            return;
        }
        renderer.getUndoer().checkpoint(grid);
        boolean changed = false;
        for (Point3i p : target) {
            Block b = grid.get(p);
            if (b == null) {
                continue;
            }
            short material = pickMaterial();
            if (material < 0) {
                continue;
            }
            short newID = convertShapes ? equivalentForm(material, b.getBlockID()) : material;
            if (newID != -1 && newID != b.getBlockID()) {
                b.setBlockID(newID);
                changed = true;
            }
        }
        if (changed) {
            renderer.updateTiles();
        }
    }

    /** Safety cap on a flood-fill's size (a whole hull is well under this). */
    private static final int FLOOD_CAP = 500000;

    /**
     * Flood-fills from {@code start} through 6-connected neighbours, collecting every
     * block of the <em>same type</em> — any shape or slab of the same family (so a
     * grey-armor cube, wedge and ½-slab all count as one type).
     */
    private static Set<Point3i> contiguousByType(Point3i start, SparseMatrix<Block> grid, boolean diagonals) {
        Set<Point3i> found = new LinkedHashSet<>();
        if (start == null || grid == null) {
            return found;
        }
        Block seed = grid.get(start);
        if (seed == null) {
            return found;
        }
        short family = familyOf(seed.getBlockID());
        Deque<Point3i> queue = new ArrayDeque<>();
        queue.add(new Point3i(start));
        found.add(new Point3i(start));
        while (!queue.isEmpty() && found.size() < FLOOD_CAP) {
            Point3i p = queue.poll();
            for (Point3i n : (diagonals ? neighbours26(p) : neighbours(p))) {
                if (found.contains(n)) {
                    continue;
                }
                Block nb = grid.get(n);
                if (nb != null && familyOf(nb.getBlockID()) == family) {
                    found.add(n);
                    queue.add(n);
                }
            }
        }
        return found;
    }

    /** The block's "type" for flood-fill: its shape-family base (or slab base). */
    private static short familyOf(short id) {
        return BlockSlabs.isSlab(id) ? BlockSlabs.baseOf(id) : BlockShapes.baseCube(id);
    }

    /**
     * The {@code material} block converted to the {@code target}'s form (slab or
     * shape) where the material's family offers it, else the material's base form.
     * This is what makes Paint keep a wedge a wedge, a ½-slab a ½-slab, etc. — across
     * hull and armor families, not just standard hull.
     */
    private static short equivalentForm(short material, short target) {
        int slab = BlockTypeColors.getBlockSlab(target);
        if (slab > 0 && BlockSlabs.hasSlabs(material)) {
            return BlockSlabs.slabVariant(material, slab);
        }
        if (BlockShapes.supportsShapes(material)) {
            return BlockShapes.variant(material, BlockShapes.styleOf(target));
        }
        return material;
    }

    /** The raw brush footprint: cells of the chosen {@link BrushShape}, radius {@code brushSize-1}. */
    private Set<Point3i> shapeFootprint(Point3i c) {
        int rad = Math.max(0, brushSize - 1);
        Set<Point3i> cells = new LinkedHashSet<>();
        if (brushShape == BrushShape.POINT || rad == 0) {
            cells.add(new Point3i(c));
            return cells;
        }
        double r2 = (rad + 0.5) * (rad + 0.5);
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dy = -rad; dy <= rad; dy++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    if (inShape(dx, dy, dz, r2)) {
                        cells.add(new Point3i(c.x + dx, c.y + dy, c.z + dz));
                    }
                }
            }
        }
        return cells;
    }

    private boolean inShape(int dx, int dy, int dz, double r2) {
        switch (brushShape) {
            case BOX:
                return true;
            case SPHERE:
                return dx * dx + dy * dy + dz * dz <= r2;
            case DISC:   // a single-layer disc in the X/Z plane through the cursor
                return dy == 0 && dx * dx + dz * dz <= r2;
            default:
                return dx == 0 && dy == 0 && dz == 0;
        }
    }

    /** Restricts the footprint per the fill mode (SOLID = no restriction). */
    private Set<Point3i> applyFillMode(Set<Point3i> footprint, SparseMatrix<Block> grid) {
        switch (fillMode) {
            case HOLLOW:
                return shellOf(footprint);
            case SURFACE:
                Set<Point3i> skin = new LinkedHashSet<>();
                for (Point3i p : footprint) {
                    if (grid.get(p) != null && hasEmptyNeighbour(grid, p)) {
                        skin.add(p);
                    }
                }
                return skin;
            default:
                return footprint;
        }
    }

    /** Footprint cells with at least one 6-neighbour outside the footprint. */
    private static Set<Point3i> shellOf(Set<Point3i> footprint) {
        Set<Point3i> shell = new LinkedHashSet<>();
        for (Point3i p : footprint) {
            for (Point3i n : neighbours(p)) {
                if (!footprint.contains(n)) {
                    shell.add(p);
                    break;
                }
            }
        }
        return shell;
    }

    private static boolean hasEmptyNeighbour(SparseMatrix<Block> grid, Point3i p) {
        for (Point3i n : neighbours(p)) {
            if (grid.get(n) == null) {
                return true;
            }
        }
        return false;
    }

    private static Point3i[] neighbours(Point3i p) {
        return new Point3i[]{
            new Point3i(p.x - 1, p.y, p.z), new Point3i(p.x + 1, p.y, p.z),
            new Point3i(p.x, p.y - 1, p.z), new Point3i(p.x, p.y + 1, p.z),
            new Point3i(p.x, p.y, p.z - 1), new Point3i(p.x, p.y, p.z + 1),
        };
    }

    /** All 26 face/edge/corner neighbours — the diagonal flood's connectivity. */
    private static Point3i[] neighbours26(Point3i p) {
        Point3i[] out = new Point3i[26];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        out[i++] = new Point3i(p.x + dx, p.y + dy, p.z + dz);
                    }
                }
            }
        }
        return out;
    }

    /** Adds the mirror image of every cell across each enabled symmetry plane. */
    private Set<Point3i> withSymmetry(Set<Point3i> cells) {
        Set<Point3i> out = new LinkedHashSet<>(cells);
        if (symX) {
            mirror(out, 'x');
        }
        if (symY) {
            mirror(out, 'y');
        }
        if (symZ) {
            mirror(out, 'z');
        }
        return out;
    }

    private static void mirror(Set<Point3i> cells, char axis) {
        for (Point3i p : new ArrayList<>(cells)) {
            switch (axis) {
                case 'x':
                    if (p.x != SYM_AXIS) {
                        cells.add(new Point3i(SYM_CENTER - p.x, p.y, p.z));
                    }
                    break;
                case 'y':
                    if (p.y != SYM_AXIS) {
                        cells.add(new Point3i(p.x, SYM_CENTER - p.y, p.z));
                    }
                    break;
                case 'z':
                    if (p.z != SYM_AXIS) {
                        cells.add(new Point3i(p.x, p.y, SYM_CENTER - p.z));
                    }
                    break;
                default:
                    break;
            }
        }
    }
}
