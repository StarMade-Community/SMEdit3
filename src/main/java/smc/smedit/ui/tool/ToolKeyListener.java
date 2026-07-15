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

import java.awt.KeyboardFocusManager;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.text.JTextComponent;

/**
 * Single-key tool shortcuts for the viewport (V=Select, B=Build, P=Paint, …).
 * Registered on the GL canvas alongside the fly-camera keys, so it uses the same
 * LWJGL-polled key path (see {@link LWJGLKeyEventDispatcher}). Tools whose letter
 * clashes with a camera key (Erase=E, Sphere=S) have no shortcut — see
 * {@link EditorTool#hasShortcut()}.
 */
public class ToolKeyListener extends KeyAdapter {

    private final Map<Integer, EditorTool> byKey = new HashMap<>();

    public ToolKeyListener() {
        for (EditorTool tool : EditorTool.values()) {
            if (tool.hasShortcut()) {
                byKey.put((int) Character.toUpperCase(tool.getMnemonic()), tool);
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        // Don't switch tools while typing in a text field (brush search, console).
        if (isTypingInTextField()) {
            return;
        }
        EditorTool tool = byKey.get(e.getKeyCode());
        if (tool != null) {
            ToolController.get().setActive(tool);
        }
    }

    private static boolean isTypingInTextField() {
        java.awt.Component fo = KeyboardFocusManager
                .getCurrentKeyboardFocusManager().getFocusOwner();
        return fo instanceof JTextComponent;
    }
}
