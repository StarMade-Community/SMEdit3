package smc.smedit.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.Iterator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import smc.smedit.BlueprintFixtures;
import smc.smedit.data.SparseMatrix;
import smc.smedit.mods.IPluginCallback;
import smc.smedit.ship.data.Block;
import smc.smedit.ship.data.Blueprint;
import smc.smedit.ship.logic.ShipLogic;
import smc.smedit.vecmath.Matrix4f;

/**
 * Round-trips a {@link Scene} through the {@code .smedit} container and asserts
 * everything survives: object metadata, per-object world transform, docking tree,
 * groups, and the local block grids block-for-block.
 */
class SceneRoundTripTest {

    @Test
    void syntheticSceneSurvivesRoundTrip(@TempDir File tmp) throws Exception {
        // --- build a two-object scene ---
        SceneObject hull = new SceneObject("00000000-0000-0000-0000-00000000aaaa", "Battleship");
        SparseMatrix<Block> hullGrid = new SparseMatrix<>();
        put(hullGrid, 8, 8, 8, (short) 1, (short) 0, (short) 50);
        put(hullGrid, 9, 8, 8, (short) 5, (short) 3, (short) 100);
        put(hullGrid, 8, 9, 8, (short) 598, (short) 12, (short) 75);
        hull.setGrid(hullGrid);

        SceneObject turret = new SceneObject("00000000-0000-0000-0000-00000000bbbb", "Turret 1");
        turret.setVisible(false);
        turret.setLocked(true);
        turret.setDockParentId(hull.getId()); // docked to the hull
        // non-trivial transform: 90° about Y, then translated
        Matrix4f m = new Matrix4f();
        m.setIdentity();
        m.m00 = 0;  m.m02 = 1;
        m.m20 = -1; m.m22 = 0;
        m.m03 = 12; m.m13 = -4; m.m23 = 30;
        turret.setTransform(m);
        SparseMatrix<Block> turretGrid = new SparseMatrix<>();
        put(turretGrid, 0, 0, 0, (short) 3, (short) 1, (short) 25);
        put(turretGrid, 1, 0, 0, (short) 3, (short) 2, (short) 25);
        turret.setGrid(turretGrid);

        Scene scene = new Scene();
        scene.setName("My Fleet");
        scene.setAuthor("tester");
        scene.setCreated("2026-07-15T12:00:00Z");
        scene.getObjects().add(hull);
        scene.getObjects().add(turret);

        SceneGroup escorts = new SceneGroup("g-escorts", "Escorts");
        escorts.setExpanded(false);
        escorts.getMemberIds().add(turret.getId());
        scene.getGroups().add(escorts);

        // --- write then read ---
        File file = new File(tmp, "fleet.smedit");
        SceneLogic.writeScene(scene, file);
        assertTrue(file.isFile() && file.length() > 0, "scene file not written");

        Scene loaded = SceneLogic.readScene(file);

        // --- scene metadata ---
        assertEquals("My Fleet", loaded.getName());
        assertEquals("tester", loaded.getAuthor());
        assertEquals("2026-07-15T12:00:00Z", loaded.getCreated());
        assertEquals(2, loaded.getObjects().size());

        // --- object metadata + docking ---
        SceneObject lHull = loaded.objectById(hull.getId());
        SceneObject lTurret = loaded.objectById(turret.getId());
        assertEquals("Battleship", lHull.getName());
        assertTrue(lHull.isVisible());
        assertFalse(lHull.isLocked());
        assertNull(lHull.getDockParentId());

        assertEquals("Turret 1", lTurret.getName());
        assertFalse(lTurret.isVisible());
        assertTrue(lTurret.isLocked());
        assertEquals(hull.getId(), lTurret.getDockParentId());
        assertEquals(1, loaded.childrenOf(hull.getId()).size());
        assertEquals(1, loaded.roots().size());

        // --- transform preserved field-for-field ---
        assertMatrixEquals(m, lTurret.getTransform());
        Matrix4f id = new Matrix4f();
        id.setIdentity();
        assertMatrixEquals(id, lHull.getTransform());

        // --- groups ---
        assertEquals(1, loaded.getGroups().size());
        SceneGroup lg = loaded.getGroups().get(0);
        assertEquals("Escorts", lg.getName());
        assertFalse(lg.isExpanded());
        assertEquals(1, lg.getMemberIds().size());
        assertEquals(turret.getId(), lg.getMemberIds().get(0));

        // --- grids block-for-block ---
        assertGridEquals(hull.getGrid(), lHull.getGrid());
        assertGridEquals(turret.getGrid(), lTurret.getGrid());
    }

    @Test
    void realBlueprintObjectSurvivesRoundTrip(@TempDir File tmp) throws Exception {
        File bpDir = BlueprintFixtures.blueprint("Isanth Type-PNR-25-B");
        assumeTrue(bpDir != null, "StarMade blueprint fixture not present; skipping");

        Blueprint bp = smc.smedit.logic.BlueprintLogic.readBlueprint(bpDir, new NoopCallback());
        SparseMatrix<Block> original = ShipLogic.getBlocks(bp.getData());
        assumeTrue(original.size() > 10, "fixture produced too few blocks");

        SceneObject ship = new SceneObject();
        ship.setName("Isanth");
        ship.setGrid(original);

        Scene scene = new Scene();
        scene.setName("Single Ship");
        scene.getObjects().add(ship);

        File file = new File(tmp, "isanth.smedit");
        SceneLogic.writeScene(scene, file);
        Scene loaded = SceneLogic.readScene(file);

        assertEquals(1, loaded.getObjects().size());
        assertGridEquals(original, loaded.getObjects().get(0).getGrid());
    }

    // ---- helpers ----

    private static void put(SparseMatrix<Block> grid, int x, int y, int z,
            short id, short orientation, short hp) {
        Block b = new Block(id);
        b.setOrientation(orientation);
        b.setHitPoints(hp);
        grid.set(x, y, z, b);
    }

    private static void assertGridEquals(SparseMatrix<Block> expected, SparseMatrix<Block> actual) {
        assertEquals(expected.size(), actual.size(), "block count differs");
        for (Iterator<smc.smedit.vecmath.Point3i> it = expected.iteratorNonNull(); it.hasNext();) {
            smc.smedit.vecmath.Point3i p = it.next();
            Block e = expected.get(p);
            Block a = actual.get(p);
            assertTrue(a != null, "missing block at " + p);
            assertEquals(e.getBlockID(), a.getBlockID(), "id at " + p);
            assertEquals(e.getOrientation(), a.getOrientation(), "orientation at " + p);
            assertEquals(e.getHitPoints(), a.getHitPoints(), "hitpoints at " + p);
            assertEquals(e.isActive(), a.isActive(), "active at " + p);
        }
    }

    private static void assertMatrixEquals(Matrix4f e, Matrix4f a) {
        assertEquals(e.m00, a.m00, 0f); assertEquals(e.m01, a.m01, 0f);
        assertEquals(e.m02, a.m02, 0f); assertEquals(e.m03, a.m03, 0f);
        assertEquals(e.m10, a.m10, 0f); assertEquals(e.m11, a.m11, 0f);
        assertEquals(e.m12, a.m12, 0f); assertEquals(e.m13, a.m13, 0f);
        assertEquals(e.m20, a.m20, 0f); assertEquals(e.m21, a.m21, 0f);
        assertEquals(e.m22, a.m22, 0f); assertEquals(e.m23, a.m23, 0f);
        assertEquals(e.m30, a.m30, 0f); assertEquals(e.m31, a.m31, 0f);
        assertEquals(e.m32, a.m32, 0f); assertEquals(e.m33, a.m33, 0f);
    }

    private static final class NoopCallback implements IPluginCallback {
        @Override public void setStatus(String status) { }
        @Override public void startTask(int size) { }
        @Override public void workTask(int amnt) { }
        @Override public void endTask() { }
        @Override public void setErrorTitle(String title) { }
        @Override public void setErrorDescription(String desc) { }
        @Override public void setError(Throwable t) { }
        @Override public boolean isPleaseCancel() { return false; }
    }
}
