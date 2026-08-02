package ruiseki.okmodular.api.recipe.expression;

/**
 * Turns an evaluation seed into a number in [0, 1).
 * <p>
 * {@code random()} and {@code chance()} used {@code new Random(seed).nextDouble()} as a
 * one-shot. Java's {@code Random} is a linear congruential generator, and its <em>first</em>
 * output is a poor hash of the seed - seeds a few apart answer almost the same number:
 *
 * <pre>
 * seed +0 -&gt; 0.997969    seed +200 -&gt; 0.028785
 * seed +1 -&gt; 0.997521    seed +400 -&gt; 0.942070
 * seed +2 -&gt; 0.997432    seed +600 -&gt; 0.967153
 * </pre>
 *
 * A machine's seed is built from its position, the world seed and how many recipes it has
 * run, so one machine draws from a narrow band of seeds for its whole life. An output of
 * {@code "1 + floor(random() * 3)"} came out as a machine that only ever made two and a
 * machine that only ever made three.
 * <p>
 * The seed itself is fine - it does vary per machine and per recipe. Only the way it was
 * used was wrong. Running it through the SplitMix64 finalizer first spreads nearby seeds
 * across the whole range.
 */
public final class SeedMixer {

    /** The stream {@code random()} draws from. */
    public static final long RANDOM = 0L;

    /** The stream {@code chance()} draws from, so the two never track each other. */
    public static final long CHANCE = 1L;

    /** The stream a recipe's {@code chance} decorator draws from. */
    public static final long RECIPE_CHANCE = 2L;

    /**
     * Reserved by {@link #forPosition}. Not a {@link #toUnitInterval} stream - it is listed
     * here so a later stream does not claim the same slot and track position-derived seeds.
     */
    public static final long POSITION = 3L;

    // One stream per decorator that draws, so two decorators on the same recipe do not fire
    // and skip together. They all see the same evaluation seed; without separate streams a
    // recipe carrying both a bonus and a weighted pick would have them agree every time.

    /** The stream {@code bonus_output} draws from. */
    public static final long BONUS_OUTPUT = 4L;

    /** The stream {@code bonus_block_output} draws from. */
    public static final long BONUS_BLOCK_OUTPUT = 5L;

    /** The stream {@code weighted_random} draws from. */
    public static final long WEIGHTED_OUTPUT = 6L;

    /** The stream {@code random_block_output} draws from. */
    public static final long RANDOM_BLOCK_OUTPUT = 7L;

    /** The stream {@code per_position_probability} draws from. */
    public static final long PER_POSITION = 8L;

    /** Odd 64-bit constant, so distinct streams land far apart. */
    private static final long STREAM_GAMMA = 0x9E3779B97F4A7C15L;

    /** Per-axis odd constants, so swapping two coordinates does not land on the same seed. */
    private static final long X_GAMMA = 0xFF51AFD7ED558CCDL;
    private static final long Y_GAMMA = 0xC2B2AE3D27D4EB4FL;
    private static final long Z_GAMMA = 0x165667B19E3779F9L;

    private static final double UNIT = 1.0 / (double) (1L << 53);

    private SeedMixer() {}

    /**
     * @param seed   the context's evaluation seed; any value, including zero and negatives
     * @param stream {@link #RANDOM}, {@link #CHANCE} or {@link #RECIPE_CHANCE}
     * @return a number in [0, 1), stable for the same seed and stream
     */
    public static double toUnitInterval(long seed, long stream) {
        return (mix(seed + stream * STREAM_GAMMA) >>> 11) * UNIT;
    }

    /**
     * A seed for one of several draws made from the same context.
     * <p>
     * A batch of n is n runs of the recipe, so an amount written with {@code random()} is
     * drawn n times rather than once and multiplied - otherwise a batch of three can only
     * ever pay out multiples of three.
     *
     * @param index which draw, from zero; index zero returns the seed unchanged so a batch
     *              of one is bit-for-bit what it was
     */
    public static long forDraw(long seed, int index) {
        return index == 0 ? seed : mix(seed + (index + 8L) * STREAM_GAMMA);
    }

    /**
     * A seed for one position, from a seed that is fixed for the whole run.
     * <p>
     * A machine's evaluation seed is settled when the recipe starts and persisted in NBT, so
     * every evaluation inside one run answers the same number. That is the property that
     * makes a run survive a save and reload - but it also means anything drawn <em>per
     * position</em> draws the same number at every position. A decorator that walks fifty
     * cells asking "does this one appear?" got fifty identical answers, so it placed all of
     * them or none of them.
     * <p>
     * Mixing the coordinates in keeps the run reproducible while letting neighbours disagree.
     * Coordinates may be world positions or structure-local {@code (a, b, c)} cells, and
     * either may be negative - a structure's cells are anchor-relative, so about half of them
     * are.
     * <p>
     * Unlike {@link #forDraw}, no argument is passed through unchanged: the cell at the
     * origin has to differ from a draw that mixed in no position at all.
     *
     * @return a seed to hand to {@link #toUnitInterval}, stable for the same seed and position
     */
    public static long forPosition(long seed, int x, int y, int z) {
        long h = seed + POSITION * STREAM_GAMMA;
        h += x * X_GAMMA;
        h += y * Y_GAMMA;
        h += z * Z_GAMMA;
        return mix(h);
    }

    /** SplitMix64's finalizer. Every input bit reaches every output bit. */
    private static long mix(long z) {
        z += STREAM_GAMMA;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
