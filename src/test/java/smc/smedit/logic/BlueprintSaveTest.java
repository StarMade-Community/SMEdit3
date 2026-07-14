package smc.smedit.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import smc.smedit.BlueprintFixtures;
import smc.smedit.data.SparseMatrix;
import smc.smedit.data.StarMade;
import smc.smedit.mods.IPluginCallback;
import smc.smedit.ship.data.Block;
import smc.smedit.ship.data.Blueprint;
import smc.smedit.ship.data.ControllerEntry;
import smc.smedit.ship.data.Logic;
import smc.smedit.ship.logic.ShipLogic;
import smc.smedit.ui.logic.ShipSpec;

/**
 * End-to-end save-fidelity test: loading a real blueprint and saving it back
 * (unedited) must preserve its control map exactly. Uses the in-repo
 * {@code blueprints/} fixtures, so it runs in CI without a StarMade install.
 */
class BlueprintSaveTest {

    /** A bundled test blueprint dir (unpacked from default-blueprints.zip), or null. */
    private static File repoBlueprint() {
        for (String name : new String[] {
                "Isanth Type-PNR-25-B", "Isanth Type-PNR-25-C", "Isanth Type-PNR-25-M"}) {
            File dir = BlueprintFixtures.blueprint(name);
            if (dir != null) {
                return dir;
            }
        }
        return null;
    }

    @Test
    void uneditedLoadThenSavePreservesLogicByteExact(@TempDir Path tmp) throws Exception {
        File src = repoBlueprint();
        assumeTrue(src != null, "No bundled blueprint fixture; skipping");

        IPluginCallback cb = new NoopCallback();
        Blueprint bp = BlueprintLogic.readBlueprint(src, cb);
        SparseMatrix<Block> grid = ShipLogic.getBlocks(bp.getData());
        assertTrue(grid.size() > 10, "expected a real ship");

        // Reproduce the load-path wiring: record the control map against its grid.
        StarMadeLogic.getInstance().setLoadedLogic(bp.getLogic(), grid);
        StarMadeLogic.setModel(grid);

        File out = tmp.resolve(src.getName()).toFile();
        ShipSpec spec = new ShipSpec();
        spec.setType(ShipSpec.BLUEPRINT);
        spec.setName(src.getName());
        spec.setFile(out);

        BlueprintLogic.saveBlueprint(grid, spec, false, cb);

        byte[] original = Files.readAllBytes(new File(src, "logic.smbpl").toPath());
        byte[] saved = Files.readAllBytes(new File(out, "logic.smbpl").toPath());
        assertArrayEquals(original, saved,
                "an unedited load->save must reproduce the original logic.smbpl exactly");
    }

    @Test
    void logicIsOnlyReusedForTheExactLoadedGrid() {
        StarMade sm = StarMadeLogic.getInstance();

        SparseMatrix<Block> loadedGrid = new SparseMatrix<>();
        Logic logic = new Logic();
        logic.getControllers().add(new ControllerEntry());
        sm.setLoadedLogic(logic, loadedGrid);

        assertSame(logic, sm.getLogicFor(loadedGrid),
                "the grid it was loaded with must get its logic back");

        // A different grid (an import / new ship / plugin-replaced model) must not
        // inherit the previous ship's wiring — save then falls back to empty.
        SparseMatrix<Block> otherGrid = new SparseMatrix<>();
        assertNull(sm.getLogicFor(otherGrid), "a different grid must not reuse the logic");
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
