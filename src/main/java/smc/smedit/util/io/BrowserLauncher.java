/**
 * Copyright 2014
 * SMEdit https://github.com/StarMade/SMEdit
 * SMTools https://github.com/StarMade/SMTools
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
package smc.smedit.util.io;

import java.awt.Desktop;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

import smc.smedit.util.GlobalConfiguration;
import smc.smedit.util.OperatingSystem;

/**
 * Opens a URL in the user's default web browser (the "Go To" menu links).
 *
 * <p>This is all that survived of the old {@code HttpClient}: its HTTP
 * download/User-Agent machinery existed only for the remote {@code jo_sm.jar}
 * self-update that was removed in Phase 0, so it was dead code.
 */
public final class BrowserLauncher {

    private static final Logger log = Logger.getLogger(BrowserLauncher.class.getName());

    private BrowserLauncher() {
    }

    /** Opens {@code url} in the user's default browser; logs (does not throw) on failure. */
    public static void openURL(final String url) {
        // Prefer the standard AWT Desktop API when the platform supports it.
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    desktop.browse(new URI(url));
                    return;
                } catch (final Exception e) {
                    log.log(Level.FINE, "Desktop.browse failed; falling back to a native launcher", e);
                }
            }
        }
        openURLNative(url);
    }

    /** Fallback for headless-ish platforms where {@link Desktop} isn't usable. */
    private static void openURLNative(final String url) {
        final OperatingSystem os = GlobalConfiguration.getCurrentOperatingSystem();
        try {
            if (os == OperatingSystem.WINDOWS) {
                Runtime.getRuntime().exec(new String[] {"rundll32", "url.dll,FileProtocolHandler", url});
            } else if (os == OperatingSystem.MAC) {
                Runtime.getRuntime().exec(new String[] {"open", url});
            } else { // Unix / Linux
                final String[] browsers = {"xdg-open", "firefox", "chromium",
                    "google-chrome", "opera", "konqueror", "epiphany", "mozilla"};
                for (final String browser : browsers) {
                    if (Runtime.getRuntime().exec(new String[] {"which", browser}).waitFor() == 0) {
                        Runtime.getRuntime().exec(new String[] {browser, url});
                        return;
                    }
                }
                log.log(Level.WARNING, "No web browser found to open {0}", url);
            }
        } catch (final Exception e) {
            log.log(Level.WARNING, "Could not open URL " + url, e);
        }
    }
}
