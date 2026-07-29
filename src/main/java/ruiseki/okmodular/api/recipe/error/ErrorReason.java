package ruiseki.okmodular.api.recipe.error;

/**
 * Represents the reason why a recipe cannot run or is blocked.
 */
public enum ErrorReason {

    NONE("none", ""),
    IDLE("idle", "Idle"),
    RUNNING("running", "Processing"),
    NO_RECIPES("no_recipes", "No recipes registered"),
    NO_INPUT_PORTS("no_input_ports", "No input ports connected"),
    NO_OUTPUT_PORTS("no_output_ports", "No output ports connected"),
    NO_ENERGY("no_energy", "Insufficient energy"),
    OUTPUT_FULL("output_full", "Output full"),
    INPUT_MISSING("input_missing", "Input missing"),
    NO_MATCHING_RECIPE("no_matching_recipe", "No matching recipe"),
    WAITING_OUTPUT("waiting_output", "Waiting for output space"),
    NO_INPUT("no_input", "No input resources"),
    PAUSED("paused", "Paused by Redstone"),
    MISSING_BLUEPRINT("missing_blueprint", "No Blueprint"),
    OUTPUT_CAPACITY_INSUFFICIENT("output_capacity_insufficient", "Output Capacity Insufficient"),
    NO_MANA("no_mana", "Insufficient Mana"),
    BLOCK_MISSING("block_missing", "Block missing"),
    BLOCK_OUTPUT_FULL("block_output_full", "No space for Block");

    private final String id;
    private final String defaultMessage;
    private String detail = "";

    ErrorReason(String id, String defaultMessage) {
        this.id = id;
        this.defaultMessage = defaultMessage;
    }

    public String getId() {
        return id;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public String getUnlocalizedName() {
        return "gui.status." + id;
    }

    /**
     * Whether an idle machine should say this rather than just "Idle".
     * <p>
     * <b>The default is yes, and that is the point.</b> The GUI used to pick from a
     * hand-written list of five, so the other thirteen were set, synced, and then dropped
     * on the floor - {@code PAUSED}, {@code NO_MANA}, {@code BLOCK_MISSING},
     * {@code MISSING_BLUEPRINT}, {@code BLOCK_OUTPUT_FULL} and {@code WAITING_OUTPUT} all
     * reached the client and none of them was ever displayed. A list cannot notice a
     * constant that was added after it was written.
     * <p>
     * Answering yes by default turns that failure inside out: a new constant shows up
     * immediately, and if nobody wrote it a translation then
     * {@code ErrorReasonLangCoverageTest} fails rather than a player seeing "Idle" for a
     * machine that is actually stuck.
     * <p>
     * Only two say no. {@link #NONE} means there is no error at all, and {@link #RUNNING}
     * would claim the machine is working while this very method is being asked what to
     * show an idle one.
     */
    public boolean showsWhenIdle() {
        return this != NONE && this != RUNNING;
    }

    public ErrorReason withDetail(String detail) {
        this.detail = detail;
        return this;
    }

    public String getMessage() {
        if (detail != null && !detail.isEmpty()) {
            return defaultMessage + ": " + detail;
        }
        return defaultMessage;
    }
}
