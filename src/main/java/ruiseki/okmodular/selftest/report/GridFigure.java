package ruiseki.okmodular.selftest.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ruiseki.okmodular.selftest.grid.BlockGrid;

/** Renders a BlockGrid as one top-down SVG per layer (north up) plus a colour legend, as an HTML fragment. */
final class GridFigure {

    static final int CELL_PX = 18;
    private static final int WIDE_LIMIT = 64;
    private static final int WIDE_CELL_PX = 8;
    private static final int MARGIN_LEFT = 30;
    private static final int MARGIN_TOP = 16;

    private GridFigure() {}

    static String render(String title, BlockGrid grid) {
        int[] from = grid.from();
        int[] to = grid.to();
        int cell = (to[0] - from[0] + 1) > WIDE_LIMIT ? WIDE_CELL_PX : CELL_PX;
        StringBuilder sb = new StringBuilder();
        sb.append("<figure class=\"grid-figure\">\n<figcaption>")
            .append(esc(title))
            .append("</figcaption>\n");
        for (int y = to[1]; y >= from[1]; y--) {
            sb.append("<h5 class=\"layer\">y=")
                .append(y)
                .append("</h5>\n");
            layer(sb, grid, y, cell);
        }
        legend(sb, grid);
        sb.append("</figure>\n");
        return sb.toString();
    }

    private static void layer(StringBuilder sb, BlockGrid g, int y, int cell) {
        int[] from = g.from();
        int[] to = g.to();
        int sx = to[0] - from[0] + 1;
        int sz = to[2] - from[2] + 1;
        int w = sx * cell;
        int h = sz * cell;
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"layer-svg\" width=\"")
            .append(MARGIN_LEFT + w + 4)
            .append("\" height=\"")
            .append(MARGIN_TOP + h + 4)
            .append("\" font-family=\"sans-serif\" font-size=\"9\">\n");
        // x ticks along the top, z ticks down the left; north (smaller z) is up
        for (int dx = from[0]; dx <= to[0]; dx++) {
            if (Math.floorMod(dx, 5) == 0) {
                sb.append("<text class=\"tick\" x=\"")
                    .append(MARGIN_LEFT + (dx - from[0]) * cell + cell / 2)
                    .append("\" y=\"")
                    .append(MARGIN_TOP - 4)
                    .append("\" text-anchor=\"middle\" fill=\"#666\">")
                    .append(dx)
                    .append("</text>\n");
            }
        }
        for (int dz = from[2]; dz <= to[2]; dz++) {
            if (Math.floorMod(dz, 5) == 0) {
                sb.append("<text class=\"tick\" x=\"")
                    .append(MARGIN_LEFT - 4)
                    .append("\" y=\"")
                    .append(MARGIN_TOP + (dz - from[2]) * cell + cell / 2 + 3)
                    .append("\" text-anchor=\"end\" fill=\"#666\">")
                    .append(dz)
                    .append("</text>\n");
            }
        }
        sb.append("<g transform=\"translate(")
            .append(MARGIN_LEFT)
            .append(',')
            .append(MARGIN_TOP)
            .append(")\">\n");
        sb.append("<rect class=\"bg\" width=\"")
            .append(w)
            .append("\" height=\"")
            .append(h)
            .append("\" fill=\"none\" stroke=\"#bbb\" stroke-width=\"1\"/>\n");
        StringBuilder d = new StringBuilder();
        for (int i = 1; i < sx; i++) {
            d.append('M')
                .append(i * cell)
                .append(",0V")
                .append(h);
        }
        for (int i = 1; i < sz; i++) {
            d.append("M0,")
                .append(i * cell)
                .append('H')
                .append(w);
        }
        sb.append("<path class=\"grid\" d=\"")
            .append(d)
            .append("\" stroke=\"#ddd\" stroke-width=\"0.5\" fill=\"none\"/>\n");
        for (int dx = from[0]; dx <= to[0]; dx++) {
            for (int dz = from[2]; dz <= to[2]; dz++) {
                String b = g.get(dx, y, dz);
                if (BlockGrid.AIR.equals(b)) continue;
                sb.append("<rect class=\"cell\" x=\"")
                    .append((dx - from[0]) * cell)
                    .append("\" y=\"")
                    .append((dz - from[2]) * cell)
                    .append("\" width=\"")
                    .append(cell)
                    .append("\" height=\"")
                    .append(cell)
                    .append("\" fill=\"")
                    .append(esc(BlockPalette.colourOf(b)))
                    .append("\"><title>")
                    .append(esc(b))
                    .append("</title></rect>\n");
            }
        }
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
                text(sb, "marker controller-label", px, pz, cell, "C", "#000", true);
            } else {
                text(sb, "marker anchor-label", px, pz, cell, "★", "#c00", false);
            }
        }
        sb.append("</g>\n</svg>\n");
    }

    private static void text(StringBuilder sb, String cls, int px, int pz, int cell, String s, String fill,
        boolean bold) {
        sb.append("<text class=\"")
            .append(cls)
            .append("\" x=\"")
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
            sb.append("<tr><td><span class=\"swatch\" style=\"display:inline-block;width:12px;height:12px;background:")
                .append(esc(BlockPalette.colourOf(id)))
                .append("\"></span></td><td class=\"id\">")
                .append(esc(id))
                .append("</td><td class=\"n\">")
                .append(g.count(id))
                .append("</td></tr>\n");
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
