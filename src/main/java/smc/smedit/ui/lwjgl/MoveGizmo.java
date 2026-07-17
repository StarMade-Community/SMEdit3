package smc.smedit.ui.lwjgl;

import java.util.List;

import smc.smedit.data.SparseMatrix;
import smc.smedit.logic.SelectionModel;
import smc.smedit.logic.StarMadeLogic;
import smc.smedit.scene.SceneModel;
import smc.smedit.scene.SceneObject;
import smc.smedit.ship.data.Block;
import smc.smedit.vecmath.Matrix4f;
import smc.smedit.vecmath.Point3f;
import smc.smedit.vecmath.Point3i;
import smc.smedit.vecmath.Vector3f;

/**
 * The transform frame the Move tool draws its manipulator at: a world-space centre
 * and the three axis directions (the active object's local axes, in world space).
 * A single-axis handle constrains a drag to one line; a plane handle constrains it
 * to the plane spanned by the other two. The renderer ({@link LWJGLRenderPanel})
 * draws it and hit-tests the handles; {@link smc.smedit.ui.tool.ToolController} reads
 * this frame to turn a constrained drag into an offset.
 */
public final class MoveGizmo {

    /** Which handle a click grabbed. {@code PLANE_X} is the plane whose normal is X (the Y/Z plane). */
    public enum Handle {
        X, Y, Z, PLANE_X, PLANE_Y, PLANE_Z;

        public boolean isPlane() {
            return this == PLANE_X || this == PLANE_Y || this == PLANE_Z;
        }
    }

    private final Point3f center;
    private final Vector3f[] axes; // world-space unit X, Y, Z of the object
    private final boolean entityMode;
    private final SceneObject object;

    private MoveGizmo(Point3f center, Vector3f x, Vector3f y, Vector3f z,
            boolean entityMode, SceneObject object) {
        this.center = center;
        this.axes = new Vector3f[]{x, y, z};
        this.entityMode = entityMode;
        this.object = object;
    }

    public Point3f center() {
        return new Point3f(center);
    }

    /** World-space unit axis: 0 = X, 1 = Y, 2 = Z. */
    public Vector3f axis(int i) {
        return new Vector3f(axes[i]);
    }

    /** Whether the gizmo is moving the whole entity (vs. a block selection). */
    public boolean isEntityMode() {
        return entityMode;
    }

    public SceneObject object() {
        return object;
    }

    /**
     * The gizmo frame for the current Move target, or {@code null} if there's nothing
     * to move (no active object, an empty entity, or no block selection). In ENTITY
     * mode it sits at the entity's bounding-box centre; in BLOCKS mode at the
     * selection's centre. Axes follow the object's orientation.
     */
    public static MoveGizmo forCurrent() {
        SceneModel sm = StarMadeLogic.getInstance().getSceneModel();
        SceneObject active = sm != null ? sm.getActiveObject() : null;
        if (active == null) {
            return null;
        }
        SelectionModel sel = StarMadeLogic.getInstance().getSelection();
        boolean entity = sel.getMode() == SelectionModel.Mode.ENTITY;
        Matrix4f world = new Matrix4f();
        world.set(active.getTransform());
        Point3f centerLocal;
        if (entity) {
            SparseMatrix<Block> grid = active.getGrid();
            if (grid == null || grid.size() == 0) {
                return null;
            }
            Point3i lo = new Point3i();
            Point3i hi = new Point3i();
            grid.getBounds(lo, hi);
            centerLocal = new Point3f((lo.x + hi.x) / 2f, (lo.y + hi.y) / 2f, (lo.z + hi.z) / 2f);
        } else {
            List<Point3i> cells = sel.getSelected();
            if (cells.isEmpty()) {
                return null;
            }
            int minx = Integer.MAX_VALUE, miny = Integer.MAX_VALUE, minz = Integer.MAX_VALUE;
            int maxx = Integer.MIN_VALUE, maxy = Integer.MIN_VALUE, maxz = Integer.MIN_VALUE;
            for (Point3i p : cells) {
                minx = Math.min(minx, p.x);
                miny = Math.min(miny, p.y);
                minz = Math.min(minz, p.z);
                maxx = Math.max(maxx, p.x);
                maxy = Math.max(maxy, p.y);
                maxz = Math.max(maxz, p.z);
            }
            centerLocal = new Point3f((minx + maxx) / 2f, (miny + maxy) / 2f, (minz + maxz) / 2f);
        }
        Point3f center = new Point3f(centerLocal);
        world.transform(center);
        return new MoveGizmo(center, unitAxis(world, 1, 0, 0),
                unitAxis(world, 0, 1, 0), unitAxis(world, 0, 0, 1), entity, active);
    }

    /** The object's local axis (x,y,z) rotated into world space and normalized. */
    private static Vector3f unitAxis(Matrix4f world, float x, float y, float z) {
        Vector3f v = new Vector3f(x, y, z);
        world.transform(v);   // 3x3 rotation part only (translation ignored for vectors)
        float len = v.length();
        if (len > 1e-6f) {
            v.scale(1f / len);
        }
        return v;
    }
}
