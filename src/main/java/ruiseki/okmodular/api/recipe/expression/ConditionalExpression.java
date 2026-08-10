package ruiseki.okmodular.api.recipe.expression;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ICondition;

/**
 * {@code condition ? whenTrue : whenFalse}.
 */
public class ConditionalExpression implements IExpression {

    private final ICondition condition;
    private final IExpression whenTrue;
    private final IExpression whenFalse;

    public ConditionalExpression(ICondition condition, IExpression whenTrue, IExpression whenFalse) {
        this.condition = condition;
        this.whenTrue = whenTrue;
        this.whenFalse = whenFalse;
    }

    @Override
    public EvaluationValue evaluate(ConditionContext context) {
        return condition.isMet(context) ? whenTrue.evaluate(context) : whenFalse.evaluate(context);
    }

    /**
     * Reproduces the expression as a recipe script.
     */
    @Override
    public String toString() {
        return "(" + condition + " ? " + whenTrue + " : " + whenFalse + ")";
    }
}
