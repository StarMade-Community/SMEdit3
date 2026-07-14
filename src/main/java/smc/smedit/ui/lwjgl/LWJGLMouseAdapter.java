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
package smc.smedit.ui.lwjgl;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

import smc.smedit.logic.StarMadeLogic;
import smc.smedit.vecmath.Point3f;
import smc.smedit.vecmath.Point3i;
import smc.smedit.vecmath.logic.TransformEye;

public class LWJGLMouseAdapter extends MouseAdapter {

    public static final float PIXEL_TO_RADIANS = (1f / 3.14159f / 16f);
    /** Radians per pixel when orbiting (left-drag). */
    private static final float ORBIT_SPEED = 0.01f;
    /** World units per pixel when panning (right/middle-drag). */
    private static final float PAN_SPEED = 0.05f;

    private static final int MOUSE_MODE_NULL = 0;
    private static final int MOUSE_MODE_PIVOT = 1; // orbit around the ship centre
    private static final int MOUSE_MODE_PAN = 3;

    private final LWJGLRenderPanel mPanel;

    private Point mMouseDownAt;
    private Point3f mMousePivotAround;
    private int mMouseMode;

    public LWJGLMouseAdapter(LWJGLRenderPanel panel) {
        mPanel = panel;
    }

    @Override
    public void mousePressed(MouseEvent ev) {
        doMouseDown(ev.getPoint(), ev.getModifiers(), ev.getButton());
    }

    @Override
    public void mouseReleased(MouseEvent ev) {
        doMouseUp(ev.getPoint(), ev.getModifiers());
    }

    @Override
    public void mouseDragged(MouseEvent ev) {
        if (mMouseDownAt != null) {
            doMouseMove(ev.getPoint(), ev.getModifiers());
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        doMouseWheel(e.getWheelRotation());
    }

    private void doMouseDown(Point p, int modifiers, int button) {
        mMouseDownAt = p;
        if (button == MouseEvent.BUTTON3) {
            // Right-drag orbits around the ship centre.
            mMouseMode = MOUSE_MODE_PIVOT;
        } else if (button == MouseEvent.BUTTON2) {
            // Middle-drag pans the view.
            mMouseMode = MOUSE_MODE_PAN;
        } else {
            // Left-click selects the block under the cursor. Shift/Ctrl forces
            // additive (toggle) selection regardless of the active mode.
            mMouseMode = MOUSE_MODE_NULL;
            Point3i hit = mPanel.getPointAt(p.x, p.y);
            boolean additive = (modifiers
                    & (java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK)) != 0;
            StarMadeLogic.getInstance().getSelection()
                    .applyPick(hit, StarMadeLogic.getModel(), additive);
        }
    }

    private void doMouseMove(Point p, int modifiers) {
        int dx = p.x - mMouseDownAt.x;
        int dy = p.y - mMouseDownAt.y;
        if (mMouseMode == MOUSE_MODE_PIVOT) {
            mMouseDownAt = p;
            if ((dx != 0) || (dy != 0)) {
                // Turntable orbit: move the camera around the ship centre, then
                // re-level it with lookAt so the horizon stays flat (a plain
                // rotateAround accumulates roll and the view drifts sideways).
                TransformEye cam = mPanel.mUniverse.getCamera();
                cam.rotateAround(mPanel.mOrbitCenter,
                        new Point3f(dx * ORBIT_SPEED, dy * ORBIT_SPEED, 0));
                cam.lookAt(cam.getLocation(), mPanel.mOrbitCenter);
                mPanel.updateTransform();
            }
        } else if (mMouseMode == MOUSE_MODE_PAN) {
            mMouseDownAt = p;
            if ((dx != 0) || (dy != 0)) {
                mPanel.mUniverse.getCamera().moveRight(-dx * PAN_SPEED);
                mPanel.mUniverse.getCamera().moveUp(dy * PAN_SPEED);
                mPanel.updateTransform();
            }
        }
    }

    private void doMouseUp(Point p, int modifiers) {
        if (mMouseMode == MOUSE_MODE_PIVOT) {
            doMouseMove(p, modifiers);
            mMouseDownAt = null;
        }
        mMouseMode = MOUSE_MODE_NULL;
    }

    private void doMouseWheel(int roll) {
        if (roll == 0) {
            return;
        }
        // Zoom proportionally to the distance from the ship, so it feels the same
        // whether zoomed out or in close.
        Point3f loc = mPanel.mUniverse.getCamera().getLocation();
        Point3f c = mPanel.mOrbitCenter;
        float dist = (float) Math.sqrt((loc.x - c.x) * (loc.x - c.x)
                + (loc.y - c.y) * (loc.y - c.y) + (loc.z - c.z) * (loc.z - c.z));
        float step = Math.max(1f, dist * 0.15f);
        mPanel.mUniverse.getCamera().moveForward(-roll * step);
        mPanel.updateTransform();
    }

}
