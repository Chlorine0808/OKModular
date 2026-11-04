package ruiseki.okmodular.common.recipe.capture;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Appends recipes to {@code <recipes>/captured/<group>.json}. A file that cannot be read is
 * never overwritten.
 */
public final class CapturedRecipeFile {

    private static final Pattern GROUP = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private CapturedRecipeFile() {}

    public static boolean isValidGroup(String group) {
        return group != null && GROUP.matcher(group)
            .matches() && !group.equals(".") && !group.equals("..");
    }

    public static File fileFor(File recipesDir, String group) {
        if (!isValidGroup(group)) throw new IllegalArgumentException("invalid group name: " + group);
        return new File(new File(recipesDir, "captured"), group + ".json");
    }

    public static void append(File recipesDir, String group, JsonObject recipe) throws IOException {
        File target = fileFor(recipesDir, group);
        JsonObject root;
        if (target.exists()) {
            root = readExisting(target);
        } else {
            root = new JsonObject();
            root.addProperty("group", group);
            root.add("recipes", new JsonArray());
        }
        root.getAsJsonArray("recipes")
            .add(recipe);

        File dir = target.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        Path tmpFile = new File(dir, group + ".json.tmp").toPath();
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

    private static JsonObject readExisting(File file) throws IOException {
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
        return parsed.getAsJsonObject();
    }
}
