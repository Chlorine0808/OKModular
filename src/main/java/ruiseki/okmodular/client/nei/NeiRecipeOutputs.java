package ruiseki.okmodular.client.nei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.decorator.RecipeDecorator;
import ruiseki.okmodular.api.recipe.io.BlockIdMatcher;
import ruiseki.okmodular.api.recipe.io.BlockOutput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.io.StructureOutput;
import ruiseki.okmodular.structure.pattern.StructurePattern;

/** What NEI treats as a recipe's results, including what its decorators may add. */
public final class NeiRecipeOutputs {

    private static final String AIR = "minecraft:air";

    private NeiRecipeOutputs() {}

    /** The decorators wrapped around a recipe, in the order they were written (innermost first). */
    public static List<RecipeDecorator> decorators(IModularRecipe recipe) {
        List<RecipeDecorator> found = new ArrayList<>();
        IModularRecipe current = recipe;
        while (current instanceof RecipeDecorator decorator) {
            found.add(decorator);
            current = decorator.getInternal();
        }
        Collections.reverse(found);
        return found;
    }

    /** The recipe's own outputs, then each decorator's possible outputs. */
    public static List<IRecipeOutput> all(IModularRecipe recipe) {
        List<IRecipeOutput> outputs = new ArrayList<>(recipe.getOutputs());
        for (RecipeDecorator decorator : decorators(recipe)) {
            outputs.addAll(decorator.getPossibleOutputs());
        }
        return outputs;
    }

    /**
     * Whether the recipe may place this block, directly or through a structure pattern.
     *
     * @param blockId  {@code modid:name:meta}, as {@link BlockIdMatcher#idOf} gives it
     * @param patterns looks a pattern up by name; null for an unknown one
     */
    public static boolean placesBlock(IModularRecipe recipe, String blockId,
        Function<String, StructurePattern> patterns) {
        for (IRecipeOutput output : all(recipe)) {
            if (output instanceof BlockOutput block) {
                if (names(blockId, block.getBlock())) return true;
            } else if (output instanceof StructureOutput structure) {
                StructurePattern pattern = patterns.apply(structure.getPatternName());
                if (pattern == null) continue;
                for (String placed : pattern.placedBlocks()) {
                    if (names(blockId, placed)) return true;
                }
            }
        }
        return false;
    }

    /** A wildcard would answer every search and air has no item, so neither counts. */
    private static boolean names(String blockId, String spec) {
        if (spec == null || spec.equals("*") || spec.startsWith(AIR)) return false;
        return BlockIdMatcher.matches(blockId, spec);
    }
}
