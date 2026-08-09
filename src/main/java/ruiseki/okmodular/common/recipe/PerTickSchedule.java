package ruiseki.okmodular.common.recipe;

/**
 * When a per-tick input or output falls due.
 * <p>
 * The test that models this lives in {@code PerTickScheduleTest}, and it needs one spelling
 * of the rule to model. There were four copies of {@code interval > 0 && progress % interval
 * == 0} in {@link ProcessAgent} - two for the check pass, two for the consuming pass - and a
 * check that disagreed with its own consumption would draw a resource it had not confirmed
 * was there.
 */
public final class PerTickSchedule {

    private PerTickSchedule() {}

    /**
     * Whether a resource with this interval is due at this progress value.
     * <p>
     * Progress is {@code (long) workProgress} and {@code workProgress} advances by the speed
     * multiplier, so a machine faster than 1x <b>skips progress values</b>. At 1.5x, progress
     * runs 0, 1, 3, 4, 6, 7, 9 - and an interval that only lands on a skipped value never
     * comes due. The machine keeps the work but not the cost.
     *
     * @param progress the work done so far, in whole units
     * @param interval how often the resource is drawn, in the same units; zero or less means
     *                 "not a per-tick resource"
     */
    public static boolean isDue(long progress, int interval) {
        return interval > 0 && progress % interval == 0;
    }
}
