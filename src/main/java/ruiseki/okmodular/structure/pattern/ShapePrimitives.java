package ruiseki.okmodular.structure.pattern;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.structure.StructureException;

/** The shapes a pattern can ask for instead of drawing its {@code layers} by hand. */
public final class ShapePrimitives {

    /**
     * Cells one generated shape may hold, refused past this when the pattern loads.
     * <p>
     * A pattern is written cell by cell in one go, so a shape this size is already a visible hitch
     * and anything past it is a mistake worth catching before the game runs. Radius 16 is 17077
     * cells and fits; radius 17 does not.
     */
    static final int MAX_CELLS = 20000;

    /** What one {@code shape} type reads out of its JSON object and draws. */
    interface Generator {

        String[][] draw(JsonObject shape, String fileName, String name);
    }

    /**
     * The type names a {@code shape} may use.
     * <p>
     * One list, read by both the load error that names the known types and
     * {@code DocumentedJsonTypeTest} - which is what keeps a type documented in {@code docs/} from
     * being one the parser silently ignores.
     */
    private static final Map<String, Generator> TYPES = new LinkedHashMap<>();

    static {
        register("sphere", ShapePrimitives::drawSphere);
    }

    private ShapePrimitives() {}

    public static void register(String type, Generator generator) {
        TYPES.put(type, generator);
    }

    static Generator get(String type) {
        return type == null ? null : TYPES.get(type);
    }

    static Set<String> types() {
        return Collections.unmodifiableSet(TYPES.keySet());
    }

    // ============================================================
    // sphere
    // ============================================================

    private static String[][] drawSphere(JsonObject shape, String fileName, String name) {
        char symbol = readSymbol(shape, fileName, name);
        int radius = readRadius(shape, fileName, name);

        int cells = sphereCells(radius);
        if (cells > MAX_CELLS) {
            throw new StructureException(
                StructureException.ErrorType.VALIDATION_ERROR,
                fileName,
                name,
                "'shape' of radius " + radius
                    + " is "
                    + cells
                    + " cells, past the "
                    + MAX_CELLS
                    + " a pattern may hold. Every cell is written in one go, so this would be a visible freeze");
        }
        return sphere(radius, symbol);
    }

    /**
     * A solid sphere, as the {@code layers} a pattern would otherwise have been drawn with.
     * <p>
     * The bounding box is a cube of {@code 2 * radius + 1} on every side and the centre cell is the
     * origin, which is what lets the caller hand that index over rather than searching for a
     * character - every cell here holds the same one.
     * <p>
     * Membership is {@code dx² + dy² + dz² <= radius²} in integer arithmetic, so the same radius
     * always draws the same cells.
     */
    static String[][] sphere(int radius, char symbol) {
        int side = radius * 2 + 1;
        String[][] layers = new String[side][side];

        char[] row = new char[side];
        for (int layer = 0; layer < side; layer++) {
            int dy = layer - radius;
            for (int rowIndex = 0; rowIndex < side; rowIndex++) {
                int dz = rowIndex - radius;
                for (int col = 0; col < side; col++) {
                    int dx = col - radius;
                    row[col] = dx * dx + dy * dy + dz * dz <= radius * radius ? symbol : StructurePatternParser.SKIP;
                }
                layers[layer][rowIndex] = new String(row);
            }
        }
        return layers;
    }

    /** How many cells {@link #sphere} will draw, for the limit above. */
    static int sphereCells(int radius) {
        int cells = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx * dx + dy * dy + dz * dz <= radius * radius) cells++;
                }
            }
        }
        return cells;
    }

    // ============================================================
    // shared readers
    // ============================================================

    /**
     * The one character every cell of the shape gets.
     * <p>
     * This is a pattern character, the kind {@code layers} is drawn with and {@code mappings} gives
     * a meaning to - <b>not</b> a structure symbol a recipe points at, so
     * {@code StructureSymbols.requireAddressable} has nothing to say about it. A space is refused
     * for the pattern's own reason: it means "not part of the pattern", so the shape would be empty.
     */
    private static char readSymbol(JsonObject shape, String fileName, String name) {
        if (!shape.has("symbol")) {
            throw StructureException.missingField(fileName, name, "shape.symbol");
        }
        String declared = shape.get("symbol")
            .getAsString();
        if (declared.length() != 1) {
            throw new StructureException(
                StructureException.ErrorType.INVALID_FORMAT,
                fileName,
                name,
                "'shape.symbol' must be a single character, got: \"" + declared + "\"");
        }
        if (declared.charAt(0) == StructurePatternParser.SKIP) {
            throw new StructureException(
                StructureException.ErrorType.INVALID_FORMAT,
                fileName,
                name,
                "'shape.symbol' is a space, which means \"not part of the pattern\" - the shape would be empty");
        }
        return declared.charAt(0);
    }

    /**
     * The radius, which has to be a plain number.
     * <p>
     * The cells are settled when the file is read and cached per facing from then on -
     * {@code cellsFor} is given a facing and nothing else - so a radius that depended on the
     * machine would have nowhere to be evaluated. Carving the drawn shape with the machine is what
     * a mapping's {@code chance} is for.
     */
    private static int readRadius(JsonObject shape, String fileName, String name) {
        if (!shape.has("radius")) {
            throw StructureException.missingField(fileName, name, "shape.radius");
        }
        JsonElement radius = shape.get("radius");
        if (!radius.isJsonPrimitive() || !radius.getAsJsonPrimitive()
            .isNumber()) {
            throw new StructureException(
                StructureException.ErrorType.INVALID_FORMAT,
                fileName,
                name,
                "'shape.radius' must be a plain number, not an expression. The shape is settled when this file "
                    + "is read and cached per facing, so there is nowhere to evaluate one; use a mapping's "
                    + "'chance' to carve the shape with the machine instead");
        }
        int value = radius.getAsInt();
        if (value < 0) {
            throw new StructureException(
                StructureException.ErrorType.INVALID_FORMAT,
                fileName,
                name,
                "'shape.radius' is negative (" + value + ")");
        }
        return value;
    }
}
