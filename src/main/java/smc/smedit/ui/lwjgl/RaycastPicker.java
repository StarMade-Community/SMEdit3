package smc.smedit.ui.lwjgl;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.glu.GLU;

import smc.smedit.data.SparseMatrix;
import smc.smedit.scene.SceneObject;
import smc.smedit.ship.data.Block;
import smc.smedit.ui.BlockTypeColors;
import smc.smedit.vecmath.Matrix4f;
import smc.smedit.vecmath.Point3f;
import smc.smedit.vecmath.Point3i;

/**
 * Screen-click to block picking. Unprojects the click into a world-space ray
 * (pure {@link GLU#gluUnProject} math — no GL context needed) and walks the voxel
 * grid with Amanatides &amp; Woo traversal to the first occupied cell.
 */
public final class RaycastPicker {

    private RaycastPicker() {
    }

    /** Safety cap on cells walked before giving up on a stray ray. */
    private static final int MAX_STEPS = 8192;

    /**
     * The outcome of a pick: the block the ray hit ({@link #cell}) and the empty
     * cell just outside the face it entered through ({@link #place}, for building —
     * {@code null} when the ray started inside the hit cell).
     */
    public static final class Hit {
        public final Point3i cell;
        public final Point3i place;

        Hit(Point3i cell, Point3i place) {
            this.cell = cell;
            this.place = place;
        }
    }

    /**
     * Picks the first block along the ray through screen pixel (screenX, screenY),
     * in LWJGL/GL window space (origin bottom-left — matches {@code Mouse.getEventX/Y}).
     *
     * @return the hit grid position, or {@code null} on a miss.
     */
    public static Point3i pick(float screenX, float screenY, PickMatrices.Snapshot snap,
            SparseMatrix<Block> grid) {
        Hit h = pickHit(screenX, screenY, snap, grid);
        return h == null ? null : h.cell;
    }

    /** One pickable scene object: its grid and the world transform it's rendered at. */
    public static final class Target {
        public final SceneObject object;
        public final Matrix4f world;
        public final SparseMatrix<Block> grid;

        public Target(SceneObject object, Matrix4f world, SparseMatrix<Block> grid) {
            this.object = object;
            this.world = world;
            this.grid = grid;
        }
    }

    /**
     * Picks the nearest block across every {@link Target}. The screen ray is
     * unprojected to world space (the pick matrices are world→eye), then transformed
     * into each object's local space by {@code world⁻¹} and walked; the hit closest
     * to the eye (compared in world space) wins. Returns a {@link PickResult} with
     * the object and its <em>local</em> cell / placement cell, or {@code null}.
     */
    public static PickResult pickScene(float screenX, float screenY, PickMatrices.Snapshot snap,
            List<Target> targets) {
        if (snap == null || targets == null || targets.isEmpty()) {
            return null;
        }
        FloatBuffer mv = wrap16(snap.modelview);
        FloatBuffer proj = wrap16(snap.projection);
        IntBuffer vp = BufferUtils.createIntBuffer(16);
        vp.put(snap.viewport).flip();
        float[] nearW = unproject(screenX, screenY, 0f, mv, proj, vp);
        float[] farW = unproject(screenX, screenY, 1f, mv, proj, vp);
        if (nearW == null || farW == null) {
            return null;
        }
        PickResult best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Target t : targets) {
            if (t.grid == null) {
                continue;
            }
            Matrix4f inv = new Matrix4f();
            inv.set(t.world);
            try {
                inv.invert();
            } catch (RuntimeException e) {
                continue; // singular transform — not pickable
            }
            Hit hit = walk(apply(inv, nearW), apply(inv, farW), t.grid);
            if (hit == null) {
                continue;
            }
            // Compare hits in a common frame: distance from the eye to the world-space
            // centre of the hit cell. Nearest wins across objects.
            Point3f wc = new Point3f(hit.cell.x, hit.cell.y, hit.cell.z);
            t.world.transform(wc);
            double d = sq(wc.x - nearW[0]) + sq(wc.y - nearW[1]) + sq(wc.z - nearW[2]);
            if (d < bestDist) {
                bestDist = d;
                best = new PickResult(t.object, hit.cell, hit.place);
            }
        }
        return best;
    }

    private static float[] apply(Matrix4f m, float[] p) {
        Point3f pt = new Point3f(p[0], p[1], p[2]);
        m.transform(pt);
        return new float[]{pt.x, pt.y, pt.z};
    }

    private static double sq(double v) {
        return v * v;
    }

    /** As {@link #pick} but also returns the adjacent empty cell for block placement. */
    public static Hit pickHit(float screenX, float screenY, PickMatrices.Snapshot snap,
            SparseMatrix<Block> grid) {
        if (snap == null || grid == null) {
            return null;
        }
        FloatBuffer mv = wrap16(snap.modelview);
        FloatBuffer proj = wrap16(snap.projection);
        IntBuffer vp = BufferUtils.createIntBuffer(16);
        vp.put(snap.viewport).flip();
        float[] near = unproject(screenX, screenY, 0f, mv, proj, vp);
        float[] far = unproject(screenX, screenY, 1f, mv, proj, vp);
        if (near == null || far == null) {
            return null;
        }
        return walk(near, far, grid);
    }

    /**
     * The world-space ray through screen pixel (sx, sy), as
     * {@code {nearX, nearY, nearZ, farX, farY, farZ}} — the unprojection of the
     * pixel at the near and far clip planes. Null if nothing has been rendered
     * yet or the unprojection fails. Used by the Move tool to drag on a plane.
     */
    public static float[] worldRay(float sx, float sy, PickMatrices.Snapshot snap) {
        if (snap == null) {
            return null;
        }
        FloatBuffer mv = wrap16(snap.modelview);
        FloatBuffer proj = wrap16(snap.projection);
        IntBuffer vp = BufferUtils.createIntBuffer(16);
        vp.put(snap.viewport).flip();
        float[] near = unproject(sx, sy, 0f, mv, proj, vp);
        float[] far = unproject(sx, sy, 1f, mv, proj, vp);
        if (near == null || far == null) {
            return null;
        }
        return new float[]{near[0], near[1], near[2], far[0], far[1], far[2]};
    }

    /**
     * Projects a world point to window coordinates as {@code {winX, winY, winZ}}
     * (origin bottom-left, matching {@link #worldRay} and the synthesized mouse
     * events). {@code winZ > 1} means the point is behind the camera. Null if nothing
     * has been rendered yet or the projection fails. Used to hit-test gizmo handles.
     */
    public static float[] project(float wx, float wy, float wz, PickMatrices.Snapshot snap) {
        if (snap == null) {
            return null;
        }
        FloatBuffer mv = wrap16(snap.modelview);
        FloatBuffer proj = wrap16(snap.projection);
        IntBuffer vp = BufferUtils.createIntBuffer(16);
        vp.put(snap.viewport).flip();
        FloatBuffer out = BufferUtils.createFloatBuffer(3);
        if (!GLU.gluProject(wx, wy, wz, mv, proj, vp, out)) {
            return null;
        }
        return new float[]{out.get(0), out.get(1), out.get(2)};
    }

    private static FloatBuffer wrap16(float[] a) {
        FloatBuffer b = BufferUtils.createFloatBuffer(16);
        b.put(a).flip();
        return b;
    }

    private static float[] unproject(float x, float y, float z, FloatBuffer mv, FloatBuffer proj, IntBuffer vp) {
        FloatBuffer out = BufferUtils.createFloatBuffer(3);
        if (!GLU.gluUnProject(x, y, z, mv, proj, vp, out)) {
            return null;
        }
        return new float[]{out.get(0), out.get(1), out.get(2)};
    }

    /**
     * Voxel walk from {@code near} to {@code far}. A block at integer (bx,by,bz)
     * spans [b-0.5, b+0.5], so we shift the ray origin by +0.5 to get unit cells
     * [n, n+1) whose floor is the block index.
     */
    private static Hit walk(float[] near, float[] far, SparseMatrix<Block> grid) {
        double ox = near[0] + 0.5, oy = near[1] + 0.5, oz = near[2] + 0.5;
        double dx = far[0] - near[0], dy = far[1] - near[1], dz = far[2] - near[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) {
            return null;
        }
        dx /= len;
        dy /= len;
        dz /= len;

        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int stepX = dx >= 0 ? 1 : -1, stepY = dy >= 0 ? 1 : -1, stepZ = dz >= 0 ? 1 : -1;
        double tMaxX = tMax(ox, dx, x, stepX), tMaxY = tMax(oy, dy, y, stepY), tMaxZ = tMax(oz, dz, z, stepZ);
        double tDeltaX = dx != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
        double tDeltaY = dy != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
        double tDeltaZ = dz != 0 ? Math.abs(1.0 / dz) : Double.POSITIVE_INFINITY;

        // Entry normal: the axis of the last step, negated — points back to the empty
        // cell the ray came from (where a Build tool places a block). Zero until we
        // step, so a hit on the very first (origin) cell has no placement cell.
        int nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < MAX_STEPS; i++) {
            Block hit = grid.get(x, y, z);
            if (hit != null && hitsCell(hit, x, y, z, near, dx, dy, dz, len)) {
                Point3i cell = new Point3i(x, y, z);
                Point3i place = (nx | ny | nz) != 0 ? new Point3i(x + nx, y + ny, z + nz) : null;
                return new Hit(cell, place);
            }
            double tNext;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                nx = -stepX;
                ny = 0;
                nz = 0;
                tNext = tMaxX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                ny = -stepY;
                nx = 0;
                nz = 0;
                tNext = tMaxY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                nz = -stepZ;
                nx = 0;
                ny = 0;
                tNext = tMaxZ;
                tMaxZ += tDeltaZ;
            }
            if (tNext > len) {
                break; // walked past the far plane
            }
        }
        return null;
    }

    private static double tMax(double origin, double dir, int cell, int step) {
        if (dir == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0 ? cell + 1 : cell;
        return (boundary - origin) / dir;
    }

    /**
     * Whether the ray actually intersects the block occupying cell (cx,cy,cz).
     * Ordinary cube/shape blocks fill the cell, so entering it counts as a hit.
     * A LOD-model block is only hit if the ray crosses its real mesh — otherwise
     * the ray passes through the gaps to whatever is behind (matching what's drawn,
     * so you can click the block behind a thin console/pipe/light).
     */
    private static boolean hitsCell(Block b, int cx, int cy, int cz,
            float[] origin, double dx, double dy, double dz, double maxT) {
        short id = b.getBlockID();
        if (!BlockTypeColors.hasLodModel(id)) {
            return true;
        }
        LodModelCache.LodModel model = LodModelCache.getModel(BlockTypeColors.getLodShape(id));
        if (model == null || model.tris.length == 0) {
            return true; // model unavailable -> drawn as a cube, so pick as a cube
        }
        return rayHitsModel(model, b.getOrientation(), cx, cy, cz,
                origin[0], origin[1], origin[2], dx, dy, dz, maxT);
    }

    private static boolean rayHitsModel(LodModelCache.LodModel model, int orient,
            int cx, int cy, int cz, double ox, double oy, double oz,
            double dx, double dy, double dz, double maxT) {
        float[] pos = model.positions;
        int[] tris = model.tris;
        float[] a = new float[3], b = new float[3], c = new float[3];
        for (int i = 0; i + 2 < tris.length; i += 3) {
            worldVert(pos, tris[i], orient, cx, cy, cz, a);
            worldVert(pos, tris[i + 1], orient, cx, cy, cz, b);
            worldVert(pos, tris[i + 2], orient, cx, cy, cz, c);
            double t = rayTriangle(ox, oy, oz, dx, dy, dz, a, b, c);
            if (t >= -1e-4 && t <= maxT) {
                return true;
            }
        }
        return false;
    }

    /** Model vertex → world: rotate by the block orientation, offset by the cell. */
    private static void worldVert(float[] pos, int idx, int orient, int cx, int cy, int cz, float[] out) {
        float[] r = new float[3];
        LodOrientation.rotate(orient, pos[idx * 3], pos[idx * 3 + 1], pos[idx * 3 + 2], r);
        out[0] = cx + r[0];
        out[1] = cy + r[1];
        out[2] = cz + r[2];
    }

    /**
     * Möller–Trumbore ray/triangle intersection (double-sided, matching the
     * cull-off renderer). Returns the ray parameter t, or NaN on a miss.
     */
    private static double rayTriangle(double ox, double oy, double oz,
            double dx, double dy, double dz, float[] a, float[] b, float[] c) {
        double e1x = b[0] - a[0], e1y = b[1] - a[1], e1z = b[2] - a[2];
        double e2x = c[0] - a[0], e2y = c[1] - a[1], e2z = c[2] - a[2];
        double px = dy * e2z - dz * e2y, py = dz * e2x - dx * e2z, pz = dx * e2y - dy * e2x;
        double det = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(det) < 1e-12) {
            return Double.NaN;
        }
        double inv = 1.0 / det;
        double tx = ox - a[0], ty = oy - a[1], tz = oz - a[2];
        double u = (tx * px + ty * py + tz * pz) * inv;
        if (u < -1e-5 || u > 1 + 1e-5) {
            return Double.NaN;
        }
        double qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
        double v = (dx * qx + dy * qy + dz * qz) * inv;
        if (v < -1e-5 || u + v > 1 + 1e-5) {
            return Double.NaN;
        }
        return (e2x * qx + e2y * qy + e2z * qz) * inv;
    }
}
