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
package smc.smedit.plugins.ship.imp;

import smc.smedit.ui.act.plugin.Description;

/**
 * Parameters for {@link ImportModelPlugin}. The {@code palette} is stored as a
 * CSV of block ids (see {@link ConversionPalette#toText()}) so it round-trips
 * through the String-only parameter persistence; a custom editor lets the user
 * build/preview it.
 *
 * @author SMEdit3
 **/
@Description(displayName = "Import 3D Model",
        shortDescription = "Import and voxelize a 3D model (OBJ, VRML, DAE, or Binvox)")
public class ImportModelParameters {

    @Description(displayName = "File",
            shortDescription = "Model to import (.obj, .wrl/.vrml, .dae, .binvox)", priority = 10)
    private String mFile;

    @Description(displayName = "Longest Dimension",
            shortDescription = "Scale the model so its longest side is this many blocks", priority = 20)
    private int mLongestDimension;

    @Description(displayName = "Solid Fill",
            shortDescription = "Fill the interior of closed meshes before hollowing (cleaner hull)",
            priority = 30)
    private boolean mSolid;

    @Description(displayName = "Blocks",
            shortDescription = "Which StarMade blocks the import may use; click Edit to choose",
            priority = 40)
    private String mPalette;

    public ImportModelParameters() {
        mLongestDimension = 100;
        mSolid = true;
        mPalette = ConversionPalette.defaultPalette().toText();
    }

    public String getFile() {
        return mFile;
    }

    public void setFile(String file) {
        mFile = file;
    }

    public int getLongestDimension() {
        return mLongestDimension;
    }

    public void setLongestDimension(int longestDimension) {
        mLongestDimension = longestDimension;
    }

    public boolean isSolid() {
        return mSolid;
    }

    public void setSolid(boolean solid) {
        mSolid = solid;
    }

    public String getPalette() {
        return mPalette;
    }

    public void setPalette(String palette) {
        mPalette = palette;
    }
}
