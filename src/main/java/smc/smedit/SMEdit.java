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

import smc.smedit.util.GlobalConfiguration;
import smc.smedit.util.OptionScreen;

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

    public static void main(final String[] args) {
        GlobalConfiguration.createDirectories();
        new OptionScreen(args);
    }
}
