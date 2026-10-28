package ruiseki.okmodular.selftest.scenario;

/** Inclusive box in coordinates relative to the machine. */
public final class Region {

    public final int[] from;
    public final int[] to;

    public Region(int[] from, int[] to) {
        this.from = from;
        this.to = to;
    }

    public int sizeX() {
        return Math.abs(to[0] - from[0]) + 1;
    }

    public int sizeY() {
        return Math.abs(to[1] - from[1]) + 1;
    }

    public int sizeZ() {
        return Math.abs(to[2] - from[2]) + 1;
    }

    public boolean contains(int dx, int dy, int dz) {
        return between(dx, from[0], to[0]) && between(dy, from[1], to[1]) && between(dz, from[2], to[2]);
    }

    private static boolean between(int v, int a, int b) {
        return v >= Math.min(a, b) && v <= Math.max(a, b);
    }
}
