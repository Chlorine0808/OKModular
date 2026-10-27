package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.expression.ConstantExpression;
import ruiseki.okmodular.api.recipe.expression.ExpressionParser;
import ruiseki.okmodular.api.recipe.io.BlockOutput;
import ruiseki.okmodular.api.recipe.io.EnergyInput;
import ruiseki.okmodular.api.recipe.io.EnergyOutput;

/**
 * The line NEI shows for a decorator. Drops depend on the block in the cell, so harvest_block is
 * described in words rather than with items.
 */
@DisplayName("デコレータの説明文")
public class DecoratorDescriptionTest {

    private static final ModularRecipe BARE = ModularRecipe.builder()
        .registryName("test")
        .recipeGroup("test")
        .name("test")
        .duration(1)
        .build();

    private static DecoratorDescription line(String key, Object... args) {
        return new DecoratorDescription(key, args);
    }

    @Test
    @DisplayName("harvest_block は道具の効果と採掘レベルを書く")
    public void testHarvestBlock() {
        assertEquals(
            Collections.singletonList(
                line(
                    "okmodular.nei.decorator.harvest_block.with",
                    line("okmodular.nei.decorator.harvest_block.fortune", line("enchantment.level.2")),
                    "2")),
            new HarvestBlockDecorator(BARE, 2, false, false, new ConstantExpression(2)).describe());

        // Silk touch wins over shears, as it does when mining
        assertEquals(
            Collections.singletonList(
                line(
                    "okmodular.nei.decorator.harvest_block.with",
                    line("okmodular.nei.decorator.harvest_block.silk_touch"),
                    "0")),
            new HarvestBlockDecorator(BARE, 0, true, true, new ConstantExpression(0)).describe());

        assertEquals(
            Collections.singletonList(line("okmodular.nei.decorator.harvest_block", "tier")),
            new HarvestBlockDecorator(BARE, 0, false, false, ExpressionParser.parseExpression("tier")).describe());
    }

    @Test
    @DisplayName("確率は定数なら百分率、式ならそのまま")
    public void testChance() {
        assertEquals(
            Collections.singletonList(line("okmodular.nei.decorator.chance", "25%")),
            new ChanceRecipeDecorator(BARE, new ConstantExpression(0.25)).describe());
        assertEquals(
            Collections.singletonList(line("okmodular.nei.decorator.chance", "12.5%")),
            new ChanceRecipeDecorator(BARE, new ConstantExpression(0.125)).describe());
        assertEquals(
            Collections.singletonList(line("okmodular.nei.decorator.chance", "(tier * 0.1)")),
            new ChanceRecipeDecorator(BARE, ExpressionParser.parseExpression("tier * 0.1")).describe());
    }

    @Test
    @DisplayName("weighted_random は回数と重みを書く")
    public void testWeightedRandom() {
        WeightedRandomDecorator decorator = new WeightedRandomDecorator(
            BARE,
            Arrays.asList(
                new WeightedRandomDecorator.WeightedOutputEntry(new EnergyOutput(1), 70),
                new WeightedRandomDecorator.WeightedOutputEntry(new EnergyOutput(2), 30)),
            1);

        assertEquals(
            Collections.singletonList(line("okmodular.nei.decorator.weighted_random", "1", "70 / 30")),
            decorator.describe());
    }

    @Test
    @DisplayName("ブロックを足すデコレータは確率と記号を書く")
    public void testBlockDecorators() {
        BlockOutput block = new BlockOutput('L', "minecraft:leaves", null, 1, false, null);

        assertEquals(
            Collections.singletonList(line("okmodular.nei.decorator.per_position_probability", "L", "5%")),
            new PerPositionProbabilityDecorator(BARE, new ConstantExpression(0.05), 'L', block).describe());
        assertEquals(
            Collections.singletonList(line("okmodular.nei.decorator.bonus_block_output", "50%")),
            new BonusBlockOutputDecorator(BARE, new ConstantExpression(0.5), Collections.singletonList(block))
                .describe());
        assertEquals(
            Collections.singletonList(line("okmodular.nei.decorator.random_block_output", "3")),
            new RandomBlockOutputDecorator(
                BARE,
                new ConstantExpression(3),
                Collections.singletonList(new RandomBlockOutputDecorator.BlockOutputSelection('L', block))).describe());
    }

    @Test
    @DisplayName("requirement は条件と触媒をそれぞれ 1 行にする")
    public void testRequirement() {
        assertEquals(
            Collections.singletonList(line("okmodular.nei.decorator.requirement.catalysts")),
            new RequirementDecorator(BARE, null, Collections.singletonList(new EnergyInput(10))).describe());
        assertEquals(Collections.emptyList(), new RequirementDecorator(BARE, null).describe());
    }
}
