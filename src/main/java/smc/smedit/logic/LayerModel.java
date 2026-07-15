package smc.smedit.logic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import smc.smedit.data.SparseMatrix;
import smc.smedit.ship.data.Block;
import smc.smedit.ui.BlockTypeColors;
import smc.smedit.vecmath.Point3i;

/**
 * Groups the loaded model's blocks into visibility "layers". Auto layers are
 * derived from each block's BlockConfig category; custom layers are user-made
 * (e.g. from a selection). Hiding a layer hides its block types in the viewport —
 * the renderer's mesh build consults {@link #isBlockVisible(short)} per block.
 *
 * <p>Mutation (rebuild, toggle, add/remove) happens on the EDT, but
 * {@link #isBlockVisible(short)} is read from the render thread, so the hidden-id
 * set is published as a fresh immutable snapshot on every change and held in a
 * {@code volatile} field.
 */
public final class LayerModel {

    /** Notified whenever the layer set or any layer's visibility changes. */
    public interface Listener {
        void layersChanged();
    }

    /** One visibility group: a display name, colour, and the block IDs it owns. */
    public static final class Layer {
        private final String name;
        private final boolean custom;
        private final Set<Short> blockIds;
        private Color color;
        private boolean visible;
        private int count;

        Layer(String name, boolean custom, Set<Short> ids, Color color, int count, boolean visible) {
            this.name = name;
            this.custom = custom;
            this.blockIds = ids;
            this.color = color;
            this.count = count;
            this.visible = visible;
        }

        public String getName() {
            return name;
        }

        public boolean isCustom() {
            return custom;
        }

        public Color getColor() {
            return color;
        }

        public boolean isVisible() {
            return visible;
        }

        public int getCount() {
            return count;
        }

        /** The block type IDs this layer covers (unmodifiable). */
        public Set<Short> getBlockIds() {
            return Collections.unmodifiableSet(blockIds);
        }
    }

    private final List<Layer> mAuto = new ArrayList<>();
    private final List<Layer> mCustom = new ArrayList<>();
    private volatile Set<Short> mHidden = Collections.emptySet();
    private final List<Listener> mListeners = new ArrayList<>();
    private int mCustomSeq;

    /**
     * Whether a block of type {@code id} should be drawn. Hot path — called once
     * per block during a mesh rebuild, on the render thread.
     */
    public boolean isBlockVisible(short id) {
        Set<Short> hidden = mHidden; // volatile read of an immutable snapshot
        return hidden.isEmpty() || !hidden.contains(id);
    }

    /** True if at least one layer is currently hidden. */
    public boolean anyHidden() {
        return !mHidden.isEmpty();
    }

    /**
     * Returns {@code grid} unchanged if nothing is hidden; otherwise a copy with
     * every hidden-layer block removed. The blocks must be dropped from the grid
     * (not merely skipped while meshing) so neighbour face-culling treats the gaps
     * as empty and re-exposes the interior faces — otherwise hiding, say, the armour
     * layer would leave the revealed inner blocks with culled, see-through faces.
     */
    public SparseMatrix<Block> applyVisibility(SparseMatrix<Block> grid) {
        if (grid == null || mHidden.isEmpty()) {
            return grid;
        }
        SparseMatrix<Block> out = new SparseMatrix<>();
        for (Iterator<Point3i> it = grid.iteratorNonNull(); it.hasNext();) {
            Point3i p = it.next();
            Block b = grid.get(p);
            if (b != null && isBlockVisible(b.getBlockID())) {
                out.set(p, b);
            }
        }
        return out;
    }

    /** Layers in display order: auto layers (by descending count) then custom. */
    public List<Layer> getLayers() {
        List<Layer> all = new ArrayList<>(mAuto.size() + mCustom.size());
        all.addAll(mAuto);
        all.addAll(mCustom);
        return all;
    }

    public List<Layer> getAutoLayers() {
        return Collections.unmodifiableList(mAuto);
    }

    public List<Layer> getCustomLayers() {
        return Collections.unmodifiableList(mCustom);
    }

    public void setVisible(Layer layer, boolean visible) {
        if (layer == null || layer.visible == visible) {
            return;
        }
        layer.visible = visible;
        recomputeHidden();
        fireChanged();
    }

    public void toggle(Layer layer) {
        if (layer != null) {
            setVisible(layer, !layer.visible);
        }
    }

    /** Makes every layer visible again. */
    public void showAll() {
        boolean changed = false;
        for (Layer l : getLayers()) {
            if (!l.visible) {
                l.visible = true;
                changed = true;
            }
        }
        if (changed) {
            recomputeHidden();
            fireChanged();
        }
    }

    /**
     * Rebuilds the auto layers from the current model — one per top-level block
     * category — preserving each layer's shown/hidden state by name across loads.
     * Custom layers are kept; their counts are refreshed against the new model.
     */
    public void rebuildFromModel(SparseMatrix<Block> grid) {
        Map<String, Boolean> prevVis = new HashMap<>();
        for (Layer l : mAuto) {
            prevVis.put(l.name, l.visible);
        }

        Map<Short, Integer> idCount = tally(grid);

        // Category -> its block IDs / total block count, keeping first-seen order.
        Map<String, Set<Short>> catIds = new LinkedHashMap<>();
        Map<String, Integer> catCount = new LinkedHashMap<>();
        for (Map.Entry<Short, Integer> e : idCount.entrySet()) {
            String cat = categoryOf(e.getKey());
            catIds.computeIfAbsent(cat, k -> new HashSet<>()).add(e.getKey());
            catCount.merge(cat, e.getValue(), Integer::sum);
        }

        mAuto.clear();
        for (Map.Entry<String, Set<Short>> e : catIds.entrySet()) {
            String cat = e.getKey();
            Set<Short> ids = e.getValue();
            mAuto.add(new Layer(cat, false, ids, dominantColor(ids, idCount),
                    catCount.getOrDefault(cat, 0), prevVis.getOrDefault(cat, true)));
        }
        mAuto.sort((a, b) -> Integer.compare(b.count, a.count));

        for (Layer l : mCustom) {
            l.count = countOf(l.blockIds, idCount);
            l.color = dominantColor(l.blockIds, idCount);
        }

        recomputeHidden();
        fireChanged();
    }

    /**
     * Creates a custom layer covering the given block type IDs (e.g. the distinct
     * types in the current selection). Returns the new layer.
     */
    public Layer addCustomLayer(String name, Set<Short> ids, SparseMatrix<Block> grid) {
        Set<Short> copy = new HashSet<>(ids);
        String label = (name == null || name.isBlank()) ? "Custom " + (++mCustomSeq) : name;
        Map<Short, Integer> idCount = tally(grid);
        Layer l = new Layer(label, true, copy, dominantColor(copy, idCount), countOf(copy, idCount), true);
        mCustom.add(l);
        recomputeHidden();
        fireChanged();
        return l;
    }

    /** Removes a custom layer (auto layers can't be removed). */
    public void removeLayer(Layer layer) {
        if (layer != null && mCustom.remove(layer)) {
            recomputeHidden();
            fireChanged();
        }
    }

    // ---- listeners ----

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
            l.layersChanged();
        }
    }

    // ---- helpers ----

    private void recomputeHidden() {
        Set<Short> hidden = new HashSet<>();
        for (Layer l : mAuto) {
            if (!l.visible) {
                hidden.addAll(l.blockIds);
            }
        }
        for (Layer l : mCustom) {
            if (!l.visible) {
                hidden.addAll(l.blockIds);
            }
        }
        mHidden = hidden.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(hidden);
    }

    private static Map<Short, Integer> tally(SparseMatrix<Block> grid) {
        Map<Short, Integer> idCount = new LinkedHashMap<>();
        if (grid == null) {
            return idCount;
        }
        for (Iterator<Point3i> it = grid.iteratorNonNull(); it.hasNext();) {
            Block b = grid.get(it.next());
            if (b != null) {
                idCount.merge(b.getBlockID(), 1, Integer::sum);
            }
        }
        return idCount;
    }

    private static int countOf(Set<Short> ids, Map<Short, Integer> idCount) {
        int n = 0;
        for (Short id : ids) {
            n += idCount.getOrDefault(id, 0);
        }
        return n;
    }

    private static String categoryOf(short id) {
        String[] path = BlockTypeColors.BLOCK_CATEGORY.get(id);
        if (path != null && path.length > 0) {
            return path[0];
        }
        return "Other";
    }

    /** The fill colour of the most common present block ID in the group. */
    private static Color dominantColor(Set<Short> ids, Map<Short, Integer> idCount) {
        short best = -1;
        int bestN = -1;
        for (Short id : ids) {
            int n = idCount.getOrDefault(id, 0);
            if (n > bestN) {
                bestN = n;
                best = id;
            }
        }
        return best >= 0 ? BlockTypeColors.getFillColor(best) : Color.GRAY;
    }
}
