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
package smc.smedit.ui.act.view;

import java.awt.event.ActionEvent;

import smc.smedit.ui.RenderFrame;
import smc.smedit.ui.RenderPanel;
import smc.smedit.ui.act.GenericAction;

/**
 * View-menu toggle between the orbit (turntable) camera and the first-person
 * free-look camera. Mirrors the {@code C} keyboard toggle in the viewport.
 **/
@SuppressWarnings("serial")
public class CameraModeAction extends GenericAction {

    private final RenderFrame mFrame;

    public CameraModeAction(RenderFrame frame) {
        mFrame = frame;
        setName("First-Person Camera");
        setToolTipText("Free-look camera — right-drag to look, WASD to fly (toggle with C)");
        setChecked(isFirstPerson());
    }

    private boolean isFirstPerson() {
        return mFrame.getClient().getCameraMode() == RenderPanel.CameraMode.FIRST_PERSON;
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        mFrame.getClient().toggleCameraMode();
        setChecked(isFirstPerson());
    }
}
