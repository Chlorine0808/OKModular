package ruiseki.okmodular.api.recipe.decorator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ConditionParserRegistry;
import ruiseki.okmodular.api.condition.ICondition;
import ruiseki.okmodular.api.condition.OffsetCondition;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.expression.ExpressionParser;
import ruiseki.okmodular.api.recipe.io.AbstractRecipeInput;
import ruiseki.okmodular.api.recipe.io.IRecipeInput;
import ruiseki.okmodular.api.recipe.parser.InputParserRegistry;

/**
 * Decorator that adds requirements a recipe must keep meeting while it runs.
 * <p>
 * Two kinds, either or both:
 * <ul>
 * <li><b>condition</b> — an {@link ICondition}, checked alongside the recipe's own
 * conditions.</li>
 * <li><b>requirements</b> — resources that must be present but are not consumed,
 * i.e. catalysts. These are appended to the recipe's inputs as non-consuming ones,
 * so the engine checks them at start and again every tick through the path it
 * already uses for that.</li>
 * </ul>
 * Note that a <code>requirements</code> entry is exactly a non-consuming input, so
 * the same effect is available by writing one directly:
 *
 * <pre>
 * "inputs": [ { "item": "minecraft:redstone", "amount": 10, "consume": false } ]
 * </pre>
 *
 * Declaring it here instead is useful when a parent recipe should contribute a
 * catalyst to everything that inherits from it, without touching their inputs.
 */
public class RequirementDecorator extends RecipeDecorator {

    private final ICondition extraCondition;
    private final List<IRecipeInput> requirements;

    public RequirementDecorator(IModularRecipe internal, ICondition extraCondition) {
        this(internal, extraCondition, Collections.emptyList());
    }

    public RequirementDecorator(IModularRecipe internal, ICondition extraCondition, List<IRecipeInput> requirements) {
        super(internal);
        this.extraCondition = extraCondition;
        this.requirements = requirements != null ? Collections.unmodifiableList(new ArrayList<>(requirements))
            : Collections.<IRecipeInput>emptyList();
    }

    @Override
    public boolean isConditionMet(ConditionContext context) {
        if (extraCondition != null && !extraCondition.isMet(context)) return false;
        return internal.isConditionMet(context);
    }

    /**
     * The recipe's own inputs plus this decorator's requirements.
     * <p>
     * The requirements are non-consuming, so appending them here is enough: every
     * engine path that checks inputs goes through this list.
     */
    @Override
    public List<IRecipeInput> getInputs() {
        if (requirements.isEmpty()) return internal.getInputs();

        List<IRecipeInput> combined = new ArrayList<>(internal.getInputs());
        combined.addAll(requirements);
        return Collections.unmodifiableList(combined);
    }

    public ICondition getExtraCondition() {
        return extraCondition;
    }

    public List<IRecipeInput> getRequirements() {
        return requirements;
    }

    /**
     * Reads the condition, the requirements, or both.
     * <p>
     * A condition may be spelled either way: a recipe script goes in as a string —
     * <code>"condition": "tier.glass >= 1"</code> — and a structured condition as an
     * object. {@link OffsetCondition} already accepts both; this follows it.
     */
    public static IModularRecipe fromJson(IModularRecipe recipe, JsonObject json) {
        ICondition condition = readCondition(json);
        List<IRecipeInput> requirements = readRequirements(json);

        if (condition == null && requirements.isEmpty()) {
            throw new IllegalArgumentException(
                "A requirement decorator needs a \"condition\", a \"requirements\" array, or both: " + json);
        }
        return new RequirementDecorator(recipe, condition, requirements);
    }

    private static ICondition readCondition(JsonObject json) {
        JsonElement element = json.get("condition");
        if (element == null) return null;

        ICondition condition = element.isJsonPrimitive() ? ExpressionParser.parseCondition(element.getAsString())
            : ConditionParserRegistry.parse(element.getAsJsonObject());

        if (condition == null) {
            throw new IllegalArgumentException("A requirement decorator could not read its condition: " + element);
        }
        return condition;
    }

    private static List<IRecipeInput> readRequirements(JsonObject json) {
        if (!json.has("requirements")) return Collections.emptyList();

        JsonElement element = json.get("requirements");
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException(
                "A requirement decorator's \"requirements\" must be an array: " + element);
        }

        List<IRecipeInput> requirements = new ArrayList<>();
        for (JsonElement entry : (JsonArray) element) {
            IRecipeInput input = InputParserRegistry.parse(entry.getAsJsonObject());
            if (input == null) {
                throw new IllegalArgumentException("A requirement decorator could not read a requirement: " + entry);
            }

            // A requirement is a catalyst: it has to be there, but the recipe does not
            // take it. Forced rather than defaulted, so "consume": true in a
            // requirements entry cannot quietly turn it into an extra input - write
            // that in "inputs" instead, where it reads as one.
            if (input instanceof AbstractRecipeInput) {
                ((AbstractRecipeInput) input).setConsume(false);
            }
            requirements.add(input);
        }
        return requirements;
    }
}
