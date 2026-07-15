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
package smc.smedit.data;

import java.util.ArrayDeque;
import java.util.Deque;

import smc.smedit.logic.GridLogic;
import smc.smedit.ship.data.Block;

/**
 * Undo/redo history for the block grid, as two stacks of serialized snapshots.
 *
 * <p>{@link #checkpoint} is called with the <em>pre-edit</em> grid right before a
 * change is applied; it pushes that snapshot onto the undo stack and discards any
 * redo history (a fresh edit forks the timeline). {@link #undo}/{@link #redo} take
 * the current (live) grid so the state being left can be banked onto the opposite
 * stack — that's what makes redo able to return to the most recent edit, which the
 * old single-pointer buffer could never do.
 **/
public class UndoBuffer {

    /** Grids this big aren't snapshotted (serializing them each edit is too costly). */
    private static final int MAX_SNAPSHOT_BLOCKS = 10000;
    /** Cap on retained history, so a long session doesn't grow memory without bound. */
    private static final int MAX_DEPTH = 64;

    private final Deque<byte[]> mUndo = new ArrayDeque<>();
    private final Deque<byte[]> mRedo = new ArrayDeque<>();

    public UndoBuffer() {
    }

    /** Records the pre-edit grid; call right before mutating the model. */
    public void checkpoint(SparseMatrix<Block> grid) {
        if (grid == null || grid.size() > MAX_SNAPSHOT_BLOCKS) {
            return;
        }
        mUndo.push(GridLogic.toBytes(grid));
        while (mUndo.size() > MAX_DEPTH) {
            mUndo.removeLast();
        }
        mRedo.clear();
    }

    /**
     * Restores the previous state, banking {@code current} (the live grid) for redo.
     * Returns the grid to install, or {@code null} if there's nothing to undo.
     */
    public SparseMatrix<Block> undo(SparseMatrix<Block> current) {
        if (mUndo.isEmpty()) {
            return null;
        }
        if (current != null) {
            mRedo.push(GridLogic.toBytes(current));
        }
        return GridLogic.fromBytes(mUndo.pop());
    }

    /**
     * Re-applies the next state, banking {@code current} (the live grid) for undo.
     * Returns the grid to install, or {@code null} if there's nothing to redo.
     */
    public SparseMatrix<Block> redo(SparseMatrix<Block> current) {
        if (mRedo.isEmpty()) {
            return null;
        }
        if (current != null) {
            mUndo.push(GridLogic.toBytes(current));
        }
        return GridLogic.fromBytes(mRedo.pop());
    }

    public boolean canUndo() {
        return !mUndo.isEmpty();
    }

    public boolean canRedo() {
        return !mRedo.isEmpty();
    }

    public void clear() {
        mUndo.clear();
        mRedo.clear();
    }
}
