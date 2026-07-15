/**
 * Copyright 2014
 * SMEdit https://github.com/StarMade/SMEdit
 * SMTools https://github.com/StarMade/SMTools
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 **/
package smc.smedit.util.voxel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import smc.smedit.mods.IPluginCallback;
import smc.smedit.vecmath.Point3f;
import smc.smedit.vecmath.ext.Hull3f;
import smc.smedit.vecmath.ext.Triangle3f;

/**
 * Verifies the conservative surface voxelization (no holes) and solid fill.
 */
class MeshVoxelizerTest {

    private static final short BASE = 5;

    /** A no-op progress callback. */
    private static IPluginCallback cb() {
        return new IPluginCallback() {
            @Override public void setStatus(String status) { }
            @Override public void startTask(int size) { }
            @Override public void workTask(int amnt) { }
            @Override public void endTask() { }
            @Override public boolean isPleaseCancel() { return false; }
            @Override public void setErrorTitle(String title) { }
            @Override public void setErrorDescription(String desc) { }
            @Override public void setError(Throwable t) { }
        };
    }

    @Test
    void hollowCubeIsWatertightAndSolidFillsInterior() {
        Hull3f cube = unitCube();
        ColorPalette empty = ColorPalette.fromBlockIds(java.util.Collections.emptyList());

        VoxelGrid hollow = MeshVoxelizer.voxelize(cube, 16, false, empty, BASE, cb());
        assertTrue(hollow.occupiedCount() > 0, "surface voxelization produced no voxels");
        int cx = hollow.getSizeX() / 2, cy = hollow.getSizeY() / 2, cz = hollow.getSizeZ() / 2;
        assertFalse(hollow.isSet(cx, cy, cz),
                "surface-only voxelization must leave the interior empty");

        VoxelGrid solid = MeshVoxelizer.voxelize(cube, 16, true, empty, BASE, cb());
        assertTrue(solid.isSet(cx, cy, cz),
                "solid fill must reach the center — which only works if the shell is watertight");
        assertTrue(solid.occupiedCount() > hollow.occupiedCount(),
                "solid fill must add interior voxels");
    }

    @Test
    void rotatedCubeStaysWatertight() {
        // A cube rotated off-axis exercises the SAT edge-cross axes (diagonal
        // faces), unlike an axis-aligned cube. Solid fill reaching the center
        // proves the conservative surface left no leaks.
        Hull3f cube = unitCube();
        for (Triangle3f t : cube.getTriangles()) {
            rotate(t.getA());
            rotate(t.getB());
            rotate(t.getC());
        }
        ColorPalette empty = ColorPalette.fromBlockIds(java.util.Collections.emptyList());
        VoxelGrid solid = MeshVoxelizer.voxelize(cube, 24, true, empty, BASE, cb());
        assertTrue(solid.isSet(solid.getSizeX() / 2, solid.getSizeY() / 2, solid.getSizeZ() / 2),
                "rotated cube's interior center must fill — surface must be watertight on diagonal faces");
    }

    /** Rotate a point ~35 deg about Y then ~25 deg about X, in place. */
    private static void rotate(Point3f p) {
        double ay = Math.toRadians(35), ax = Math.toRadians(25);
        double x1 = p.x * Math.cos(ay) + p.z * Math.sin(ay);
        double z1 = -p.x * Math.sin(ay) + p.z * Math.cos(ay);
        double y2 = p.y * Math.cos(ax) - z1 * Math.sin(ax);
        double z2 = p.y * Math.sin(ax) + z1 * Math.cos(ax);
        p.x = (float) x1;
        p.y = (float) y2;
        p.z = (float) z2;
    }

    @Test
    void triBoxOverlapDetectsCrossingTriangles() {
        double[] a = {-1, -1, 0}, b = {1, -1, 0}, c = {0, 1, 0};
        assertTrue(MeshVoxelizer.triBoxOverlap(0, 0, 0, 0.5, 0.5, 0.5, a, b, c),
                "a triangle through the origin must overlap the unit box there");

        double[] fa = {10, 10, 10}, fb = {11, 10, 10}, fc = {10, 11, 10};
        assertFalse(MeshVoxelizer.triBoxOverlap(0, 0, 0, 0.5, 0.5, 0.5, fa, fb, fc),
                "a far-away triangle must not overlap the box at the origin");
    }

    private static Hull3f unitCube() {
        Point3f v000 = new Point3f(0, 0, 0), v100 = new Point3f(1, 0, 0);
        Point3f v010 = new Point3f(0, 1, 0), v110 = new Point3f(1, 1, 0);
        Point3f v001 = new Point3f(0, 0, 1), v101 = new Point3f(1, 0, 1);
        Point3f v011 = new Point3f(0, 1, 1), v111 = new Point3f(1, 1, 1);
        Hull3f hull = new Hull3f();
        addQuad(hull, v000, v100, v110, v010); // z = 0
        addQuad(hull, v001, v101, v111, v011); // z = 1
        addQuad(hull, v000, v100, v101, v001); // y = 0
        addQuad(hull, v010, v110, v111, v011); // y = 1
        addQuad(hull, v000, v010, v011, v001); // x = 0
        addQuad(hull, v100, v110, v111, v101); // x = 1
        return hull;
    }

    private static void addQuad(Hull3f hull, Point3f a, Point3f b, Point3f c, Point3f d) {
        hull.getTriangles().add(new Triangle3f(a, b, c));
        hull.getTriangles().add(new Triangle3f(a, c, d));
    }
}
