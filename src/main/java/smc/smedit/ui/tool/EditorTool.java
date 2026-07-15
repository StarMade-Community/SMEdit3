/**
 * Copyright 2014 SMEdit https://github.com/StarMade/SMEdit SMTools
 * https://github.com/StarMade/SMTools
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package smc.smedit.ui.tool;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;

/**
 * The interactive editing tools shown on the left tool rail — the "active tool"
 * model shared by paint / voxel-modelling programs (MagicaVoxel, Aseprite,
 * Blender). Exactly one is active at a time (see {@link ToolController}); a
 * left-click in the viewport is routed to whichever tool that is.
 *
 * <p>This is deliberately separate from one-shot <em>operations</em> (import,
 * generate, export, macros), which stay in the plugin shelf / menus / command
 * palette. A tool is a persistent mode you paint with; an operation is a command
 * you run once.
 *
 * <p>Not every tool is wired up yet — {@link ToolController} implements SELECT,
 * PAINT, ERASE and PICKER, and logs a "coming soon" note for the rest so the
 * rail stays complete while the behaviours land incrementally.
 */
public enum EditorTool {

    SELECT("Select", 'V', Feather.MOUSE_POINTER,
            "Select blocks — the selected region restricts where other tools apply"),
    BUILD("Build", 'B', Feather.BOX,
            "Place the active block"),
    PAINT("Paint", 'P', Feather.DROPLET,
            "Recolour blocks with the active material"),
    ERASE("Erase", 'E', Feather.TRASH_2,
            "Remove blocks"),
    FILL("Fill", 'G', Feather.GRID,
            "Flood-fill a region with the active block"),
    LINE("Line", 'L', Feather.MINUS,
            "Draw a straight line of blocks"),
    BOX("Box", 'X', Feather.SQUARE,
            "Draw a box / cuboid"),
    SPHERE("Sphere", 'S', Feather.CIRCLE,
            "Draw a sphere / ellipsoid"),
    PICKER("Picker", 'I', Feather.CROSSHAIR,
            "Eyedropper — sample a block type into the brush (also Alt-click)"),
    MOVE("Move", 'M', Feather.MOVE,
            "Move / transform the current selection"),
    MIRROR("Mirror", 'R', Feather.COPY,
            "Mirror / reflect the current selection");

    private final String displayName;
    private final char mnemonic;
    private final Ikon icon;
    private final String tooltip;

    EditorTool(String displayName, char mnemonic, Ikon icon, String tooltip) {
        this.displayName = displayName;
        this.mnemonic = mnemonic;
        this.icon = icon;
        this.tooltip = tooltip;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Keys reserved for the fly-camera (W/A/S/D/E/Q); a tool sharing one gets no shortcut. */
    private static final String CAMERA_KEYS = "WASDEQ";

    /** The tool's single-key shortcut letter (see {@link #hasShortcut()}). */
    public char getMnemonic() {
        return mnemonic;
    }

    /**
     * Whether {@link #getMnemonic()} is an active keyboard shortcut. It isn't when
     * the letter clashes with a fly-camera key (Erase=E, Sphere=S) — those tools
     * stay mouse-only on the rail so the camera keeps the key.
     */
    public boolean hasShortcut() {
        return CAMERA_KEYS.indexOf(Character.toUpperCase(mnemonic)) < 0;
    }

    public Ikon getIcon() {
        return icon;
    }

    public String getTooltip() {
        return tooltip;
    }
}
