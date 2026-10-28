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
    @DisplayName("セルの rect は空気でないマスの数に一致する")
    void cellCountMatchesNonAirCells() {
        assertEquals(4, count(GridFigure.render("t", sample()), "<rect class=\"cell\""));
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
    }

    @Test
    @DisplayName("幅 64 マス超は 8px に縮める")
    void wideGridsShrinkCells() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 79, 0, 1 });
        g.set(0, 0, 0, "minecraft:stone");
        String html = GridFigure.render("t", g);
        assertTrue(html.contains("<rect class=\"cell\" x=\"0\" y=\"0\" width=\"8\""));
        assertFalse(html.contains("width=\"18\""));
    }

    @Test
    @DisplayName("標準の大きさは 18px")
    void normalGridsUseDefaultCells() {
        assertTrue(
            GridFigure.render("t", sample())
                .contains("width=\"" + GridFigure.CELL_PX + "\""));
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
