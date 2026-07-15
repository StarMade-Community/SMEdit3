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

import javax.swing.JPanel;

import smc.smedit.data.RenderPoly;
import smc.smedit.data.UndoBuffer;
import smc.smedit.ship.data.Block;
import smc.smedit.vecmath.Point3i;

@SuppressWarnings("serial")
public abstract class RenderPanel extends JPanel {

    public abstract void updateTransform();

    public abstract void updateTiles();

    /** Resets the camera to a default 3/4 view looking down on the ship from the top-left. */
    public abstract void resetCamera();

    /** Toggles orthographic (vs. perspective) projection. */
    public abstract void setOrthographic(boolean orthographic);

    public abstract boolean isOrthographic();

    public abstract RenderPoly getTileAt(double x, double y);

    public abstract Block getBlockAt(double x, double y);

    /**
     * The empty grid cell a new block would be placed in for a click at screen
     * (x, y) — the cell just outside the face of the block under the cursor. Returns
     * {@code null} on a miss or when placement can't be determined. Used by the
     * Build tool. The software renderer may not support this.
     */
    public abstract Point3i getPlacementAt(double x, double y);

    public abstract boolean isPlainGraphics();

    public abstract void setPlainGraphics(boolean plainGraphics);

    public abstract boolean isAxis();

    public abstract void setAxis(boolean axis);

    /** Where the axis/grid guide is centred. */
    public enum AxisAnchor {
        /** The centre of the whole model's bounding box (the build's middle). */
        SCENE,
        /** The centre of the current selection (falls back to the scene centre if empty). */
        SELECTION
    }

    public AxisAnchor getAxisAnchor() {
        return AxisAnchor.SCENE;
    }

    public void setAxisAnchor(AxisAnchor anchor) {
    }

    /** Whether the ground grid guide is shown. */
    public boolean isGrid() {
        return false;
    }

    public void setGrid(boolean grid) {
    }

    public abstract boolean isDontDraw();

    public abstract void setDontDraw(boolean dontDraw);

    public abstract void setCloseRequested(boolean pleaseClose);

    public abstract UndoBuffer getUndoer();

    public abstract void setUndoer(UndoBuffer undoer);

    public abstract void undo();

    public abstract void redo();
}
