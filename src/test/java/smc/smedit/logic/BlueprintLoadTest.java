package smc.smedit.logic;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

import smc.smedit.data.SparseMatrix;
import smc.smedit.mods.IPluginCallback;
import smc.smedit.ship.data.Block;
import smc.smedit.ship.data.Blueprint;
import smc.smedit.ship.logic.ShipLogic;

/**
 * End-to-end load of a modern StarMade blueprint <em>directory</em> (v5
 * header/meta + {@code .smd3} DATA). Skipped when the StarMade source isn't
 * present. Verifies that newer, unparseable metadata does not block loading the
 * blocks.
 */
class BlueprintLoadTest {

    private static final File ISANTH = new File("/home/videogoose/Projects/StarMade/src/main/"
            + "resources/blueprints-default/Isanth Type-PNR-25-B");

    @Test
    void opensModernBlueprintDirectory() throws Exception {
        assumeTrue(new File(ISANTH, "DATA").isDirectory(), "StarMade blueprint fixture not present; skipping");

        Blueprint bp = BlueprintLogic.readBlueprint(ISANTH, new NoopCallback());
        SparseMatrix<Block> grid = ShipLogic.getBlocks(bp.getData());

        assertTrue(grid.size() > 10, "expected a real ship, got " + grid.size() + " blocks");
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
