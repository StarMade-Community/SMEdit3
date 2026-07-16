package smc.smedit.ui.lwjgl;

import smc.smedit.scene.SceneObject;
import smc.smedit.vecmath.Point3i;

/**
 * A viewport pick resolved against the whole scene: the {@link SceneObject} the
 * ray hit and the cell it hit in that object's <em>local</em> grid space, plus the
 * adjacent empty cell for building. All cells are object-local — an edit routed by
 * a pick must apply to {@link #object}'s grid, not the globally-active one.
 */
public final class PickResult {

    /** The object whose grid was hit, or {@code null} for the legacy single-grid fallback. */
    public final SceneObject object;
    /** The block cell the ray hit, in {@link #object}'s local grid space. */
    public final Point3i cell;
    /** The empty cell just outside the entered face (for Build), object-local, or {@code null}. */
    public final Point3i place;

    public PickResult(SceneObject object, Point3i cell, Point3i place) {
        this.object = object;
        this.cell = cell;
        this.place = place;
    }
}
