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

import java.awt.BorderLayout;
import java.awt.Component;

import javax.swing.JPanel;

import io.github.andrewauclair.moderndocking.Dockable;
import io.github.andrewauclair.moderndocking.app.Docking;

/**
 * A generic {@link Dockable} that wraps an existing Swing component, so the
 * editor's panels (viewport, brush palette, console, …) become
 * dockable/floating/collapsible without each having to implement the docking
 * API itself.
 *
 * <p>Registers itself with {@link Docking} on construction, so
 * {@code Docking.initialize(window)} must have been called first.
 */
public class DockPanel extends JPanel implements Dockable {

    private final String persistentId;
    private final String tabText;
    private final boolean closable;
    private final boolean floatingAllowed;
    private final boolean autoHideAllowed;

    /**
     * @param persistentId    stable id used for layout persistence (must be unique)
     * @param tabText         title shown in the dock header / tab
     * @param content         the component to host (added to CENTER); may be null
     * @param closable        whether the user can close the panel
     * @param floatingAllowed whether the panel may be torn out into its own window
     *                        (keep false for the OpenGL viewport — tearing a
     *                        heavyweight canvas into a new window breaks its context)
     * @param autoHideAllowed whether the panel can be collapsed/minimized (pinned)
     *                        to a window edge and re-expanded on demand
     */
    public DockPanel(String persistentId, String tabText, Component content,
            boolean closable, boolean floatingAllowed, boolean autoHideAllowed) {
        super(new BorderLayout());
        this.persistentId = persistentId;
        this.tabText = tabText;
        this.closable = closable;
        this.floatingAllowed = floatingAllowed;
        this.autoHideAllowed = autoHideAllowed;
        if (content != null) {
            add(content, BorderLayout.CENTER);
        }
        Docking.registerDockable(this);
    }

    @Override
    public String getPersistentID() {
        return persistentId;
    }

    @Override
    public String getTabText() {
        return tabText;
    }

    @Override
    public boolean isClosable() {
        return closable;
    }

    @Override
    public boolean isFloatingAllowed() {
        return floatingAllowed;
    }

    @Override
    public boolean isAutoHideAllowed() {
        return autoHideAllowed;
    }

    @Override
    public boolean isWrappableInScrollpane() {
        // Never let the docking framework wrap our content in a JScrollPane: the
        // viewport is a heavyweight GL canvas that must simply fill its area (an
        // outer scroll pane gives it stray horizontal/vertical scrollbars), and the
        // console/block-info panels already contain their own scroll panes.
        return false;
    }
}
