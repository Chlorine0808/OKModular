package ruiseki.okmodular.api.structure.core;

/**
 * What becomes of a running recipe when the machine's own conditions stop being met.
 * <p>
 * A machine's conditions - declared at the top level of a structure definition - gate the
 * machine as a whole, the way a redstone signal does. This says what that gate means for
 * work already under way. It has no bearing on a machine with no conditions.
 */
public enum ConditionPolicy {

    /**
     * Freeze where it is and wait for the conditions to hold again.
     * <p>
     * Progress and the recipe are kept, so <b>nothing already consumed is lost</b>, and the
     * run continues from where it stopped. This is what a redstone signal does, and it is
     * the default for the same reason: a machine that quietly resumes is easier to reason
     * about than one that throws work away.
     */
    PAUSE,

    /**
     * Throw the recipe away.
     * <p>
     * Inputs already consumed do not come back. For machines that should feel fragile -
     * where losing the weather part-way through is meant to ruin the batch - rather than
     * for anything a player would run unattended.
     */
    ABORT;

    /**
     * Reads a policy name as written in a structure definition, accepting
     * <code>pause</code>, <code>PAUSE</code> and <code>Pause</code> alike.
     * <p>
     * An unrecognised name falls back rather than throwing: a misspelling in JSON should
     * not stop a machine, and the reader warns about it at load time.
     *
     * @param name         The name to read, may be null
     * @param defaultValue What to return when the name is null or unrecognised
     */
    public static ConditionPolicy fromString(String name, ConditionPolicy defaultValue) {
        if (name == null) return defaultValue;

        String normalised = name.trim()
            .replace("-", "")
            .replace("_", "")
            .toLowerCase();
        switch (normalised) {
            case "pause":
                return PAUSE;
            case "abort":
                return ABORT;
            default:
                return defaultValue;
        }
    }
}
