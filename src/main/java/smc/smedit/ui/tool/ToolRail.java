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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

import org.kordamp.ikonli.swing.FontIcon;

/**
 * The fixed left-hand tool rail — a vertical column of icon toggles, one per
 * {@link EditorTool}, with exactly one active at a time. This is the visual
 * spine of the paint/modelling-tool layout: picking a tool here decides what a
 * left-click in the viewport does (via {@link ToolController}).
 *
 * <p>It's a plain always-visible chrome strip (like an IDE activity bar), not a
 * dockable panel — the rail should never be torn off or hidden.
 */
@SuppressWarnings("serial")
public class ToolRail extends JPanel {

    private static final int ICON_SIZE = 20;
    private static final int BUTTON = 40;

    private final Map<EditorTool, JToggleButton> buttons = new EnumMap<>(EditorTool.class);
    private final transient ToolController controller = ToolController.get();

    public ToolRail() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(6, 5, 6, 5));

        ButtonGroup group = new ButtonGroup();
        for (EditorTool tool : EditorTool.values()) {
            JToggleButton b = makeButton(tool);
            group.add(b);
            buttons.put(tool, b);
            add(b);
            add(Box.createVerticalStrut(3));
        }
        add(Box.createVerticalGlue());

        buttons.get(controller.getActive()).setSelected(true);

        // Keep the rail in sync when the tool is changed elsewhere (keyboard
        // shortcuts, command palette, etc.).
        controller.addListener(tool -> {
            JToggleButton b = buttons.get(tool);
            if (b != null && !b.isSelected()) {
                b.setSelected(true);
            }
        });
    }

    private JToggleButton makeButton(EditorTool tool) {
        JToggleButton b = new JToggleButton(icon(tool));
        String key = tool.hasShortcut() ? "  (" + tool.getMnemonic() + ")" : "";
        b.setToolTipText("<html><b>" + tool.getDisplayName() + "</b>" + key
                + "<br>" + tool.getTooltip() + "</html>");
        b.setFocusable(false);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMargin(new Insets(4, 4, 4, 4));
        b.setMaximumSize(new Dimension(BUTTON, BUTTON));
        b.setPreferredSize(new Dimension(BUTTON, BUTTON));
        // Rounded, borderless "toolbar button" look; FlatLaf paints the selected
        // state as a filled accent chip.
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.addActionListener(e -> controller.setActive(tool));
        return b;
    }

    private static Icon icon(EditorTool tool) {
        return FontIcon.of(tool.getIcon(), ICON_SIZE, iconColor());
    }

    /** The current theme's foreground colour for glyphs (dark on light, light on dark). */
    private static Color iconColor() {
        Color fg = UIManager.getColor("Button.foreground");
        return fg != null ? fg : new Color(0xB8, 0xB8, 0xB8);
    }
}
