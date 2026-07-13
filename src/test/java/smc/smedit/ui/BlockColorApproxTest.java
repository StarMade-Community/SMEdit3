package smc.smedit.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import smc.smedit.logic.StarMadeLogic;

/**
 * Covers the texture-derived, disk-cached block fill colors in
 * {@link BlockTypeColors}. The end-to-end sampling test needs a StarMade install
 * (BlockConfig.xml + Default texture pack) and is skipped otherwise; the cache
 * fingerprint/parse test is self-contained.
 */
class BlockColorApproxTest {

    /** First install dir that has the config + textures we need, or null. */
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
    void approximatesVariedColorsFromTextures() {
        File base = installBaseDir();
        assumeTrue(base != null, "StarMade install with textures not present; skipping");

        StarMadeLogic.setBaseDir(base.getAbsolutePath());

        // Any lookup triggers the one-time load + sampling.
        Color c = BlockTypeColors.getFillColor((short) 5);
        assertNotNull(c, "fill color should never be null");

        Map<Short, Color> approx = BlockTypeColors.approximatedFillColors();
        assertFalse(approx.isEmpty(), "expected colors sampled from block textures");

        // A real block set spans many textures, so the sampled colors must vary
        // (a constant result would mean we're returning a fallback, not sampling).
        long distinct = approx.values().stream().distinct().count();
        assertTrue(distinct > 10, "expected varied sampled colors, got " + distinct + " distinct");

        for (Color v : approx.values()) {
            assertTrue(v.getRed() >= 0 && v.getRed() <= 255, "red in range");
            assertTrue(v.getGreen() >= 0 && v.getGreen() <= 255, "green in range");
            assertTrue(v.getBlue() >= 0 && v.getBlue() <= 255, "blue in range");
        }
    }

    @Test
    void cacheReloadsOnFingerprintMatchAndRebuildsOnMismatch(@TempDir Path tmp) throws Exception {
        File cache = tmp.resolve("block-colors.properties").toFile();
        String fingerprint = "v1;xml=123@456;pack=Default;maps=2;blocks=3";

        // Hand-write a cache file in the on-disk format: fingerprint + id=rrggbb.
        String contents = "fingerprint=" + fingerprint + "\n"
                + "5=ff8040\n"
                + "6=112233\n"
                + "bad=zzzz\n"; // malformed line must be skipped, not fatal
        Files.writeString(cache.toPath(), contents);

        assertTrue(BlockTypeColors.loadColorCache(cache, fingerprint),
                "matching fingerprint should load the cache");
        Map<Short, Color> loaded = BlockTypeColors.approximatedFillColors();
        assertEquals(new Color(0xff8040), loaded.get((short) 5));
        assertEquals(new Color(0x112233), loaded.get((short) 6));

        assertFalse(BlockTypeColors.loadColorCache(cache, "v1;different"),
                "mismatched fingerprint should force a rebuild");
    }

    @Test
    void saveThenLoadRoundTrips(@TempDir Path tmp) throws Exception {
        File cache = tmp.resolve("roundtrip.properties").toFile();
        String fingerprint = "v1;roundtrip";

        // Seed a couple of colors, then persist and reload.
        Map<Short, Color> approx = BlockTypeColors.approximatedFillColors();
        approx.put((short) 5, new Color(0x204060));
        approx.put((short) 6, new Color(0xa0b0c0));

        BlockTypeColors.saveColorCache(cache, fingerprint);
        assertTrue(cache.isFile(), "cache file should be written");

        assertTrue(BlockTypeColors.loadColorCache(cache, fingerprint));
        Map<Short, Color> reloaded = BlockTypeColors.approximatedFillColors();
        assertEquals(new Color(0x204060), reloaded.get((short) 5));
        assertEquals(new Color(0xa0b0c0), reloaded.get((short) 6));
    }
}
