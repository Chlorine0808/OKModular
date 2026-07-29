package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * docs が変数として書いている式名が、実際に変数として使えることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * 式名は 2 箇所に登録しないと動かない（評価する実装 と ExpressionRegistry への
 * 登録）。energy_per_tick は「登録はあるが実装が無い」側の欠落だったが、
 * **逆向きの欠落**も溜まっていた — 実装は書かれているのに登録が無く、
 * 書くと `Unknown variable` で弾かれる名前が 12 件あった。
 *
 * docs/jp/recipes/EXPRESSION_REFERENCE.md が仕様の正本なので、そこに載って
 * いて動かないものは実装漏れ。該当したのは連続した 2 ブロック:
 *
 * - L62-64 `random_seed` / `world_seed` / `facing`
 * - L76-80 item 系 9 件（`item` `item_total` `item_max` `item_capacity`
 * `item_f` `item_free` `item_space` `item_p` `item_percent`）
 *
 * item 系は関数形（`item("id")`）だけが登録されていたため、
 * 「引数を付ければ動くが、総量を読む裸の変数は書けない」状態だった。
 *
 * ============================================
 * 期待値は StubMachineContext の値から導いている
 * ============================================
 *
 * 量そのものが正しいかではなく、**docs が書いている意味の実装に到達するか**を
 * 見ている。だから期待値はスタブの定数から計算した値を置く。
 *
 * ============================================
 */
@DisplayName("docs が約束している変数")
public class DocumentedVariableTest {

    /** item_max はスロット数 × 64。 */
    private static final double ITEM_MAX = StubMachineContext.ITEM_CAPACITY;

    private static double evaluate(String expression) {
        return ExpressionParser.parseExpression(expression)
            .evaluateDouble(StubMachineContext.withMachine());
    }

    @Test
    @DisplayName("item / item_total はアイテム総数")
    public void testアイテム総数() {
        assertEquals(StubMachineContext.ITEM_COUNT, evaluate("item"));
        assertEquals(StubMachineContext.ITEM_COUNT, evaluate("item_total"));
    }

    @Test
    @DisplayName("item_max / item_capacity は最大収容数")
    public void test最大収容数() {
        assertEquals(ITEM_MAX, evaluate("item_max"));
        assertEquals(ITEM_MAX, evaluate("item_capacity"));
    }

    @Test
    @DisplayName("item_f / item_free / item_space は空き容量")
    public void test空き容量() {
        assertEquals(StubMachineContext.ITEM_SPACE, evaluate("item_f"));
        assertEquals(StubMachineContext.ITEM_SPACE, evaluate("item_free"));
        assertEquals(StubMachineContext.ITEM_SPACE, evaluate("item_space"));
    }

    @Test
    @DisplayName("item_p / item_percent は充填率")
    public void test充填率() {
        assertEquals(StubMachineContext.ITEM_COUNT / ITEM_MAX, evaluate("item_p"));
        assertEquals(StubMachineContext.ITEM_COUNT / ITEM_MAX, evaluate("item_percent"));
    }

    @Test
    @DisplayName("裸の item と関数形の item('id') が併存する")
    public void test変数形と関数形が併存する() {
        assertEquals(StubMachineContext.ITEM_COUNT, evaluate("item"), "変数形");
        assertEquals(StubMachineContext.ITEM_COUNT, evaluate("item('minecraft:stone')"), "関数形");
    }

    @Test
    @DisplayName("facing はマシンの向き")
    public void test向き() {
        assertEquals(ForgeDirection.NORTH.ordinal(), evaluate("facing"), "docs は 2:北 と書いている");
    }

    @Test
    @DisplayName("world_seed / random_seed は変数として書ける")
    public void testシード値が書ける() {
        // 値はワールドと評価セッションに依存するので、ここでは到達できることだけ見る
        assertDoesNotThrow(() -> ExpressionParser.parseExpression("world_seed"));
        assertDoesNotThrow(() -> ExpressionParser.parseExpression("random_seed"));
    }
}
