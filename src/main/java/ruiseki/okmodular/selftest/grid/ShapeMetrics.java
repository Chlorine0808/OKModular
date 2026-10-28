package ruiseki.okmodular.selftest.grid;

/** Shape statistics over a BlockGrid: density, centroid, clustering, radial profile. */
public final class ShapeMetrics {

    private static final int[][] NEIGHBOURS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };

    private ShapeMetrics() {}

    public static double density(BlockGrid g, String block) {
        return (double) g.count(block) / g.cells();
    }

    /** Mean relative coordinate of the matching cells; NaN components when there are none. */
    public static double[] centroid(BlockGrid g, String block) {
        double[] sum = new double[3];
        int n = 0;
        int[] f = g.from(), t = g.to();
        for (int x = f[0]; x <= t[0]; x++) for (int y = f[1]; y <= t[1]; y++) for (int z = f[2]; z <= t[2]; z++) {
            if (g.get(x, y, z)
                .equals(block)) {
                sum[0] += x;
                sum[1] += y;
                sum[2] += z;
                n++;
            }
        }
        if (n == 0) return new double[] { Double.NaN, Double.NaN, Double.NaN };
        return new double[] { sum[0] / n, sum[1] / n, sum[2] / n };
    }

    /** Share of in-grid 6-neighbours of matching cells that match as well; 0 when there are none. */
    public static double sameNeighbourRatio(BlockGrid g, String block) {
        long same = 0, total = 0;
        int[] f = g.from(), t = g.to();
        for (int x = f[0]; x <= t[0]; x++) for (int y = f[1]; y <= t[1]; y++) for (int z = f[2]; z <= t[2]; z++) {
            if (!g.get(x, y, z)
                .equals(block)) continue;
            for (int[] d : NEIGHBOURS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (!g.contains(nx, ny, nz)) continue;
                total++;
                if (g.get(nx, ny, nz)
                    .equals(block)) same++;
            }
        }
        return total == 0 ? 0.0 : (double) same / total;
    }

    /** Density of the block in equal-width shells over [0, radius) around centre; cells beyond radius are ignored. */
    public static double[] radialDensity(BlockGrid g, String block, double[] centre, int bands, double radius) {
        long[] hit = new long[bands];
        long[] all = new long[bands];
        int[] f = g.from(), t = g.to();
        for (int x = f[0]; x <= t[0]; x++) for (int y = f[1]; y <= t[1]; y++) for (int z = f[2]; z <= t[2]; z++) {
            double dx = x - centre[0], dy = y - centre[1], dz = z - centre[2];
            double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (r >= radius) continue;
            int b = (int) (r / radius * bands);
            all[b]++;
            if (g.get(x, y, z)
                .equals(block)) hit[b]++;
        }
        double[] out = new double[bands];
        for (int i = 0; i < bands; i++) out[i] = all[i] == 0 ? 0.0 : (double) hit[i] / all[i];
        return out;
    }

    /** Number of cells whose block differs; both grids must have the same size. */
    public static int differingCells(BlockGrid a, BlockGrid b) {
        int[] fa = a.from(), ta = a.to(), fb = b.from(), tb = b.to();
        for (int i = 0; i < 3; i++) {
            if (ta[i] - fa[i] != tb[i] - fb[i]) throw new IllegalArgumentException("grids differ in size");
        }
        int n = 0;
        for (int x = 0; x <= ta[0] - fa[0]; x++)
            for (int y = 0; y <= ta[1] - fa[1]; y++) for (int z = 0; z <= ta[2] - fa[2]; z++) {
                String ba = a.get(fa[0] + x, fa[1] + y, fa[2] + z);
                String bb = b.get(fb[0] + x, fb[1] + y, fb[2] + z);
                if (!ba.equals(bb)) n++;
            }
        return n;
    }

    /** True when, in every column along axis ('x', 'y' or 'z'), all cells match or none does. */
    public static boolean uniformAlong(BlockGrid g, String block, char axis) {
        int a = axis - 'x';
        if (a < 0 || a > 2) throw new IllegalArgumentException("axis must be x, y or z");
        int[] f = g.from(), t = g.to();
        int[] c = new int[3];
        for (c[0] = f[0]; c[0] <= t[0]; c[0]++)
            for (c[1] = f[1]; c[1] <= t[1]; c[1]++) for (c[2] = f[2]; c[2] <= t[2]; c[2]++) {
                if (c[a] != f[a]) continue; // visit each column once, at its start
                boolean first = g.get(c[0], c[1], c[2])
                    .equals(block);
                int[] p = c.clone();
                for (p[a] = f[a] + 1; p[a] <= t[a]; p[a]++) {
                    if (g.get(p[0], p[1], p[2])
                        .equals(block) != first) return false;
                }
            }
        return true;
    }
}
