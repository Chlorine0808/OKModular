package ruiseki.okmodular.selftest.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.selftest.grid.BlockGrid;

class GridFigureTest {

    private static int count(String hay, String needle) {
        int n = 0;
        for (int i = hay.indexOf(needle); i >= 0; i = hay.indexOf(needle, i + 1)) n++;
        return n;
    }

    private static BlockGrid sample() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 2, 2, 2 });
        g.set(0, 0, 0, "minecraft:stone");
        g.set(1, 0, 0, "minecraft:stone");
        g.set(2, 1, 2, "minecraft:dirt");
        g.set(1, 2, 1, "okmodular:modular_machine_controller");
        g.mark(1, 2, 1, BlockGrid.Marker.CONTROLLER);
        g.mark(0, 0, 0, BlockGrid.Marker.ANCHOR);
        return g;
    }

    @Test
    @DisplayName("層ごとに SVG が 1 枚，y の大きい層が先")
    void oneSvgPerLayerTopFirst() {
        String html = GridFigure.render("t", sample());
        assertEquals(3, count(html, "<svg"));
        assertTrue(html.indexOf("y=2") < html.indexOf("y=1"));
        assertTrue(html.indexOf("y=1") < html.indexOf("y=0"));
        assertTrue(html.indexOf("y=2") < html.indexOf("<svg"));
    }

    @Test
    @DisplayName("セルの四角は空気でないマスの数に一致し，層ごとに同じブロックを 1 本の path にまとめる")
    void cellCountMatchesNonAirCells() {
        String html = GridFigure.render("t", sample());
        assertEquals(4, count(html, "h18v18h-18z"));
        assertEquals(3, count(html, "<path class=\"cell\""));
    }

    @Test
    @DisplayName("格子線はセルの上に描く")
    void gridLinesAreDrawnOverCells() {
        String html = GridFigure.render("t", sample());
        int cell = html.indexOf("class=\"cell\"");
        assertTrue(cell >= 0);
        int grid = html.indexOf("class=\"grid\"", cell);
        assertTrue(grid > cell && grid < html.indexOf("</svg>", cell));
    }

    @Test
    @DisplayName("軸の向き (x→, z↓, N↑) を書く")
    void axisLabelsAreDrawn() {
        String html = GridFigure.render("t", sample());
        assertTrue(html.contains(">x→<"));
        assertTrue(html.contains(">z↓<"));
        assertTrue(html.contains(">N↑<"));
    }

    @Test
    @DisplayName("隣り合う同じ層は 1 枚にまとめる")
    void identicalAdjacentLayersCollapse() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 1, 3, 1 });
        for (int y = 0; y <= 1; y++) g.set(0, y, 0, "minecraft:stone");
        String html = GridFigure.render("t", g);
        assertEquals(2, count(html, "<svg"));
        assertTrue(html.contains(">y=3〜2（同じ 2 層）<"));
        assertTrue(html.contains(">y=1〜0（同じ 2 層）<"));
    }

    @Test
    @DisplayName("高い格子は層を間引き，最上・最下と印のある層は残して注記する")
    void tallGridsAreSampled() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 29, 29, 0 });
        for (int y = 0; y <= 29; y++) g.set(y, y, 0, "minecraft:stone");
        g.mark(13, 13, 0, BlockGrid.Marker.ANCHOR);
        String html = GridFigure.render("t", g);
        int svgs = count(html, "<svg");
        assertTrue(svgs <= GridFigure.MAX_LAYERS + 1, "svgs=" + svgs);
        assertTrue(svgs >= GridFigure.MAX_LAYERS, "svgs=" + svgs);
        assertTrue(html.contains(">y=29<"));
        assertTrue(html.contains(">y=0<"));
        assertTrue(html.contains(">y=13<"));
        assertTrue(html.contains("class=\"layer-note\""));
        assertTrue(html.contains("30 層のうち " + svgs + " 層"));
    }

    @Test
    @DisplayName("凡例にすべてのブロックと個数が並ぶ")
    void legendListsEveryBlockWithCount() {
        String html = GridFigure.render("t", sample());
        assertTrue(html.contains("minecraft:stone"));
        assertTrue(html.contains("minecraft:dirt"));
        assertTrue(html.contains("okmodular:modular_machine_controller"));
        assertTrue(html.contains("<td class=\"n\">2</td>"));
        assertFalse(html.contains("minecraft:air"));
    }

    @Test
    @DisplayName("コントローラと基点を描く")
    void markersAreDrawn() {
        String html = GridFigure.render("t", sample());
        assertTrue(html.contains("class=\"marker controller\""));
        assertTrue(html.contains("★"));
        assertTrue(html.contains(">C<"));
        int star = html.indexOf("class=\"marker anchor-label\"");
        String starTag = html.substring(star, html.indexOf('>', star));
        assertTrue(starTag.contains("stroke=\"#fff\""), starTag);
    }

    @Test
    @DisplayName("幅 64 マス超は 8px に縮める")
    void wideGridsShrinkCells() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 79, 0, 1 });
        g.set(0, 0, 0, "minecraft:stone");
        String html = GridFigure.render("t", g);
        assertTrue(html.contains("M0,0h8v8h-8z"));
        assertFalse(html.contains("h18v18"));
    }

    @Test
    @DisplayName("標準の大きさは 18px")
    void normalGridsUseDefaultCells() {
        assertTrue(
            GridFigure.render("t", sample())
                .contains("h" + GridFigure.CELL_PX + "v" + GridFigure.CELL_PX));
    }

    @Test
    @DisplayName("ID とタイトルはエスケープする")
    void escapesMarkup() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 0, 0, 0 });
        g.set(0, 0, 0, "x:<b>&");
        String html = GridFigure.render("a<i>&", g);
        assertFalse(html.contains("<b>"));
        assertFalse(html.contains("<i>"));
        assertTrue(html.contains("x:&lt;b&gt;&amp;"));
        assertTrue(html.contains("a&lt;i&gt;&amp;"));
    }
}
