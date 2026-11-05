package ruiseki.okmodular.common.recipe.capture;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Appends recipes to {@code <recipes>/captured/<file>.json}, where the file name is the group made
 * safe for a file system. A file that cannot be read is never overwritten.
 */
public final class CapturedRecipeFile {

    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9_.-]");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    /** Windows device names, which cannot be file names even with an extension. */
    private static final Pattern RESERVED = Pattern.compile("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?");

    private CapturedRecipeFile() {}

    public static boolean isValidGroup(String group) {
        return group != null && !group.trim()
            .isEmpty();
    }

    public static File fileFor(File recipesDir, String group) {
        return new File(new File(recipesDir, "captured"), fileStem(group) + ".json");
    }

    /** Replaces characters a file name cannot hold, and prefixes "_" to dot names and Windows device names. */
    static String fileStem(String group) {
        if (!isValidGroup(group)) throw new IllegalArgumentException("invalid group name: " + group);
        String stem = UNSAFE.matcher(group)
            .replaceAll("_");
        if (stem.equals(".") || stem.equals("..")
            || RESERVED.matcher(stem)
                .matches())
            stem = "_" + stem;
        return stem;
    }

    public static void append(File recipesDir, String group, JsonObject recipe) throws IOException {
        File target = fileFor(recipesDir, group);
        JsonObject root;
        if (target.exists()) {
            root = readExisting(target, group);
        } else {
            root = new JsonObject();
            root.addProperty("group", group);
            root.add("recipes", new JsonArray());
        }
        JsonArray recipes = root.getAsJsonArray("recipes");
        recipes.add(named(recipe, fileStem(group), recipes));

        File dir = target.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        Path tmpFile = new File(dir, target.getName() + ".tmp").toPath();
        try {
            Files.write(
                tmpFile,
                GSON.toJson(root)
                    .getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(
                    tmpFile,
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmpFile, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    /** The loader needs a registryName; a recipe without one gets {@code captured_<file>_<n>}. */
    private static JsonObject named(JsonObject recipe, String stem, JsonArray existing) {
        if (recipe.has("registryName")) return recipe;
        Set<String> taken = new HashSet<>();
        for (JsonElement e : existing) {
            if (e.isJsonObject() && e.getAsJsonObject()
                .has("registryName")) {
                taken.add(
                    e.getAsJsonObject()
                        .get("registryName")
                        .getAsString());
            }
        }
        int n = existing.size() + 1;
        while (taken.contains("captured_" + stem + "_" + n)) n++;
        JsonObject out = new JsonObject();
        out.addProperty("registryName", "captured_" + stem + "_" + n);
        for (Map.Entry<String, JsonElement> e : recipe.entrySet()) out.add(e.getKey(), e.getValue());
        return out;
    }

    /** Refuses a file whose header names another group, since appending would move the recipe there. */
    private static JsonObject readExisting(File file, String group) throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        JsonElement parsed;
        try {
            parsed = new JsonParser().parse(text);
        } catch (JsonParseException e) {
            throw new IOException(file.getName() + " is not valid JSON: " + e.getMessage(), e);
        }
        if (!parsed.isJsonObject() || !parsed.getAsJsonObject()
            .has("recipes")
            || !parsed.getAsJsonObject()
                .get("recipes")
                .isJsonArray()) {
            throw new IOException(file.getName() + " has no \"recipes\" array");
        }
        JsonObject root = parsed.getAsJsonObject();
        JsonElement header = root.get("group");
        if (header == null || !header.isJsonPrimitive()
            || !header.getAsString()
                .equals(group)) {
            throw new IOException(file.getName() + " belongs to group " + header + ", not \"" + group + "\"");
        }
        return root;
    }
}
