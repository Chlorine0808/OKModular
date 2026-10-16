package ruiseki.okmodular.structure.pattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.api.recipe.expression.ConstantExpression;
import ruiseki.okmodular.api.recipe.expression.ExpressionsParser;
import ruiseki.okmodular.api.recipe.expression.IExpression;
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
    static final char SKIP = ' ';

    private static final char DEFAULT_ANCHOR = 'Q';

    private StructurePatternParser() {}

    /**
     * @param fileName the file this object came from, for the error report only
     * @throws StructureException when the pattern cannot be used as written
     */
    public static StructurePattern parse(JsonObject json, String fileName) {
        String name = readName(json, fileName);
        String[][] shape = readShape(json, fileName, name);

        boolean generated = json.has("shape");

        char anchor = DEFAULT_ANCHOR;
        boolean anchorDeclared = false;
        if (json.has("anchor")) {
            if (generated) {
                throw new StructureException(
                    StructureException.ErrorType.INVALID_FORMAT,
                    fileName,
                    name,
                    "has both 'shape' and 'anchor'. A generated shape is anchored at its centre and every "
                        + "cell of it holds the same character, so there is nothing for 'anchor' to pick out");
            }
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

        Map<Character, CellMapping> mappings = readMappings(json, fileName, name);
        verifySymbols(shape, mappings, anchor, fileName, name);

        if (anchorDeclared && StructureShape.findSymbolOffset(shape, anchor) == null) {
            throw new StructureException(
                StructureException.ErrorType.VALIDATION_ERROR,
                fileName,
                name,
                "anchor '" + anchor + "' does not appear in 'layers'");
        }

        return new StructurePattern(
            name,
            anchor,
            shape,
            mappings,
            generated ? originOf(shape, json.getAsJsonObject("shape"), fileName, name) : null);
    }

    /**
     * The middle of a generated shape, handed over rather than searched for.
     * <p>
     * Every cell of a generated shape holds the same character, so looking one up would find the
     * topmost cell and measure every offset from there.
     */
    private static int[] centreOf(String[][] shape) {
        int middle = (shape.length - 1) / 2;
        return new int[] { middle, middle, middle };
    }

    /**
     * Where a generated shape is anchored: its centre, or with {@code offset}, the cell that puts
     * the centre at that offset. A centre on the anchor is a centre on the machine's own block,
     * which an output can never write, so a shape meant as an output has to be moved off it.
     */
    private static int[] originOf(String[][] shape, JsonObject shapeJson, String fileName, String name) {
        int[] centre = centreOf(shape);
        if (!shapeJson.has("offset")) return centre;
        return StructurePattern.anchorFor(shape, centre, readOffset(shapeJson.get("offset"), fileName, name));
    }

    private static int[] readOffset(JsonElement value, String fileName, String name) {
        boolean ok = value.isJsonArray() && value.getAsJsonArray()
            .size() == 3;
        int[] offset = new int[3];
        for (int i = 0; ok && i < 3; i++) {
            JsonElement e = value.getAsJsonArray()
                .get(i);
            ok = e.isJsonPrimitive() && e.getAsJsonPrimitive()
                .isNumber() && e.getAsDouble() == Math.rint(e.getAsDouble());
            if (ok) offset[i] = e.getAsInt();
        }
        if (!ok) {
            throw new StructureException(
                StructureException.ErrorType.INVALID_FORMAT,
                fileName,
                name,
                "'shape.offset' must be three whole numbers [x, y, z], got: " + value);
        }
        return offset;
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

    /**
     * The cells, drawn by hand as {@code layers} or asked for as a {@code shape}.
     * <p>
     * A generated shape becomes the same {@code layers} array a hand-drawn one produces, so
     * everything downstream - the facing transform, the anchor, the per-facing cache, the check
     * that every character is mapped - is the code that already existed.
     */
    private static String[][] readShape(JsonObject json, String fileName, String name) {
        if (json.has("layers") && json.has("shape")) {
            throw new StructureException(
                StructureException.ErrorType.INVALID_FORMAT,
                fileName,
                name,
                "has both 'layers' and 'shape', which say what the cells are twice; "
                    + "draw them or generate them, not both");
        }
        if (json.has("shape")) {
            return generate(json.get("shape"), fileName, name);
        }
        if (!json.has("layers")) {
            throw StructureException.missingField(fileName, name, "layers' or 'shape");
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

    /**
     * Reads one {@code shape} object through whichever generator its {@code type} names.
     * <p>
     * The known types come from {@link ShapePrimitives#types()} rather than being listed here, so
     * the load error names exactly what the registry accepts.
     */
    private static String[][] generate(JsonElement value, String fileName, String name) {
        if (!value.isJsonObject()) {
            throw new StructureException(
                StructureException.ErrorType.INVALID_FORMAT,
                fileName,
                name,
                "'shape' must be an object, as in { \"type\": \"sphere\", \"radius\": 8, \"symbol\": \"X\" }");
        }
        JsonObject shape = value.getAsJsonObject();

        String type = shape.has("type") ? shape.get("type")
            .getAsString() : null;
        ShapePrimitives.Generator generator = ShapePrimitives.get(type);

        if (generator == null) {
            throw new StructureException(
                StructureException.ErrorType.INVALID_FORMAT,
                fileName,
                name,
                "'shape' has type " + (type == null ? "nothing" : "\"" + type + "\"")
                    + "; the known types are: "
                    + String.join(", ", ShapePrimitives.types()));
        }
        return generator.draw(shape, fileName, name);
    }

    private static Map<Character, CellMapping> readMappings(JsonObject json, String fileName, String name) {
        Map<Character, CellMapping> mappings = new HashMap<>();
        // Put air in first so a pattern may still override it, the way a structure's 'F' can be
        // overridden -- the reserved meaning is a default, not a lock.
        mappings.put('_', new CellMapping(AIR));

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
                mappings.put(
                    entry.getKey()
                        .charAt(0),
                    readMapping(entry.getValue(), entry.getKey(), fileName, name));
            }
        }
        return mappings;
    }

    /**
     * One entry of {@code mappings}: a block ID, or an object that says more about the cell.
     * <p>
     * The object form is <b>not</b> the one a structure definition accepts. A structure's
     * object names a block and its metadata; this one names what goes in the cell and how
     * often, and its keys are the pattern's own.
     */
    private static CellMapping readMapping(JsonElement value, String key, String fileName, String name) {
        if (value.isJsonPrimitive()) return new CellMapping(value.getAsString());

        if (!value.isJsonObject()) {
            throw new StructureException(
                StructureException.ErrorType.INVALID_FORMAT,
                fileName,
                name,
                "mapping '" + key
                    + "' must be a block ID string or an object; the list form a structure accepts is not supported here");
        }

        JsonObject mapping = value.getAsJsonObject();
        // An expression as readily as a number: "tier * 0.1" for a vein that thickens with the
        // machine. It is settled once per placement -- see CellMapping.
        IExpression chance = mapping.has("chance") ? ExpressionsParser.parse(mapping.get("chance"))
            : new ConstantExpression(1.0);

        if (mapping.has("block") && mapping.has("weights")) {
            throw new StructureException(
                StructureException.ErrorType.INVALID_FORMAT,
                fileName,
                name,
                "mapping '" + key
                    + "' has both 'block' and 'weights', which say what goes in the same cell twice; "
                    + "'block' is the one-entry form of 'weights', so write whichever fits");
        }

        if (mapping.has("block")) {
            return new CellMapping(
                mapping.get("block")
                    .getAsString(),
                chance);
        }

        if (mapping.has("weights")) {
            return readWeights(mapping.getAsJsonObject("weights"), chance, key, fileName, name);
        }

        throw new StructureException(
            StructureException.ErrorType.INVALID_FORMAT,
            fileName,
            name,
            "mapping '" + key + "' says how often to place something but not what to place; add 'block' or 'weights'");
    }

    /**
     * A weight table: block ID to how often it is drawn, relative to the others.
     * <p>
     * The two reserved characters keep the meaning they have in {@code layers}, because this
     * is the same author writing the same cells: {@code _} is air, and a space leaves the cell
     * alone. "Sometimes clear it" and "sometimes skip it" are different things and both want
     * saying.
     * <p>
     * A wildcard is refused. Elsewhere a block ID may end in {@code :*} to match any metadata,
     * and matching is exactly what this is not for - there is no such block to place. Left in,
     * it would be a table entry that silently does nothing when drawn.
     */
    private static CellMapping readWeights(JsonObject table, IExpression chance, String key, String fileName,
        String name) {
        if (table.entrySet()
            .isEmpty()) {
            throw new StructureException(
                StructureException.ErrorType.INVALID_FORMAT,
                fileName,
                name,
                "mapping '" + key + "' has an empty 'weights'; there is nothing for it to draw");
        }

        List<String> choices = new ArrayList<>();
        List<IExpression> weights = new ArrayList<>();
        // Only what is written down can be checked here. A weight written as an expression is
        // whatever the machine makes it, so the "adds up to zero" guard below only sees the
        // constants -- an expression that reaches zero at run time places nothing, which
        // CellMapping.Resolved.pick handles rather than handing out the first entry free.
        double constantTotal = 0;
        boolean allConstant = true;

        for (Map.Entry<String, JsonElement> entry : table.entrySet()) {
            String blockId = entry.getKey();
            IExpression weight = ExpressionsParser.parse(entry.getValue());

            if (weight instanceof ConstantExpression) {
                double value = weight.evaluateDouble(null);
                if (value < 0) {
                    throw new StructureException(
                        StructureException.ErrorType.INVALID_FORMAT,
                        fileName,
                        name,
                        "mapping '" + key + "' gives \"" + blockId + "\" a negative weight (" + value + ")");
                }
                constantTotal += value;
            } else {
                allConstant = false;
            }

            if (blockId.endsWith(":*")) {
                throw new StructureException(
                    StructureException.ErrorType.INVALID_FORMAT,
                    fileName,
                    name,
                    "mapping '" + key
                        + "' draws from \""
                        + blockId
                        + "\", and a wildcard matches blocks rather than naming one to place");
            }

            choices.add(resolveChoice(blockId));
            weights.add(weight);
        }

        if (allConstant && constantTotal <= 0) {
            throw new StructureException(
                StructureException.ErrorType.INVALID_FORMAT,
                fileName,
                name,
                "mapping '" + key + "' has 'weights' that add up to zero; there is nothing for it to draw");
        }

        return new CellMapping(choices.toArray(new String[0]), weights.toArray(new IExpression[0]), chance);
    }

    /** {@code _} is air and a space is "leave it alone", as in {@code layers}. */
    private static String resolveChoice(String blockId) {
        if ("_".equals(blockId)) return AIR;
        if (" ".equals(blockId)) return null;
        return blockId;
    }

    /**
     * Every drawn character has to mean something.
     * <p>
     * An unmapped symbol in a structure definition is a validation error, and it has to be one here
     * too: a pattern that silently drops a character checks fewer blocks than it looks like it
     * checks, and the recipe just never fires.
     */
    private static void verifySymbols(String[][] shape, Map<Character, CellMapping> mappings, char anchor,
        String fileName, String name) {
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
