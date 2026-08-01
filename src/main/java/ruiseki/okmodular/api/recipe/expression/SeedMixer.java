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

    /** Odd 64-bit constant, so distinct streams land far apart. */
    private static final long STREAM_GAMMA = 0x9E3779B97F4A7C15L;

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

    /** SplitMix64's finalizer. Every input bit reaches every output bit. */
    private static long mix(long z) {
        z += STREAM_GAMMA;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
