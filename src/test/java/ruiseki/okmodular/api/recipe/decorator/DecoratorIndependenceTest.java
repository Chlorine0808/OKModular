package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.expression.SeedMixer;

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
    @DisplayName("depth and draw index do not collide")
    public void depthAndDrawDoNotCollide() {
        for (long s = 0; s < 1000; s++) {
            assertNotEquals(
                SeedMixer.forDraw(SeedMixer.forDecorator(s, 1), 1),
                SeedMixer.forDecorator(SeedMixer.forDraw(s, 1), 1),
                "seed " + s);
        }
    }
}
