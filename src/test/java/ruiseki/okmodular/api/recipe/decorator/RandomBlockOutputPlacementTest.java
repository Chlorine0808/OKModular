package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.util.ChunkCoordinates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.decorator.RandomBlockOutputDecorator.WeightedBlockOutput;
import ruiseki.okmodular.api.recipe.expression.ExpressionsParser;
import ruiseki.okmodular.api.recipe.io.BlockOutput;

/**
 * random_block_output glue: which outputs may write a cell, and which cells are candidates.
 *
 * A World cannot be built in a test, so the cell test (acceptsAt) and the write (applyAt) are
 * stubbed on a recording BlockOutput, and the recipe context is a Proxy that only answers
 * getSymbolPositions.
 */
@DisplayName("random_block_output の置き場所")
public class RandomBlockOutputPlacementTest {

    /** Accepts the cells in {@code accepts}; records the cells it was applied to. */
    private static class StubOutput extends BlockOutput {

        final List<ChunkCoordinates> accepts;
        final List<ChunkCoordinates> applied = new ArrayList<>();

        StubOutput(List<ChunkCoordinates> accepts) {
            this.accepts = accepts;
        }

        @Override
        public boolean acceptsAt(IRecipeContext context, ChunkCoordinates pos) {
            return accepts.contains(pos);
        }

        @Override
        public void applyAt(IRecipeContext context, ChunkCoordinates pos, ConditionContext condContext) {
            applied.add(pos);
        }
    }

    private static IModularRecipe inertRecipe() {
        return (IModularRecipe) Proxy.newProxyInstance(
            IModularRecipe.class.getClassLoader(),
            new Class<?>[] { IModularRecipe.class },
            (proxy, method, args) -> null);
    }

    private static IRecipeContext contextWith(List<ChunkCoordinates> cells) {
        return (IRecipeContext) Proxy.newProxyInstance(
            IRecipeContext.class.getClassLoader(),
            new Class<?>[] { IRecipeContext.class },
            (proxy, method, args) -> "getSymbolPositions".equals(method.getName()) ? cells : null);
    }

    private static ChunkCoordinates cell(int x) {
        return new ChunkCoordinates(x, 0, 0);
    }

    private static ConditionContext seeded(IRecipeContext recipeContext, long seed) {
        return new ConditionContext(null, 0, 0, 0, recipeContext, seed);
    }

    @Test
    @DisplayName("1 つしか受けない cell には必ずその出力が置かれ，どれも受けない cell は触れない")
    public void cellTakesOnlyTheOutputsThatAcceptIt() {
        ChunkCoordinates glass = cell(0);
        ChunkCoordinates stone = cell(1);
        ChunkCoordinates neither = cell(2);
        IRecipeContext ctx = contextWith(Arrays.asList(glass, stone, neither));

        for (long seed = 0; seed < 100; seed++) {
            StubOutput onGlass = new StubOutput(Arrays.asList(glass));
            StubOutput onStone = new StubOutput(Arrays.asList(stone));
            RandomBlockOutputDecorator decorator = new RandomBlockOutputDecorator(
                inertRecipe(),
                'L',
                null,
                Arrays.asList(new WeightedBlockOutput(onGlass, 1), new WeightedBlockOutput(onStone, 1)));

            decorator.produceExtraOutputs(new ArrayList<>(), seeded(ctx, seed));

            assertEquals(Arrays.asList(glass), onGlass.applied, "seed=" + seed);
            assertEquals(Arrays.asList(stone), onStone.applied, "seed=" + seed);
        }
    }

    @Test
    @DisplayName("count を省くと候補の cell すべてに置かれ，count があれば候補の中から数だけ")
    public void omittedCountPlacesOnEveryCandidateCell() {
        List<ChunkCoordinates> cells = new ArrayList<>();
        for (int x = 0; x < 6; x++) cells.add(cell(x));
        IRecipeContext ctx = contextWith(cells);

        StubOutput all = new StubOutput(cells);
        new RandomBlockOutputDecorator(inertRecipe(), 'L', null, Arrays.asList(new WeightedBlockOutput(all, 1)))
            .produceExtraOutputs(new ArrayList<>(), seeded(ctx, 7));
        assertEquals(6, all.applied.size());
        assertTrue(all.applied.containsAll(cells));

        StubOutput some = new StubOutput(cells);
        new RandomBlockOutputDecorator(
            inertRecipe(),
            'L',
            ExpressionsParser.parse(new com.google.gson.JsonPrimitive(2)),
            Arrays.asList(new WeightedBlockOutput(some, 1))).produceExtraOutputs(new ArrayList<>(), seeded(ctx, 7));
        assertEquals(2, some.applied.size());
    }
}
