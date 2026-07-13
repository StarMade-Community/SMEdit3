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
package smc.smedit.ship.data;

import smc.smedit.ui.BlockTypeColors;

/**
 * @Auther Jo Jaquinta for SMEdit Classic - version 1.0
 **/
public final class Block {

    private short mBlockID;
    private byte mOrientation;
    /** -1 = unset; fall back to the block-type default in {@link #getHitPoints()}. */
    private short mHitPoints = -1;
    private boolean mActive;

    public Block() {
    }

    public Block(short id) {
        this();
        setBlockID(id);
    }

    public Block(Block b) {
        mBlockID = b.mBlockID;
        mOrientation = b.mOrientation;
        mHitPoints = b.mHitPoints;
        mActive = b.mActive;
    }

    public short getBlockID() {
        return mBlockID;
    }

    public void setBlockID(short blockID) {
        mBlockID = blockID;
    }

    public boolean isActive() {
        return mActive;
    }

    public void setActive(boolean active) {
        mActive = active;
    }

    public short getHitPoints() {
        if (mHitPoints >= 0) {
            return mHitPoints;
        }
        if (BlockTypeColors.BLOCK_HITPOINTS.containsKey(mBlockID)) {
            return BlockTypeColors.BLOCK_HITPOINTS.get(mBlockID);
        } else {
            return 100;
        }
    }

    public void setHitPoints(short hitPoints) {
        mHitPoints = hitPoints;
    }

    public short getOrientation() {
        return mOrientation;
    }

    public void setOrientation(short orientation) {
        mOrientation = (byte) orientation;
    }
}
