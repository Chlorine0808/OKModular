package ruiseki.okmodular.api.recipe.expression;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import ruiseki.okmodular.api.modular.IPortType.Direction;
import ruiseki.okmodular.api.modular.IPortType.Type;
import ruiseki.okmodular.api.recipe.expression.ResourceFunctionExpression.Mode;

/**
 * Registry for variables and functions used in ExpressionParser.
 */
public class ExpressionRegistry {

    private static final Map<String, Function<String, IExpression>> VARIABLE_REGISTRY = new HashMap<>();
    private static final Map<String, IFunctionFactory> FUNCTION_REGISTRY = new HashMap<>();

    static {
        // --- World Properties ---
        registerWorldProperty("day");
        registerWorldProperty("total_days");
        registerWorldProperty("time");
        registerWorldProperty("moon_phase");
        registerVariable("moon", name -> new WorldPropertyExpression("moon_phase"));
        registerWorldProperty("x");
        registerWorldProperty("y");
        registerWorldProperty("z");
        registerWorldProperty("dimension");
        registerWorldProperty("light");
        registerWorldProperty("light_block");
        registerWorldProperty("light_sky");
        registerWorldProperty("temp");
        registerWorldProperty("humidity");
        registerWorldProperty("is_day");
        registerWorldProperty("is_night");
        registerWorldProperty("is_raining");
        registerWorldProperty("is_thundering");
        // Both spellings. can_see_sky() takes block ids to see through; the bare name is the
        // same test with no ids, and WorldPropertyExpression delegates to the function so
        // that stays true - answering it separately is what made the variable a constant 0.
        // Only the function was registered, so `can_see_sky == 1` was rejected as an unknown
        // variable even though the evaluation existed - the same two-hand-written-lists
        // failure the machine properties were driven off a single table to avoid.
        registerWorldProperty("can_see_sky");
        registerWorldProperty("can_see_void");
        registerWorldProperty("tick");
        registerWorldProperty("recipe_tick");
        registerWorldProperty("progress_tick");
        registerWorldProperty("redstone");
        registerWorldProperty("seed");
        registerWorldProperty("world_seed");
        registerWorldProperty("random_seed");

        // --- Machine Properties ---
        // Driven from the definition table rather than repeated here. A name needs
        // both a definition and a registration to work, and keeping two hand-written
        // lists in step failed in both directions: names that parsed and then
        // silently evaluated to 0, and names that evaluated fine but were rejected
        // by the parser. Reading one from the other removes the failure mode.
        for (String property : MachinePropertyExpression.propertyNames()) {
            registerMachineProperty(property);
        }

        // --- Constants ---
        registerVariable("pi", name -> ConstantExpression.PI);
        registerVariable("e", name -> ConstantExpression.E);

        // --- Functions ---
        // nbt('key') / nbt('a.b.c') — the machine's own NBT
        // nbt('S', 'key') / nbt('S', 'a.b.c') — the TileEntity at structure symbol S
        //
        // The target is an argument rather than part of the syntax, so every NBT
        // access reads the same way. The two-argument form used to be documented but
        // not implemented: the second argument was silently discarded and the symbol
        // was read as the key.
        registerFunction("nbt", (args, parser) -> readNbtAccess(args, parser, "nbt"));

        // has_nbt('key') / has_nbt('S', 'key') — presence rather than value.
        // nbt() answers 0 for an absent key, which is indistinguishable from a key
        // holding 0. That is harmless for >= but wrong for <=, where an absent key
        // would otherwise pass.
        registerFunction(
            "has_nbt",
            (args, parser) -> new NbtPresenceExpression(readNbtAccess(args, parser, "has_nbt")));

        // Named resource reads. Each row is (function name, kind, direction, amount
        // or space); the expression itself no longer needs a constant per
        // combination. Kinds that had no directional or space variants before
        // (essentia, vis) can gain rows here without touching any other file.
        registerResourceFunction("essentia", Type.ESSENTIA, Direction.BOTH, Mode.AMOUNT);
        registerResourceFunction("vis", Type.VIS, Direction.BOTH, Mode.AMOUNT);

        registerResourceFunction("fluid", Type.FLUID, Direction.BOTH, Mode.AMOUNT);
        registerResourceFunction("fluid_in", Type.FLUID, Direction.INPUT, Mode.AMOUNT);
        registerResourceFunction("fluid_out", Type.FLUID, Direction.OUTPUT, Mode.AMOUNT);
        registerResourceFunction("fluid_f_in", Type.FLUID, Direction.INPUT, Mode.SPACE);
        registerResourceFunction("fluid_f_out", Type.FLUID, Direction.OUTPUT, Mode.SPACE);

        registerResourceFunction("gas", Type.GAS, Direction.BOTH, Mode.AMOUNT);
        registerResourceFunction("gas_in", Type.GAS, Direction.INPUT, Mode.AMOUNT);
        registerResourceFunction("gas_out", Type.GAS, Direction.OUTPUT, Mode.AMOUNT);
        registerResourceFunction("gas_f_in", Type.GAS, Direction.INPUT, Mode.SPACE);
        registerResourceFunction("gas_f_out", Type.GAS, Direction.OUTPUT, Mode.SPACE);

        registerResourceFunction("item", Type.ITEM, Direction.BOTH, Mode.AMOUNT);
        registerResourceFunction("item_in", Type.ITEM, Direction.INPUT, Mode.AMOUNT);
        registerResourceFunction("item_out", Type.ITEM, Direction.OUTPUT, Mode.AMOUNT);
        registerResourceFunction("item_f", Type.ITEM, Direction.BOTH, Mode.SPACE);
        registerResourceFunction("item_f_in", Type.ITEM, Direction.INPUT, Mode.SPACE);
        registerResourceFunction("item_f_out", Type.ITEM, Direction.OUTPUT, Mode.SPACE);

        // Math Functions
        for (String mathFunc : MathFunctionExpression.SUPPORTED_FUNCTIONS) {
            registerFunction(mathFunc, (args, parser) -> {
                validateMathArgs(mathFunc, args.size(), parser);
                return new MathFunctionExpression(mathFunc, args);
            });
        }

        // --- Vision Functions ---
        registerFunction(
            "can_see_sky",
            (args, parser) -> new VisionFunctionExpression(VisionFunctionExpression.Direction.SKY, args));
        registerFunction(
            "can_see_void",
            (args, parser) -> new VisionFunctionExpression(VisionFunctionExpression.Direction.VOID, args));

        // Item Slot Functions
        registerFunction(
            "item_slot",
            (args, parser) -> new ItemFunctionExpression(ItemFunctionExpression.FunctionType.SLOT_COUNT, args));
        registerFunction("item_slot_in", (args, parser) -> {
            // Directional slot count: use ItemFunctionExpression with direction argument
            return new ItemFunctionExpression(
                ItemFunctionExpression.FunctionType.SLOT_COUNT,
                Arrays.asList(new StringLiteralExpression("input")));
        });
        registerFunction("item_slot_out", (args, parser) -> {
            return new ItemFunctionExpression(
                ItemFunctionExpression.FunctionType.SLOT_COUNT,
                Arrays.asList(new StringLiteralExpression("output")));
        });
        registerFunction(
            "item_slot_empty",
            (args, parser) -> new ItemFunctionExpression(ItemFunctionExpression.FunctionType.SLOT_EMPTY, args));

        // Environmental Functions
        registerFunction("count_blocks", (args, parser) -> new CountBlocksFunctionExpression(args));
    }

    private static void registerResourceFunction(String name, Type kind, Direction direction, Mode mode) {
        registerFunction(name, (args, parser) -> {
            if (args.size() != 1) {
                throw parser.error(name + "() requires 1 argument (" + argumentHint(kind) + ")");
            }
            return new ResourceFunctionExpression(name, kind, direction, mode, args.get(0));
        });
    }

    /** What the single argument of a resource function names, for error messages. */
    private static String argumentHint(Type kind) {
        switch (kind) {
            case ITEM:
                return "item ID or OreDict";
            case FLUID:
                return "fluid name";
            case GAS:
                return "gas name";
            case ESSENTIA:
            case VIS:
                return "aspect name";
            default:
                return "resource name";
        }
    }

    public static IExpression getVariable(String name) {
        Function<String, IExpression> factory = VARIABLE_REGISTRY.get(name.toLowerCase());
        return factory != null ? factory.apply(name.toLowerCase()) : null;
    }

    public static IExpression createFunction(String name, List<IExpression> args, ExpressionParser parser) {
        IFunctionFactory factory = FUNCTION_REGISTRY.get(name.toLowerCase());
        return factory != null ? factory.create(args, parser) : null;
    }

    /**
     * Reads the argument form shared by the NBT accessors: a key, or a symbol and a
     * key. Both <code>nbt()</code> and <code>has_nbt()</code> take exactly this, so
     * the rule is defined once.
     *
     * @param name the function name, for error messages
     */
    private static NbtExpression readNbtAccess(List<IExpression> args, ExpressionParser parser, String name) {
        if (args.isEmpty()) throw parser.error(name + "() requires at least one argument");
        if (args.size() > 2) {
            throw parser.error(name + "() takes a key, or a symbol and a key - got " + args.size() + " arguments");
        }

        if (args.size() == 1) {
            return new NbtExpression(stringArgument(args.get(0), parser, name + "() key"), EvaluationValue.ZERO, '\0');
        }

        String symbol = stringArgument(args.get(0), parser, name + "() symbol");
        if (symbol.length() != 1) {
            throw parser.error(name + "() symbol must be a single character, got '" + symbol + "'");
        }
        return new NbtExpression(
            stringArgument(args.get(1), parser, name + "() key"),
            EvaluationValue.ZERO,
            symbol.charAt(0));
    }

    /**
     * Reads a function argument that has to be a literal string.
     *
     * @param what what the argument is, for the error message
     */
    private static String stringArgument(IExpression arg, ExpressionParser parser, String what) {
        if (arg instanceof StringLiteralExpression strExpr) {
            return strExpr.getStringValue();
        }
        throw parser.error(what + " must be a string literal, e.g. nbt('energy')");
    }

    public static void registerVariable(String name, Function<String, IExpression> factory) {
        VARIABLE_REGISTRY.put(name.toLowerCase(), factory);
    }

    public static void registerWorldProperty(String name) {
        registerVariable(name, WorldPropertyExpression::new);
    }

    public static void registerMachineProperty(String name) {
        registerVariable(name, MachinePropertyExpression::new);
    }

    public static void registerFunction(String name, IFunctionFactory factory) {
        FUNCTION_REGISTRY.put(name.toLowerCase(), factory);
    }

    private static void validateMathArgs(String name, int argCount, ExpressionParser parser) {
        if (name.equals("pow") || name.equals("min")
            || name.equals("max")
            || name.equals("atan2")
            || name.equals("npr")
            || name.equals("ncr")
            || name.equals("perm")
            || name.equals("permu")
            || name.equals("permutation")
            || name.equals("combi")
            || name.equals("combination")) {
            if (argCount < 2) throw parser.error(name + "() requires at least 2 arguments");
        } else if (name.equals("log")) {
            if (argCount < 1 || argCount > 2) throw parser.error("log() requires 1 or 2 arguments");
        } else if (name.equals("clamp")) {
            if (argCount < 3) throw parser.error(name + "() requires at least 3 arguments");
        } else if (name.equals("random")) {
            if (argCount != 0) throw parser.error(name + "() takes no arguments");
        } else {
            if (argCount != 1) throw parser.error(name + "() requires exactly 1 argument");
        }
    }

    @FunctionalInterface
    public interface IFunctionFactory {

        IExpression create(List<IExpression> args, ExpressionParser parser);
    }
}
