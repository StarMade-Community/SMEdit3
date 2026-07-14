package smc.smedit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared access to the bundled default StarMade blueprints used as test fixtures.
 *
 * <p>The raw blueprint files are large, so only {@code default-blueprints.zip} is
 * committed; the unpacked {@code default-blueprints/} directory is git-ignored and
 * created on demand the first time a test asks for it. Tests that need a fixture
 * call {@link #blueprint(String)} and {@code assumeTrue} on a non-null result, so
 * they simply skip if neither the unpacked dir nor the zip is present.
 */
public final class BlueprintFixtures {

    private static final File DIR = new File("default-blueprints");
    private static final File ZIP = new File("default-blueprints.zip");
    private static boolean ensured = false;

    private BlueprintFixtures() {
    }

    /** @return the unpacked default-blueprints directory, or {@code null} if unavailable. */
    public static synchronized File dir() {
        if (!ensured) {
            ensured = true;
            if (!isPopulated(DIR) && ZIP.isFile()) {
                try {
                    unzip(ZIP, new File("."));
                } catch (IOException e) {
                    System.err.println("Could not unpack " + ZIP + ": " + e);
                }
            }
        }
        return isPopulated(DIR) ? DIR : null;
    }

    /** @return the named blueprint dir (must contain DATA/), or {@code null} if unavailable. */
    public static File blueprint(String name) {
        File root = dir();
        if (root == null) {
            return null;
        }
        File bp = new File(root, name);
        return new File(bp, "DATA").isDirectory() ? bp : null;
    }

    private static boolean isPopulated(File dir) {
        String[] kids = dir.list();
        return kids != null && kids.length > 0;
    }

    private static void unzip(File zip, File destRoot) throws IOException {
        Path root = destRoot.toPath().toAbsolutePath().normalize();
        try (InputStream fis = Files.newInputStream(zip.toPath());
                ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = root.resolve(entry.getName()).normalize();
                if (!out.startsWith(root)) {
                    throw new IOException("Refusing zip entry outside target: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }
}
