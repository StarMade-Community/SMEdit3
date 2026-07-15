/**
 * Copyright 2014
 * SMEdit https://github.com/StarMade/SMEdit
 * SMTools https://github.com/StarMade/SMTools
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 **/
package smc.smedit.util.voxel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import smc.smedit.data.BlockTypes;
import smc.smedit.ui.BlockTypeColors;

/**
 * Verifies nearest-color matching over a candidate palette. Ground-truth colors
 * are read from the same source the palette uses ({@link BlockTypeColors}) so the
 * test is independent of which StarMade install (if any) is configured.
 */
class ColorPaletteTest {

    @Test
    void emptyPaletteReturnsFallback() {
        ColorPalette empty = ColorPalette.fromBlockIds(Collections.emptyList());
        assertTrue(empty.isEmpty());
        assertEquals((short) 42, empty.nearest(0x123456, (short) 42));
    }

    @Test
    void nearestMatchesTheClosestCandidate() {
        short red = BlockTypes.HULL_COLOR_RED_ID;
        short blue = BlockTypes.HULL_COLOR_BLUE_ID;
        Color redColor = BlockTypeColors.getFillColor(red);
        Color blueColor = BlockTypeColors.getFillColor(blue);
        assumeTrue(!redColor.equals(blueColor), "test needs two distinct block colors");

        ColorPalette palette = ColorPalette.fromBlockIds(Arrays.asList(red, blue));
        assertEquals(red, palette.nearest(redColor.getRGB() & 0xffffff, (short) 0));
        assertEquals(blue, palette.nearest(blueColor.getRGB() & 0xffffff, (short) 0));
    }

    @Test
    void duplicateIdsAreCollapsed() {
        short red = BlockTypes.HULL_COLOR_RED_ID;
        ColorPalette palette = ColorPalette.fromBlockIds(Arrays.asList(red, red, red));
        assertEquals(1, palette.size());
    }
}
