package smc.smedit.ui.lwjgl;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.glu.GLU;

import smc.smedit.data.SparseMatrix;
import smc.smedit.ship.data.Block;
import smc.smedit.ui.BlockTypeColors;
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
     * Picks the first block along the ray through screen pixel (screenX, screenY),
     * in LWJGL/GL window space (origin bottom-left — matches {@code Mouse.getEventX/Y}).
     *
     * @return the hit grid position, or {@code null} on a miss.
     */
    public static Point3i pick(float screenX, float screenY, PickMatrices.Snapshot snap,
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
    private static Point3i walk(float[] near, float[] far, SparseMatrix<Block> grid) {
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

        for (int i = 0; i < MAX_STEPS; i++) {
            Block hit = grid.get(x, y, z);
            if (hit != null && hitsCell(hit, x, y, z, near, dx, dy, dz, len)) {
                return new Point3i(x, y, z);
            }
            double tNext;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX;
                tNext = tMaxX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                y += stepY;
                tNext = tMaxY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
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
