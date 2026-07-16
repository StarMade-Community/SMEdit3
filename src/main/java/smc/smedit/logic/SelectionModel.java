package smc.smedit.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import smc.smedit.data.SparseMatrix;
import smc.smedit.ship.data.Block;
import smc.smedit.vecmath.Point3i;

/**
 * The current block selection and how clicks extend it. Shared between the
 * viewport (which picks blocks) and the Block Info panel (which lists them).
 * Not thread-safe; all access is expected on the EDT.
 */
public final class SelectionModel {

    /**
     * What a viewport pick selects. In {@link #BLOCKS} mode a click selects the
     * block under the cursor (Shift/Ctrl-click toggles, and a left-drag box-selects
     * a region); {@link #ENTITY} mode always selects the whole entity.
     */
    public enum Mode {
        BLOCKS("Blocks"),
        ENTITY("Whole Entity");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Notified whenever the selection set changes. */
    public interface Listener {
        void selectionChanged();
    }

    private final LinkedHashSet<Point3i> mSelected = new LinkedHashSet<>();
    private Mode mMode = Mode.BLOCKS;
    private final List<Listener> mListeners = new ArrayList<>();

    public Mode getMode() {
        return mMode;
    }

    public void setMode(Mode mode) {
        if (mode != null) {
            mMode = mode;
        }
    }

    public void addListener(Listener l) {
        if (l != null && !mListeners.contains(l)) {
            mListeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        mListeners.remove(l);
    }

    private void fireChanged() {
        for (Listener l : new ArrayList<>(mListeners)) {
            l.selectionChanged();
        }
    }

    /** Selected positions in insertion order (snapshot copy). */
    public List<Point3i> getSelected() {
        return Collections.unmodifiableList(new ArrayList<>(mSelected));
    }

    public int size() {
        return mSelected.size();
    }

    public boolean isEmpty() {
        return mSelected.isEmpty();
    }

    public void clear() {
        if (!mSelected.isEmpty()) {
            mSelected.clear();
            fireChanged();
        }
    }

    /**
     * Applies a pick at grid position {@code p} using the current mode. In ENTITY
     * mode any click selects the whole entity. In BLOCKS mode a plain click replaces
     * the selection with the single block; {@code additive} (Shift/Ctrl-click)
     * toggles that block in/out of the running selection instead.
     */
    public void applyPick(Point3i p, SparseMatrix<Block> grid, boolean additive) {
        if (mMode == Mode.ENTITY) {
            selectAll(grid);
            fireChanged();
            return;
        }
        if (p == null) {
            // Clicked empty space: deselect everything — but keep an accumulating
            // additive selection (a stray shift-click on air shouldn't wipe it).
            if (!additive && !mSelected.isEmpty()) {
                mSelected.clear();
                fireChanged();
            }
            return;
        }
        if (additive) {
            if (!mSelected.remove(p)) {
                mSelected.add(new Point3i(p));
            }
        } else {
            mSelected.clear();
            mSelected.add(new Point3i(p));
        }
        fireChanged();
    }

    private void selectAll(SparseMatrix<Block> grid) {
        mSelected.clear();
        if (grid == null) {
            return;
        }
        for (Iterator<Point3i> it = grid.iteratorNonNull(); it.hasNext();) {
            mSelected.add(it.next());
        }
    }

    /** Selects every block in the grid (Whole Entity), firing a change. */
    public void selectEntity(SparseMatrix<Block> grid) {
        selectAll(grid);
        fireChanged();
    }

    /** Replaces the selection with the given cells (e.g. a flood-fill by type). */
    public void select(java.util.Collection<Point3i> cells) {
        mSelected.clear();
        for (Point3i p : cells) {
            if (p != null) {
                mSelected.add(new Point3i(p));
            }
        }
        fireChanged();
    }

    /** Adds the given cells to the selection (Shift double-click flood). */
    public void addAll(java.util.Collection<Point3i> cells) {
        boolean changed = false;
        for (Point3i p : cells) {
            if (p != null && mSelected.add(new Point3i(p))) {
                changed = true;
            }
        }
        if (changed) {
            fireChanged();
        }
    }

    /** Removes the given cells from the selection (Ctrl double-click flood). */
    public void removeAll(java.util.Collection<Point3i> cells) {
        boolean changed = false;
        for (Point3i p : cells) {
            if (p != null && mSelected.remove(p)) {
                changed = true;
            }
        }
        if (changed) {
            fireChanged();
        }
    }
}
