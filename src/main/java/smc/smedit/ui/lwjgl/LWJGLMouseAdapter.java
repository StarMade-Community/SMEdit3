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

import smc.smedit.ui.tool.ToolController;
import smc.smedit.vecmath.Point3f;
import smc.smedit.vecmath.Point3i;
import smc.smedit.vecmath.logic.TransformEye;

public class LWJGLMouseAdapter extends MouseAdapter {

    public static final float PIXEL_TO_RADIANS = (1f / 3.14159f / 16f);
    /** Radians per pixel when orbiting (left-drag). */
    private static final float ORBIT_SPEED = 0.01f;
    /** World units per pixel when panning (right/middle-drag). */
    private static final float PAN_SPEED = 0.05f;
    /** Radians per pixel for first-person mouse-look. */
    private static final float LOOK_SPEED = 0.005f;
    /** Pitch clamp for first-person look (~87°), so the view can't flip past vertical. */
    private static final float PITCH_LIMIT = 1.52f;
    /** World units dollied per wheel notch in first-person mode. */
    private static final float FP_DOLLY_STEP = 2f;

    private static final int MOUSE_MODE_NULL = 0;
    private static final int MOUSE_MODE_PIVOT = 1; // orbit around the ship centre
    private static final int MOUSE_MODE_PAN = 3;
    private static final int MOUSE_MODE_TOOL = 4;  // left-drag drives the active tool

    private final LWJGLRenderPanel mPanel;

    private Point mMouseDownAt;
    private Point3f mMousePivotAround;
    private int mMouseMode;

    public LWJGLMouseAdapter(LWJGLRenderPanel panel) {
        mPanel = panel;
    }

    @Override
    public void mousePressed(MouseEvent ev) {
        doMouseDown(ev.getPoint(), ev.getModifiers(), ev.getButton(), ev.getClickCount());
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

    private void doMouseDown(Point p, int modifiers, int button, int clickCount) {
        // Clicking the viewport claims keyboard focus so the fly-camera keys work.
        mPanel.requestViewportFocus();
        mMouseDownAt = p;
        if (button == MouseEvent.BUTTON3) {
            // Right-drag orbits around the ship centre.
            mMouseMode = MOUSE_MODE_PIVOT;
        } else if (button == MouseEvent.BUTTON2) {
            // Middle-drag pans the view.
            mMouseMode = MOUSE_MODE_PAN;
        } else {
            // Left-click is the ACTIVE TOOL's action (paint / erase / select / …),
            // routed through the ToolController — no longer an implicit select.
            // The controller reads the modifiers itself (Alt = pick, Shift/Ctrl =
            // additive select). Left-drag then continues the tool (e.g. painting).
            mMouseMode = MOUSE_MODE_TOOL;
            Point3i hit = mPanel.getPointAt(p.x, p.y);
            if (clickCount >= 2) {
                // Double-click = flood-select the contiguous same-type region (Select
                // tool). Shift adds the region to the selection, Ctrl removes it.
                ToolController.get().onDoubleClick(hit, modifiers);
            } else {
                Point3i place = mPanel.getPlacementAt(p.x, p.y);
                ToolController.get().onPress(hit, place, mPanel, modifiers);
            }
        }
    }

    private void doMouseMove(Point p, int modifiers) {
        int dx = p.x - mMouseDownAt.x;
        int dy = p.y - mMouseDownAt.y;
        if (mMouseMode == MOUSE_MODE_PIVOT) {
            mMouseDownAt = p;
            if ((dx != 0) || (dy != 0)) {
                TransformEye cam = mPanel.mUniverse.getCamera();
                if (mPanel.getCameraMode() == LWJGLRenderPanel.CameraMode.FIRST_PERSON) {
                    // First-person mouse-look: turn in place (location unchanged).
                    // Yaw around WORLD up and pitch around the camera's own right
                    // axis, so the horizon stays level (no roll accumulates), and
                    // clamp pitch just shy of vertical to avoid flipping over.
                    cam.rotate(0, 1, 0, -dx * LOOK_SPEED);
                    float elev = (float) Math.asin(clamp(cam.getForward().y, -1f, 1f));
                    float want = clamp(elev + (-dy * LOOK_SPEED), -PITCH_LIMIT, PITCH_LIMIT);
                    cam.pitch(want - elev);
                } else {
                    // Turntable orbit: move the camera around the ship centre, then
                    // re-level it with lookAt so the horizon stays flat (a plain
                    // rotateAround accumulates roll and the view drifts sideways).
                    cam.rotateAround(mPanel.mOrbitCenter,
                            new Point3f(dx * ORBIT_SPEED, dy * ORBIT_SPEED, 0));
                    cam.lookAt(cam.getLocation(), mPanel.mOrbitCenter);
                }
                mPanel.updateTransform();
            }
        } else if (mMouseMode == MOUSE_MODE_PAN) {
            mMouseDownAt = p;
            if ((dx != 0) || (dy != 0)) {
                mPanel.mUniverse.getCamera().moveRight(-dx * PAN_SPEED);
                mPanel.mUniverse.getCamera().moveUp(dy * PAN_SPEED);
                mPanel.updateTransform();
            }
        } else if (mMouseMode == MOUSE_MODE_TOOL) {
            // Drag-paint / drag-erase / drag-build: re-pick under the cursor and
            // continue the stroke (the controller ignores drags for non-stroke tools).
            Point3i hit = mPanel.getPointAt(p.x, p.y);
            Point3i place = mPanel.getPlacementAt(p.x, p.y);
            ToolController.get().onDrag(hit, place, mPanel);
        }
    }

    private void doMouseUp(Point p, int modifiers) {
        if (mMouseMode == MOUSE_MODE_PIVOT) {
            doMouseMove(p, modifiers);
        } else if (mMouseMode == MOUSE_MODE_TOOL) {
            ToolController.get().onRelease();
        }
        mMouseDownAt = null;
        mMouseMode = MOUSE_MODE_NULL;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private void doMouseWheel(int roll) {
        if (roll == 0) {
            return;
        }
        if (mPanel.getCameraMode() == LWJGLRenderPanel.CameraMode.FIRST_PERSON) {
            // No orbit centre to scale against — just dolly a fixed step.
            mPanel.mUniverse.getCamera().moveForward(-roll * FP_DOLLY_STEP);
            mPanel.updateTransform();
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
