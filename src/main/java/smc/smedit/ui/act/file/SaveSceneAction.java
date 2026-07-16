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
package smc.smedit.ui.act.file;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import smc.smedit.ui.RenderFrame;
import smc.smedit.ui.act.GenericAction;

/**
 * File &gt; Save. The scene is SMEdit's native document, so this saves the whole
 * scene as a {@code .smedit} file — writing back to the bound file if there is
 * one, otherwise prompting for a location.
 */
@SuppressWarnings("serial")
public class SaveSceneAction extends GenericAction {

    private final RenderFrame mFrame;

    public SaveSceneAction(RenderFrame frame) {
        mFrame = frame;
        setName("Save");
        setToolTipText("Save the scene");
        putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_S,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        SceneIO.save(mFrame);
    }
}
