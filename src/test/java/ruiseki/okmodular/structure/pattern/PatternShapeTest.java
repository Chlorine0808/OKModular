package ruiseki.okmodular.structure.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import ruiseki.okmodular.structure.StructureException;
import ruiseki.okmodular.structure.StructureShape;

/**
 * O — `"shape": { "type": "sphere" }` で `layers` を生成する。
 *
 * ============================================
 * なぜ必要か
 * ============================================
 *
 * K1 で「距離で濃さを変える」が書けるようになったが、**削る対象は手で描く**しかなかった。
 * 半径 8 の球を出すには 17×17×17 = 4913 文字を書いてから式で削ることになる。
 * O はその 4913 文字を消す。
 *
 * ============================================
 * 生成した形は `layers` になる
 * ============================================
 *
 * 別経路を作らず、**手描きと同じ `String[][]` を作って既存の配管に流す**。
 * だから facing の変換・アンカー解決・facing ごとのキャッシュ・記号が全部 `mappings` に
 * あるかの検査は、すべて既にあるコードがそのまま動く。
 *
 * 「機械に対して回転しない」保証（`StructureShapeTest` が値で固定）を作り直さずに済むのが要点。
 *
 * ============================================
 * アンカーを検索できない
 * ============================================
 *
 * 生成した形は**全セルが同じ記号**なので、`findSymbolOffset` は一番上のセルを返す。
 * そこを原点にすると、球の全オフセットが上端から測られて `cell_y` が全部負になる。
 * だから中心の添字を**明示的に渡す**。
 *
 * ⚠ 渡す添字は「書かれたままの形」の座標で、`resolve` は `process` を通した形を見る。
 * 生成した形の外接箱は**立方体**なので、`process` の 2 つの変換はどちらも中心を動かさない
 * （`rotate180` は行を反転、`transformForVertical` は層と行を入れ替えるだけ）。
 * **立方体でない形を足すときはここが崩れる**ので、下でその不変を値で縛ってある。
 */
@DisplayName("パターンの形状生成")
public class PatternShapeTest {

    private static StructurePattern parse(String json) {
        return StructurePatternParser.parse(
            new JsonParser().parse(json.replace('\'', '"'))
                .getAsJsonObject(),
            "test.json");
    }

    private static StructurePattern sphere(int radius) {
        return parse(
            "{ 'name': 'vein', 'shape': { 'type': 'sphere', 'radius': " + radius
                + ", 'symbol': 'X' }, 'mappings': { 'X': 'minecraft:iron_ore' } }");
    }

    // ============================================
    // 何が生成されるか
    // ============================================

    @Test
    @DisplayName("半径ごとのセル数")
    public void testセル数() {
        // ⚠ 半径 8 は **2109** セル。2145 と書いてあった記述はどれも誤りだった
        // （手計算の値がそのまま todo とコメントに広がっていた）。
        assertEquals(
            1,
            sphere(0).cellsFor(null)
                .size(),
            "半径 0 は中心 1 セル");
        assertEquals(
            7,
            sphere(1).cellsFor(null)
                .size(),
            "半径 1 は中心 + 6 面");
        assertEquals(
            33,
            sphere(2).cellsFor(null)
                .size());
        assertEquals(
            257,
            sphere(4).cellsFor(null)
                .size());
        assertEquals(
            2109,
            sphere(8).cellsFor(null)
                .size(),
            "半径 8 のセル数。2145 ではない");
    }

    @Test
    @DisplayName("中心がアンカーになる")
    public void test中心がアンカー() {
        List<StructurePattern.Cell> cells = sphere(2).cellsFor(null);

        // アンカーからの相対で返るので、中心のセルは (0,0,0) にある
        assertTrue(
            cells.stream()
                .anyMatch(c -> c.a == 0 && c.b == 0 && c.c == 0),
            "中心のセルが (0,0,0) に無い。原点が中心になっていない");

        // 上下・左右・前後に同じだけ伸びていること。原点が端なら片側に寄る
        assertEquals(
            -2,
            cells.stream()
                .mapToInt(c -> c.b)
                .min()
                .getAsInt());
        assertEquals(
            2,
            cells.stream()
                .mapToInt(c -> c.b)
                .max()
                .getAsInt());
        assertEquals(
            -2,
            cells.stream()
                .mapToInt(c -> c.a)
                .min()
                .getAsInt());
        assertEquals(
            2,
            cells.stream()
                .mapToInt(c -> c.a)
                .max()
                .getAsInt());
        assertEquals(
            -2,
            cells.stream()
                .mapToInt(c -> c.c)
                .min()
                .getAsInt());
        assertEquals(
            2,
            cells.stream()
                .mapToInt(c -> c.c)
                .max()
                .getAsInt());
    }

    @Test
    @DisplayName("球の内側だけが入る")
    public void test球になっている() {
        List<StructurePattern.Cell> cells = sphere(4).cellsFor(null);

        for (StructurePattern.Cell cell : cells) {
            int distance = cell.a * cell.a + cell.b * cell.b + cell.c * cell.c;
            assertTrue(distance <= 16, "半径 4 の外のセルが入っている: " + cell);
        }

        // 角が抜けていること。立方体をそのまま出していたら通ってしまう
        assertTrue(
            cells.stream()
                .noneMatch(c -> c.a == 4 && c.b == 4 && c.c == 4),
            "角のセルが入っている。球ではなく立方体になっている");
        assertTrue(
            cells.stream()
                .anyMatch(c -> c.a == 4 && c.b == 0 && c.c == 0),
            "軸上の端のセルが抜けている（境界は含む）");
    }

    @Test
    @DisplayName("全セルが同じ記号になる")
    public void test記号が1つ() {
        for (StructurePattern.Cell cell : sphere(2).cellsFor(null)) {
            assertEquals('X', cell.symbol, "生成したセルに別の記号が混ざっている");
        }
    }

    @Test
    @DisplayName("生成した形も機械の向きで変換される")
    public void test向きの変換に乗る() {
        // 球は変換に対して不変なので、どの向きでも同じセル集合になる。
        // ここで見ているのは「変換を通っている経路にいること」で、
        // 通っていなければ縦置き機械で別のものが出る形を足したときに気づけない。
        int horizontal = sphere(3).cellsFor(null)
            .size();
        int up = sphere(3).cellsFor("UP")
            .size();
        int down = sphere(3).cellsFor("DOWN")
            .size();

        assertEquals(horizontal, up, "縦置きでセル数が変わった");
        assertEquals(horizontal, down);
    }

    @Test
    @DisplayName("⚠ 立方体の中心は process で動かない（明示的な原点の前提）")
    public void test立方体の中心は不変() {
        // 明示的に渡した中心の添字が正しいのは、この不変が成り立つからだけ。
        // 立方体でない形（円柱など）を足すときは、原点も変換する必要が出る。
        for (int side : new int[] { 1, 3, 5, 17 }) {
            String[][] cube = new String[side][side];
            for (int layer = 0; layer < side; layer++) {
                for (int row = 0; row < side; row++) {
                    StringBuilder text = new StringBuilder();
                    for (int col = 0; col < side; col++) text.append('X');
                    cube[layer][row] = text.toString();
                }
            }
            int middle = (side - 1) / 2;

            // 中心に印を付けて、変換後も同じ添字にいることを見る
            cube[middle][middle] = replaceAt(cube[middle][middle], middle, 'Q');

            for (String facing : new String[] { null, "UP", "DOWN" }) {
                String[][] processed = StructureShape.process(cube, facing);
                int[] found = StructureShape.findSymbolOffset(processed, 'Q');

                assertNotNull(found, "側 " + side + " / 向き " + facing + " で中心が消えた");
                assertEquals(middle, found[0], "側 " + side + " / 向き " + facing + " で列がずれた");
                assertEquals(middle, found[1], "側 " + side + " / 向き " + facing + " で層がずれた");
                assertEquals(middle, found[2], "側 " + side + " / 向き " + facing + " で行がずれた");
            }
        }
    }

    private static String replaceAt(String text, int index, char with) {
        char[] chars = text.toCharArray();
        chars[index] = with;
        return new String(chars);
    }

    // ============================================
    // 書き方の間違いはロードで断る
    // ============================================

    @Test
    @DisplayName("layers と shape は排他")
    public void test両方書いたら断る() {
        StructureException thrown = assertThrows(
            StructureException.class,
            () -> parse(
                "{ 'name': 'x', 'shape': { 'type': 'sphere', 'radius': 1, 'symbol': 'X' },"
                    + " 'layers': [ ['X'] ], 'mappings': { 'X': 'minecraft:stone' } }"),
            "layers と shape の両方が通っている");

        assertTrue(
            thrown.getMessage()
                .contains("shape"),
            "何が衝突しているのか message が言っていない: " + thrown.getMessage());
    }

    @Test
    @DisplayName("どちらも無ければ断る")
    public void testどちらも無ければ断る() {
        assertThrows(
            StructureException.class,
            () -> parse("{ 'name': 'x', 'mappings': { 'X': 'minecraft:stone' } }"),
            "セルの書かれていないパターンが通っている");
    }

    @Test
    @DisplayName("知らない型は断る")
    public void test未知の型は断る() {
        StructureException thrown = assertThrows(
            StructureException.class,
            () -> parse(
                "{ 'name': 'x', 'shape': { 'type': 'donut', 'radius': 2, 'symbol': 'X' },"
                    + " 'mappings': { 'X': 'minecraft:stone' } }"));

        assertTrue(
            thrown.getMessage()
                .contains("sphere"),
            "知っている型を message が並べていない: " + thrown.getMessage());
    }

    @Test
    @DisplayName("radius に式は書けない")
    public void test式のradiusは断る() {
        // 形はロード時に確定して facing ごとにキャッシュされるので、式を評価する場所が無い。
        StructureException thrown = assertThrows(
            StructureException.class,
            () -> parse(
                "{ 'name': 'x', 'shape': { 'type': 'sphere', 'radius': 'tier', 'symbol': 'X' },"
                    + " 'mappings': { 'X': 'minecraft:stone' } }"),
            "式の radius が通っている");

        assertTrue(
            thrown.getMessage()
                .contains("chance"),
            "代わりに何を使えばいいのか message が言っていない: " + thrown.getMessage());
    }

    @Test
    @DisplayName("負の radius は断る")
    public void test負のradiusは断る() {
        assertThrows(
            StructureException.class,
            () -> parse(
                "{ 'name': 'x', 'shape': { 'type': 'sphere', 'radius': -1, 'symbol': 'X' },"
                    + " 'mappings': { 'X': 'minecraft:stone' } }"));
    }

    @Test
    @DisplayName("大きすぎる形は断る")
    public void test大きすぎる形は断る() {
        // 半径 16 は 17077 セルで通り、17 は上限を超える。
        assertEquals(
            17077,
            sphere(16).cellsFor(null)
                .size(),
            "半径 16 が通らなくなっている");

        StructureException thrown = assertThrows(StructureException.class, () -> sphere(17), "上限を超える形が通っている");

        assertTrue(
            thrown.getMessage()
                .contains("cells"),
            "何セルなのか message が言っていない: " + thrown.getMessage());
    }

    @Test
    @DisplayName("shape と anchor の併記は断る")
    public void testanchor併記は断る() {
        // 全セルが同じ記号なので anchor に選べるものが無い。黙って無視すると
        // 「アンカーを指定したのに中心が使われている」ことに気づけない。
        StructureException thrown = assertThrows(
            StructureException.class,
            () -> parse(
                "{ 'name': 'x', 'anchor': 'Q', 'shape': { 'type': 'sphere', 'radius': 1, 'symbol': 'X' },"
                    + " 'mappings': { 'X': 'minecraft:stone' } }"),
            "shape と anchor の併記が通っている");

        assertTrue(
            thrown.getMessage()
                .contains("centre"),
            "中心がアンカーになることを message が言っていない: " + thrown.getMessage());
    }

    @Test
    @DisplayName("symbol は必須で、1 文字で、空白は不可")
    public void testsymbolの検査() {
        assertThrows(
            StructureException.class,
            () -> parse(
                "{ 'name': 'x', 'shape': { 'type': 'sphere', 'radius': 1 }, 'mappings': { 'X': 'minecraft:stone' } }"),
            "symbol 無しが通っている");

        assertThrows(
            StructureException.class,
            () -> parse(
                "{ 'name': 'x', 'shape': { 'type': 'sphere', 'radius': 1, 'symbol': 'XY' },"
                    + " 'mappings': { 'X': 'minecraft:stone' } }"),
            "2 文字の symbol が通っている");

        assertThrows(
            StructureException.class,
            () -> parse(
                "{ 'name': 'x', 'shape': { 'type': 'sphere', 'radius': 1, 'symbol': ' ' },"
                    + " 'mappings': { 'X': 'minecraft:stone' } }"),
            "空白の symbol が通っている（形が空になる）");
    }

    @Test
    @DisplayName("symbol に mappings が無ければ断る（既存の検査に乗る）")
    public void test記号の対応が無ければ断る() {
        assertThrows(
            StructureException.class,
            () -> parse(
                "{ 'name': 'x', 'shape': { 'type': 'sphere', 'radius': 1, 'symbol': 'X' },"
                    + " 'mappings': { 'S': 'minecraft:stone' } }"),
            "対応の無い記号で生成できてしまっている");
    }

    // ============================================
    // K1 と組み合わせる（これが O の目的）
    // ============================================

    @Test
    @DisplayName("生成した球を K1 の式で削れる")
    public void test式で削れる() {
        // 4913 文字を書かずに、輪郭のぼけた球が出る形。O + K1 の組み合わせそのもの。
        StructurePattern pattern = parse(
            "{ 'name': 'vein', 'shape': { 'type': 'sphere', 'radius': 8, 'symbol': 'X' }, 'mappings': { 'X': "
                + "{ 'block': 'minecraft:iron_ore', 'chance': '1 - sqrt(cell_x^2 + cell_y^2 + cell_z^2) / 8' } } }");

        assertEquals(
            2109,
            pattern.cellsFor(null)
                .size());
        assertTrue(!pattern.isDeterministic(), "式の chance を持つのに決定的だと判定されている");
    }
}
