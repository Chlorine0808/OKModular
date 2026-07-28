package ruiseki.okmodular.structure.migration;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.api.structure.migration.IDataMigrator;
import ruiseki.okmodular.util.Logger;
import ruiseki.okmodular.util.VersionComparator;

/**
 * Central registry for structure data migrators.
 */
public class StructureMigrationRegistry {

    private static final List<IDataMigrator> MIGRATORS = new ArrayList<>();

    static {
        MIGRATORS.add(new V1_SnakeCaseMigrator()); // v1.5.4.1
        MIGRATORS.add(new V2_ModularCasingMigrator()); // v2.0.1
    }

    /**
     * Applies migrations to the given structure JSON if its version is older than
     * the target version of any registered migrator.
     *
     * @param root The structure JSON element (Object or Array)
     * @return true if any migration was applied, false otherwise
     */
    public static boolean migrate(JsonElement root) {
        if (root == null || (!root.isJsonObject() && !root.isJsonArray())) {
            return false;
        }

        boolean[] migrated = { false };

        if (root.isJsonObject()) {
            migrateObject(root.getAsJsonObject(), migrated);
        } else if (root.isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    migrateObject(element.getAsJsonObject(), migrated);
                }
            }
        }

        return migrated[0];
    }

    /**
     * The data schema version stamped into migrated structure JSON files.
     *
     * This is derived from the registered migrators, not from the mod version: a file stamped
     * with the mod version would be seen as older than a migrator's target whenever the mod
     * version sorts below it, re-running every migrator on every load.
     *
     * @return the highest target version among the registered migrators
     */
    public static String getLatestDataVersion() {
        String latest = "0.0.0";
        for (IDataMigrator migrator : MIGRATORS) {
            if (VersionComparator.compare(migrator.getTargetModVersion(), latest) > 0) {
                latest = migrator.getTargetModVersion();
            }
        }
        return latest;
    }

    private static void migrateObject(JsonObject json, boolean[] migratedFlag) {
        String fileVersion = "0.0.0";
        if (json.has("modVersion")) {
            fileVersion = json.get("modVersion")
                .getAsString();
        }

        boolean thisObjectMigrated = false;

        for (IDataMigrator migrator : MIGRATORS) {
            String targetVer = migrator.getTargetModVersion();

            // If file is older than the target version of this migrator
            if (VersionComparator.compare(fileVersion, targetVer) < 0) {
                boolean changed = migrator.migrate(json);
                fileVersion = targetVer;
                thisObjectMigrated = true;
                migratedFlag[0] = true;
                if (changed) {
                    Logger.info(
                        "[Migration] Applied migrator to version " + targetVer
                            + " for structure: "
                            + (json.has("name") ? json.get("name")
                                .getAsString() : "unknown"));
                }
            }
        }

        // Final update to the latest schema version if migrated
        if (thisObjectMigrated) {
            json.addProperty("modVersion", getLatestDataVersion());
        }
    }
}
