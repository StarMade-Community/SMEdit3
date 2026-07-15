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
 **/
package smc.smedit.ui;

import java.util.ArrayList;
import java.util.List;

import smc.smedit.vecmath.Point3i;

/**
 * Broadcasts the grid cell under the viewport cursor to the status bar. The
 * viewport mouse adapters report the hovered cell here on {@code mouseMoved} (and
 * {@code null} on exit); the {@link StatusPanel} listens and shows the live
 * x / y / z readout. A tiny singleton observable — like
 * {@link smc.smedit.ui.tool.ToolController} — so the renderer and the status bar
 * stay decoupled. All access is on the AWT event thread.
 */
public final class CursorStatus {

    /** Notified when the hovered grid cell changes ({@code null} = cursor off any block). */
    public interface Listener {
        void cursorMoved(Point3i cell);
    }

    private static final CursorStatus INSTANCE = new CursorStatus();

    public static CursorStatus get() {
        return INSTANCE;
    }

    private final List<Listener> listeners = new ArrayList<>();
    private Point3i cell;

    private CursorStatus() {
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    /** The last reported hovered cell, or {@code null}. */
    public Point3i getCell() {
        return cell;
    }

    /** Reports the grid cell under the cursor (or {@code null} on a miss / exit). */
    public void report(Point3i cell) {
        this.cell = cell;
        for (Listener l : new ArrayList<>(listeners)) {
            l.cursorMoved(cell);
        }
    }
}
