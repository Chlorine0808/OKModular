package ruiseki.okmodular.common.tile;

import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.structure.core.IStructureEntry;

/**
 * Evaluates the performance modifiers a structure definition gives its machine.
 * <p>
 * The four of them - {@code speedMultiplier}, {@code energyMultiplier}, {@code batchMin} and
 * {@code batchMax} - can all be written as expressions, so each needs a context to be
 * evaluated against. <b>That context has to know the machine.</b> A bare
 * {@link ConditionContext} answers zero for every machine property, which turns
 * {@code "speedMultiplier": "tier"} into a silent zero rather than an error. The context is
 * taken as a {@link Supplier} so the caller decides what it carries and so nothing is
 * allocated for a machine that has no structure - this runs every tick.
 *
 * <h2>Why the guard is part of the same class</h2>
 *
 * {@code speed_multi} and {@code energy_multi} are registered machine properties that resolve
 * back to the very methods that ask for these values. A definition that reads the value it
 * defines is therefore a genuine cycle:
 *
 * <pre>
 * getSpeedMultiplier() → evaluate → speed_multi → getSpeedMultiplier() → ...
 * </pre>
 *
 * Nothing stopped that before, because the missing machine made {@code speed_multi} answer
 * zero and the recursion died there. Handing over a context that knows the machine removes
 * that accident, so the cycle has to be broken deliberately or the first definition that
 * writes one takes the server down with a {@code StackOverflowError}.
 * <p>
 * A cycle yields the neutral value - 1.0 for a multiplier, 1 for a batch bound - and is
 * reported <b>once per modifier</b>. The guard is per modifier rather than global so that one
 * modifier may legitimately read another ({@code "speedMultiplier": "energy_multi"}).
 */
public final class MachineModifiers {

    /** A multiplier that changes nothing. */
    public static final double NEUTRAL_MULTIPLIER = 1.0;

    /** A batch bound that changes nothing. */
    public static final int NEUTRAL_BATCH = 1;

    /** The modifiers, named as they are written in the structure JSON. */
    public enum Modifier {

        SPEED_MULTIPLIER("speedMultiplier"),
        ENERGY_MULTIPLIER("energyMultiplier"),
        BATCH_MIN("batchMin"),
        BATCH_MAX("batchMax");

        private final String jsonKey;

        Modifier(String jsonKey) {
            this.jsonKey = jsonKey;
        }

        /** The key this modifier is written under in a structure definition. */
        public String getJsonKey() {
            return jsonKey;
        }
    }

    private final EnumSet<Modifier> evaluating = EnumSet.noneOf(Modifier.class);
    private final EnumSet<Modifier> reported = EnumSet.noneOf(Modifier.class);
    private final Consumer<Modifier> onCycle;

    /**
     * @param onCycle told about a cycle the first time each modifier hits one. May be null.
     */
    public MachineModifiers(Consumer<Modifier> onCycle) {
        this.onCycle = onCycle;
    }

    public double speedMultiplier(IStructureEntry entry, Supplier<ConditionContext> context) {
        if (entry == null || !enter(Modifier.SPEED_MULTIPLIER)) return NEUTRAL_MULTIPLIER;
        try {
            return entry.evaluateSpeedMultiplier(context.get());
        } finally {
            evaluating.remove(Modifier.SPEED_MULTIPLIER);
        }
    }

    public double energyMultiplier(IStructureEntry entry, Supplier<ConditionContext> context) {
        if (entry == null || !enter(Modifier.ENERGY_MULTIPLIER)) return NEUTRAL_MULTIPLIER;
        try {
            return entry.evaluateEnergyMultiplier(context.get());
        } finally {
            evaluating.remove(Modifier.ENERGY_MULTIPLIER);
        }
    }

    public int batchMin(IStructureEntry entry, Supplier<ConditionContext> context) {
        if (entry == null || !enter(Modifier.BATCH_MIN)) return NEUTRAL_BATCH;
        try {
            return entry.evaluateBatchMin(context.get());
        } finally {
            evaluating.remove(Modifier.BATCH_MIN);
        }
    }

    public int batchMax(IStructureEntry entry, Supplier<ConditionContext> context) {
        if (entry == null || !enter(Modifier.BATCH_MAX)) return NEUTRAL_BATCH;
        try {
            return entry.evaluateBatchMax(context.get());
        } finally {
            evaluating.remove(Modifier.BATCH_MAX);
        }
    }

    /**
     * Re-arm the one-shot cycle report.
     * <p>
     * Called when the machine takes on a different structure definition: the cycle that was
     * already reported belonged to the previous one, and the new one deserves to be heard.
     * Does not touch which modifiers are mid-evaluation - that is unwound by {@code finally}
     * and clearing it here would only hide a leak.
     */
    public void reset() {
        reported.clear();
    }

    /**
     * @return true when this modifier may be evaluated; false when it is already being
     *         evaluated further up the stack, in which case the cycle is reported once.
     */
    private boolean enter(Modifier modifier) {
        if (evaluating.add(modifier)) return true;
        if (onCycle != null && reported.add(modifier)) {
            onCycle.accept(modifier);
        }
        return false;
    }
}
