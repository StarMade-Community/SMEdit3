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
package smc.smedit.ui.shelf;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.UIManager;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.swing.FontIcon;

/**
 * Resolves a tool-shelf glyph for a plugin. Plugins don't carry their own icon,
 * so we map a keyword in the tool's display name to an Ikonli
 * <a href="https://feathericons.com/">Feather</a> glyph (the icon set already
 * used by the toolbar), falling back to a per-category glyph and finally a
 * generic tool glyph. This keeps icons entirely data-driven: no per-plugin code
 * change is needed, and external/factory plugins get a sensible icon for free.
 */
public final class ShelfIcons {

    private ShelfIcons() {
    }

    /**
     * Name-substring (lower-case) → glyph, tested in order so more specific
     * entries must precede broader ones ("fill with block" before "fill",
     * "select all" before "select", "replace blocks" before "replace").
     */
    private static final Object[][] NAME_GLYPHS = {
        {"import", Feather.DOWNLOAD},
        {"export", Feather.UPLOAD},
        {"library", Feather.ARCHIVE},
        {"copy", Feather.COPY},
        {"cut", Feather.SCISSORS},
        {"paste", Feather.CLIPBOARD},
        {"delete", Feather.TRASH_2},
        {"select all", Feather.CHECK_SQUARE},
        {"select none", Feather.SQUARE},
        {"select", Feather.CROSSHAIR},
        {"move", Feather.MOVE},
        {"rotate", Feather.ROTATE_CW},
        {"scale", Feather.MAXIMIZE_2},
        {"symmetr", Feather.COLUMNS},
        {"reflect", Feather.SHUFFLE},
        {"duplicate", Feather.COPY},
        {"fill with block", Feather.GRID},
        {"fill", Feather.DROPLET},
        {"deck", Feather.LAYERS},
        {"hollow", Feather.CIRCLE},
        {"hull", Feather.BOX},
        {"smooth", Feather.WIND},
        {"soften", Feather.FEATHER},
        {"harden", Feather.SHIELD},
        {"replace blocks", Feather.REFRESH_CW},
        {"replace", Feather.REPEAT},
        {"stripe", Feather.ALIGN_JUSTIFY},
        {"ombre", Feather.SLIDERS},
        {"text", Feather.TYPE},
        {"image", Feather.IMAGE},
        {"causeway", Feather.OCTAGON},
        {"volcano", Feather.APERTURE},
        {"dome", Feather.CIRCLE},
        {"undulating", Feather.ACTIVITY},
        {"shape", Feather.HEXAGON},
        {"record", Feather.DISC},
        {"run", Feather.PLAY},
        {"macro", Feather.TERMINAL},
        {"report", Feather.INFO},
        {"propert", Feather.SLIDERS},
        {"filter", Feather.FILTER},
    };

    /** Fallback glyph per operation category (see {@link PluginCategories#categoryOf}). */
    private static final Map<String, Ikon> CATEGORY_GLYPHS = new LinkedHashMap<>();

    static {
        CATEGORY_GLYPHS.put("Select", Feather.CROSSHAIR);
        CATEGORY_GLYPHS.put("Clipboard", Feather.CLIPBOARD);
        CATEGORY_GLYPHS.put("Transform", Feather.MOVE);
        CATEGORY_GLYPHS.put("Build", Feather.BOX);
        CATEGORY_GLYPHS.put("Surface", Feather.WIND);
        CATEGORY_GLYPHS.put("Paint", Feather.DROPLET);
        CATEGORY_GLYPHS.put("Shape", Feather.HEXAGON);
        CATEGORY_GLYPHS.put("Generate", Feather.BOX);
        CATEGORY_GLYPHS.put("Import", Feather.DOWNLOAD);
        CATEGORY_GLYPHS.put("Export", Feather.UPLOAD);
        CATEGORY_GLYPHS.put("Macro", Feather.TERMINAL);
        CATEGORY_GLYPHS.put("Info", Feather.INFO);
        CATEGORY_GLYPHS.put("Edit", Feather.EDIT_2);
        CATEGORY_GLYPHS.put("Modify", Feather.TOOL);
        CATEGORY_GLYPHS.put("View", Feather.EYE);
        CATEGORY_GLYPHS.put("File", Feather.FILE);
        CATEGORY_GLYPHS.put("General", Feather.GRID);
    }

    /**
     * @param name     the tool's display name (may contain a {@code /} group prefix)
     * @param category the shelf tab it was sorted into, for the fallback glyph
     * @param size     glyph size in px
     * @return a themed {@link Icon} for the tool
     */
    public static Icon iconFor(String name, String category, int size) {
        Ikon glyph = glyphFor(name, category);
        return FontIcon.of(glyph, size, foreground());
    }

    private static Ikon glyphFor(String name, String category) {
        String lower = name == null ? "" : name.toLowerCase();
        for (Object[] entry : NAME_GLYPHS) {
            if (lower.contains((String) entry[0])) {
                return (Ikon) entry[1];
            }
        }
        Ikon byCategory = CATEGORY_GLYPHS.get(category);
        return byCategory != null ? byCategory : Feather.TOOL;
    }

    /** Current theme foreground, matching the toolbar glyph tint. */
    private static Color foreground() {
        Color fg = UIManager.getColor("Button.foreground");
        return fg != null ? fg : new Color(0xB8, 0xB8, 0xB8);
    }
}
