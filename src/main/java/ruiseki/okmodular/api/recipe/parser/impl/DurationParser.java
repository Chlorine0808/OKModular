package ruiseki.okmodular.api.recipe.parser.impl;

import com.google.gson.JsonElement;

import ruiseki.okmodular.api.recipe.core.ModularRecipe;
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
            builder.durationExpr(expr);

            // Leave a static value behind for callers with no machine to evaluate
            // against - NEI, validation. A constant expression folds to a real
            // number; anything reading machine or world state cannot, and keeps the
            // builder's default.
            Integer folded = foldToConstant(expr);
            if (folded != null) builder.duration(folded);
        } else {
            builder.duration(element.getAsInt());
        }
    }

    /**
     * Evaluates the expression with no context, which succeeds only if it never
     * reaches for machine or world state.
     *
     * @return the value, or null if the expression needs a machine
     */
    private static Integer foldToConstant(IExpression expr) {
        try {
            double value = expr.evaluateDouble(null);
            if (value >= 1 && !Double.isNaN(value) && !Double.isInfinite(value)) {
                return (int) value;
            }
        } catch (Exception e) {
            // Machine-dependent. MachinePropertyExpression dereferences the context
            // without checking it, so this arrives as a NullPointerException.
        }
        return null;
    }
}
