package ruiseki.okmodular.common.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The example machines are written into config only where a directory is missing.
 *
 * A fresh install has no structures/, recipes/ or structure_io/ and gets every example. A pack
 * that already has the directory gets nothing, so an example the author deleted stays deleted.
 * The check is per directory: restoring the examples means deleting the directory they live in.
 */
class DefaultConfigWriterTest {

    @TempDir
    File configDir;

    private final Map<String, byte[]> resources = new HashMap<>();

    private Map<String, List<String>> manifest() {
        Map<String, List<String>> manifest = new LinkedHashMap<>();
        manifest.put(DefaultConfigWriter.STRUCTURES, Arrays.asList("a.json", "b.json"));
        manifest.put(DefaultConfigWriter.RECIPES, Collections.singletonList("a.json"));
        manifest.put(DefaultConfigWriter.STRUCTURE_IO, Collections.singletonList("a.json"));
        for (Map.Entry<String, List<String>> e : manifest.entrySet()) {
            for (String file : e.getValue()) {
                resources.put(
                    DefaultConfigWriter.resourcePath(e.getKey(), file),
                    ("{\"from\":\"" + e.getKey() + "/" + file + "\"}").getBytes(StandardCharsets.UTF_8));
            }
        }
        return manifest;
    }

    private void write(File patternDir) {
        DefaultConfigWriter.writeMissing(configDir, patternDir, manifest(), path -> {
            byte[] bytes = resources.get(path);
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        });
    }

    @Test
    void writesEveryManifestFileIntoMissingDirectories() {
        write(new File(configDir, "structure_io"));

        assertTrue(new File(configDir, "structures/a.json").isFile());
        assertTrue(new File(configDir, "structures/b.json").isFile());
        assertTrue(new File(configDir, "recipes/a.json").isFile());
        assertTrue(new File(configDir, "structure_io/a.json").isFile());
    }

    @Test
    void leavesExistingDirectoryUntouched() {
        File recipes = new File(configDir, "recipes");
        assertTrue(recipes.mkdirs());

        write(new File(configDir, "structure_io"));

        String[] contents = recipes.list();
        assertEquals(0, contents == null ? -1 : contents.length, "an existing recipes/ must stay as it was");
        assertTrue(new File(configDir, "structures/a.json").isFile(), "the other directories are still written");
    }

    @Test
    void leavesAFileInTheWayUntouched() throws IOException {
        File recipes = new File(configDir, "recipes");
        Files.write(recipes.toPath(), "mine".getBytes(StandardCharsets.UTF_8));

        write(new File(configDir, "structure_io"));

        assertTrue(recipes.isFile(), "a file where recipes/ would go must be left as it is");
        assertEquals("mine", new String(Files.readAllBytes(recipes.toPath()), StandardCharsets.UTF_8));
    }

    /** A directory written only in part is never retried, so the count must say so. */
    @Test
    void reportsHowManyFilesItActuallyWrote() {
        resources.clear();
        Map<String, List<String>> manifest = manifest();
        resources.remove(DefaultConfigWriter.resourcePath(DefaultConfigWriter.STRUCTURES, "a.json"));

        Map<String, Integer> written = DefaultConfigWriter
            .writeMissing(configDir, new File(configDir, "structure_io"), manifest, path -> {
                byte[] bytes = resources.get(path);
                return bytes == null ? null : new ByteArrayInputStream(bytes);
            });

        assertEquals(Integer.valueOf(1), written.get(DefaultConfigWriter.STRUCTURES));
        assertEquals(Integer.valueOf(1), written.get(DefaultConfigWriter.RECIPES));
    }

    @Test
    void writesPatternsToConfiguredDirectory() {
        write(new File(configDir, "custom_io"));

        assertTrue(new File(configDir, "custom_io/a.json").isFile());
        assertFalse(new File(configDir, "structure_io").exists());
    }

    @Test
    void writesResourceBytesUnchanged() throws IOException {
        write(new File(configDir, "structure_io"));

        assertArrayEquals(
            resources.get(DefaultConfigWriter.resourcePath(DefaultConfigWriter.STRUCTURES, "b.json")),
            Files.readAllBytes(new File(configDir, "structures/b.json").toPath()));
    }

    @Test
    void skipsAMissingResourceAndWritesTheRest() {
        Map<String, List<String>> manifest = manifest();
        resources.remove(DefaultConfigWriter.resourcePath(DefaultConfigWriter.STRUCTURES, "a.json"));

        DefaultConfigWriter.writeMissing(configDir, new File(configDir, "structure_io"), manifest, path -> {
            byte[] bytes = resources.get(path);
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        });

        assertFalse(new File(configDir, "structures/a.json").exists());
        assertTrue(new File(configDir, "structures/b.json").isFile());
    }
}
