package ruiseki.okmodular.api.recipe.expression;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;

/**
 * One machine's recent world reads, reused for up to {@link #MAX_AGE} ticks.
 */
public final class WorldReadCache {

    /** How many ticks a read is reused for. The docs promise results lag by at most this. */
    public static final int MAX_AGE = 20;

    private static final class Entry {

        final EvaluationValue value;
        final long readAt;

        Entry(EvaluationValue value, long readAt) {
            this.value = value;
            this.readAt = readAt;
        }
    }

    private final Map<String, Entry> entries = new HashMap<>();

    /**
     * Reads through the machine's cache. A pattern cell, or a context with no machine, reads
     * live.
     */
    public static EvaluationValue read(ConditionContext context, long now, String key,
        Supplier<EvaluationValue> read) {
        IRecipeContext machine = context.getRecipeContext();
        WorldReadCache cache = machine != null && !context.hasCell() ? machine.getWorldReadCache() : null;
        return cache != null ? cache.get(key, now, read) : read.get();
    }

    /** The value read under this key within the last {@link #MAX_AGE} ticks, or a fresh read. */
    public EvaluationValue get(String key, long now, Supplier<EvaluationValue> read) {
        Entry entry = entries.get(key);
        if (entry != null && now >= entry.readAt && now - entry.readAt < MAX_AGE) {
            return entry.value;
        }
        EvaluationValue value = read.get();
        entries.put(key, new Entry(value, now));
        return value;
    }

    public void clear() {
        entries.clear();
    }
}
