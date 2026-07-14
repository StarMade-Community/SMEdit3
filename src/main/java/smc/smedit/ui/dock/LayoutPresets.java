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
package smc.smedit.ui.dock;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.andrewauclair.moderndocking.app.DockingState;
import io.github.andrewauclair.moderndocking.app.LayoutPersistence;
import io.github.andrewauclair.moderndocking.exception.DockingLayoutException;
import io.github.andrewauclair.moderndocking.layouts.ApplicationLayout;

import smc.smedit.util.Paths;

/**
 * Named workspace layout presets. Each preset is the full docking arrangement
 * saved to {@code <Settings>/layouts/<name>.layout}, so the user can save the
 * current panel layout under a name and switch between presets later.
 */
public final class LayoutPresets {

    private static final String EXT = ".layout";

    private LayoutPresets() {
    }

    private static File dir() {
        File d = new File(Paths.getSettingsDirectory(), "layouts");
        if (!d.isDirectory()) {
            d.mkdirs();
        }
        return d;
    }

    /** Sanitizes a user-supplied name into a safe file base name. */
    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9 _-]", "_").trim();
    }

    private static File file(String name) {
        return new File(dir(), sanitize(name) + EXT);
    }

    /** @return the saved preset names, sorted. */
    public static List<String> names() {
        File[] files = dir().listFiles((d, n) -> n.endsWith(EXT));
        List<String> names = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                String n = f.getName();
                names.add(n.substring(0, n.length() - EXT.length()));
            }
        }
        return names;
    }

    /** Saves the current docking arrangement under {@code name}. */
    public static void save(String name) throws DockingLayoutException {
        LayoutPersistence.saveLayoutToFile(file(name), DockingState.getApplicationLayout());
    }

    /** Restores the named preset's docking arrangement. */
    public static void load(String name) throws DockingLayoutException {
        ApplicationLayout layout = LayoutPersistence.loadApplicationLayoutFromFile(file(name));
        DockingState.restoreApplicationLayout(layout);
    }

    /** Deletes the named preset. */
    public static void delete(String name) {
        file(name).delete();
    }
}
