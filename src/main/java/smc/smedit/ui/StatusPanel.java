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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;



public class StatusPanel extends JPanel {

    /** Right-aligned slot in the status bar (e.g. the memory-usage bar). */
    private final JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    private final ToolPanel toolBar;
    /** Non-modal loading indicator (left of the status bar); hidden when idle. */
    private final JPanel loadingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JLabel loadingLabel = new JLabel();
    private final JProgressBar loadingBar = new JProgressBar();

    /**
     * A single thin status row. Application logging now goes to the Console panel
     * (not a status-bar label). Left: web links + the loading indicator. Right: the
     * memory-usage bar + the window resize grip.
     */
    public StatusPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 2));
        toolBar = new ToolPanel(this);

        loadingBar.setIndeterminate(true);
        loadingBar.setPreferredSize(new Dimension(150, 14));
        loadingPanel.add(loadingLabel);
        loadingPanel.add(loadingBar);
        loadingPanel.setVisible(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.add(toolBar);
        left.add(loadingPanel);
        add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.add(rightPanel);
        right.add(new JLabel(new TriangleSquareWindowsCornerIcon()));
        add(right, BorderLayout.EAST);
    }

    /** Adds a component to the bottom-right of the status bar (left of the resize grip). */
    public void addRightComponent(java.awt.Component c) {
        rightPanel.add(c);
        rightPanel.revalidate();
    }

    /** Shows the non-modal loading indicator with the given text. Call on the EDT. */
    public void showLoading(String text) {
        loadingLabel.setText(text);
        loadingPanel.setVisible(true);
        loadingPanel.revalidate();
        loadingPanel.repaint();
    }

    /** Updates the loading indicator's text (e.g. from a progress callback). Call on the EDT. */
    public void setLoadingText(String text) {
        loadingLabel.setText(text);
    }

    /** Hides the loading indicator. Call on the EDT. */
    public void hideLoading() {
        loadingPanel.setVisible(false);
        loadingPanel.revalidate();
        loadingPanel.repaint();
    }
}

