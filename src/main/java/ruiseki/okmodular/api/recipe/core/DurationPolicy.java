package ruiseki.okmodular.api.recipe.core;

/**
 * When a machine re-resolves a recipe duration that was written as an
 * expression.
 * <p>
 * This has no effect on durations written as plain numbers.
 */
public enum DurationPolicy {

    /**
     * Evaluate once, when the recipe starts.
     * <p>
     * The work amount is then fixed for the run, which is what the progress display
     * assumes. This is the default.
     */
    ON_START,

    /**
     * Re-evaluate every tick, so changes in world or machine state take effect
     * part-way through a run.
     * <p>
     * The work amount is the denominator of the progress display, so it moves in
     * both directions: the bar can jump backwards, and a duration that drops below
     * the work already done completes the recipe on that tick. Machines whose
     * durations depend on something volatile — weather, moon phase — want this;
     * most do not.
     */
    PER_TICK;

    /**
     * Reads a policy name as written in a structure definition, accepting
     * <code>onStart</code>, <code>on_start</code> and <code>ON_START</code> alike.
     *
     * @param name         The name to read, may be null
     * @param defaultValue What to return when the name is null or unrecognised
     */
    public static DurationPolicy fromString(String name, DurationPolicy defaultValue) {
        if (name == null) return defaultValue;

        String normalised = name.trim()
            .replace("-", "")
            .replace("_", "")
            .toLowerCase();
        switch (normalised) {
            case "onstart":
                return ON_START;
            case "pertick":
                return PER_TICK;
            default:
                return defaultValue;
        }
    }
}
