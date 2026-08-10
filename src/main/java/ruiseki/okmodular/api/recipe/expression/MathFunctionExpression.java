package ruiseki.okmodular.api.recipe.expression;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.util.Logger;

/**
 * Expression that performs mathematical functions like sin, cos, sqrt, etc.
 */
public class MathFunctionExpression implements IExpression {

    private final String functionName;
    private final List<IExpression> arguments;

    /**
     * The names a recipe may write. {@code ExpressionRegistry} registers exactly these, so a
     * function with a {@code case} below but no entry here cannot be called at all - which is
     * where {@code cbrt}, {@code ceiling} and {@code ln} sat until
     * {@code MathFunctionCoverageTest} went looking. Implemented, correct, unreachable.
     */
    public static final Set<String> SUPPORTED_FUNCTIONS = Collections.unmodifiableSet(
        new HashSet<>(
            Arrays.asList(
                "abs",
                "sqrt",
                "cbrt",
                "ceiling",
                "ln",
                "sin",
                "cos",
                "tan",
                "rad",
                "deg",
                "exp",
                "log",
                "log10",
                "asin",
                "acos",
                "atan",
                "atan2",
                "ncr",
                "npr",
                "fact",
                "combi",
                "combination",
                "perm",
                "permu",
                "permutation",
                "sign",
                "clamp",
                "random",
                "floor",
                "ceil",
                "round",
                "chance",
                "pow",
                "tetrate",
                "min",
                "max")));

    /**
     * How many levels of a tower are built before it is abandoned.
     * <p>
     * <b>Not a guard against big numbers -- a guard against not finishing.</b> A base above
     * e^(1/e) diverges and saturates the double range within a handful of steps, so Infinity
     * stops it on its own. A base at or below that <i>converges</i> ({@code 1.4 ^^ n} settles
     * near 1.887, {@code 1 ^^ n} is 1 forever), and nothing about the value ever says stop.
     * {@code "1.4 ^^ 1000000000"} is the expression that would sit in the tick loop, not
     * {@code "2 ^^ 1000000000"}.
     */
    private static final int TOWER_LIMIT = 64;

    public MathFunctionExpression(String functionName, List<IExpression> arguments) {
        this.functionName = functionName.toLowerCase();
        this.arguments = arguments;
    }

    /**
     * Reproduces the call as a recipe script.
     * <p>
     * Callers persist expressions by writing this string to NBT or JSON and parsing
     * it back later, so the result has to be readable by
     * {@link ExpressionParser#parseExpression}.
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(functionName).append('(');
        if (arguments != null) {
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) text.append(", ");
                text.append(arguments.get(i));
            }
        }
        return text.append(')')
            .toString();
    }

    @Override
    public EvaluationValue evaluate(ConditionContext context) {
        if (functionName.equals("random")) {
            // See SeedMixer: new Random(seed).nextDouble() answers almost the same number for
            // seeds a few apart, and one machine's seeds are always close together.
            return new EvaluationValue(SeedMixer.toUnitInterval(context.getEvaluationSeed(), SeedMixer.RANDOM));
        }

        if (arguments == null || arguments.isEmpty()) return EvaluationValue.ZERO;

        // Special case: log with 2 arguments
        if (functionName.equals("log") && arguments.size() == 2) {
            double x = arguments.get(0)
                .evaluate(context)
                .asDouble();
            double base = arguments.get(1)
                .evaluate(context)
                .asDouble();
            if (base <= 0 || base == 1 || x <= 0) return EvaluationValue.ZERO;
            return new EvaluationValue(Math.log(x) / Math.log(base));
        }

        double val = arguments.get(0)
            .evaluate(context)
            .asDouble();

        switch (functionName) {
            case "abs":
                return new EvaluationValue(Math.abs(val));
            case "ceil":
            case "ceiling":
                return new EvaluationValue(Math.ceil(val));
            case "floor":
                return new EvaluationValue(Math.floor(val));
            case "sqrt":
                return new EvaluationValue(val < 0 ? 0 : Math.sqrt(val));
            case "cbrt":
                return new EvaluationValue(Math.cbrt(val));
            case "sin":
                return new EvaluationValue(Math.sin(val));
            case "cos":
                return new EvaluationValue(Math.cos(val));
            case "tan":
                return new EvaluationValue(Math.tan(val));
            case "asin":
                return new EvaluationValue((val < -1 || val > 1) ? 0 : Math.asin(val));
            case "acos":
                return new EvaluationValue((val < -1 || val > 1) ? 0 : Math.acos(val));
            case "atan":
                return new EvaluationValue(Math.atan(val));
            case "rad":
                return new EvaluationValue(Math.toRadians(val));
            case "deg":
                return new EvaluationValue(Math.toDegrees(val));
            case "log":
                return new EvaluationValue(val <= 0 ? 0 : Math.log(val));
            case "log10":
                return new EvaluationValue(val <= 0 ? 0 : Math.log10(val));
            case "ln":
                return new EvaluationValue(val <= 0 ? 0 : Math.log(val));
            case "exp":
                return new EvaluationValue(Math.exp(val));
            case "round":
                return new EvaluationValue((double) Math.round(val));
            case "fact": {
                // The step is optional, the way log()'s base is: fact(n) is n! and fact(n, 2)
                // is the double factorial. One name, because fact(n, 1) has to mean n!.
                double step = arguments.size() >= 2 ? arguments.get(1)
                    .evaluate(context)
                    .asDouble() : 1;
                return new EvaluationValue(factorial(val, step));
            }
            case "sign":
                return new EvaluationValue(Math.signum(val));
            case "chance":
                return new EvaluationValue(
                    SeedMixer.toUnitInterval(context.getEvaluationSeed(), SeedMixer.CHANCE) < val);
            case "npr":
            case "perm":
            case "permu":
            case "permutation": {
                if (arguments.size() < 2) return EvaluationValue.ZERO;
                double r = arguments.get(1)
                    .evaluate(context)
                    .asDouble();
                return new EvaluationValue(permutation(val, r));
            }
            case "ncr":
            case "combi":
            case "combination": {
                if (arguments.size() < 2) return EvaluationValue.ZERO;
                double r = arguments.get(1)
                    .evaluate(context)
                    .asDouble();
                return new EvaluationValue(combination(val, r));
            }
            case "atan2": {
                if (arguments.size() < 2) return EvaluationValue.ZERO;
                double x = arguments.get(1)
                    .evaluate(context)
                    .asDouble();
                return new EvaluationValue(Math.atan2(val, x));
            }
            case "pow": {
                if (arguments.size() < 2) return EvaluationValue.ZERO;
                double p = arguments.get(1)
                    .evaluate(context)
                    .asDouble();
                return new EvaluationValue(Math.pow(val, p));
            }
            case "tetrate": {
                if (arguments.size() < 2) return EvaluationValue.ZERO;
                double height = arguments.get(1)
                    .evaluate(context)
                    .asDouble();
                return new EvaluationValue(tetrate(val, height));
            }
            case "min": {
                if (arguments.size() < 2) return EvaluationValue.ZERO;
                double v2 = arguments.get(1)
                    .evaluate(context)
                    .asDouble();
                return new EvaluationValue(Math.min(val, v2));
            }
            case "max": {
                if (arguments.size() < 2) return EvaluationValue.ZERO;
                double v2 = arguments.get(1)
                    .evaluate(context)
                    .asDouble();
                return new EvaluationValue(Math.max(val, v2));
            }
            case "clamp": {
                if (arguments.size() < 3) return new EvaluationValue(val);
                double min = arguments.get(1)
                    .evaluate(context)
                    .asDouble();
                double max = arguments.get(2)
                    .evaluate(context)
                    .asDouble();
                if (min > max) {
                    Logger.warn(
                        "[Expression] clamp(v, min, max) called with min > max (min=" + min + ", max=" + max + ")");
                    double temp = min;
                    min = max;
                    max = temp;
                }
                return new EvaluationValue(Math.max(min, Math.min(max, val)));
            }
        }
        return EvaluationValue.ZERO;
    }

    private double factorial(double n) {
        return factorial(n, 1);
    }

    /**
     * The n-tuple factorial: n(n-k)(n-2k)... for as long as the terms stay positive.
     * <p>
     * A step of one is the ordinary factorial, a step of two the double factorial. There is one
     * loop rather than a separate one per step <b>because the answers have to agree</b>: the
     * terms are not exact in a double past 18!, so multiplying the same chain in a different
     * order gives a different number in the low bits, and {@code fact(n, 1)} has to keep
     * answering exactly what {@code fact(n)} answered before this existed.
     * <p>
     * That is why the loop climbs from the smallest term of the chain instead of descending from
     * {@code n}. For a step of one it is 1·2·3·…·n, unchanged.
     *
     * @param step the gap between terms; zero or negative gives 0, having no chain to walk (and
     *             a step of zero would never terminate)
     */
    private double factorial(double n, double step) {
        if (step <= 0) return 0;

        long k = (long) Math.floor(step);
        long last = (long) Math.floor(n);
        if (last <= 0) return 1;

        double res = 1;
        // i > 0 catches the overflow a huge step would cause; the chain is bounded either way,
        // because the product reaches Infinity within 171 terms whatever the step is.
        for (long i = ((last - 1) % k) + 1; i > 0 && i <= last; i += k) {
            res *= i;
            if (Double.isInfinite(res)) return Double.POSITIVE_INFINITY;
        }
        return res;
    }

    /**
     * Tetration: a tower of {@code height} copies of {@code base}, evaluated from the top.
     * <p>
     * {@code a ^^ 0} is 1, {@code a ^^ 1} is a, {@code a ^^ 2} is a^a. <b>The usable range is
     * tiny</b> -- {@code 2 ^^ 4} is 65536 and {@code 2 ^^ 5} is already past what a double
     * holds, as is {@code 3 ^^ 4} and {@code 10 ^^ 3}. That is the notation's nature rather
     * than a limitation here.
     * <p>
     * A negative height gives 0 and a base that would need a fractional power of a negative
     * number gives 0, which is how the rest of this class answers a question outside its
     * domain ({@code sqrt(-1)}, {@code asin(2)}). Infinity is passed through, because
     * "too big to hold" is a real answer and {@code fact} already reports it that way.
     *
     * @param height rounded down; a tower of fractional height is not elementary
     */
    private double tetrate(double base, double height) {
        if (height < 0) return 0;

        long levels = (long) Math.floor(height);
        if (levels == 0) return 1;

        double res = base;
        long steps = Math.min(levels - 1, TOWER_LIMIT);
        for (long i = 0; i < steps; i++) {
            res = Math.pow(base, res);
            if (Double.isNaN(res)) return 0;
            if (Double.isInfinite(res)) return res;
        }
        return res;
    }

    private double permutation(double n, double r) {
        if (n < r || r < 0) {
            return 0;
        }
        return factorial(n) / factorial(n - r);
    }

    private double combination(double n, double r) {
        if (n < r || r < 0) {
            return 0;
        }
        return factorial(n) / (factorial(r) * factorial(n - r));
    }
}
