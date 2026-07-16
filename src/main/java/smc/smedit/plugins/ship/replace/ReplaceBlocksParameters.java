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
package smc.smedit.plugins.ship.replace;

import smc.smedit.data.Blocks;
import smc.smedit.data.BlockGroups;
import smc.smedit.ui.act.plugin.Description;

/**
 * @Auther Jo Jaquinta for SMEdit Classic - version 1.0
 **/
@Description(displayName = "Replace Blocks", shortDescription = "Do a selective replace of specific blocks")
public class ReplaceBlocksParameters {

    @Description(displayName = "Replace this")
    private short mColor1;
    @Description(displayName = "With this")
    private short mColor2;

    public ReplaceBlocksParameters() {
        mColor1 = Blocks.BLACK_STANDARD_ARMOR.getId();
        mColor2 = Blocks.WHITE_STANDARD_ARMOR.getId();
    }

    public short getColor1() {
        return mColor1;
    }

    public void setColor1(short color1) {
        mColor1 = color1;
    }

    public short getColor2() {
        return mColor2;
    }

    public void setColor2(short color2) {
        mColor2 = color2;
    }
}
