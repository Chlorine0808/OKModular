package ruiseki.okmodular.api.recipe.decorator;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.expression.ExpressionsParser;
import ruiseki.okmodular.api.recipe.expression.IExpression;
import ruiseki.okmodular.api.recipe.expression.SeedMixer;
import ruiseki.okmodular.api.recipe.io.IModularRecipeOutput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.parser.OutputParserRegistry;
import ruiseki.okmodular.util.Logger;

/**
 * Decorator that adds a chance for bonus outputs when the recipe is completed.
 *
 * <h2>There used to be a {@code modifierKey}</h2>
 *
 * It was read into a field and carried a {@code TODO} where it should have been applied, so
 * writing it drew exactly the same odds as leaving it out. What it was reaching for -
 * "scale the chance by something about the machine" - is what {@code chance} already does,
 * because {@code chance} is parsed as an expression:
 *
 * <pre>
 * "chance": "0.25 * speed_multi"
 * "chance": "0.1 + tier * 0.05"
 * </pre>
 *
 * Every modifier {@code modifierKey} could have named is reachable that way -
 * {@code speed_multi}, {@code energy_multi}, {@code batch} - and an expression can combine
 * them, which a single key never could. Finishing it would have meant two spellings for one
 * idea, with the weaker one answering differently. It is gone rather than implemented.
 */
public class BonusOutputDecorator extends RecipeDecorator {

    private final IExpression baseChanceExpr;
    private final List<IRecipeOutput> bonusOutputs;

    public BonusOutputDecorator(IModularRecipe internal, IExpression baseChanceExpr, List<IRecipeOutput> bonusOutputs) {
        super(internal);
        this.baseChanceExpr = baseChanceExpr;
        this.bonusOutputs = bonusOutputs;
    }

    @Override
    public void produceExtraOutputs(List<IModularPort> outputPorts, ConditionContext context) {
        super.produceExtraOutputs(outputPorts, context);

        int times = timesFiring(context);
        for (int fired = 0; fired < times; fired++) {
            for (IRecipeOutput bonus : bonusOutputs) {
                if (bonus instanceof IModularRecipeOutput modularBonus) {
                    List<IModularPort> filtered = filterByType(outputPorts, modularBonus.getPortType());
                    // A bonus that does not fit is dropped rather than holding the recipe
                    // open: it is a bonus.
                    if (modularBonus.checkCapacity(filtered, 1, context)) {
                        modularBonus.apply(filtered, 1, context);
                    }
                }
            }
        }
    }

    /**
     * Whether the bonus fires.
     * <p>
     * This used to be {@code rand.nextFloat()} on a {@link java.util.Random} held as a field.
     * A recipe is parsed once and shared by every machine running it, so that field was
     * shared too: what a machine drew depended on how many times <em>other</em> machines had
     * drawn, and a restart reset the sequence. Nothing about the outcome could be reproduced,
     * so a run that was checked and then reapplied - after a save and reload, say - could
     * disagree with itself.
     * <p>
     * The machine's evaluation seed is fixed for the whole run and persisted in NBT, so
     * drawing from it gives the same answer every time it is asked, and a different answer on
     * the next run. A context with no seed at all draws from zero rather than throwing; the
     * older code still fired in that case, and this keeps that.
     */
    boolean rolls(ConditionContext context) {
        double chance = baseChanceExpr.evaluateDouble(context);
        long seed = context != null ? context.getEvaluationSeed() : 0L;
        return SeedMixer.toUnitInterval(seed, SeedMixer.BONUS_OUTPUT) < chance;
    }

    /**
     * How many times the bonus fires for this completion.
     * <p>
     * See {@link RecipeDecorator#batchSizeOf} for why a batch has to roll more than once.
     */
    int timesFiring(ConditionContext context) {
        return timesFiring(context, batchSizeOf(context));
    }

    /**
     * The same, with the batch size supplied rather than read from the machine.
     * <p>
     * Draw zero is the context itself, so a batch of one is bit-for-bit what a single roll
     * was before batching entered the picture. The chance is re-evaluated per draw, so a
     * chance written with {@code random()} varies between the runs in a batch rather than
     * being decided once for all of them.
     */
    int timesFiring(ConditionContext context, int batch) {
        int fired = 0;
        for (int draw = 0; draw < batch; draw++) {
            if (rolls(context != null ? context.forDraw(draw) : null)) fired++;
        }
        return fired;
    }

    private List<IModularPort> filterByType(List<IModularPort> ports, IPortType.Type type) {
        List<IModularPort> filtered = new ArrayList<>();
        for (IModularPort port : ports) {
            if (port.getPortType() == type) {
                filtered.add(port);
            }
        }
        return filtered;
    }

    public IExpression getBaseChanceExpression() {
        return baseChanceExpr;
    }

    public List<IRecipeOutput> getBonusOutputs() {
        return bonusOutputs;
    }

    public static IModularRecipe fromJson(IModularRecipe recipe, JsonObject json) {
        IExpression chance = ExpressionsParser.parse(json.get("chance"));
        List<IRecipeOutput> outputs = new ArrayList<>();
        JsonArray arr = json.getAsJsonArray("outputs");
        for (JsonElement e : arr) {
            outputs.add(OutputParserRegistry.parse(e.getAsJsonObject()));
        }
        if (json.has("modifierKey")) {
            Logger.warn(
                "modifierKey is gone. It never did anything - the chance was always drawn "
                    + "without it - and what it was for is already how chance works: write "
                    + "\"chance\": \"0.25 * speed_multi\" or any other expression. "
                    + "The key is ignored.");
        }
        return new BonusOutputDecorator(recipe, chance, outputs);
    }
}
