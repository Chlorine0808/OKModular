package ruiseki.okmodular.api.recipe.parser.impl;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.google.gson.JsonElement;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.recipe.core.MachinePerformance;
import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.expression.EvaluationValue;
import ruiseki.okmodular.api.recipe.expression.ExpressionParser;
import ruiseki.okmodular.api.recipe.expression.IExpression;
import ruiseki.okmodular.api.recipe.parser.IRecipePropertyParser;

public class DurationParser implements IRecipePropertyParser {

    @Override
    public void parse(ModularRecipe.Builder builder, JsonElement element) {
        if (!element.isJsonPrimitive()) return;

        if (element.getAsJsonPrimitive()
            .isString()) {
            IExpression expr = ExpressionParser.parseExpression(element.getAsString());
            Integer folded = foldToConstant(expr);

            if (folded != null) {
                // A constant expression is a number written the long way. Keeping it
                // as an expression would only deny NEI and validation a value they
                // could have read.
                builder.duration(folded);
            } else {
                builder.durationExpr(expr);
            }
        } else {
            builder.duration(element.getAsInt());
        }
    }

    /**
     * Evaluates the expression against a context that refuses every question, which
     * succeeds only if it never reaches for machine or world state.
     * <p>
     * A plain null context is not enough: count_blocks and can_see_sky answer 0 to null
     * rather than throwing, and that 0 used to be folded in as if it were the duration.
     *
     * @return the value, or null if the expression needs a machine
     */
    private static Integer foldToConstant(IExpression expr) {
        try {
            double value = expr.evaluateDouble(new NoContext());
            if (value >= 1 && !Double.isNaN(value) && !Double.isInfinite(value)) {
                return (int) value;
            }
        } catch (Exception e) {
            // Machine-dependent: it asked the context something.
        }
        return null;
    }

    /** A context whose every accessor throws, so any read of it marks the expression as not constant. */
    private static final class NoContext extends ConditionContext {

        NoContext() {
            super(null, 0, 0, 0);
        }

        private static IllegalStateException asked() {
            return new IllegalStateException("not a constant expression");
        }

        @Override
        public ConditionContext withPerformance(MachinePerformance performance) {
            throw asked();
        }

        @Override
        public MachinePerformance getPerformance() {
            throw asked();
        }

        @Override
        public boolean hasCell() {
            throw asked();
        }

        @Override
        public int getCellX() {
            throw asked();
        }

        @Override
        public int getCellY() {
            throw asked();
        }

        @Override
        public int getCellZ() {
            throw asked();
        }

        @Override
        public EvaluationValue getCachedValue(String key) {
            throw asked();
        }

        @Override
        public void setCachedValue(String key, EvaluationValue value) {
            throw asked();
        }

        @Override
        public NBTTagCompound getWorkingNBT() {
            throw asked();
        }

        @Override
        public void setWorkingNBT(NBTTagCompound nbt) {
            throw asked();
        }

        @Override
        public void clearCache() {
            throw asked();
        }

        @Override
        public World getWorld() {
            throw asked();
        }

        @Override
        public int getX() {
            throw asked();
        }

        @Override
        public int getY() {
            throw asked();
        }

        @Override
        public int getZ() {
            throw asked();
        }

        @Override
        public IRecipeContext getRecipeContext() {
            throw asked();
        }

        @Override
        public ConditionContext forDraw(int index) {
            throw asked();
        }

        @Override
        public long getEvaluationSeed() {
            throw asked();
        }

        @Override
        public long getPlacementSeed() {
            throw asked();
        }
    }
}
