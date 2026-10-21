package ruiseki.okmodular.structure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 構造 JSON の `layers` / `rows` を StructureLib の ABC 座標に対応させる変換の検証。
 *
 * ============================================
 * なぜ切り出したか
 * ============================================
 *
 * **構造 JSON の `layers`/`rows` はそのままの向きでは使われていない。**
 * `CustomStructureRegistry` は StructureLib に渡す前に
 * `rotate180`（縦置きなら `transformForVertical`）を通し、そのあと
 * `StructureUtility.transpose` に掛けている。
 *
 * 構造 IO のファイルが同じ `layers`/`rows` の書き方を流用する以上、
 * **同じ変換を通さないと、機械に対してパターンだけ回転する**。しかも例外は出ない。
 *
 * 変換は `CustomStructureRegistry` の private メソッドで、**テストが 1 本も無かった**。
 * コピーして 2 箇所に置けば、片方を直した日に静かに食い違う
 * （`can_see_sky` が 2 つの実装を持っていたのと同じ形）。よって 1 箇所に集めてここで縛る。
 *
 * ============================================
 * 何が契約か
 * ============================================
 *
 * **処理後の形状において `(col, layer, row)` が `(A, B, C)` に対応する。**
 * `findControllerOffset` がその順で返しており、その値がそのまま
 * `IStructureDefinition.check` の `basePositionA/B/C` に渡っている。
 */
@DisplayName("構造形状の変換")
public class StructureShapeTest {

    /**
     * 層・行・列がすべて違う長さで、どの文字がどこかが一意に分かる形状。
     * 対称な形状だと**軸を取り違えても同じ結果になる**ので使わない。
     */
    private static final String[][] SHAPE = { { "ab", "cd", "ef" }, { "gh", "ij", "kl" } };

    @Test
    @DisplayName("rotate180 は層ごとに行の順を逆にする")
    public void test行の順が逆になる() {
        String[][] rotated = StructureShape.rotate180(SHAPE);

        assertArrayEquals(new String[] { "ef", "cd", "ab" }, rotated[0], "層 0 の行が逆順になっていない");
        assertArrayEquals(new String[] { "kl", "ij", "gh" }, rotated[1], "層 1 の行が逆順になっていない");
    }

    @Test
    @DisplayName("rotate180 は元の配列を壊さない")
    public void test元を壊さない() {
        String[][] before = { SHAPE[0].clone(), SHAPE[1].clone() };
        StructureShape.rotate180(SHAPE);

        assertArrayEquals(before[0], SHAPE[0], "入力の層 0 が書き換えられている");
        assertArrayEquals(before[1], SHAPE[1], "入力の層 1 が書き換えられている");
    }

    @Test
    @DisplayName("findControllerOffset は {col, layer, row} を返す")
    public void testコントローラの位置は列層行の順() {
        // Q を層 1・行 2・列 0 に置く
        String[][] shape = { { "..", ".." }, { "..", "..", "Q." } };

        assertArrayEquals(
            new int[] { 0, 1, 2 },
            StructureShape.findControllerOffset(shape),
            "戻り値の順が {col, layer, row} = {A, B, C} になっていない。" + "この値がそのまま basePositionA/B/C として走査に渡る");
    }

    @Test
    @DisplayName("Q が無ければ原点")
    public void testコントローラが無いとき() {
        assertArrayEquals(new int[] { 0, 0, 0 }, StructureShape.findControllerOffset(new String[][] { { ".." } }));
    }

    /**
     * 構造 IO のパターンは自分のアンカー記号を選べる。
     *
     * 機械側の 'Q' は 1 種類しか無いが、パターン側は「どのセルが参照ブロックに重なるか」を
     * ファイルごとに決める。**同じ探索を 2 つ書けば片方を直した日に食い違う**ので、
     * `findControllerOffset` をこちらへ委譲させて 1 箇所に集める。
     */
    @Test
    @DisplayName("findSymbolOffset は任意の記号を同じ順で返す")
    public void test任意の記号を探せる() {
        String[][] shape = { { "..", ".." }, { "..", "..", ".X" } };

        assertArrayEquals(
            new int[] { 1, 1, 2 },
            StructureShape.findSymbolOffset(shape, 'X'),
            "戻り値の順が {col, layer, row} = {A, B, C} になっていない");
    }

    @Test
    @DisplayName("findSymbolOffset は見つからなければ null")
    public void test記号が無いときは原点ではない() {
        assertNull(
            StructureShape.findSymbolOffset(new String[][] { { ".." } }, 'X'),
            "無い記号に原点を返すと、書き忘れたアンカーが「原点指定」として黙って通る");
    }

    @Test
    @DisplayName("findControllerOffset は findSymbolOffset の 'Q' 版")
    public void testコントローラ探索は委譲() {
        String[][] shape = { { "..", ".." }, { "..", "..", "Q." } };

        assertArrayEquals(
            StructureShape.findSymbolOffset(shape, 'Q'),
            StructureShape.findControllerOffset(shape),
            "2 つの探索が別々の答えを返している");
    }

    @Test
    @DisplayName("縦置きは層を行に移し替える")
    public void test縦置きの変換() {
        String[][] up = StructureShape.transformForVertical(SHAPE, "UP");

        // 元は 2 層 × 3 行。層と行が入れ替わるので 3 層 × 2 行になる
        assertEquals(3, up.length, "層数が元の行数になっていない");
        assertEquals(2, up[0].length, "行数が元の層数になっていない");
        assertArrayEquals(new String[] { "ab", "gh" }, up[0], "UP で層 0 の中身が違う");
    }

    @Test
    @DisplayName("DOWN は UP の行を逆順にしたもの")
    public void test下向きは行が逆() {
        String[][] up = StructureShape.transformForVertical(SHAPE, "UP");
        String[][] down = StructureShape.transformForVertical(SHAPE, "DOWN");

        for (int layer = 0; layer < up.length; layer++) {
            String[] reversed = up[layer].clone();
            java.util.Collections.reverse(Arrays.asList(reversed));
            assertArrayEquals(reversed, down[layer], "層 " + layer + " が UP の逆順になっていない");
        }
    }

    @Test
    @DisplayName("process は向きに応じて変換を選ぶ")
    public void test向きで変換が選ばれる() {
        assertArrayEquals(
            StructureShape.rotate180(SHAPE),
            StructureShape.process(SHAPE, "SOUTH"),
            "横向きは rotate180 を通っていない");
        assertArrayEquals(StructureShape.rotate180(SHAPE), StructureShape.process(SHAPE, null), "向き未指定は横向き扱いでなければならない");
        assertArrayEquals(
            StructureShape.transformForVertical(SHAPE, "UP"),
            StructureShape.process(SHAPE, "up"),
            "縦置きの判定が大文字小文字を無視していない");
    }
}
