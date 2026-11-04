package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.decorator.WeightedRandomDecorator.WeightedOutputEntry;
import ruiseki.okmodular.api.recipe.io.BlockOutput;

/**
 * P24 (B): weighted_random can place blocks, so no new decorator is needed.
 * A block output in the pool reaches checkCapacity / apply like any other modular output.
 */
@DisplayName("weighted_random の block 出力")
public class WeightedBlockOutputTest {

    private static class RecordingBlockOutput extends BlockOutput {

        final boolean fits;
        int applied;
        int lastMultiplier;
        boolean checkedBeforeApply = true;
        private boolean checked;

        RecordingBlockOutput(boolean fits) {
            this.fits = fits;
        }

        @Override
        public boolean checkCapacity(List<IModularPort> ports, int multiplier, ConditionContext context) {
            checked = true;
            return fits;
        }

        @Override
        public void apply(List<IModularPort> ports, int multiplier, ConditionContext context) {
            if (!checked) checkedBeforeApply = false;
            applied++;
            lastMultiplier = multiplier;
        }
    }

    /** Inner recipe that does nothing; the decorator only forwards calls to it. */
    private static IModularRecipe inertRecipe() {
        return (IModularRecipe) Proxy.newProxyInstance(
            IModularRecipe.class.getClassLoader(),
            new Class<?>[] { IModularRecipe.class },
            (proxy, method, args) -> null);
    }

    private static ConditionContext seeded(long seed) {
        return new ConditionContext(null, 0, 0, 0, null, seed);
    }

    private static WeightedRandomDecorator pool(RecordingBlockOutput a, RecordingBlockOutput b) {
        return new WeightedRandomDecorator(
            inertRecipe(),
            Arrays.asList(new WeightedOutputEntry(a, 1), new WeightedOutputEntry(b, 1)),
            1);
    }

    @Test
    @DisplayName("引かれた block 出力が置かれる")
    public void pickedBlockOutputIsApplied() {
        boolean aPicked = false;
        boolean bPicked = false;

        for (long seed = 0; seed < 200; seed++) {
            RecordingBlockOutput a = new RecordingBlockOutput(true);
            RecordingBlockOutput b = new RecordingBlockOutput(true);

            pool(a, b).produceExtraOutputs(new ArrayList<>(), seeded(seed));

            assertEquals(1, a.applied + b.applied, "ちょうど 1 つが置かれる: seed=" + seed);
            RecordingBlockOutput picked = a.applied == 1 ? a : b;
            assertEquals(1, picked.lastMultiplier);
            assertTrue(picked.checkedBeforeApply, "checkCapacity が先: seed=" + seed);
            if (picked == a) aPicked = true;
            else bPicked = true;
        }

        assertTrue(aPicked && bPicked, "両方が引かれる");
    }

    @Test
    @DisplayName("置けない block 出力は飛ばされる")
    public void blockOutputThatCannotFitIsSkipped() {
        for (long seed = 0; seed < 50; seed++) {
            RecordingBlockOutput a = new RecordingBlockOutput(false);
            RecordingBlockOutput b = new RecordingBlockOutput(false);

            pool(a, b).produceExtraOutputs(Collections.emptyList(), seeded(seed));

            assertEquals(0, a.applied + b.applied, "置けないなら apply しない: seed=" + seed);
        }
    }
}
