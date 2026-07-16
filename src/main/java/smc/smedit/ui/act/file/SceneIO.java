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

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import smc.smedit.logic.StarMadeLogic;
import smc.smedit.scene.Scene;
import smc.smedit.scene.SceneLogic;
import smc.smedit.scene.SceneModel;
import smc.smedit.ui.RenderFrame;

/**
 * Shared open/save flow for the {@code .smedit} scene document. This is the single
 * place the scene container is read and written; the File menu actions
 * ({@link OpenSceneAction}, {@link SaveSceneAction}, {@link SaveAsSceneAction}),
 * the toolbar Save button, and the Scene outliner's own buttons all route through
 * here so their behaviour stays in lock-step.
 *
 * <p>The scene is the editor's <em>native</em> document, so plain {@code Save}
 * writes back to the file the scene is currently bound to
 * ({@link SceneModel#getSceneFile()}), prompting only when there is none yet.
 */
public final class SceneIO {

    private static final String SCENE_EXT = ".smedit";

    private SceneIO() {
    }

    private static SceneModel model() {
        return StarMadeLogic.getInstance().getSceneModel();
    }

    // ---- public flow ----

    /** Prompts for a {@code .smedit} file and loads it as the current scene. */
    public static void open(final RenderFrame frame) {
        JFileChooser fc = chooser("Open Scene");
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File file = fc.getSelectedFile();
        rememberDir(file);
        frame.loadInBackground("Opening " + file.getName() + "…", cb -> {
            try {
                final Scene scene = SceneLogic.readScene(file);
                SwingUtilities.invokeLater(() -> {
                    model().loadScene(scene);
                    model().setSceneFile(file);
                });
            } catch (Exception ex) {
                error(frame, "Could not open scene", ex);
            }
        });
    }

    /**
     * Saves the scene to the file it is bound to. If it has never been saved,
     * this is equivalent to {@link #saveAs(RenderFrame)}.
     */
    public static void save(RenderFrame frame) {
        File file = model().getSceneFile();
        if (file == null) {
            saveAs(frame);
        } else {
            write(frame, file);
        }
    }

    /** Always prompts for a target {@code .smedit} file, then saves. */
    public static void saveAs(RenderFrame frame) {
        JFileChooser fc = chooser("Save Scene As");
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = fc.getSelectedFile();
        File file = chosen.getName().toLowerCase().endsWith(SCENE_EXT)
                ? chosen : new File(chosen.getParentFile(), chosen.getName() + SCENE_EXT);
        if (file.exists()) {
            int n = JOptionPane.showConfirmDialog(frame,
                    "File " + file.getName() + " already exists. Overwrite?",
                    "Save Scene", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (n != JOptionPane.YES_OPTION) {
                return;
            }
        }
        write(frame, file);
    }

    // ---- internals ----

    private static void write(final RenderFrame frame, final File file) {
        rememberDir(file);
        final Scene scene = model().getScene();
        frame.loadInBackground("Saving " + file.getName() + "…", cb -> {
            try {
                SceneLogic.writeScene(scene, file);
                SwingUtilities.invokeLater(() -> model().setSceneFile(file));
            } catch (Exception ex) {
                error(frame, "Could not save scene", ex);
            }
        });
    }

    private static JFileChooser chooser(String title) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(title);
        fc.setFileFilter(new FileNameExtensionFilter("SMEdit Scene (*.smedit)", "smedit"));
        File current = model().getSceneFile();
        if (current != null) {
            fc.setSelectedFile(current);
        } else {
            String dir = StarMadeLogic.getProps().getProperty("open.scene.dir", "");
            if (!dir.isEmpty()) {
                fc.setCurrentDirectory(new File(dir));
            }
        }
        return fc;
    }

    private static void rememberDir(File file) {
        if (file != null && file.getParentFile() != null) {
            StarMadeLogic.getProps().setProperty("open.scene.dir", file.getParentFile().getAbsolutePath());
            StarMadeLogic.saveProps();
        }
    }

    private static void error(RenderFrame frame, String msg, Throwable ex) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame,
                msg + ":\n" + ex.getMessage(), "Scene", JOptionPane.ERROR_MESSAGE));
    }
}
