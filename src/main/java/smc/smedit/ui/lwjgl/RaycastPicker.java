package smc.smedit.ui.lwjgl;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.glu.GLU;

import smc.smedit.data.SparseMatrix;
import smc.smedit.ship.data.Block;
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
            if (grid.contains(x, y, z)) {
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
}
