package smc.smedit.plugins;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

import smc.smedit.factories.planet.comp.MaterialFactory;
import smc.smedit.factories.planet.veg.VegetationFactory;
import smc.smedit.factories.ship.filter.ViewFilterFactory;
import smc.smedit.logic.StarMadeLogic;
import smc.smedit.mods.IBlocksPlugin;
import smc.smedit.mods.IStarMadePlugin;
import smc.smedit.mods.IStarMadePluginFactory;

/**
 * Verifies the in-tree ("built-in") plugins and factories are actually
 * registered and become queryable by menu category. This is the wiring that had
 * been missing — the classes compiled but nothing ever instantiated them.
 *
 * <p>The block-plugin registration needs no StarMade install (the plugins are
 * constructed straight from the classpath). The factory tests need an install,
 * because the factories parse the block config at construction time; they're
 * skipped when no install is present.
 */
class BuiltinPluginsTest {

    /** First install dir that has the block config + textures, or null. */
    private static File installBaseDir() {
        File[] candidates = {
            new File("/home/videogoose/Projects/StarMade/src/main/resources"),
            new File("/home/videogoose/Games/StarMade/Release"),
        };
        for (File base : candidates) {
            if (new File(base, "data/config/BlockConfig.xml").isFile()
                    && new File(base, "data/config/BlockTypes.properties").isFile()
                    && new File(base, "data/textures/block/Default/64/t000.png").isFile()) {
                return base;
            }
        }
        return null;
    }

    @Test
    void registersBuiltinBlockPlugins() {
        BuiltinPlugins.register();

        int direct = StarMadeLogic.getInstance().getBlocksPlugins().size();
        // The full ~45 built-ins must construct cleanly straight from the classpath.
        assertTrue(direct >= 40, "expected the built-in block plugins to register, got " + direct);
    }

    @Test
    void registrationIsIdempotent() {
        BuiltinPlugins.register();
        int after1 = StarMadeLogic.getInstance().getBlocksPlugins().size();
        BuiltinPlugins.register();
        int after2 = StarMadeLogic.getInstance().getBlocksPlugins().size();
        assertTrue(after1 == after2, "register() must not double-register (" + after1 + " -> " + after2 + ")");
    }

    @Test
    void blockPluginsAreQueryableByCategory() {
        BuiltinPlugins.register();

        // Ship import/export tools classify as SUBTYPE_FILE; there should be some.
        List<IBlocksPlugin> file = StarMadeLogic.getBlocksPlugins(
                IBlocksPlugin.TYPE_SHIP, IBlocksPlugin.SUBTYPE_FILE);
        assertFalse(file.isEmpty(), "expected ship file (import/export) plugins in the menu query");

        // Every built-in must expose a name without throwing (the menus call this).
        for (IBlocksPlugin p : StarMadeLogic.getAllBlocksPlugins()) {
            String name = p.getName();
            assertTrue(name != null && !name.isEmpty(),
                    p.getClass().getName() + " returned an empty name");
        }
    }

    @Test
    void resourceBackedFactoriesLoadTheirBundledDefinitions() {
        File base = installBaseDir();
        assumeTrue(base != null, "StarMade install not present; skipping factory test");
        StarMadeLogic.setBaseDir(base.getAbsolutePath());

        // Each factory parses a definitions XML that now ships as a classpath
        // resource; construction must not throw and should yield plugins.
        IStarMadePluginFactory material = assertDoesNotThrow(MaterialFactory::new);
        IStarMadePluginFactory vegetation = assertDoesNotThrow(VegetationFactory::new);
        IStarMadePluginFactory viewFilter = assertDoesNotThrow(ViewFilterFactory::new);

        assertNotNull(material.getPlugins());
        assertNotNull(vegetation.getPlugins());
        assertNotNull(viewFilter.getPlugins());

        // At least one of the bundled definition files must actually parse into
        // plugins (proves the resources are on the classpath and readable).
        int total = material.getPlugins().length
                + vegetation.getPlugins().length
                + viewFilter.getPlugins().length;
        assertTrue(total > 0, "expected the bundled factory definitions to produce plugins, got " + total);

        for (IStarMadePlugin p : material.getPlugins()) {
            assertNotNull(p.getName(), "material plugin name");
        }
    }
}
