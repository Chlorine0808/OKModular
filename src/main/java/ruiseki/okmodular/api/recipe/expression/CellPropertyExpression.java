package ruiseki.okmodular.api.recipe.expression;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import ruiseki.okmodular.api.condition.ConditionContext;

/**
 * How far a pattern cell sits from the anchor it was placed at, measured on the world's axes:
 * {@code cell_x} / {@code cell_y} / {@code cell_z}.
 */
public class CellPropertyExpression implements IExpression {

    /** The names come from here and nowhere else, so one cannot be defined but not registered. */
    public enum Axis {

        X,
        Y,
        Z;

        String variable() {
            return "cell_" + name().toLowerCase();
        }
    }

    private final Axis axis;

    public CellPropertyExpression(String name) {
        this.axis = axisOf(name);
    }

    private static Axis axisOf(String name) {
        for (Axis axis : Axis.values()) {
            if (axis.variable()
                .equalsIgnoreCase(name)) return axis;
        }
        throw new IllegalArgumentException("not a cell coordinate: '" + name + "'");
    }

    /** Read by {@link ExpressionRegistry} to register the names. */
    public static Set<String> propertyNames() {
        Set<String> names = new LinkedHashSet<>();
        for (Axis axis : Axis.values()) {
            names.add(axis.variable());
        }
        return Collections.unmodifiableSet(names);
    }

    @Override
    public EvaluationValue evaluate(ConditionContext context) {
        // Outside a pattern there is no cell to report, the way there is no machine to report
        // when a recipe is drawn in NEI. See PatternCellCoordinateTest for the boundary.
        if (context == null || !context.hasCell()) return EvaluationValue.ZERO;

        switch (axis) {
            case X:
                return new EvaluationValue((double) context.getCellX());
            case Y:
                return new EvaluationValue((double) context.getCellY());
            default:
                return new EvaluationValue((double) context.getCellZ());
        }
    }

    @Override
    public String toString() {
        return axis.variable();
    }
}
