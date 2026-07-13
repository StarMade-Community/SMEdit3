/**
 * Copyright 2014 SMEdit
 * https://github.com/StarMade/SMEdit SMTools
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
package smc.smedit.ui;

import java.awt.Component;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import smc.smedit.logic.StarMadeLogic;

/**
 * A folder picker for manually choosing the StarMade installation directory.
 *
 * <p>Presents a directories-only {@link JFileChooser}. If the chosen folder does
 * not look like a StarMade install (no {@code StarMade.jar}), the user can pick
 * again, use it anyway (manual override), or cancel — so an unusual layout never
 * hard-blocks the user.
 */
public final class StarMadeDirChooser {

    private StarMadeDirChooser() {
    }

    /**
     * Prompts for a StarMade install folder.
     *
     * @param parent  parent component for the dialogs (may be {@code null})
     * @param initial folder to start browsing from (may be {@code null})
     * @return the chosen directory, or {@code null} if the user cancelled
     */
    public static File choose(final Component parent, final File initial) {
        final JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle("Select your StarMade installation folder");
        if (initial != null && initial.isDirectory()) {
            fc.setCurrentDirectory(initial);
        }
        while (true) {
            if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
                return null; // cancelled
            }
            final File dir = fc.getSelectedFile();
            if (dir != null && StarMadeLogic.isStarMadeDirectory(dir.getAbsolutePath())) {
                return dir;
            }
            final Object[] options = {"Choose again", "Use anyway", "Cancel"};
            final int choice = JOptionPane.showOptionDialog(parent,
                    "\"" + dir + "\"\ndoes not look like a StarMade install "
                    + "(no StarMade.jar was found).\n\n"
                    + "You can pick a different folder, use this one anyway, or cancel.",
                    "Not a StarMade folder", JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, options, options[0]);
            if (choice == JOptionPane.YES_OPTION) {
                continue; // Choose again
            } else if (choice == JOptionPane.NO_OPTION) {
                return dir; // Use anyway (manual override)
            } else {
                return null; // Cancel / closed
            }
        }
    }
}
