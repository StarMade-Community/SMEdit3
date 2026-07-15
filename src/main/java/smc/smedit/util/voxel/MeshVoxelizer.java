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
package smc.smedit.util.voxel;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

import smc.smedit.mods.IPluginCallback;
import smc.smedit.vecmath.Color3f;
import smc.smedit.vecmath.Point2f;
import smc.smedit.vecmath.Point3f;
import smc.smedit.vecmath.ext.Hull3f;
import smc.smedit.vecmath.ext.Triangle3f;
import smc.smedit.vecmath.logic.ext.Hull3fLogic;

/**
 * Converts a triangle mesh ({@link Hull3f}) into a dense {@link VoxelGrid}. This
 * is the pure-Java, in-house replacement for the external <em>binvox</em> tool:
 * every voxel a triangle passes through is filled (conservative, 26-separating
 * surface voxelization via the Akenine-Möller triangle/box separating-axis
 * test), so the resulting shell is watertight — no holes, unlike the legacy
 * edge-plotting importer. Optionally the interior is then filled solid.
 *
 * <p>Reference: Akenine-Möller, "Fast 3D Triangle-Box Overlap Testing" (2001).
 *
 * @author SMEdit3
 **/
public final class MeshVoxelizer {

    private static final Logger log = Logger.getLogger(MeshVoxelizer.class.getName());

    /** Empty margin (in voxels) left around the model on every side. */
    private static final int PAD = 1;
    /** Upper bound on the longest grid axis, to keep memory sane. */
    public static final int MAX_LONGEST_DIM = 384;
    /** Refuse grids larger than this many cells (~200MB of short[] + bits). */
    private static final long MAX_CELLS = 100_000_000L;

    private MeshVoxelizer() {
    }

    /**
     * Voxelizes {@code hull} so its longest axis spans {@code longestDim} voxels.
     *
     * @param solid      when true, the interior of a closed mesh is filled solid
     *                   (filled voxels get {@code baseColor}); when false only the
     *                   surface is voxelized.
     * @param palette    blocks the import may use; source colors are matched to
     *                   the nearest one. When empty every voxel gets
     *                   {@code baseColor}.
     * @param baseColor  block id used for uncolored triangles, interior fill, and
     *                   as the fallback when the palette is empty.
     */
    public static VoxelGrid voxelize(Hull3f hull, int longestDim, boolean solid,
            ColorPalette palette, short baseColor, IPluginCallback cb) {
        if (hull == null || hull.getTriangles().isEmpty()) {
            throw new IllegalArgumentException("Model has no triangles to voxelize.");
        }
        if (longestDim < 1) {
            longestDim = 1;
        }
        if (longestDim > MAX_LONGEST_DIM) {
            log.log(Level.WARNING, "Requested longest dimension {0} exceeds the {1}-voxel cap; clamping.",
                    new Object[] {longestDim, MAX_LONGEST_DIM});
            longestDim = MAX_LONGEST_DIM;
        }

        Point3f lower = new Point3f();
        Point3f upper = new Point3f();
        Hull3fLogic.getBounds(hull, lower, upper);
        float spanX = upper.x - lower.x;
        float spanY = upper.y - lower.y;
        float spanZ = upper.z - lower.z;
        float longest = Math.max(spanX, Math.max(spanY, spanZ));
        if (longest <= 0f) {
            throw new IllegalArgumentException("Model has zero size; nothing to voxelize.");
        }
        float scale = longestDim / longest;

        int sizeX = (int) Math.ceil(spanX * scale) + 2 * PAD + 1;
        int sizeY = (int) Math.ceil(spanY * scale) + 2 * PAD + 1;
        int sizeZ = (int) Math.ceil(spanZ * scale) + 2 * PAD + 1;
        long cells = (long) sizeX * sizeY * sizeZ;
        if (cells > MAX_CELLS) {
            throw new IllegalArgumentException("Voxel grid too large (" + sizeX + "x" + sizeY + "x" + sizeZ
                    + "). Reduce the longest dimension.");
        }
        VoxelGrid grid = new VoxelGrid(sizeX, sizeY, sizeZ);

        cb.setStatus("Voxelizing " + sizeX + "x" + sizeY + "x" + sizeZ);
        cb.startTask(hull.getTriangles().size());
        double[] v0 = new double[3];
        double[] v1 = new double[3];
        double[] v2 = new double[3];
        for (Triangle3f t : hull.getTriangles()) {
            cb.workTask(1);
            toVoxelSpace(t.getA(), lower, scale, v0);
            toVoxelSpace(t.getB(), lower, scale, v1);
            toVoxelSpace(t.getC(), lower, scale, v2);
            short id = colorFor(t, palette, baseColor);
            rasterize(grid, v0, v1, v2, id);
            if (cb.isPleaseCancel()) {
                cb.endTask();
                return grid;
            }
        }
        cb.endTask();

        if (solid) {
            fillInterior(grid, baseColor, cb);
        }
        log.log(Level.INFO, "Voxelized {0} triangles into {1} surface voxels",
                new Object[] {hull.getTriangles().size(), grid.occupiedCount()});
        return grid;
    }

    private static void toVoxelSpace(Point3f p, Point3f lower, float scale, double[] out) {
        out[0] = (p.x - lower.x) * scale + PAD;
        out[1] = (p.y - lower.y) * scale + PAD;
        out[2] = (p.z - lower.z) * scale + PAD;
    }

    /** Marks every voxel the triangle overlaps (conservative). */
    private static void rasterize(VoxelGrid grid, double[] v0, double[] v1, double[] v2, short id) {
        int minX = clamp((int) Math.floor(min3(v0[0], v1[0], v2[0])), 0, grid.getSizeX() - 1);
        int maxX = clamp((int) Math.floor(max3(v0[0], v1[0], v2[0])), 0, grid.getSizeX() - 1);
        int minY = clamp((int) Math.floor(min3(v0[1], v1[1], v2[1])), 0, grid.getSizeY() - 1);
        int maxY = clamp((int) Math.floor(max3(v0[1], v1[1], v2[1])), 0, grid.getSizeY() - 1);
        int minZ = clamp((int) Math.floor(min3(v0[2], v1[2], v2[2])), 0, grid.getSizeZ() - 1);
        int maxZ = clamp((int) Math.floor(max3(v0[2], v1[2], v2[2])), 0, grid.getSizeZ() - 1);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (triBoxOverlap(x + 0.5, y + 0.5, z + 0.5, 0.5, 0.5, 0.5, v0, v1, v2)) {
                        grid.set(x, y, z, id);
                    }
                }
            }
        }
    }

    /**
     * Fills the interior of a closed, watertight shell. Flood-fills the "outside"
     * from the grid border through empty voxels (6-connectivity); any empty voxel
     * the flood never reaches is interior and gets {@code baseColor}. Because the
     * surface pass is 26-separating, the flood cannot leak through it.
     */
    private static void fillInterior(VoxelGrid grid, short baseColor, IPluginCallback cb) {
        cb.setStatus("Filling interior");
        int sx = grid.getSizeX();
        int sy = grid.getSizeY();
        int sz = grid.getSizeZ();
        java.util.BitSet outside = new java.util.BitSet(grid.cellCount());
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        // Seed from every empty border voxel.
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    boolean border = x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1;
                    if (border && !grid.isSet(x, y, z)) {
                        int idx = (x * sy + y) * sz + z;
                        if (!outside.get(idx)) {
                            outside.set(idx);
                            stack.push(new int[] {x, y, z});
                        }
                    }
                }
            }
        }
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            for (int[] d : dirs) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                int nz = p[2] + d[2];
                if (!grid.inBounds(nx, ny, nz) || grid.isSet(nx, ny, nz)) {
                    continue;
                }
                int idx = (nx * sy + ny) * sz + nz;
                if (!outside.get(idx)) {
                    outside.set(idx);
                    stack.push(new int[] {nx, ny, nz});
                }
            }
        }
        // Anything empty and not reached from outside is interior.
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    if (!grid.isSet(x, y, z)) {
                        int idx = (x * sy + y) * sz + z;
                        if (!outside.get(idx)) {
                            grid.set(x, y, z, baseColor);
                        }
                    }
                }
            }
        }
    }

    // ---- color selection ----

    private static short colorFor(Triangle3f t, ColorPalette palette, short baseColor) {
        if (t.getAUV() != null && t.getBUV() != null && t.getCUV() != null && t.getTexture() != null) {
            int rgb = sampleTexture(t);
            return palette.nearest(rgb, baseColor);
        }
        if (t.getColor() != null) {
            return palette.nearest(toRgb(t.getColor()), baseColor);
        }
        return baseColor;
    }

    /** Samples the triangle's texture at its centroid UV. */
    private static int sampleTexture(Triangle3f t) {
        Point2f a = t.getAUV();
        Point2f b = t.getBUV();
        Point2f c = t.getCUV();
        float u = (a.x + b.x + c.x) / 3f;
        float v = (a.y + b.y + c.y) / 3f;
        u -= Math.floor(u); // wrap to [0,1)
        v -= Math.floor(v);
        BufferedImage img = t.getTexture();
        int x = clamp((int) (u * (img.getWidth() - 1)), 0, img.getWidth() - 1);
        int y = clamp((int) (v * (img.getHeight() - 1)), 0, img.getHeight() - 1);
        return img.getRGB(x, y) & 0xffffff;
    }

    private static int toRgb(Color3f c) {
        int r = clamp((int) (c.x * 255), 0, 255);
        int g = clamp((int) (c.y * 255), 0, 255);
        int b = clamp((int) (c.z * 255), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    // ---- Akenine-Möller triangle/box overlap (SAT, 13 axes) ----

    /**
     * True if the axis-aligned box centered at ({@code cx,cy,cz}) with half-sizes
     * ({@code hx,hy,hz}) overlaps triangle ({@code a,b,c}). Verts are given as
     * {@code double[3]}. Translated faithfully from Akenine-Möller's reference.
     */
    static boolean triBoxOverlap(double cx, double cy, double cz,
            double hx, double hy, double hz, double[] a, double[] b, double[] c) {
        // Move everything so the box is centered at the origin.
        double v0x = a[0] - cx, v0y = a[1] - cy, v0z = a[2] - cz;
        double v1x = b[0] - cx, v1y = b[1] - cy, v1z = b[2] - cz;
        double v2x = c[0] - cx, v2y = c[1] - cy, v2z = c[2] - cz;

        // Triangle edges.
        double e0x = v1x - v0x, e0y = v1y - v0y, e0z = v1z - v0z;
        double e1x = v2x - v1x, e1y = v2y - v1y, e1z = v2z - v1z;
        double e2x = v0x - v2x, e2y = v0y - v2y, e2z = v0z - v2z;

        // 9 edge-cross-axis tests.
        double fex = Math.abs(e0x), fey = Math.abs(e0y), fez = Math.abs(e0z);
        if (sep(e0z * v0y - e0y * v0z, e0z * v2y - e0y * v2z, fez * hy + fey * hz)) return false; // X01
        if (sep(-e0z * v0x + e0x * v0z, -e0z * v2x + e0x * v2z, fez * hx + fex * hz)) return false; // Y02
        if (sep(e0y * v1x - e0x * v1y, e0y * v2x - e0x * v2y, fey * hx + fex * hy)) return false; // Z12

        fex = Math.abs(e1x); fey = Math.abs(e1y); fez = Math.abs(e1z);
        if (sep(e1z * v0y - e1y * v0z, e1z * v2y - e1y * v2z, fez * hy + fey * hz)) return false; // X01
        if (sep(-e1z * v0x + e1x * v0z, -e1z * v2x + e1x * v2z, fez * hx + fex * hz)) return false; // Y02
        if (sep(e1y * v0x - e1x * v0y, e1y * v1x - e1x * v1y, fey * hx + fex * hy)) return false; // Z0

        fex = Math.abs(e2x); fey = Math.abs(e2y); fez = Math.abs(e2z);
        if (sep(e2z * v0y - e2y * v0z, e2z * v1y - e2y * v1z, fez * hy + fey * hz)) return false; // X2
        if (sep(-e2z * v0x + e2x * v0z, -e2z * v1x + e2x * v1z, fez * hx + fex * hz)) return false; // Y1
        if (sep(e2y * v1x - e2x * v1y, e2y * v2x - e2x * v2y, fey * hx + fex * hy)) return false; // Z12

        // 3 box-face axes: the triangle's AABB must overlap the box.
        if (min3(v0x, v1x, v2x) > hx || max3(v0x, v1x, v2x) < -hx) return false;
        if (min3(v0y, v1y, v2y) > hy || max3(v0y, v1y, v2y) < -hy) return false;
        if (min3(v0z, v1z, v2z) > hz || max3(v0z, v1z, v2z) < -hz) return false;

        // Triangle-plane axis.
        double nx = e0y * e1z - e0z * e1y;
        double ny = e0z * e1x - e0x * e1z;
        double nz = e0x * e1y - e0y * e1x;
        return planeBoxOverlap(nx, ny, nz, v0x, v0y, v0z, hx, hy, hz);
    }

    /** Shared body of the AXISTEST macros: separated if the projected span clears the box radius. */
    private static boolean sep(double p0, double p1, double rad) {
        double min = Math.min(p0, p1);
        double max = Math.max(p0, p1);
        return min > rad || max < -rad;
    }

    private static boolean planeBoxOverlap(double nx, double ny, double nz,
            double vx, double vy, double vz, double hx, double hy, double hz) {
        double vminx, vmaxx, vminy, vmaxy, vminz, vmaxz;
        if (nx > 0) { vminx = -hx - vx; vmaxx = hx - vx; } else { vminx = hx - vx; vmaxx = -hx - vx; }
        if (ny > 0) { vminy = -hy - vy; vmaxy = hy - vy; } else { vminy = hy - vy; vmaxy = -hy - vy; }
        if (nz > 0) { vminz = -hz - vz; vmaxz = hz - vz; } else { vminz = hz - vz; vmaxz = -hz - vz; }
        if (nx * vminx + ny * vminy + nz * vminz > 0) return false;
        return nx * vmaxx + ny * vmaxy + nz * vmaxz >= 0;
    }

    private static double min3(double a, double b, double c) {
        return Math.min(a, Math.min(b, c));
    }

    private static double max3(double a, double b, double c) {
        return Math.max(a, Math.max(b, c));
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
