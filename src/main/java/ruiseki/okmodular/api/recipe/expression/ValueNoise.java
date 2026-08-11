package ruiseki.okmodular.api.recipe.expression;

/** A settled field over positions where neighbours answer alike, so a threshold of it comes out in blobs. */
public final class ValueNoise {

    /**
     * The finest lattice worth asking for, in blocks.
     * <p>
     * At a spacing of one every block <em>is</em> a lattice corner, so nothing is interpolated and
     * the field degenerates to one independent draw per block - which is what {@code random()}
     * already is. Anything below that has no further meaning, so a scale that arrives smaller (an
     * expression that dipped) is raised to this rather than being refused mid-tick.
     */
    static final double MIN_SCALE = 1.0;

    private ValueNoise() {}

    /**
     * @param seed  the same value at every position being asked about, or the field is not a field:
     *              hashing each lattice corner under a different seed leaves white noise
     * @param scale how far apart the lattice corners sit, in blocks - roughly the size of one blob
     * @return a number in {@code [0, 1)}, the same for the same seed, position and scale.
     *         <b>Not uniformly distributed</b>: interpolating between corners piles values up
     *         around the middle, so a threshold does not cut the fraction it looks like it cuts
     */
    public static double at(long seed, double x, double y, double z, double scale) {
        double spacing = scale < MIN_SCALE ? MIN_SCALE : scale;

        double px = x / spacing;
        double py = y / spacing;
        double pz = z / spacing;

        int gx = (int) Math.floor(px);
        int gy = (int) Math.floor(py);
        int gz = (int) Math.floor(pz);

        // Smoothstep rather than the raw fraction: its slope is zero at both ends, so the field has
        // no visible creases where one lattice cell meets the next.
        double tx = smoothstep(px - gx);
        double ty = smoothstep(py - gy);
        double tz = smoothstep(pz - gz);

        double y0 = lerp(
            lerp(corner(seed, gx, gy, gz), corner(seed, gx + 1, gy, gz), tx),
            lerp(corner(seed, gx, gy, gz + 1), corner(seed, gx + 1, gy, gz + 1), tx),
            tz);
        double y1 = lerp(
            lerp(corner(seed, gx, gy + 1, gz), corner(seed, gx + 1, gy + 1, gz), tx),
            lerp(corner(seed, gx, gy + 1, gz + 1), corner(seed, gx + 1, gy + 1, gz + 1), tx),
            tz);

        return lerp(y0, y1, ty);
    }

    /**
     * One lattice corner's value.
     * <p>
     * This is the draw {@link SeedMixer#forPosition} already gives, on its own stream - the hashing
     * a value noise needs was here before the interpolation was, so nothing new hashes anything.
     */
    private static double corner(long seed, int x, int y, int z) {
        return SeedMixer.toUnitInterval(SeedMixer.forPosition(seed, x, y, z), SeedMixer.NOISE);
    }

    private static double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
