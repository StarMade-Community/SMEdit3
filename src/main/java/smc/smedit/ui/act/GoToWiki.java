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
package smc.smedit.ui.act;

import smc.smedit.util.URLs;
import smc.smedit.util.io.BrowserLauncher;

/**
 * @author Robert Barefoot
 */
public class GoToWiki extends Base{

    private static final long serialVersionUID = 4455772293454372123L;

	public GoToWiki() {
	}


    @Override
	public void actionPerformed(final java.awt.event.ActionEvent e) {
		BrowserLauncher.openURL(URLs.WIKI);
	}

    
}
