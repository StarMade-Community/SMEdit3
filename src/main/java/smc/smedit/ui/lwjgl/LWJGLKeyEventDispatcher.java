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

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JFrame;
import javax.swing.Timer;

import smc.smedit.vecmath.Point3f;
import smc.smedit.vecmath.logic.TransformEye;

/**
 * Fly-style keyboard camera controls for the OpenGL viewport.
 *
 * <p>Keys are <em>held</em>: a key press adds it to {@link #mPressed} and a
 * timer applies movement every tick until it is released, so holding a key
 * moves continuously (the LWJGL-&gt;AWT bridge only sends one press/one release,
 * never auto-repeats). W/S/A/D move relative to the view; E/Q move along world
 * vertical (so "up" is always up, whatever angle you've orbited to). Hold Shift
 * to move faster.
 */
public class LWJGLKeyEventDispatcher implements KeyEventDispatcher, KeyListener {

    private static final int MOVE_FORWARD = 'W';
    private static final int MOVE_BACK = 'S';
    private static final int MOVE_LEFT = 'A';
    private static final int MOVE_RIGHT = 'D';
    private static final int MOVE_UP = 'E';
    private static final int MOVE_DOWN = 'Q';

    private static final Set<Integer> MOVE_KEYS = Set.of(
            MOVE_FORWARD, MOVE_BACK, MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN);

    /** World units per tick (~60 ticks/sec); Shift multiplies this. */
    private static final float STEP = 0.4f;
    private static final float FAST_MULTIPLIER = 4f;

    private final LWJGLRenderPanel mPanel;
    private final Set<Integer> mPressed = Collections.synchronizedSet(new HashSet<>());
    private final Timer mMoveTimer;
    private volatile boolean mShiftDown;

    public LWJGLKeyEventDispatcher(LWJGLRenderPanel panel) {
        mPanel = panel;
        mMoveTimer = new Timer(16, e -> tick());
        mMoveTimer.setCoalesce(true);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        if (!isFocused()) {
            return false;
        }
        if (ev.getID() == KeyEvent.KEY_PRESSED) {
            keyPressed(ev);
        } else if (ev.getID() == KeyEvent.KEY_RELEASED) {
            keyReleased(ev);
        }
        return false;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        mShiftDown = e.isShiftDown();
        int code = e.getKeyCode();
        if (MOVE_KEYS.contains(code)) {
            mPressed.add(code);
            if (!mMoveTimer.isRunning()) {
                mMoveTimer.start();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        mShiftDown = e.isShiftDown();
        mPressed.remove(e.getKeyCode());
        if (mPressed.isEmpty()) {
            mMoveTimer.stop();
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    /** Applies one frame of movement for all currently-held keys (runs on the EDT). */
    private void tick() {
        if (mPressed.isEmpty() || !isFocused()) {
            mPressed.clear();
            mMoveTimer.stop();
            return;
        }
        float step = STEP * (mShiftDown ? FAST_MULTIPLIER : 1f);
        TransformEye cam = mPanel.mUniverse.getCamera();

        // View-relative forward/back and strafe.
        if (mPressed.contains(MOVE_FORWARD)) {
            cam.moveForward(step);
        }
        if (mPressed.contains(MOVE_BACK)) {
            cam.moveForward(-step);
        }
        if (mPressed.contains(MOVE_RIGHT)) {
            cam.moveRight(step);
        }
        if (mPressed.contains(MOVE_LEFT)) {
            cam.moveRight(-step);
        }
        // World-vertical up/down (independent of where the camera is pointing).
        if (mPressed.contains(MOVE_UP) || mPressed.contains(MOVE_DOWN)) {
            Point3f loc = cam.getLocation();
            if (mPressed.contains(MOVE_UP)) {
                loc.y += step;
            }
            if (mPressed.contains(MOVE_DOWN)) {
                loc.y -= step;
            }
            cam.setLocation(loc);
        }
        mPanel.updateTransform();
    }

    private boolean isFocused() {
        for (Component c = mPanel; c != null; c = c.getParent()) {
            if (c instanceof JFrame) {
                return ((JFrame) c).isActive();
            }
        }
        return false;
    }
}
