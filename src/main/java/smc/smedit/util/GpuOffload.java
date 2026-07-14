/**
 * Copyright 2014 SMEdit
 * https://github.com/StarMade/SMEdit SMTools
 * https://github.com/StarMade/SMTools
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
 */
package smc.smedit.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Best-effort discrete-GPU selection so the 3D editor renders on the dedicated
 * GPU instead of the integrated one.
 *
 * <p>Which GPU an OpenGL/GLX context uses is decided by environment variables
 * read when the context is created, so they must be present <em>before</em> the
 * JVM's GL driver loads. A running JVM cannot change its own environment for
 * native libraries, so when a discrete GPU is detected and we are not already
 * offloaded, we re-exec the JVM once with the right variables set:
 * <ul>
 *   <li><b>NVIDIA</b>: {@code __NV_PRIME_RENDER_OFFLOAD=1} +
 *       {@code __GLX_VENDOR_LIBRARY_NAME=nvidia} — uses the native NVIDIA GLX
 *       driver (as opposed to {@code DRI_PRIME=1}, which routes through Mesa's
 *       GL-on-Vulkan "zink" translation layer).</li>
 *   <li><b>AMD</b> (PRIME): {@code DRI_PRIME=1}.</li>
 * </ul>
 *
 * <p>macOS manages GPU switching automatically; on Windows the discrete GPU is
 * selected via the driver control panel or a native launcher, so this does
 * nothing on those platforms. Opt out anywhere with the {@code -igpu} argument
 * or {@code SMEDIT_NO_GPU_OFFLOAD=1}. Every failure is swallowed — the editor
 * simply continues on whatever GPU is already active.
 */
public final class GpuOffload {

    private static final Logger log = Logger.getLogger(GpuOffload.class.getName());
    /** Set on the relaunched child so it does not relaunch again. */
    private static final String OFFLOAD_MARKER = "SMEDIT_GPU_OFFLOAD";
    private static final String MAIN_CLASS = "smc.smedit.SMEdit";

    private GpuOffload() {
    }

    /**
     * Re-execs the JVM onto the discrete GPU if one is detected and appropriate.
     * Call this first thing in {@code main}, before any AWT/GL initialization.
     * On success it does not return — it waits for the child and calls
     * {@link System#exit(int)} with the child's exit code.
     */
    public static void preferDiscreteGpu(final String[] args) {
        try {
            if (System.getenv(OFFLOAD_MARKER) != null) {
                return; // this is the relaunched child
            }
            if (System.getenv("SMEDIT_NO_GPU_OFFLOAD") != null) {
                return;
            }
            for (final String a : args) {
                if ("-igpu".equals(a)) {
                    return;
                }
            }
            if ("false".equalsIgnoreCase(pref("gpu.offload"))) {
                return; // disabled in Settings > Performance
            }
            if (!System.getProperty("os.name", "").toLowerCase().contains("linux")) {
                return; // macOS: automatic; Windows: driver profile / native launcher
            }
            Map<String, String> env = discreteGpuEnv();
            if (env.isEmpty()) {
                return; // single GPU, or nothing to offload to
            }
            relaunch(env, args);
        } catch (final Throwable t) {
            log.log(Level.WARNING, "Discrete-GPU offload skipped; "
                    + "continuing on the current GPU. Cause: " + t);
        }
    }

    /**
     * Inspects PCI devices via {@code /sys} (no external commands) and returns
     * the environment needed to offload to a discrete GPU, or an empty map when
     * there is no hybrid setup to act on.
     */
    private static Map<String, String> discreteGpuEnv() {
        final File[] entries = new File("/sys/bus/pci/devices").listFiles();
        if (entries == null) {
            return Map.of();
        }
        int gpuCount = 0;
        boolean nvidia = false;
        boolean amd = false;
        for (final File dev : entries) {
            final String cls = read(new File(dev, "class")); // 0x0300xx VGA, 0x0302xx 3D, 0x0380xx display
            if (cls == null
                    || !(cls.startsWith("0x0300") || cls.startsWith("0x0302") || cls.startsWith("0x0380"))) {
                continue;
            }
            gpuCount++;
            final String vendor = read(new File(dev, "vendor"));
            if ("0x10de".equals(vendor)) {
                nvidia = true;
            } else if ("0x1002".equals(vendor) || "0x1022".equals(vendor)) {
                amd = true;
            }
        }
        if (gpuCount < 2) {
            return Map.of(); // not a hybrid-graphics system
        }
        final Map<String, String> env = new HashMap<>();
        if (nvidia) {
            env.put("__NV_PRIME_RENDER_OFFLOAD", "1");
            env.put("__GLX_VENDOR_LIBRARY_NAME", "nvidia");
        } else if (amd) {
            env.put("DRI_PRIME", "1");
        }
        return env;
    }

    private static String read(final File f) {
        try {
            return Files.readString(f.toPath()).trim();
        } catch (final Exception e) {
            return null;
        }
    }

    /**
     * Reads one preference from the shared {@code ~/.josm} store directly (this
     * runs before the toolkit / StarMadeLogic are initialised). Returns null on
     * any problem.
     */
    private static String pref(final String key) {
        try {
            final File f = new File(System.getProperty("user.home"), ".josm");
            if (!f.isFile()) {
                return null;
            }
            final Properties p = new Properties();
            try (InputStream is = new FileInputStream(f)) {
                p.load(is);
            }
            return p.getProperty(key);
        } catch (final Exception e) {
            return null;
        }
    }

    private static void relaunch(final Map<String, String> env, final String[] args) throws Exception {
        final String javaBin = System.getProperty("java.home")
                + File.separator + "bin" + File.separator + "java";
        final List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        // Heap: prefer the Settings "memory" (GB) value; else carry over the parent
        // JVM's -Xmx. Also carry the parent's other -Xm*/-Xss/-XX tuning, so the
        // child doesn't silently drop to the default max heap. Debug/agent args are
        // skipped so we don't re-bind their ports.
        final String memPref = pref("memory");
        final boolean heapFromPref = memPref != null && memPref.matches("\\d+");
        if (heapFromPref) {
            cmd.add("-Xmx" + memPref + "g");
        }
        for (final String a : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (a.startsWith("-Xmx")) {
                if (!heapFromPref) {
                    cmd.add(a);
                }
            } else if (a.startsWith("-Xm") || a.startsWith("-Xss") || a.startsWith("-XX")) {
                cmd.add(a);
            }
        }
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(MAIN_CLASS);
        Collections.addAll(cmd, args);

        final ProcessBuilder pb = new ProcessBuilder(cmd).inheritIO();
        pb.environment().putAll(env);
        pb.environment().put(OFFLOAD_MARKER, "1");
        log.log(Level.INFO, "Relaunching on discrete GPU ({0})", env.keySet());
        System.exit(pb.start().waitFor());
    }
}
