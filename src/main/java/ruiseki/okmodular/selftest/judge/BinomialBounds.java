package ruiseki.okmodular.selftest.judge;

/** Integer acceptance interval for a binomial count, from the normal approximation. */
public final class BinomialBounds {

    /** Wide enough that a spurious red is far below 1e-3 per run (see BinomialBoundsTest). */
    public static final double Z = 4.5;

    private BinomialBounds() {}

    /** Returns {low, high} for the count of successes in n trials of probability p, clamped to [0, n]. */
    public static int[] of(int n, double p, double z) {
        if (p <= 0.0 || p >= 1.0) {
            int k = (int) Math.round(n * p);
            return new int[] { k, k };
        }
        double mean = n * p;
        double half = z * Math.sqrt(n * p * (1.0 - p));
        int low = (int) Math.ceil(mean - half);
        int high = (int) Math.floor(mean + half);
        return new int[] { Math.max(0, low), Math.min(n, high) };
    }
}
