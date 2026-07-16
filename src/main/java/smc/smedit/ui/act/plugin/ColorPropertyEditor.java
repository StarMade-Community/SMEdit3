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
package smc.smedit.ui.act.plugin;

import java.beans.PropertyEditorSupport;

import smc.smedit.data.Blocks;
import smc.smedit.data.BlockGroups;

/**
 * @Auther Jo Jaquinta for SMEdit Classic - version 1.0
 **/
public class ColorPropertyEditor extends PropertyEditorSupport {

    public ColorPropertyEditor() {
        super();
    }

    public ColorPropertyEditor(Object bean) {
        super(bean);
    }

    @Override
    public String getAsText() {
        Short c = (Short) getValue();
        if (c == null) {
            c = Blocks.GREY_STANDARD_ARMOR.getId();
        }
        String txt = "Grey";
        if (c == Blocks.GREY_STANDARD_ARMOR.getId()) {
            return "Grey";
        }
        if (c == Blocks.PURPLE_STANDARD_ARMOR.getId()) {
            return "Purple";
        }
        if (c == Blocks.BROWN_STANDARD_ARMOR.getId()) {
            return "Brown";
        }
        if (c == Blocks.BLACK_STANDARD_ARMOR.getId()) {
            return "Black";
        }
        if (c == Blocks.BLUE_STANDARD_ARMOR.getId()) {
            return "Blue";
        }
        if (c == Blocks.RED_STANDARD_ARMOR.getId()) {
            return "Red";
        }
        if (c == Blocks.GREEN_STANDARD_ARMOR.getId()) {
            return "Green";
        }
        if (c == Blocks.YELLOW_STANDARD_ARMOR.getId()) {
            return "Yellow";
        }
        if (c == Blocks.WHITE_STANDARD_ARMOR.getId()) {
            return "White";
        }
        //System.out.println("Getting "+getValue()+" -> "+txt);
        return txt;
    }

    @Override
    public String[] getTags() {
        return new String[]{
            "Grey",
            "Purple",
            "Brown",
            "Black",
            "Red",
            "Blue",
            "Green",
            "Yellow",
            "White",};
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        switch (text) {
            case "Grey":
                setValue(Blocks.GREY_STANDARD_ARMOR.getId());
                break;
            case "Purple":
                setValue(Blocks.PURPLE_STANDARD_ARMOR.getId());
                break;
            case "Brown":
                setValue(Blocks.BROWN_STANDARD_ARMOR.getId());
                break;
            case "Black":
                setValue(Blocks.BLACK_STANDARD_ARMOR.getId());
                break;
            case "Red":
                setValue(Blocks.RED_STANDARD_ARMOR.getId());
                break;
            case "Blue":
                setValue(Blocks.BLUE_STANDARD_ARMOR.getId());
                break;
            case "Green":
                setValue(Blocks.GREEN_STANDARD_ARMOR.getId());
                break;
            case "Yellow":
                setValue(Blocks.YELLOW_STANDARD_ARMOR.getId());
                break;
            case "White":
                setValue(Blocks.WHITE_STANDARD_ARMOR.getId());
                break;
            default:
                setValue(Blocks.GREY_STANDARD_ARMOR.getId());
                break;
        }
    }
}
