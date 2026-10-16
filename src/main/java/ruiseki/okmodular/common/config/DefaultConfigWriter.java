package ruiseki.okmodular.common.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ruiseki.okmodular.structure.pattern.StructurePatternLoader;
import ruiseki.okmodular.util.Logger;

/**
 * Writes the bundled example machines into config, into each directory that does not exist yet.
 */
public final class DefaultConfigWriter {

    public static final String STRUCTURES = "structures";
    public static final String RECIPES = "recipes";
    public static final String STRUCTURE_IO = "structure_io";

    /** Bundled files per directory. A jar cannot be listed, so the names are spelled out. */
    public static final Map<String, List<String>> MANIFEST;

    static {
        Map<String, List<String>> manifest = new LinkedHashMap<>();
        manifest.put(STRUCTURES, Arrays.asList("assembler.json"));
        manifest.put(RECIPES, Arrays.asList("assembler.json"));
        manifest.put(STRUCTURE_IO, Collections.emptyList());
        MANIFEST = Collections.unmodifiableMap(manifest);
    }

    /** Opens a bundled resource, or returns null when it is absent. */
    @FunctionalInterface
    public interface ResourceOpener {

        InputStream open(String path) throws IOException;
    }

    private DefaultConfigWriter() {}

    public static String resourcePath(String directory, String file) {
        return "/assets/okmodular/defaults/" + directory + "/" + file;
    }

    public static void writeMissing(File configDir) {
        writeMissing(
            configDir,
            StructurePatternLoader.resolveDir(configDir),
            MANIFEST,
            DefaultConfigWriter.class::getResourceAsStream);
    }

    /**
     * @param patternDir where structure IO patterns live; its leaf is a config value
     */
    public static void writeMissing(File configDir, File patternDir, Map<String, List<String>> manifest,
        ResourceOpener opener) {
        for (Map.Entry<String, List<String>> entry : manifest.entrySet()) {
            File target = STRUCTURE_IO.equals(entry.getKey()) ? patternDir : new File(configDir, entry.getKey());
            if (target.exists()) continue;
            if (!target.mkdirs()) {
                Logger.warn("Could not create " + target + "; example machines not written there");
                continue;
            }
            for (String file : entry.getValue()) {
                copy(opener, resourcePath(entry.getKey(), file), new File(target, file));
            }
            Logger.info(
                "Wrote " + entry.getValue()
                    .size() + " example file(s) to " + target);
        }
    }

    private static void copy(ResourceOpener opener, String path, File destination) {
        try (InputStream in = opener.open(path)) {
            if (in == null) {
                Logger.warn("Bundled example missing from the jar: " + path);
                return;
            }
            Files.copy(in, destination.toPath());
        } catch (IOException e) {
            Logger.error("Failed to write example " + destination, e);
        }
    }
}
