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

        /** A read stamped later than now is stale too, so a clock going back cannot extend it. */
        boolean isFresh(long now) {
            return now >= readAt && now - readAt < MAX_AGE;
        }
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private long lastSweep;

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

    /** A key for one call. Each part is length-prefixed, so no two argument lists collide. */
    public static String key(String function, Object... parts) {
        StringBuilder key = new StringBuilder(function);
        for (Object part : parts) {
            String text = String.valueOf(part);
            key.append('|')
                .append(text.length())
                .append(':')
                .append(text);
        }
        return key.toString();
    }

    /** The value read under this key within the last {@link #MAX_AGE} ticks, or a fresh read. */
    public EvaluationValue get(String key, long now, Supplier<EvaluationValue> read) {
        Entry entry = entries.get(key);
        if (entry != null && entry.isFresh(now)) {
            return entry.value;
        }
        EvaluationValue value = read.get();
        sweep(now);
        entries.put(key, new Entry(value, now));
        return value;
    }

    public void clear() {
        entries.clear();
    }

    /** Drops expired entries once every {@link #MAX_AGE} ticks, for keys never asked for again. */
    private void sweep(long now) {
        if (now >= lastSweep && now - lastSweep < MAX_AGE) return;
        lastSweep = now;
        entries.values()
            .removeIf(entry -> !entry.isFresh(now));
    }

    int size() {
        return entries.size();
    }
}
