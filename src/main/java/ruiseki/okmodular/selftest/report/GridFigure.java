package ruiseki.okmodular.selftest.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import ruiseki.okmodular.selftest.grid.BlockGrid;

/** Renders a BlockGrid as top-down SVG layers (north up) plus a colour legend, as an HTML fragment. */
final class GridFigure {

    static final int CELL_PX = 18;
    /** Tall grids show at most this many layers (plus any layer holding a marker). */
    static final int MAX_LAYERS = 12;
    private static final int WIDE_LIMIT = 64;
    private static final int WIDE_CELL_PX = 8;
    private static final int MARGIN_LEFT = 30;
    private static final int MARGIN_TOP = 16;
    private static final int MARGIN_RIGHT = 20;
    private static final int MARGIN_BOTTOM = 14;

    private GridFigure() {}

    static String render(String title, BlockGrid grid) {
        int[] from = grid.from();
        int[] to = grid.to();
        int cell = (to[0] - from[0] + 1) > WIDE_LIMIT ? WIDE_CELL_PX : CELL_PX;
        // runs of identical adjacent layers, top first: {top y, bottom y}
        List<int[]> runs = new ArrayList<>();
        for (int y = to[1]; y >= from[1]; y--) {
            int[] last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
            if (last != null && sameLayer(grid, last[1], y)) last[1] = y;
            else runs.add(new int[] { y, y });
        }
        List<int[]> shown = sample(grid, runs);
        StringBuilder sb = new StringBuilder();
        sb.append("<figure class=\"grid-figure\">\n<figcaption>")
            .append(esc(title))
            .append("</figcaption>\n");
        if (shown.size() < runs.size()) {
            int layers = to[1] - from[1] + 1;
            sb.append("<p class=\"layer-note\">")
                .append(layers)
                .append(" 層のうち ")
                .append(shown.size())
                .append(" 層を表示（等間隔に間引き，最上・最下と印のある層は残す）</p>\n");
        }
        sb.append("<div class=\"layers\">\n");
        for (int[] run : shown) {
            sb.append("<div class=\"layer-box\"><h5 class=\"layer\">y=")
                .append(run[0]);
            if (run[1] != run[0]) {
                sb.append('〜')
                    .append(run[1])
                    .append("（同じ ")
                    .append(run[0] - run[1] + 1)
                    .append(" 層）");
            }
            sb.append("</h5>\n");
            layer(sb, grid, run[0], cell);
            sb.append("</div>\n");
        }
        sb.append("</div>\n");
        legend(sb, grid);
        sb.append("</figure>\n");
        return sb.toString();
    }

    private static boolean sameLayer(BlockGrid g, int y1, int y2) {
        int[] from = g.from();
        int[] to = g.to();
        for (int x = from[0]; x <= to[0]; x++) {
            for (int z = from[2]; z <= to[2]; z++) {
                if (!g.get(x, y1, z)
                    .equals(g.get(x, y2, z))) return false;
            }
        }
        return !hasMarker(g, y1) && !hasMarker(g, y2);
    }

    private static boolean hasMarker(BlockGrid g, int y) {
        for (List<Integer> k : g.markers()
            .keySet()) {
            if (k.get(1) == y) return true;
        }
        return false;
    }

    /** Evenly spaced runs, always keeping the top, the bottom and runs that hold a marker. */
    private static List<int[]> sample(BlockGrid g, List<int[]> runs) {
        int n = runs.size();
        if (n <= MAX_LAYERS) return runs;
        TreeSet<Integer> keep = new TreeSet<>();
        for (int k = 0; k < MAX_LAYERS; k++) keep.add(Math.round(k * (n - 1) / (float) (MAX_LAYERS - 1)));
        for (int i = 0; i < n; i++) {
            if (hasMarker(g, runs.get(i)[0])) keep.add(i);
        }
        List<int[]> out = new ArrayList<>();
        for (int i : keep) out.add(runs.get(i));
        return out;
    }

    private static void layer(StringBuilder sb, BlockGrid g, int y, int cell) {
        int[] from = g.from();
        int[] to = g.to();
        int sx = to[0] - from[0] + 1;
        int sz = to[2] - from[2] + 1;
        int w = sx * cell;
        int h = sz * cell;
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"layer-svg\" width=\"")
            .append(MARGIN_LEFT + w + MARGIN_RIGHT)
            .append("\" height=\"")
            .append(MARGIN_TOP + h + MARGIN_BOTTOM)
            .append("\" font-family=\"sans-serif\" font-size=\"9\">\n");
        // x ticks along the top, z ticks down the left; north (smaller z) is up
        for (int dx = from[0]; dx <= to[0]; dx++) {
            if (Math.floorMod(dx, 5) == 0) {
                axisText(sb, MARGIN_LEFT + (dx - from[0]) * cell + cell / 2, MARGIN_TOP - 4, "middle", dx);
            }
        }
        for (int dz = from[2]; dz <= to[2]; dz++) {
            if (Math.floorMod(dz, 5) == 0) {
                axisText(sb, MARGIN_LEFT - 4, MARGIN_TOP + (dz - from[2]) * cell + cell / 2 + 3, "end", dz);
            }
        }
        axisText(sb, 2, MARGIN_TOP - 4, "start", "N↑");
        axisText(sb, MARGIN_LEFT + w + 2, MARGIN_TOP - 4, "start", "x→");
        axisText(sb, MARGIN_LEFT - 4, MARGIN_TOP + h + 11, "end", "z↓");
        sb.append("<g transform=\"translate(")
            .append(MARGIN_LEFT)
            .append(',')
            .append(MARGIN_TOP)
            .append(")\">\n");
        // one path per block id keeps the page light; the title still names the block on hover
        Map<String, StringBuilder> paths = new LinkedHashMap<>();
        for (int dz = from[2]; dz <= to[2]; dz++) {
            for (int dx = from[0]; dx <= to[0]; dx++) {
                String b = g.get(dx, y, dz);
                if (BlockGrid.AIR.equals(b)) continue;
                StringBuilder d = paths.get(b);
                if (d == null) paths.put(b, d = new StringBuilder());
                d.append('M')
                    .append((dx - from[0]) * cell)
                    .append(',')
                    .append((dz - from[2]) * cell)
                    .append('h')
                    .append(cell)
                    .append('v')
                    .append(cell)
                    .append("h-")
                    .append(cell)
                    .append('z');
            }
        }
        for (Map.Entry<String, StringBuilder> e : paths.entrySet()) {
            sb.append("<path class=\"cell\" fill=\"")
                .append(esc(BlockPalette.colourOf(e.getKey())))
                .append("\" d=\"")
                .append(e.getValue())
                .append("\"><title>")
                .append(esc(e.getKey()))
                .append("</title></path>\n");
        }
        StringBuilder lines = new StringBuilder();
        for (int i = 1; i < sx; i++) {
            lines.append('M')
                .append(i * cell)
                .append(",0V")
                .append(h);
        }
        for (int i = 1; i < sz; i++) {
            lines.append("M0,")
                .append(i * cell)
                .append('H')
                .append(w);
        }
        sb.append("<path class=\"grid\" d=\"")
            .append(lines)
            .append("\" stroke=\"#000\" stroke-opacity=\"0.18\" stroke-width=\"0.6\" fill=\"none\"/>\n");
        sb.append("<rect class=\"bg\" width=\"")
            .append(w)
            .append("\" height=\"")
            .append(h)
            .append("\" fill=\"none\" stroke=\"#888\" stroke-width=\"1\"/>\n");
        for (Map.Entry<List<Integer>, BlockGrid.Marker> e : g.markers()
            .entrySet()) {
            List<Integer> k = e.getKey();
            if (k.get(1) != y) continue;
            int px = (k.get(0) - from[0]) * cell;
            int pz = (k.get(2) - from[2]) * cell;
            if (e.getValue() == BlockGrid.Marker.CONTROLLER) {
                sb.append("<rect class=\"marker controller\" x=\"")
                    .append(px)
                    .append("\" y=\"")
                    .append(pz)
                    .append("\" width=\"")
                    .append(cell)
                    .append("\" height=\"")
                    .append(cell)
                    .append("\" fill=\"none\" stroke=\"#000\" stroke-width=\"3\"/>\n");
                markText(sb, "marker controller-label", px, pz, cell, "C", "#000", true);
            } else {
                markText(sb, "marker anchor-label", px, pz, cell, "★", "#c00", false);
            }
        }
        sb.append("</g>\n</svg>\n");
    }

    private static void axisText(StringBuilder sb, int x, int y, String anchor, Object s) {
        sb.append("<text class=\"tick\" x=\"")
            .append(x)
            .append("\" y=\"")
            .append(y)
            .append("\" text-anchor=\"")
            .append(anchor)
            .append("\" fill=\"#666\">")
            .append(s)
            .append("</text>\n");
    }

    /** Marker glyph with a white halo so it stays visible on dark cells. */
    private static void markText(StringBuilder sb, String cls, int px, int pz, int cell, String s, String fill,
        boolean bold) {
        sb.append("<text class=\"")
            .append(cls)
            .append("\" stroke=\"#fff\" stroke-width=\"2\" paint-order=\"stroke\" x=\"")
            .append(px + cell / 2)
            .append("\" y=\"")
            .append(pz + cell / 2 + 4)
            .append("\" text-anchor=\"middle\" font-size=\"")
            .append(Math.max(8, cell - 4))
            .append("\" fill=\"")
            .append(fill)
            .append(bold ? "\" font-weight=\"bold\">" : "\">")
            .append(s)
            .append("</text>\n");
    }

    private static void legend(StringBuilder sb, BlockGrid g) {
        List<String> ids = new ArrayList<>(g.blocks());
        ids.remove(BlockGrid.AIR);
        ids.sort((a, b) -> {
            int c = Integer.compare(g.count(b), g.count(a));
            return c != 0 ? c : a.compareTo(b);
        });
        sb.append("<table class=\"legend\">\n");
        for (String id : ids) {
            sb.append("<tr><td><span class=\"swatch\" style=\"display:inline-block;width:14px;height:14px;border:1px solid #666;vertical-align:middle;background:")
                .append(esc(BlockPalette.colourOf(id)))
                .append("\"></span></td><td class=\"id\">")
                .append(esc(id))
                .append("</td><td class=\"n\">")
                .append(g.count(id))
                .append("</td></tr>\n");
        }
        if (!g.markers()
            .isEmpty()) {
            sb.append("<tr><td colspan=\"3\" class=\"marks\">C = コントローラ，★ = 基点</td></tr>\n");
        }
        sb.append("</table>\n");
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
