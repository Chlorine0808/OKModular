package ruiseki.okmodular.api.recipe.expression;

import ruiseki.okmodular.api.condition.ConditionContext;

/**
 * Interface for actions that perform side effects (commands).
 */
public interface IAction {

    /**
     * Execute the action.
     *
     * @param context The evaluation context.
     */
    void execute(ConditionContext context);
}
