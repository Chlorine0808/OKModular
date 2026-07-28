package ruiseki.okmodular.structure;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayer;

import ruiseki.okcore.json.JsonErrorCollector;
import ruiseki.okmodular.Reference;
import ruiseki.okmodular.api.structure.core.IStructureEntry;
import ruiseki.okmodular.api.structure.core.ISymbolMapping;
import ruiseki.okmodular.api.structure.io.StructureJsonReader;
import ruiseki.okmodular.api.structure.io.StructureJsonWriter;
import ruiseki.okmodular.api.structure.visitor.StructureValidationVisitor;
import ruiseki.okmodular.util.Logger;

/**
 * Main manager for the custom structure system.
 * Refactored to use IStructureEntry API.
 */
public class StructureManager {

    private static StructureManager INSTANCE;

    /** Cached structure definitions (name -> IStructureEntry). */
    private final Map<String, IStructureEntry> structureEntries = new LinkedHashMap<>();

    /** Custom structures that have recipe groups. */
    private final Map<String, IStructureEntry> customStructures = new LinkedHashMap<>();

    private final StructureErrorCollector errorCollector = StructureErrorCollector.getInstance();
    private File configDir;
    private boolean initialized = false;

    /**
     * Optional generator invoked before loading to create missing defaults.
     *
     * Unset: nothing ships default structure JSONs yet. Wire this up when sample machines are
     * authored, so a fresh config directory gets them written on first launch.
     */
    private static Consumer<File> defaultStructureGenerator;

    /** Optional callback fired after reload() (e.g. StructureLib refresh). */
    private static Runnable reloadCallback;

    public static void setDefaultStructureGenerator(Consumer<File> generator) {
        defaultStructureGenerator = generator;
    }

    public static void setReloadCallback(Runnable callback) {
        reloadCallback = callback;
    }

    private StructureManager() {}

    public static StructureManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new StructureManager();
        }
        return INSTANCE;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean hasErrors() {
        return errorCollector.hasErrors();
    }

    /**
     * This mod's config root (e.g. {@code config/okmodular}).
     */
    public File getConfigDir() {
        return configDir;
    }

    /**
     * The single directory structure JSON is both read from and written to.
     * Anything that writes a structure file must use this, or the result will not be loaded.
     */
    public File getStructuresDir() {
        return new File(configDir, "modular/structures");
    }

    public void initialize(File minecraftDir) {
        if (initialized) return;

        try {
            this.configDir = new File(minecraftDir, Reference.MOD_ID);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            errorCollector.setConfigDir(configDir);
            errorCollector.clear();

            if (defaultStructureGenerator != null) {
                defaultStructureGenerator.accept(configDir);
            }

            loadCustomStructures();

            if (errorCollector.hasErrors()) {
                errorCollector.writeToFile();
            }

            initialized = true;
            Logger.info(
                "StructureManager initialized, " + structureEntries.size()
                    + " structures, "
                    + customStructures.size()
                    + " custom structures");
        } catch (Exception e) {
            errorCollector.collect(StructureException.loadFailed("initialization", e));
            errorCollector.writeToFile();
        }
    }

    public void notifyPlayerIfNeeded(EntityPlayer player) {
        errorCollector.notifyPlayer(player);
        JsonErrorCollector.getInstance()
            .notifyPlayer(player);
    }

    /**
     * Validate and register a structure entry.
     *
     * @param entry           The structure entry to validate and register
     * @param defaultMappings External default mappings for validation
     * @param source          Source identifier for error reporting (e.g., "ore_miner.json",
     *                        "custom:myStructure")
     * @param isCustom        Whether this is a custom structure (will be added to customStructures map)
     * @return true if validation succeeded and structure was registered, false otherwise
     */
    private boolean validateAndRegister(IStructureEntry entry, Map<Character, ISymbolMapping> defaultMappings,
        String source, boolean isCustom) {
        // Validate structure
        StructureValidationVisitor validator = new StructureValidationVisitor();
        validator.setExternalMappings(defaultMappings);
        entry.accept(validator);

        if (validator.hasErrors()) {
            // Collect all validation errors
            for (String error : validator.getErrors()) {
                errorCollector.collect(StructureException.ErrorType.VALIDATION_ERROR, source, error);
            }

            // Log warning
            String structureType = isCustom ? "Custom structure" : "Structure";
            Logger.warn(
                structureType + " '"
                    + entry.getName()
                    + "' from "
                    + source
                    + " has validation errors and will not be registered");

            return false; // Validation failed
        }

        // Register structure (validation succeeded)
        structureEntries.put(entry.getName(), entry);

        // Also add to customStructures if this is a custom structure
        if (isCustom) {
            customStructures.put(entry.getName(), entry);
        }

        return true; // Successfully registered
    }

    public void reload() {
        structureEntries.clear();
        customStructures.clear();
        errorCollector.clear();

        loadCustomStructures();

        if (reloadCallback != null) {
            reloadCallback.run();
        }

        if (errorCollector.hasErrors()) {
            errorCollector.writeToFile();
        }
    }

    private void saveMigratedFile(File file, StructureJsonReader.FileData fileData) {
        try {
            // Create backup
            File backup = new File(file.getAbsolutePath() + ".bak");
            if (file.exists()) {
                if (backup.exists()) backup.delete();
                file.renameTo(backup);
            }

            // Write updated content
            StructureJsonWriter writer = new StructureJsonWriter(file);
            writer.writeWithDefaults(fileData.structures.values(), fileData.defaultMappings);

            Logger.info("[Migration] Successfully migrated and saved: " + file.getName());
        } catch (Exception e) {
            Logger.error("[Migration] Failed to save migrated file: " + file.getName(), e);
        }
    }

    private void loadCustomStructures() {
        File customDir = getStructuresDir();
        if (!customDir.exists()) {
            customDir.mkdirs();
            return;
        }

        File[] files = customDir.listFiles();
        if (files == null) return;

        int successCount = 0;
        int errorCount = 0;

        for (File jsonFile : files) {
            if (!jsonFile.isFile() || !jsonFile.getName()
                .endsWith(".json")) continue;
            try {
                StructureJsonReader reader = new StructureJsonReader(jsonFile);
                StructureJsonReader.FileData fileData = reader.readFile(jsonFile);
                if (fileData == null) continue;

                if (fileData.dirty) {
                    saveMigratedFile(jsonFile, fileData);
                }

                for (IStructureEntry entry : fileData.structures.values()) {
                    if (validateAndRegister(entry, fileData.defaultMappings, "custom:" + entry.getName(), true)) {
                        successCount++;
                    } else {
                        errorCount++;
                    }
                }
            } catch (Exception e) {
                errorCollector.collect(StructureException.loadFailed("custom:" + jsonFile.getName(), e));
            }
        }

        if (successCount > 0 || errorCount > 0) {
            Logger.info(
                "Custom structures: " + successCount + " loaded successfully, " + errorCount + " failed validation");
        }
    }

    public IStructureEntry getStructureEntry(String name) {
        return structureEntries.get(name);
    }

    public Set<String> getStructureNames() {
        return structureEntries.keySet();
    }

    public IStructureEntry getCustomStructure(String name) {
        return customStructures.get(name);
    }

    public Set<String> getCustomStructureNames() {
        return customStructures.keySet();
    }

    public boolean hasCustomStructure(String name) {
        return customStructures.containsKey(name);
    }

    public StructureErrorCollector getErrorCollector() {
        return errorCollector;
    }
}
