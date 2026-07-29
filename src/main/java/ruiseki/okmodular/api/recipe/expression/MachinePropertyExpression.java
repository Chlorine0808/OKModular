package ruiseki.okmodular.api.recipe.expression;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.core.IMachineState;

/**
 * Expression that evaluates to a property of the machine (e.g., energy,
 * progress).
 *
 * <p>
 * The resource properties are <em>generated</em>, one family per resource kind,
 * rather than written out per kind. Every storable kind answers the same four
 * questions - how much is held, what the capacity is, how much room is left, and
 * the ratio of the first two - and kinds with separate input and output storage
 * answer four more. Spelling that out by hand is what left the previous version
 * ragged: gas had no {@code gas_stored}, essentia and vis had no totals, and
 * {@code essentia_free} existed as a definition but was never registered as a
 * name. Generating the family makes those holes impossible.
 *
 * <p>
 * Adding a resource kind therefore adds no names here. It appends a constant to
 * {@link IPortType.Type} and a branch to the accessors on
 * {@link IMachineState}.
 */
public class MachinePropertyExpression implements IExpression {

    private final String propertyName;
    private static final Map<String, PropertyDefinition> definitions = new HashMap<>();

    public MachinePropertyExpression(String propertyName) {
        this.propertyName = propertyName.toLowerCase();
    }

    @Override
    public EvaluationValue evaluate(ConditionContext context) {
        // Every property here reads machine state, so a context without a machine has
        // nothing to report. NEI renders recipes with no machine, and so does any
        // standalone evaluation of an expression.
        if (context == null || context.getRecipeContext() == null
            || context.getRecipeContext()
                .getMachineState() == null) {
            return EvaluationValue.ZERO;
        }

        String cacheKey = "prop_" + propertyName;
        EvaluationValue cached = context.getCachedValue(cacheKey);
        if (cached != null) {
            return cached;
        }

        PropertyDefinition def = definitions.get(propertyName);
        if (def != null) {
            EvaluationValue value = def.getter.apply(context);
            context.setCachedValue(cacheKey, value);
            return value;
        }
        return EvaluationValue.ZERO;
    }

    @Override
    public String toString() {
        return propertyName;
    }

    public static class PropertyDefinition {

        public final String name;
        public final Function<ConditionContext, EvaluationValue> getter;

        public PropertyDefinition(String name, Function<ConditionContext, EvaluationValue> getter) {
            this.name = name;
            this.getter = getter;
        }
    }

    /**
     * Every property name that can be evaluated.
     *
     * <p>
     * A name has to be both defined here and registered in
     * {@link ExpressionRegistry} to be usable from a recipe. Keeping two
     * hand-written lists in step failed in both directions - names that parsed and
     * silently evaluated to 0, and names that evaluated fine but were rejected by
     * the parser - so the registry reads this instead of repeating it.
     */
    public static Set<String> propertyNames() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    private static void register(String name, Function<ConditionContext, EvaluationValue> getter) {
        definitions.put(name.toLowerCase(), new PropertyDefinition(name, getter));
    }

    private static void alias(String alias, String target) {
        PropertyDefinition targetDef = definitions.get(target.toLowerCase());
        if (targetDef != null) {
            definitions.put(alias.toLowerCase(), targetDef);
        }
    }

    // --- machine readers -------------------------------------------------
    // evaluate() has already checked that both of these are present.

    private static IMachineState machine(ConditionContext ctx) {
        return ctx.getRecipeContext()
            .getMachineState();
    }

    private static EvaluationValue amount(ConditionContext ctx, IPortType.Type kind, IPortType.Direction dir) {
        return new EvaluationValue(machine(ctx).getAmount(kind, dir, null));
    }

    private static EvaluationValue space(ConditionContext ctx, IPortType.Type kind, IPortType.Direction dir) {
        return new EvaluationValue(machine(ctx).getSpace(kind, dir, null));
    }

    private static EvaluationValue capacity(ConditionContext ctx, IPortType.Type kind) {
        return new EvaluationValue(machine(ctx).getCapacity(kind));
    }

    private static EvaluationValue ratio(ConditionContext ctx, IPortType.Type kind) {
        IMachineState state = machine(ctx);
        long max = state.getCapacity(kind);
        long held = state.getAmount(kind, IPortType.Direction.BOTH, null);
        return new EvaluationValue(max > 0 ? (double) held / max : 0);
    }

    /**
     * Register the property family for one resource kind.
     *
     * The four base names and their aliases exist for every storable kind. The
     * directional names only exist where a direction selects different storage -
     * offering {@code energy_in} would suggest energy has an input pool of its own
     * when the answer would just be the total.
     */
    private static void registerResourceFamily(IPortType.Type kind) {
        String k = kind.name()
            .toLowerCase();

        register(k, ctx -> amount(ctx, kind, IPortType.Direction.BOTH));
        register(k + "_max", ctx -> capacity(ctx, kind));
        register(k + "_f", ctx -> space(ctx, kind, IPortType.Direction.BOTH));
        register(k + "_p", ctx -> ratio(ctx, kind));

        alias(k + "_stored", k);
        alias(k + "_total", k);
        alias("total_" + k, k);
        alias(k + "_capacity", k + "_max");
        alias("total_" + k + "_max", k + "_max");
        alias("total_" + k + "_capacity", k + "_max");
        alias(k + "_free", k + "_f");
        alias(k + "_space", k + "_f");
        alias(k + "_percent", k + "_p");

        if (!kind.hasDirectionalStorage()) {
            return;
        }

        register(k + "_in", ctx -> amount(ctx, kind, IPortType.Direction.INPUT));
        register(k + "_out", ctx -> amount(ctx, kind, IPortType.Direction.OUTPUT));
        register(k + "_f_in", ctx -> space(ctx, kind, IPortType.Direction.INPUT));
        register(k + "_f_out", ctx -> space(ctx, kind, IPortType.Direction.OUTPUT));
    }

    static {
        for (IPortType.Type kind : IPortType.Type.values()) {
            if (kind.isStorable()) {
                registerResourceFamily(kind);
            }
        }

        // Energy keeps one property outside the family: what the running recipe draws
        // per tick is not an amount held.
        register("energy_per_tick", ctx -> new EvaluationValue(machine(ctx).getEnergyPerTick()));
        alias("power", "energy");
        alias("power_p", "energy_p");

        // --- recipe progress ---
        register("progress", ctx -> new EvaluationValue(machine(ctx).getProgressPercent()));
        register("is_running", ctx -> new EvaluationValue(machine(ctx).isRunning()));
        register("is_waiting", ctx -> new EvaluationValue(machine(ctx).isWaitingForOutput()));
        alias("progress_percent", "progress");

        // --- structure ---
        register("tier", ctx -> new EvaluationValue(machine(ctx).getTier()));
        register(
            "facing",
            ctx -> new EvaluationValue(
                ctx.getRecipeContext()
                    .getFacing()
                    .ordinal()));

        // --- recipe modifiers ---
        register("batch", ctx -> new EvaluationValue(machine(ctx).getBatchSize()));
        register("speed_multi", ctx -> new EvaluationValue(machine(ctx).getSpeedMultiplier()));
        register("energy_multi", ctx -> new EvaluationValue(machine(ctx).getEnergyMultiplier()));
        alias("batch_size", "batch");
        alias("current_batch", "batch");
        alias("speed_multiplier", "speed_multi");
        alias("multiplier_speed", "speed_multi");
        alias("energy_multiplier", "energy_multi");
        alias("multiplier_energy", "energy_multi");

        // --- counters ---
        register("timeplaced", ctx -> new EvaluationValue(machine(ctx).getTimePlaced()));
        register("timecontinue", ctx -> new EvaluationValue(machine(ctx).getTimeContinuous()));
        register("recipeprocessed", ctx -> new EvaluationValue(machine(ctx).getRecipeProcessedCount()));
        register("recipeprocessedtype", ctx -> new EvaluationValue(machine(ctx).getRecipeProcessedTypesCount()));
        alias("recipe_count", "recipeprocessed");
        alias("count_recipe", "recipeprocessed");
        alias("recipe_types_count", "recipeprocessedtype");
        alias("count_recipe_type", "recipeprocessedtype");
        alias("count_recipe_types", "recipeprocessedtype");
    }

    public static MachinePropertyExpression fromJson(JsonObject json) {
        return new MachinePropertyExpression(
            json.get("property")
                .getAsString());
    }
}
