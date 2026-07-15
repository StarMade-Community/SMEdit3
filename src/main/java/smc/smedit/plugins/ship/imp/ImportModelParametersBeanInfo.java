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

import java.awt.Image;
import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFileChooser;

import smc.smedit.ui.act.plugin.FilePropertyDescriptor;
import smc.smedit.ui.act.plugin.FilePropertyInfo;
import smc.smedit.ui.act.plugin.PalettePropertyDescriptor;

/**
 * Wires the {@link ImportModelParameters} bean to two custom editors: a file
 * chooser for {@code file} (accepting every supported model format) and the
 * block-palette editor for {@code palette}.
 *
 * @author SMEdit3
 **/
public class ImportModelParametersBeanInfo implements BeanInfo {

    private static final Logger log = Logger.getLogger(ImportModelParametersBeanInfo.class.getName());

    private final BeanInfo mRootBeanInfo;
    private final FilePropertyInfo mInfo;

    public ImportModelParametersBeanInfo() throws IntrospectionException {
        super();
        mInfo = new FilePropertyInfo();
        mInfo.setDialogTitle("Import 3D model");
        mInfo.setFilters(new String[][] {
            {"3D models (obj, wrl, vrml, dae, binvox)", "obj", "wrl", "vrml", "dae", "binvox"},
            {"Wavefront OBJ", "obj"},
            {"VRML", "wrl", "vrml"},
            {"COLLADA", "dae"},
            {"Binvox", "binvox"},
        });
        mInfo.setDialogType(JFileChooser.OPEN_DIALOG);
        mInfo.setApproveButtonText("Open");
        mInfo.setApproveButtonTooltipText("Select model to import");
        mRootBeanInfo = Introspector.getBeanInfo(ImportModelParameters.class, Introspector.IGNORE_IMMEDIATE_BEANINFO);
    }

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        PropertyDescriptor[] props = mRootBeanInfo.getPropertyDescriptors();
        for (int i = 0; i < props.length; i++) {
            try {
                if (props[i].getName().equals("file")) {
                    props[i] = new FilePropertyDescriptor(props[i].getName(),
                            props[i].getReadMethod(), props[i].getWriteMethod(), mInfo);
                } else if (props[i].getName().equals("palette")) {
                    props[i] = new PalettePropertyDescriptor(props[i].getName(),
                            props[i].getReadMethod(), props[i].getWriteMethod());
                }
            } catch (IntrospectionException e) {
                log.log(Level.WARNING, "Custom property descriptor failed for " + props[i].getName(), e);
            }
        }
        return props;
    }

    @Override
    public BeanInfo[] getAdditionalBeanInfo() {
        return mRootBeanInfo.getAdditionalBeanInfo();
    }

    @Override
    public BeanDescriptor getBeanDescriptor() {
        return mRootBeanInfo.getBeanDescriptor();
    }

    @Override
    public int getDefaultEventIndex() {
        return mRootBeanInfo.getDefaultEventIndex();
    }

    @Override
    public int getDefaultPropertyIndex() {
        return mRootBeanInfo.getDefaultPropertyIndex();
    }

    @Override
    public EventSetDescriptor[] getEventSetDescriptors() {
        return mRootBeanInfo.getEventSetDescriptors();
    }

    @Override
    public Image getIcon(int flags) {
        return mRootBeanInfo.getIcon(flags);
    }

    @Override
    public MethodDescriptor[] getMethodDescriptors() {
        return mRootBeanInfo.getMethodDescriptors();
    }
}
