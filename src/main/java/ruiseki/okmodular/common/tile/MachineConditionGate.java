package ruiseki.okmodular.common.tile;

import java.util.List;
import java.util.function.Supplier;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ICondition;

/**
 * Decides whether a machine is allowed to run at all.
 * <p>
 * Recipes have carried conditions for a while; this is the same idea one level up, for the
 * machine itself, declared in the structure JSON. It sits next to the redstone check in
 * the controller's tick and answers the same kind of question.
 * <p>
 * <b>Kept free of the World so it can be tested.</b> {@code TEMachineController} cannot be
 * built in a unit test - {@code MockWorld} throws from its constructor and the existing
 * tile entity tests are {@code @Disabled} - so the judgement lives here and only the
 * wiring is left to a running game. Same split as {@code ExternalPortConfigCodec},
 * {@code PortColorGrouping} and {@code ColoredRecipeSearch}.
 * <p>
 * <b>A machine with no conditions must not pay for the feature.</b> This runs every tick,
 * so the context is passed as a {@link Supplier} and is never asked for when there is
 * nothing to evaluate - no {@link ConditionContext}, and so no {@code HashMap}, is
 * allocated. The same reasoning put a copy-free path into
 * {@code PortColorGrouping.select}.
 */
public final class MachineConditionGate {

    /** Nothing failed. Shared, since it carries no state. */
    private static final Verdict MET = new Verdict(null);

    private MachineConditionGate() {}

    /**
     * The outcome, and which condition stopped the machine.
     * <p>
     * Holds the condition rather than its text: {@link ICondition#getDescription()} goes
     * through {@code StatCollector}, and the caller only needs it when it is about to put
     * something on screen.
     */
    public static final class Verdict {

        /** null when everything passed. */
        private final ICondition failed;

        Verdict(ICondition failed) {
            this.failed = failed;
        }

        public boolean isMet() {
            return failed == null;
        }

        /** The first condition that was not satisfied, or null if all were. */
        public ICondition getFailedCondition() {
            return failed;
        }

        /** Localized text for the failing condition, or null if none failed. */
        public String getFailedDescription() {
            return failed == null ? null : failed.getDescription();
        }
    }

    /**
     * Evaluates the conditions, stopping at the first one that is not satisfied.
     * <p>
     * One context is built and shared across all of them: they are all looking at the same
     * machine at the same instant, and {@link ConditionContext} caches property lookups
     * for exactly that reason. It is built lazily, so an empty list costs nothing.
     * <p>
     * Null entries are skipped. A parser that cannot read a condition returns null and
     * warns at load time, so the failure has already been reported; filtering belongs in
     * the reader, and throwing here would turn a bad line of JSON into a crash every tick.
     *
     * @param conditions      the machine's conditions; null or empty means "always run"
     * @param contextSupplier builds the evaluation context, called at most once and only
     *                        if there is something to evaluate
     */
    public static Verdict evaluate(List<ICondition> conditions, Supplier<ConditionContext> contextSupplier) {
        if (conditions == null || conditions.isEmpty()) return MET;

        ConditionContext context = null;
        for (ICondition condition : conditions) {
            if (condition == null) continue;
            if (context == null) context = contextSupplier.get();
            if (!condition.isMet(context)) return new Verdict(condition);
        }
        return MET;
    }
}
