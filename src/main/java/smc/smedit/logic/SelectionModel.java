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

    /** How a left-click pick affects the selection. */
    public enum Mode {
        SINGLE("Single Block"),
        MULTI("Multi Block"),
        BLOCK_TYPE("Block Type"),
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
    private Mode mMode = Mode.SINGLE;
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
     * Applies a pick at grid position {@code p} using the current mode.
     * {@code additive} (Shift/Ctrl-click) forces MULTI toggle regardless of mode.
     */
    public void applyPick(Point3i p, SparseMatrix<Block> grid, boolean additive) {
        Mode effective = additive ? Mode.MULTI : mMode;
        if (p == null) {
            // Clicked empty space: deselect everything — but keep an accumulating
            // multi-selection (a stray shift-click on air shouldn't wipe it).
            if (effective != Mode.MULTI && !mSelected.isEmpty()) {
                mSelected.clear();
                fireChanged();
            }
            return;
        }
        switch (effective) {
            case SINGLE:
                mSelected.clear();
                mSelected.add(new Point3i(p));
                break;
            case MULTI:
                if (!mSelected.remove(p)) {
                    mSelected.add(new Point3i(p));
                }
                break;
            case BLOCK_TYPE:
                selectByType(p, grid);
                break;
            case ENTITY:
                selectAll(grid);
                break;
            default:
                break;
        }
        fireChanged();
    }

    private void selectByType(Point3i p, SparseMatrix<Block> grid) {
        mSelected.clear();
        if (grid == null) {
            return;
        }
        Block picked = grid.get(p);
        if (picked == null) {
            return;
        }
        short id = picked.getBlockID();
        for (Iterator<Point3i> it = grid.iteratorNonNull(); it.hasNext();) {
            Point3i q = it.next();
            Block b = grid.get(q);
            if (b != null && b.getBlockID() == id) {
                mSelected.add(q);
            }
        }
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
}
