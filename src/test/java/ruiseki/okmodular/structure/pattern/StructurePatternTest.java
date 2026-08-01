package ruiseki.okmodular.structure.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import ruiseki.okmodular.structure.StructureException;

/**
 * 構造 IO パターンの読み取りと、セルがどの座標系に落ちるかの検証。
 *
 * ============================================
 * なぜセルを機械の向きで解決するのか
 * ============================================
 *
 * 構造 JSON の `layers`/`rows` は**そのままの向きでは使われていない**。
 * `StructureShape.process` を通ってはじめて `(col, layer, row)` が `(A, B, C)` になる。
 * パターンが同じ書き方を流用する以上、**同じ変換を通さないと機械に対してだけ回転する**。
 * しかも例外は出ない（`StructureShapeTest` の冒頭に書いた通り）。
 *
 * ここで効いてくるのが「どの向きで process するか」で、**答えは機械側の `defaultFacing`**。
 * パターン側に `defaultFacing` を書かせると**機械のそれと食い違ったときに黙って回る**ので、
 * パターンはファイルに向きを持たず、使う側から受け取る。
 * だから `cellsFor(facing)` であって `getCells()` ではない。
 *
 * ============================================
 * なぜアンカー相対なのか
 * ============================================
 *
 * パターンはレシピが名指しした**シンボル位置に対して**置かれる。その位置のセルは実行時に
 * `TEMachineController.getSymbolCell` から来るので、パターン側が持つべきは
 * 「アンカーから見てどれだけずれているか」だけ。絶対セルを持たせると使う側で毎回引き算になる。
 */
@DisplayName("構造 IO パターン")
public class StructurePatternTest {

    private static StructurePattern parse(String json) {
        return StructurePatternParser.parse(
            new JsonParser().parse(json)
                .getAsJsonObject(),
            "test.json");
    }

    /** セルを {@code "a,b,c"} → ブロック ID の対応に潰す。順序に依存した検証を書かないため。 */
    private static Map<String, String> byCell(List<StructurePattern.Cell> cells) {
        Map<String, String> map = new HashMap<>();
        for (StructurePattern.Cell cell : cells) {
            map.put(cell.a + "," + cell.b + "," + cell.c, cell.blockId);
        }
        return map;
    }

    @Test
    @DisplayName("セルはアンカーからの相対で返る")
    public void testセルはアンカー相対() {
        StructurePattern pattern = parse(
            "{ 'name': 'core', 'mappings': { 'S': 'minecraft:stone', 'Q': 'minecraft:obsidian' },"
                + "  'layers': [ ['SQ', 'S_'] ] }".replace('\'', '"'));

        // rotate180 で層 0 の行が逆順になり ["S_", "SQ"]。Q は {col=1, layer=0, row=1}
        Map<String, String> cells = byCell(pattern.cellsFor(null));

        assertEquals("minecraft:obsidian", cells.get("0,0,0"), "アンカー自身が原点に来ていない");
        assertEquals("minecraft:stone", cells.get("-1,0,0"), "アンカーと同じ行の S がずれている");
        assertEquals("minecraft:stone", cells.get("-1,0,-1"), "rotate180 を通っていれば S はアンカーの手前の行にある");
        assertEquals("minecraft:air", cells.get("0,0,-1"), "`_` が air として入っていない");
        assertEquals(4, cells.size(), "セル数が合わない: " + cells);
    }

    @Test
    @DisplayName("機械が縦置きなら層と行が入れ替わる")
    public void test縦置きで軸が変わる() {
        String json = "{ 'name': 'v', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['Q'], ['S'] ] }"
            .replace('\'', '"');

        assertTrue(byCell(parse(json).cellsFor(null)).containsKey("0,1,0"), "横向きの機械では、下の層の S はアンカーの 1 層下（B 方向）に来るはず");
        assertTrue(
            byCell(parse(json).cellsFor("UP")).containsKey("0,0,1"),
            "縦置きの機械では層が行に移るので S は C 方向に来るはず。" + "機械の defaultFacing を無視すると、ここが黙って食い違う");
    }

    @Test
    @DisplayName("空白はパターンに含まれない")
    public void test空白は含まない() {
        StructurePattern pattern = parse(
            "{ 'name': 'gap', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S Q'] ] }".replace('\'', '"'));

        assertEquals(
            1,
            pattern.cellsFor(null)
                .size(),
            "空白がセルとして入っている。空白は構造 JSON でも「検証から外す」意味");
    }

    @Test
    @DisplayName("マッピングの無いアンカーは印だけでセルにならない")
    public void testアンカーは印だけでもよい() {
        StructurePattern pattern = parse(
            "{ 'name': 'marker', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['SQ'] ] }".replace('\'', '"'));

        List<StructurePattern.Cell> cells = pattern.cellsFor(null);
        assertEquals(1, cells.size(), "アンカーにマッピングが無いのにセルが作られている。" + "アンカー位置には普通レシピが名指ししたブロックが既に建っている");
        assertEquals('S', cells.get(0).symbol);
    }

    @Test
    @DisplayName("マッピングの無い記号は読み込みを止める")
    public void test未定義の記号はエラー() {
        StructureException error = assertThrows(
            StructureException.class,
            () -> parse("{ 'name': 'bad', 'mappings': {}, 'layers': [ ['X'] ] }".replace('\'', '"')));

        assertTrue(
            error.getMessage()
                .contains("X"),
            "どの記号が未定義なのかがメッセージに出ていない: " + error.getMessage());
    }

    @Test
    @DisplayName("書いたアンカーが形状に無ければエラー")
    public void test居ないアンカーはエラー() {
        assertThrows(
            StructureException.class,
            () -> parse(
                "{ 'name': 'bad', 'anchor': 'A', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] }"
                    .replace('\'', '"')),
            "書いたアンカーが見つからないのに通ると、原点アンカーとして黙ってずれる");
    }

    @Test
    @DisplayName("アンカー未指定で Q も無ければ原点が基準")
    public void testアンカー省略時は原点() {
        StructurePattern pattern = parse(
            "{ 'name': 'plain', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] }".replace('\'', '"'));

        assertTrue(byCell(pattern.cellsFor(null)).containsKey("0,0,0"), "アンカーを書かない単純なパターンは、最初のセルが基準になるべき");
    }

    @Test
    @DisplayName("name の無いパターンは読み込みを止める")
    public void test名前は必須() {
        assertThrows(
            StructureException.class,
            () -> parse("{ 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] }".replace('\'', '"')),
            "名前が無いパターンはレシピから参照できない。読み込めてしまうと死蔵する");
    }

    @Test
    @DisplayName("layers の無いパターンは読み込みを止める")
    public void test形状は必須() {
        assertThrows(StructureException.class, () -> parse("{ 'name': 'empty', 'mappings': {} }".replace('\'', '"')));
    }

    @Test
    @DisplayName("layers はオブジェクト形式の rows でも書ける")
    public void testオブジェクト形式の層() {
        StructurePattern pattern = parse(
            ("{ 'name': 'obj', 'mappings': { 'S': 'minecraft:stone' }," + "  'layers': [ { 'rows': ['S'] } ] }")
                .replace('\'', '"'));

        assertEquals(
            1,
            pattern.cellsFor(null)
                .size(),
            "構造 JSON が受ける 2 つの層の書き方のうち片方しか読めていない");
    }

    /**
     * 解決結果は向きごとに 1 度だけ作って使い回す。**呼ぶ側に書き換えられると
     * 次の呼び出し以降まで壊れる**ので、渡す前に読み取り専用にしておく。
     */
    @Test
    @DisplayName("返るセル一覧は書き換えられない")
    public void test結果は読み取り専用() {
        StructurePattern pattern = parse(
            "{ 'name': 'cached', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] }".replace('\'', '"'));

        List<StructurePattern.Cell> cells = pattern.cellsFor(null);
        assertThrows(UnsupportedOperationException.class, () -> cells.clear(), "使い回す一覧を呼び出し側が空にできてしまう");
    }
}
