package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.util.ChunkCoordinates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.expression.ConstantExpression;
import ruiseki.okmodular.api.recipe.expression.SeedMixer;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;

/**
 * P24 (spec 4): two decorators of the same kind on one recipe draw from different seeds,
 * because each mixes its chain depth into the seed it draws from. Depth 0 is the seed
 * unchanged, so a lone decorator draws exactly as it did before.
 */
@DisplayName("Decorator independence")
public class DecoratorIndependenceTest {

    private static final long[] SEEDS = { 0L, 1L, -1L, 42L, Long.MIN_VALUE, Long.MAX_VALUE, 0x123456789ABCDEFL };

    @Test
    @DisplayName("depth 0 leaves the seed alone")
    public void depthZeroLeavesTheSeedAlone() {
        for (long s : SEEDS) {
            assertEquals(s, SeedMixer.forDecorator(s, 0));
        }
        ConditionContext ctx = new ConditionContext(null, 0, 0, 0, null, 7L);
        assertSame(ctx, ctx.forDecorator(0));
        assertEquals(
            SeedMixer.forDecorator(7L, 2),
            ctx.forDecorator(2)
                .getEvaluationSeed());
    }

    @Test
    @DisplayName("deeper decorators get different seeds")
    public void depthsDiffer() {
        for (long s : SEEDS) {
            assertNotEquals(SeedMixer.forDecorator(s, 1), s);
            assertNotEquals(SeedMixer.forDecorator(s, 1), SeedMixer.forDecorator(s, 2));
        }
    }

    @Test
    @DisplayName("depth 1 and draw 1 never share a seed")
    public void depthAndDrawDoNotCollide() {
        for (long s = 0; s < 1000; s++) {
            assertNotEquals(SeedMixer.forDecorator(s, 1), SeedMixer.forDraw(s, 1), "seed " + s);
            assertNotEquals(
                SeedMixer.forDraw(SeedMixer.forDecorator(s, 1), 0),
                SeedMixer.forDecorator(SeedMixer.forDraw(s, 1), 0),
                "seed " + s);
        }
    }

    @Test
    @DisplayName("depth and draw do not commute")
    public void depthAndDrawDoNotCommute() {
        for (long s = 0; s < 1000; s++) {
            assertNotEquals(
                SeedMixer.forDraw(SeedMixer.forDecorator(s, 1), 1),
                SeedMixer.forDecorator(SeedMixer.forDraw(s, 1), 1),
                "seed " + s);
        }
    }

    /** Innermost recipe: does nothing, and lets everything through. */
    private static IModularRecipe inertRecipe() {
        return (IModularRecipe) Proxy.newProxyInstance(
            IModularRecipe.class.getClassLoader(),
            new Class<?>[] { IModularRecipe.class },
            (proxy, method, args) -> method.getReturnType() == boolean.class ? Boolean.TRUE : null);
    }

    private static BonusOutputDecorator bonus(IModularRecipe inner) {
        return new BonusOutputDecorator(inner, new ConstantExpression(0.5), new ArrayList<IRecipeOutput>());
    }

    @Test
    @DisplayName("depth follows the chain")
    public void depthFollowsTheChain() {
        BonusOutputDecorator first = bonus(inertRecipe());
        BonusOutputDecorator second = bonus(first);
        BonusOutputDecorator third = bonus(second);

        assertEquals(0, first.getDepth());
        assertEquals(1, second.getDepth());
        assertEquals(2, third.getDepth());
    }

    @Test
    @DisplayName("two bonuses fire independently")
    public void twoBonusesFireIndependently() {
        BonusOutputDecorator inner = bonus(inertRecipe());
        BonusOutputDecorator outer = bonus(inner);

        int differ = 0;
        for (long seed = 0; seed < 2000; seed++) {
            ConditionContext ctx = new ConditionContext(null, 0, 0, 0, null, seed);
            if (inner.timesFiring(inner.ownDraw(ctx)) != outer.timesFiring(outer.ownDraw(ctx))) differ++;
        }
        // Independent fair coins disagree half the time; identical draws never disagree
        assertTrue(differ > 700 && differ < 1300, "differ " + differ + " of 2000");
    }

    @Test
    @DisplayName("two chances succeed independently")
    public void twoChancesSucceedIndependently() {
        ChanceRecipeDecorator inner = new ChanceRecipeDecorator(inertRecipe(), new ConstantExpression(0.5));
        ChanceRecipeDecorator outer = new ChanceRecipeDecorator(inner, new ConstantExpression(0.5));

        int both = 0;
        for (long seed = 0; seed < 2000; seed++) {
            if (outer.producesOutput(new ConditionContext(null, 0, 0, 0, null, seed))) both++;
        }
        // The outer one asks the inner one too: independent draws pass 25% of the time, shared ones 50%
        assertTrue(both > 400 && both < 600, "both " + both + " of 2000");
    }

    @Test
    @DisplayName("two random_block_output decorators pick different cells")
    public void twoRandomBlockOutputsPickDifferentCells() {
        RandomBlockOutputDecorator inner = randomBlock(inertRecipe());
        RandomBlockOutputDecorator outer = randomBlock(inner);
        List<ChunkCoordinates> cells = new ArrayList<>();
        for (int i = 0; i < 50; i++) cells.add(new ChunkCoordinates(i, 0, 0));

        int differ = 0;
        for (long seed = 0; seed < 500; seed++) {
            ConditionContext ctx = new ConditionContext(null, 0, 0, 0, null, seed);
            Set<ChunkCoordinates> a = new HashSet<>(inner.select(cells, 5, inner.ownDraw(ctx)));
            Set<ChunkCoordinates> b = new HashSet<>(outer.select(cells, 5, outer.ownDraw(ctx)));
            if (!a.equals(b)) differ++;
        }
        assertTrue(differ >= 450, "differ " + differ + " of 500");
    }

    private static RandomBlockOutputDecorator randomBlock(IModularRecipe inner) {
        return new RandomBlockOutputDecorator(inner, 'L', new ConstantExpression(5), Collections.emptyList());
    }
}
