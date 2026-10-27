package ruiseki.okmodular.client.nei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.decorator.BonusBlockOutputDecorator;
import ruiseki.okmodular.api.recipe.decorator.ChanceRecipeDecorator;
import ruiseki.okmodular.api.recipe.decorator.RecipeDecorator;
import ruiseki.okmodular.api.recipe.expression.ConstantExpression;
import ruiseki.okmodular.api.recipe.io.BlockOutput;
import ruiseki.okmodular.api.recipe.io.EnergyOutput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.io.StructureOutput;
import ruiseki.okmodular.structure.pattern.StructurePattern;
import ruiseki.okmodular.structure.pattern.StructurePatternParser;

/**
 * What NEI's output search treats as a recipe's result: its own outputs, what its decorators may
 * add, and the blocks it places, looked up by the placed block's item form.
 */
@DisplayName("NEI の出力検索が見る出力")
public class NeiRecipeOutputsTest {

    private static final Function<String, StructurePattern> NO_PATTERNS = name -> null;

    private static ModularRecipe.Builder recipe() {
        return ModularRecipe.builder()
            .registryName("test")
            .recipeGroup("test")
            .name("test")
            .duration(1);
    }

    private static BlockOutput block(String id) {
        return new BlockOutput('A', id, null, 1, false, null);
    }

    @Test
    @DisplayName("デコレータは書いた順（内側から）に並ぶ")
    public void testDecoratorsInnermostFirst() {
        ModularRecipe bare = recipe().build();
        RecipeDecorator inner = new ChanceRecipeDecorator(bare, new ConstantExpression(0.5));
        RecipeDecorator outer = new ChanceRecipeDecorator(inner, new ConstantExpression(0.25));

        assertEquals(Arrays.asList(inner, outer), NeiRecipeOutputs.decorators(outer));
        assertTrue(
            NeiRecipeOutputs.decorators(bare)
                .isEmpty());
    }

    @Test
    @DisplayName("デコレータが足しうる出力も含める")
    public void testIncludesDecoratorOutputs() {
        EnergyOutput own = new EnergyOutput(100);
        BlockOutput bonus = block("minecraft:diamond_block");
        IModularRecipe decorated = new BonusBlockOutputDecorator(
            recipe().addOutput(own)
                .build(),
            new ConstantExpression(0.1),
            Collections.singletonList(bonus));

        List<IRecipeOutput> all = NeiRecipeOutputs.all(decorated);
        assertEquals(2, all.size());
        assertSame(own, all.get(0));
        assertSame(bonus, all.get(1));
    }

    @Test
    @DisplayName("ブロック出力は置くブロックで引ける")
    public void testBlockOutput() {
        IModularRecipe r = recipe().addOutput(block("minecraft:stone"))
            .build();

        assertTrue(NeiRecipeOutputs.placesBlock(r, "minecraft:stone:0", NO_PATTERNS));
        assertTrue(NeiRecipeOutputs.placesBlock(r, "minecraft:stone:3", NO_PATTERNS), "meta を書かなければ全 meta");
        assertFalse(NeiRecipeOutputs.placesBlock(r, "minecraft:dirt:0", NO_PATTERNS));
    }

    @Test
    @DisplayName("meta 指定と空気・ワイルドカードは区別する")
    public void testMetaAndWildcards() {
        assertFalse(
            NeiRecipeOutputs.placesBlock(
                recipe().addOutput(block("minecraft:wool:14"))
                    .build(),
                "minecraft:wool:0",
                NO_PATTERNS));
        // "*" would answer every search, and air has no item worth looking up
        assertFalse(
            NeiRecipeOutputs.placesBlock(
                recipe().addOutput(block("*"))
                    .build(),
                "minecraft:stone:0",
                NO_PATTERNS));
        assertFalse(
            NeiRecipeOutputs.placesBlock(
                recipe().addOutput(block("minecraft:air"))
                    .build(),
                "minecraft:air:0",
                NO_PATTERNS));
    }

    @Test
    @DisplayName("構造出力はパターンが置くブロックで引ける")
    public void testStructureOutput() {
        StructurePattern pattern = pattern(
            "{ 'name': 'hut', 'mappings': { 'S': 'minecraft:cobblestone', 'G': 'minecraft:glass' }, 'layers': [ ['SGQ'] ] }");
        IModularRecipe r = recipe().addOutput(new StructureOutput('A', "hut", 1, false))
            .build();
        Function<String, StructurePattern> patterns = name -> "hut".equals(name) ? pattern : null;

        assertTrue(NeiRecipeOutputs.placesBlock(r, "minecraft:glass:0", patterns));
        assertTrue(NeiRecipeOutputs.placesBlock(r, "minecraft:cobblestone:0", patterns));
        assertFalse(NeiRecipeOutputs.placesBlock(r, "minecraft:stone:0", patterns));
        assertFalse(NeiRecipeOutputs.placesBlock(r, "minecraft:glass:0", NO_PATTERNS), "未知のパターンで落ちない");
    }

    @Test
    @DisplayName("デコレータのブロック出力でも引ける")
    public void testDecoratorBlockOutput() {
        IModularRecipe r = new BonusBlockOutputDecorator(
            recipe().build(),
            new ConstantExpression(0.1),
            Collections.singletonList(block("minecraft:gold_block")));

        assertTrue(NeiRecipeOutputs.placesBlock(r, "minecraft:gold_block:0", NO_PATTERNS));
    }

    private static StructurePattern pattern(String json) {
        return StructurePatternParser.parse(
            new JsonParser().parse(json.replace('\'', '"'))
                .getAsJsonObject(),
            "test.json");
    }
}
