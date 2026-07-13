package smc.smedit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import smc.smedit.data.BlockTypes;

/**
 * Sanity check that the JUnit harness is wired up and can see the main classes.
 * Real coverage (blueprint format round-trips) is added alongside the .smd3
 * reader.
 */
class HarnessSmokeTest {

    @Test
    void junitRunsAgainstMainClasses() {
        assertEquals((short) 1, BlockTypes.CORE_ID, "ship core block id");
    }
}
