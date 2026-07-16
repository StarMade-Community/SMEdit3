package smc.smedit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import smc.smedit.data.Blocks;
import smc.smedit.data.BlockGroups;

/**
 * Sanity check that the JUnit harness is wired up and can see the main classes.
 * Real coverage (blueprint format round-trips) is added alongside the .smd3
 * reader.
 */
class HarnessSmokeTest {

    @Test
    void junitRunsAgainstMainClasses() {
        assertEquals((short) 1, Blocks.SHIP_CORE.getId(), "ship core block id");
    }
}
