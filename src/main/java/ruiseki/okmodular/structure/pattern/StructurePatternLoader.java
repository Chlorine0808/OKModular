package ruiseki.okmodular.structure.pattern;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ruiseki.okmodular.config.MachineryConfig;
import ruiseki.okmodular.structure.StructureErrorCollector;
import ruiseki.okmodular.structure.StructureException;
import ruiseki.okmodular.structure.migration.StructureMigrationRegistry;
import ruiseki.okmodular.util.Logger;

/**
 * Loads the structure IO patterns a recipe can name.
 * <p>
 * Patterns live in their own directory rather than inside recipe JSON, for two reasons. An
 * n&times;n&times;n arrangement written inline makes the recipe unreadable, and -- the reason that
 * outlives the release -- <b>a separate file can be migrated</b>. Recipe JSON has no schema
 * versioning, so anything written there is frozen at the tag; {@link StructureMigrationRegistry}
 * only looks at {@code modVersion}, so it works here just as well as on structure JSON.
 * <p>
 * That mechanism only exists if the load path calls it, which is why {@link #readFile} does.
 * <p>
 * A file that cannot be read is dropped on its own and kept in {@link #getErrors}, matching how
 * structures and recipes behave: one typo should not take every machine with it. Handing the
 * errors over rather than reporting them from inside mirrors {@code StructureValidationVisitor},
 * whose caller does the collecting -- and it is the only arrangement a test can observe, because
 * {@code StructureErrorCollector} reaches OKCore's collector, which needs a running mod instance.
 */
public final class StructurePatternLoader {

    private static StructurePatternLoader instance;

    private final Map<String, StructurePattern> patterns = new LinkedHashMap<>();

    private final List<StructureException> errors = new ArrayList<>();

    private StructurePatternLoader() {}

    public static StructurePatternLoader getInstance() {
        if (instance == null) {
            instance = new StructurePatternLoader();
        }
        return instance;
    }

    /**
     * Where patterns are read from, under this mod's config root.
     * <p>
     * The leaf is a config value, so a pack can keep patterns beside {@code recipes/} and
     * {@code structures/} under whatever name it prefers. Only the leaf: the root itself is
     * resolved once by {@code MachineryModule.preInit} and everyone else is handed it.
     */
    public static File resolveDir(File configDir) {
        String leaf = MachineryConfig.structureIoDirectory;
        if (leaf == null || leaf.trim()
            .isEmpty()) {
            leaf = "structure_io";
        }
        return new File(configDir, leaf.trim());
    }

    /**
     * The production entry point: load, then report whatever went wrong.
     */
    public void loadAll(File configDir) {
        loadFrom(resolveDir(configDir));

        StructureErrorCollector collector = StructureErrorCollector.getInstance();
        for (StructureException error : errors) {
            collector.collect(error);
        }
    }

    /**
     * Replaces everything loaded so far with the contents of {@code dir}.
     * <p>
     * Replaces rather than adds, so a pattern whose file was deleted between reloads does not
     * outlive it.
     */
    public void loadFrom(File dir) {
        patterns.clear();
        errors.clear();

        if (!dir.exists()) {
            dir.mkdirs();
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) return;

        int loaded = 0;
        for (File file : files) {
            if (!file.isFile() || !file.getName()
                .endsWith(".json")) continue;
            loaded += readFile(file);
        }

        if (loaded > 0) {
            Logger.info("Loaded {} structure IO pattern(s) from {}", loaded, dir.getName());
        }
    }

    public StructurePattern get(String name) {
        return patterns.get(name);
    }

    public Set<String> getNames() {
        return Collections.unmodifiableSet(patterns.keySet());
    }

    /** What the last load could not use. Reported by {@link #loadAll}. */
    public List<StructureException> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public void clear() {
        patterns.clear();
        errors.clear();
    }

    /**
     * @return how many patterns this file contributed
     */
    private int readFile(File file) {
        JsonElement root;
        try (Reader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            root = new JsonParser().parse(reader);
        } catch (Exception e) {
            errors.add(StructureException.parseError(file.getName(), e.getMessage(), e));
            return 0;
        }

        if (StructureMigrationRegistry.migrate(root)) {
            saveMigrated(file, root);
        }

        int loaded = 0;
        for (JsonElement element : patternObjects(root, file)) {
            try {
                StructurePattern pattern = StructurePatternParser.parse(element.getAsJsonObject(), file.getName());
                patterns.put(pattern.getName(), pattern);
                loaded++;
            } catch (StructureException e) {
                errors.add(e);
            } catch (Exception e) {
                errors.add(StructureException.parseError(file.getName(), e.getMessage(), e));
            }
        }
        return loaded;
    }

    /**
     * Accepts the three shapes a hand-written file arrives in: a {@code patterns} array, a bare
     * array, or a single pattern object.
     */
    private JsonArray patternObjects(JsonElement root, File file) {
        JsonArray result = new JsonArray();

        if (root.isJsonArray()) {
            result = root.getAsJsonArray();
        } else if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            if (obj.has("patterns") && obj.get("patterns")
                .isJsonArray()) {
                result = obj.getAsJsonArray("patterns");
            } else {
                result.add(obj);
            }
        } else {
            errors.add(StructureException.invalidFormat(file.getName(), "expected an object or an array at the root"));
        }
        return result;
    }

    /**
     * Writes a migrated file back, keeping the original as {@code .bak}.
     * <p>
     * The migrated {@code JsonElement} is written straight out rather than re-serialised from the
     * parsed pattern: a migrator's whole job is to fix files the current parser may not accept yet,
     * so routing the result through that parser would defeat it.
     */
    private void saveMigrated(File file, JsonElement root) {
        try {
            File backup = new File(file.getAbsolutePath() + ".bak");
            if (backup.exists()) backup.delete();
            Files.copy(file.toPath(), backup.toPath());

            Gson gson = new GsonBuilder().setPrettyPrinting()
                .create();
            try (Writer writer = new OutputStreamWriter(Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
            Logger.info("[Migration] Migrated structure IO patterns: " + file.getName());
        } catch (IOException e) {
            Logger.error("[Migration] Failed to save migrated pattern file: " + file.getName(), e);
        }
    }
}
