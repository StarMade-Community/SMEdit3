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

import java.awt.Component;
import java.beans.PropertyEditorSupport;

/**
 * {@link java.beans.PropertyEditor} for the model-import block palette. The
 * value it holds is the CSV of block ids (a String); {@link #getCustomEditor()}
 * returns the interactive builder panel.
 *
 * @author SMEdit3
 **/
public class PalettePropertyEditor extends PropertyEditorSupport {

    public PalettePropertyEditor(Object bean) {
        super(bean);
    }

    @Override
    public boolean isPaintable() {
        return true;
    }

    @Override
    public Component getCustomEditor() {
        return new PalettePropertyPanel(this);
    }

    @Override
    public String getAsText() {
        Object v = getValue();
        return v == null ? "" : v.toString();
    }

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        setValue(text);
    }
}
