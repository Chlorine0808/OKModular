package ruiseki.okmodular.structure.pattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.structure.StructureException;
import ruiseki.okmodular.structure.StructureShape;

/**
 * Reads one structure IO pattern out of JSON.
 * <p>
 * Deliberately close to the structure format the same author already knows: {@code layers} take
 * either a bare array of rows or an object with a {@code rows} array, {@code mappings} ties
 * characters to block IDs, a space means "not part of the pattern", and {@code _} means air.
 * <p>
 * Two things are <b>not</b> shared with a structure definition:
 * <ul>
 * <li>{@code Q} is not reserved. A pattern picks its anchor with {@code anchor}, and {@code Q} is
 * only the default so the common case needs no extra key.</li>
 * <li>There is no {@code defaultFacing}. The transform is the machine's -- see
 * {@link StructurePattern#cellsFor}.</li>
 * </ul>
 * Everything checkable without knowing the machine is checked here, at load, so a typo shows up in
 * {@code errors.txt} instead of as a recipe that never matches.
 */
public final class StructurePatternParser {

    /** What {@code _} resolves to, matching the structure format's reserved air symbol. */
    static final String AIR = "minecraft:air";

    /** Excluded from the pattern entirely, as in a structure definition. */
    private static final char SKIP = ' ';

    private static final char DEFAULT_ANCHOR = 'Q';

    private StructurePatternParser() {}

    /**
     * @param fileName the file this object came from, for the error report only
     * @throws StructureException when the pattern cannot be used as written
     */
    public static StructurePattern parse(JsonObject json, String fileName) {
        String name = readName(json, fileName);
        String[][] shape = readShape(json, fileName, name);

        char anchor = DEFAULT_ANCHOR;
        boolean anchorDeclared = false;
        if (json.has("anchor")) {
            String declared = json.get("anchor")
                .getAsString();
            if (declared.length() != 1) {
                throw new StructureException(
                    StructureException.ErrorType.INVALID_FORMAT,
                    fileName,
                    name,
                    "'anchor' must be a single character, got: \"" + declared + "\"");
            }
            anchor = declared.charAt(0);
            anchorDeclared = true;
        }

        Map<Character, String> mappings = readMappings(json, fileName, name);
        verifySymbols(shape, mappings, anchor, fileName, name);

        if (anchorDeclared && StructureShape.findSymbolOffset(shape, anchor) == null) {
            throw new StructureException(
                StructureException.ErrorType.VALIDATION_ERROR,
                fileName,
                name,
                "anchor '" + anchor + "' does not appear in 'layers'");
        }

        return new StructurePattern(name, anchor, shape, mappings);
    }

    private static String readName(JsonObject json, String fileName) {
        if (!json.has("name")) {
            throw StructureException.missingField(fileName, null, "name");
        }
        String name = json.get("name")
            .getAsString();
        if (name.isEmpty()) {
            throw StructureException.missingField(fileName, null, "name");
        }
        return name;
    }

    private static String[][] readShape(JsonObject json, String fileName, String name) {
        if (!json.has("layers")) {
            throw StructureException.missingField(fileName, name, "layers");
        }
        JsonArray layers = json.getAsJsonArray("layers");
        if (layers.size() == 0) {
            throw StructureException.emptyStructure(fileName, name);
        }

        String[][] shape = new String[layers.size()][];
        for (int i = 0; i < layers.size(); i++) {
            JsonElement layer = layers.get(i);
            JsonArray rows = layer.isJsonObject() ? layer.getAsJsonObject()
                .getAsJsonArray("rows") : layer.getAsJsonArray();
            if (rows == null) {
                throw StructureException.invalidFormat(fileName, "layer " + i + " of '" + name + "' has no rows");
            }
            List<String> collected = new ArrayList<>();
            for (JsonElement row : rows) {
                collected.add(row.getAsString());
            }
            shape[i] = collected.toArray(new String[0]);
        }
        return shape;
    }

    private static Map<Character, String> readMappings(JsonObject json, String fileName, String name) {
        Map<Character, String> mappings = new HashMap<>();
        // Put air in first so a pattern may still override it, the way a structure's 'F' can be
        // overridden -- the reserved meaning is a default, not a lock.
        mappings.put('_', AIR);

        if (json.has("mappings")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("mappings")
                .entrySet()) {
                if (entry.getKey()
                    .length() != 1) {
                    throw new StructureException(
                        StructureException.ErrorType.INVALID_FORMAT,
                        fileName,
                        name,
                        "mapping keys must be single characters, got: \"" + entry.getKey() + "\"");
                }
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive()) {
                    throw new StructureException(
                        StructureException.ErrorType.INVALID_FORMAT,
                        fileName,
                        name,
                        "mapping '" + entry.getKey()
                            + "' must be a block ID string; the object and list forms a structure accepts are not supported here");
                }
                mappings.put(
                    entry.getKey()
                        .charAt(0),
                    value.getAsString());
            }
        }
        return mappings;
    }

    /**
     * Every drawn character has to mean something.
     * <p>
     * An unmapped symbol in a structure definition is a validation error, and it has to be one here
     * too: a pattern that silently drops a character checks fewer blocks than it looks like it
     * checks, and the recipe just never fires.
     */
    private static void verifySymbols(String[][] shape, Map<Character, String> mappings, char anchor, String fileName,
        String name) {
        for (String[] layer : shape) {
            for (String row : layer) {
                for (int col = 0; col < row.length(); col++) {
                    char symbol = row.charAt(col);
                    if (symbol == SKIP || symbol == anchor) continue;
                    if (!mappings.containsKey(symbol)) {
                        throw new StructureException(
                            StructureException.ErrorType.VALIDATION_ERROR,
                            fileName,
                            name,
                            "symbol '" + symbol + "' has no entry in 'mappings'");
                    }
                }
            }
        }
    }
}
