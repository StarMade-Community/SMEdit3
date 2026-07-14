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
package smc.smedit;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatDarkLaf;

import smc.smedit.util.GlobalConfiguration;
import smc.smedit.util.GpuOffload;
import smc.smedit.util.OptionScreen;
import smc.smedit.util.Paths;

/**
 * Application entry point.
 *
 * <p>Creates the local SMEdit directories and opens the {@link OptionScreen},
 * whose "Start SMEdit" button launches the main editor window
 * ({@link smc.smedit.ui.RenderFrame}).
 *
 * <p>Historically this class extended {@code JFrame} and, at startup,
 * downloaded {@code jo_sm.jar} from a remote server and reflectively invoked a
 * {@code Boot} class inside it. That remote-download bootstrap was removed:
 * the real editor ({@code RenderFrame}) already lives in this module, so it is
 * now launched directly. See {@code docs/ARCHITECTURE.md} (Phase 0).
 *
 * @Auther Jo Jaquinta for SMEdit Classic - version 1.0
 * @Auther Robert Barefoot for SMEdit - version 1.1
 */
public class SMEdit {

    private static final Logger log = Logger.getLogger(SMEdit.class.getName());

    public static void main(final String[] args) {
        // On Linux hybrid-graphics systems, re-exec onto the discrete GPU before
        // any AWT/GL init (may exit this process). No-op elsewhere / with -igpu.
        // Must run on the main thread, before the toolkit starts.
        GpuOffload.preferDiscreteGpu(args);

        // Everything below touches Swing (look-and-feel, the folder chooser, and
        // the OptionScreen window), so it must run on the Event Dispatch Thread.
        SwingUtilities.invokeLater(() -> {
            // Apply the modern look-and-feel before any Swing component is
            // created (the folder chooser below is the first one).
            setupLookAndFeel();
            // Resolve the StarMade install folder first (auto-detect, else
            // prompt). SMEdit's own directories live under it, so this must
            // happen before createDirectories(). Cancelling means there's
            // nothing to edit.
            if (!Paths.validateCurrentDirectory()) {
                System.exit(0);
            }
            GlobalConfiguration.createDirectories();
            new OptionScreen(args);
        });
    }

    private static void setupLookAndFeel() {
        try {
            FlatDarkLaf.setup();
        } catch (final Throwable t) {
            log.log(Level.WARNING, "FlatLaf setup failed; using the default look-and-feel. " + t);
        }
    }
}
