package smc.smedit.ship.logic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import smc.smedit.BlueprintFixtures;
import smc.smedit.ship.data.ControllerEntry;
import smc.smedit.ship.data.Logic;

/**
 * Verifies SMEdit reads and writes the modern {@code logic.smbpl} control-element
 * format. The key check is a <em>byte-exact round-trip</em> against a real
 * StarMade file: reading it and writing it back must reproduce the original bytes
 * exactly, which proves the reader/writer match StarMade's
 * {@code ControlElementMap#serializeForDisk} without needing the game to verify.
 *
 * <p>Previously {@link LogicLogic#readFile} misread the modern layout — it took
 * the {@code -(1024+version)} marker as the controller count and silently
 * returned an empty control map.
 */
class LogicLogicTest {

    /** First real logic.smbpl we can find (bundled fixtures first), or null. */
    private static File findRealLogicFile() {
        String[] ships = {
            "Isanth Type-PNR-25-B", "Isanth Type-PNR-25-C", "Isanth Type-PNR-25-M",
        };
        // Bundled fixtures (unpacked from default-blueprints.zip); run in CI.
        for (String ship : ships) {
            File bp = BlueprintFixtures.blueprint(ship);
            if (bp != null) {
                File f = new File(bp, "logic.smbpl");
                if (f.isFile() && f.length() > 12) {
                    return f;
                }
            }
        }
        // Fall back to a local StarMade install.
        String[] roots = {
            "/home/videogoose/Games/StarMade/Release",
            "/home/videogoose/Projects/StarMade/src/main/resources",
        };
        for (String root : roots) {
            for (String ship : ships) {
                File f = new File(root + "/blueprints-default/" + ship + "/logic.smbpl");
                if (f.isFile() && f.length() > 12) {
                    return f;
                }
            }
        }
        return null;
    }

    @Test
    void readsModernControlMapAndRoundTripsByteExact() throws Exception {
        File file = findRealLogicFile();
        assumeTrue(file != null, "No real logic.smbpl available; skipping");

        byte[] original = Files.readAllBytes(file.toPath());

        Logic logic = LogicLogic.readFile(new ByteArrayInputStream(original), true);

        // Modern on-disk marker is -(1024 + serializationVersion=2) = -1026.
        assertEquals(-(1024 + 2), logic.getControlMapMarker(),
                "expected the modern control-map marker");
        // A real ship links its core to systems, so the control map is non-empty
        // (the old reader returned zero controllers here).
        assertFalse(logic.getControllers().isEmpty(),
                "expected a non-empty control map from a real ship");
        for (ControllerEntry c : logic.getControllers()) {
            assertTrue(c.getPosition() != null, "controller must have a position");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LogicLogic.writeFile(logic, out, true);

        assertArrayEquals(original, out.toByteArray(),
                "writing the parsed logic back must reproduce the file byte-for-byte");
    }

    @Test
    void legacyFormatWithoutMarkerStillRoundTrips() throws Exception {
        // Hand-build a legacy (pre-marker) buffer: unknown1, count=0 (non-negative,
        // so it's read as the count, not a marker), no entries.
        ByteArrayOutputStream legacy = new ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(legacy);
        dos.writeInt(0);   // unknown1 / structureVersion
        dos.writeInt(0);   // controller count (non-negative -> legacy, no marker)
        dos.flush();
        byte[] original = legacy.toByteArray();

        Logic logic = LogicLogic.readFile(new ByteArrayInputStream(original), true);
        assertEquals(0, logic.getControlMapMarker(), "legacy files have no marker");
        assertTrue(logic.getControllers().isEmpty());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LogicLogic.writeFile(logic, out, true);
        assertArrayEquals(original, out.toByteArray(),
                "legacy round-trip must not inject a marker");
    }
}
