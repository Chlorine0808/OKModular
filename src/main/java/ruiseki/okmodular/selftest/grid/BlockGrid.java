package ruiseki.okmodular.selftest.grid;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dense box of block ids in machine-relative coordinates (inclusive bounds, unset cells are air). */
public final class BlockGrid {

    public enum Marker {
        CONTROLLER,
        ANCHOR
    }

    public static final String AIR = "minecraft:air";

    private final int[] from;
    private final int[] to;
    private final int sy, sz;
    private final String[] cells;
    private final Map<List<Integer>, Marker> markers = new LinkedHashMap<>();

    public BlockGrid(int[] from, int[] to) {
        this.from = new int[3];
        this.to = new int[3];
        for (int i = 0; i < 3; i++) {
            this.from[i] = Math.min(from[i], to[i]);
            this.to[i] = Math.max(from[i], to[i]);
        }
        int sx = this.to[0] - this.from[0] + 1;
        sy = this.to[1] - this.from[1] + 1;
        sz = this.to[2] - this.from[2] + 1;
        cells = new String[sx * sy * sz];
    }

    public boolean contains(int dx, int dy, int dz) {
        return dx >= from[0] && dx <= to[0] && dy >= from[1] && dy <= to[1] && dz >= from[2] && dz <= to[2];
    }

    private int index(int dx, int dy, int dz) {
        if (!contains(dx, dy, dz)) {
            throw new IndexOutOfBoundsException("(" + dx + "," + dy + "," + dz + ") outside the grid");
        }
        return ((dx - from[0]) * sy + (dy - from[1])) * sz + (dz - from[2]);
    }

    public void set(int dx, int dy, int dz, String block) {
        cells[index(dx, dy, dz)] = AIR.equals(block) ? null : block;
    }

    public String get(int dx, int dy, int dz) {
        String b = cells[index(dx, dy, dz)];
        return b == null ? AIR : b;
    }

    public void mark(int dx, int dy, int dz, Marker m) {
        index(dx, dy, dz);
        markers.put(Arrays.asList(dx, dy, dz), m);
    }

    public Map<List<Integer>, Marker> markers() {
        return Collections.unmodifiableMap(markers);
    }

    public int count(String block) {
        int n = 0;
        for (String c : cells) {
            if ((c == null ? AIR : c).equals(block)) n++;
        }
        return n;
    }

    public int cells() {
        return cells.length;
    }

    public int[] from() {
        return from.clone();
    }

    public int[] to() {
        return to.clone();
    }

    /** Copy of the part inside [from, to]; coordinates stay relative to the same origin. */
    public BlockGrid sub(int[] subFrom, int[] subTo) {
        BlockGrid s = new BlockGrid(subFrom, subTo);
        for (int x = s.from[0]; x <= s.to[0]; x++) {
            for (int y = s.from[1]; y <= s.to[1]; y++) {
                for (int z = s.from[2]; z <= s.to[2]; z++) {
                    if (contains(x, y, z)) s.set(x, y, z, get(x, y, z));
                }
            }
        }
        for (Map.Entry<List<Integer>, Marker> e : markers.entrySet()) {
            List<Integer> k = e.getKey();
            if (s.contains(k.get(0), k.get(1), k.get(2))) s.markers.put(k, e.getValue());
        }
        return s;
    }

    /** Distinct block ids present; air appears only if some cell is air. */
    public Set<String> blocks() {
        Set<String> out = new HashSet<>();
        for (String c : cells) out.add(c == null ? AIR : c);
        return out;
    }
}
