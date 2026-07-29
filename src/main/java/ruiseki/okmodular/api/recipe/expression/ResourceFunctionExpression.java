package ruiseki.okmodular.api.recipe.expression;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.recipe.core.IMachineState;

/**
 * Reads one named resource off the machine, as in {@code essentia("ignis")} or
 * {@code fluid_f_in("water")}.
 *
 * <p>
 * The question is three independent choices - which resource kind, which
 * direction, and amount or remaining space - so this holds those three rather
 * than one enum constant per combination. The cross product used to be spelled
 * out as 18 constants with a switch over them, which left holes: essentia and vis
 * had no directional or space variants while fluid and gas did. Adding a resource
 * kind now adds no code here, only a row in the registration table.
 *
 * <p>
 * The registered function name is kept alongside them because {@link #toString()}
 * has to reproduce it exactly. Expression trees are serialised through
 * {@code toString} and parsed back, so a name that cannot round-trip corrupts the
 * expression on reload.
 */
public class ResourceFunctionExpression implements IExpression {

    /** Whether the function asks how much is held or how much more fits. */
    public enum Mode {
        AMOUNT,
        SPACE
    }

    private final String functionName;
    private final IPortType.Type kind;
    private final IPortType.Direction direction;
    private final Mode mode;
    private final IExpression argument;

    public ResourceFunctionExpression(String functionName, IPortType.Type kind, IPortType.Direction direction,
        Mode mode, IExpression argument) {
        this.functionName = functionName;
        this.kind = kind;
        this.direction = direction;
        this.mode = mode;
        this.argument = argument;
    }

    @Override
    public EvaluationValue evaluate(ConditionContext context) {
        if (context == null) return EvaluationValue.ZERO;

        IRecipeContext recipeContext = context.getRecipeContext();
        if (recipeContext == null) return EvaluationValue.ZERO;

        IMachineState state = recipeContext.getMachineState();
        if (state == null) return EvaluationValue.ZERO;

        String key = argument != null ? argument.evaluate(context)
            .asString() : "";

        long value = mode == Mode.SPACE ? state.getSpace(kind, direction, key) : state.getAmount(kind, direction, key);

        return new EvaluationValue(value);
    }

    @Override
    public String toString() {
        return functionName + "(" + argument + ")";
    }
}
