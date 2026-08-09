package ruiseki.okmodular.common.recipe;

/**
 * How many times a per-tick resource falls due while progress advances by one tick.
 *
 * <h2>Per unit of work, not per tick of the clock</h2>
 *
 * {@code workProgress} advances by the speed multiplier and {@code progress} is that value
 * truncated, so the integer progress of a machine faster than 1x <b>skips values</b> and the
 * progress of one slower than 1x <b>repeats them</b>. The rule this replaced asked whether
 * the current progress was exactly on a multiple, which made both of those visible in the
 * bill:
 *
 * <pre>
 * 1.5x, interval 2   progress 0, 1, 3, 4, 6, 7 - never lands on 2, 8, 14
 *                    100 ticks paid 50 draws for 150 work, where 75 was owed
 * 0.5x, interval 2   progress 0, 0, 1, 1, 2, 2 - lands on each multiple twice
 *                    100 ticks paid 50 draws for 50 work, where 25 was owed
 * </pre>
 *
 * Faster than 1x could only underpay and slower than 1x could only overpay, because progress
 * moving by more than one per tick can skip but never repeat, and moving by less than one can
 * repeat but never skip. Only exactly 1x was right.
 * <p>
 * Counting the multiples <b>covered</b> instead makes the draw follow the work: over a whole
 * recipe the count is {@code ceil(duration / interval)} whatever the speed.
 *
 * <h2>Why the half-open interval, and not the other one</h2>
 *
 * The stretch counted is {@code [from, to)} - the multiples the tick is about to work
 * through, not the ones it has finished. Both conventions give the same rate; they differ in
 * where the draw sits and in what a partial last interval costs.
 * <p>
 * This one keeps the machine paying <b>before</b> it does the work, which is what the rest of
 * the tick assumes: the check runs first and refuses if the resource is short. It also keeps
 * a 1.0x machine billed <b>exactly</b> as it was before, for every interval - the other
 * convention loses the final partial interval, so a duration of 100 at interval 3 would drop
 * from 34 draws to 33. The whole change then only moves machines whose speed is not 1.
 *
 * <h2>Why this is a class of its own</h2>
 *
 * {@link ProcessAgent} asks twice per tick: once in {@code tick} to find out whether the
 * resource is there, and once in {@code executeTick} to take it. <b>A check that disagreed
 * with its own consumption would draw a resource it had not confirmed was there</b>, so there
 * is one spelling of the rule and both halves call it. There were four copies of the old test
 * before it moved here, and a fifth in the dead template method that has since gone.
 */
public final class PerTickSchedule {

    private PerTickSchedule() {}

    /**
     * How many multiples of {@code interval} lie in {@code [fromProgress, toProgress)}.
     * <p>
     * The result is the multiplier to hand to {@code process} or {@code apply}: zero means
     * the resource is not due this tick, and a value above one means the tick works through
     * more than one interval and owes for all of them. A machine at 1.5x with interval 1 pays
     * 1, 2, 1, 2; at interval 2 it pays 1, 1, 0, 1. One at 0.5x with interval 1 pays 0, 1, 0,
     * 1 where it used to pay every tick.
     * <p>
     * A speed of zero or less leaves {@code toProgress} at {@code fromProgress} and draws
     * nothing. That is deliberate: {@code "speedMultiplier": "0"} used to sit at progress
     * zero forever, which is a multiple of every interval, and drain the machine dry without
     * ever advancing.
     *
     * @param fromProgress the work done before this tick, in whole units
     * @param toProgress   the work done after it, in the same units
     * @param interval     how often the resource is drawn; zero or less means "not a
     *                     per-tick resource" rather than "every unit"
     */
    public static int dueCount(long fromProgress, long toProgress, int interval) {
        if (interval <= 0 || toProgress <= fromProgress) return 0;
        return (int) (Math.floorDiv(toProgress - 1, interval) - Math.floorDiv(fromProgress - 1, interval));
    }
}
