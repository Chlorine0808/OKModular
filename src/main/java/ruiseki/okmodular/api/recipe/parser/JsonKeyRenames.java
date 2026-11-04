package ruiseki.okmodular.api.recipe.parser;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.util.Logger;

/**
 * The one place that knows which JSON keys were renamed. Parsers call {@link #upgrade} first and
 * then read only the new keys.
 */
public final class JsonKeyRenames {

    /** Where a key lives; the same word can mean different things in different scopes. */
    public enum Scope {
        /** A recipe object (the keys next to {@code inputs} and {@code duration}). */
        RECIPE,
        /** One input or output object. */
        IO
    }

    /** One old-to-new pair. */
    static final class Rename {

        final Scope scope;
        final String oldKey;
        final String newKey;

        Rename(Scope scope, String oldKey, String newKey) {
            this.scope = scope;
            this.oldKey = oldKey;
            this.newKey = newKey;
        }
    }

    // Edit this list, and nothing else, to rename a key. Spec order.
    private static final List<Rename> TABLE = Collections.unmodifiableList(
        Arrays.asList(
            new Rename(Scope.RECIPE, "machine", "group"),
            new Rename(Scope.RECIPE, "time", "duration"),
            new Rename(Scope.RECIPE, "input", "inputs"),
            new Rename(Scope.RECIPE, "output", "outputs"),
            new Rename(Scope.RECIPE, "condition", "conditions"),
            new Rename(Scope.RECIPE, "tier", "tiers"),
            new Rename(Scope.RECIPE, "localizedName", "name"),
            new Rename(Scope.IO, "pertick", "perTick"),
            new Rename(Scope.IO, "nbtmatch", "nbtMatch"),
            new Rename(Scope.IO, "nbtlist", "nbtList")));

    /** Receives each warning; tests replace it to count them. */
    static Consumer<String> sink = msg -> Logger.warn(msg);

    private static final Set<String> warned = new HashSet<>();

    private JsonKeyRenames() {}

    static List<Rename> table() {
        return TABLE;
    }

    /** Moves every old key of {@code scope} to its new name in place, warning once per old key. */
    public static void upgrade(JsonObject json, Scope scope) {
        for (Rename rename : TABLE) {
            if (rename.scope != scope || !json.has(rename.oldKey)) continue;

            JsonElement value = json.remove(rename.oldKey);
            String head = "JSON key \"" + rename.oldKey + "\" is renamed to \"" + rename.newKey + "\". ";
            if (json.has(rename.newKey)) {
                warnOnce(
                    rename.oldKey,
                    head + "Both \""
                        + rename.oldKey
                        + "\" and \""
                        + rename.newKey
                        + "\" are set; \""
                        + rename.oldKey
                        + "\" is ignored.");
            } else {
                json.add(rename.newKey, value);
                warnOnce(rename.oldKey, head + "Read as \"" + rename.newKey + "\"; rename it in the file.");
            }
        }
    }

    /** Logs {@code message} unless {@code oldKey} has already warned since the last reset. */
    public static void warnOnce(String oldKey, String message) {
        if (warned.add(oldKey)) {
            sink.accept(message);
        }
    }

    /** Called where a recipe reload starts, so each reload warns again. */
    public static void resetWarnings() {
        warned.clear();
    }
}
