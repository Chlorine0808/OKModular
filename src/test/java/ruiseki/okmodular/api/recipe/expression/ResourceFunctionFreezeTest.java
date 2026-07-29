package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 種類を指定して量を読む関数（`fluid_in("water")` など）の値と表記の凍結。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * ResourceFunctionExpression は (資源種 × 方向 × 量/空き) の直積を
 * **手書きの 18 要素 enum** として並べ、switch で per-kind メソッドに振り分けている。
 * これを汎用アクセサ 2 本（getAmount / getSpace）に畳むのが B9 の一部。
 *
 * 畳むと switch が消えるので、**どの関数がどの資源種のどの向きを読んでいたか**を
 * 先に固定しておかないと、取り違えても気づけない。
 *
 * ============================================
 * toString も契約である
 * ============================================
 *
 * 式ツリーは toString() で文字列に戻され、NBT に保存されて読み直される
 * （内部ノードの toString() が欠けていて `"tier * 2"` が壊れていた件がある）。
 * つまり **toString の結果は再パースできなければならない**。
 *
 * 畳んだ実装では関数名を enum 名から導けなくなるので、
 * 登録時の名前を保持する必要がある。ここでそれを縛る。
 *
 * ============================================
 * 期待値の読み方
 * ============================================
 *
 * スタブは呼ばれたメソッドごとに違う値を返す（基数 +1 で名前指定、
 * +2 で入力側、+4 で出力側、+10 で空き）。
 * たとえば `fluid_in("water")` が 303 なら「入力側を名前で引いた」が正しく、
 * 302 なら名前が捨てられて合計を読んでいる。
 *
 * ============================================
 */
@DisplayName("種類指定の資源関数")
public class ResourceFunctionFreezeTest {

    // spotless:off
    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
        // 貯蔵量（名前で引く）
        "essentia('ignis'), 501.0",
        "vis('ignis'), 601.0",
        "fluid('water'), 301.0",
        "gas('hydrogen'), 401.0",
        "item('minecraft:stone'), 701.0",
        // 方向つきの量
        "fluid_in('water'), 303.0",
        "fluid_out('water'), 305.0",
        "gas_in('hydrogen'), 403.0",
        "gas_out('hydrogen'), 405.0",
        "item_in('minecraft:stone'), 703.0",
        "item_out('minecraft:stone'), 705.0",
        // 空き容量
        "fluid_f_in('water'), 311.0",
        "fluid_f_out('water'), 313.0",
        "gas_f_in('hydrogen'), 411.0",
        "gas_f_out('hydrogen'), 413.0",
        "item_f('minecraft:stone'), 711.0",
        "item_f_in('minecraft:stone'), 713.0",
        "item_f_out('minecraft:stone'), 715.0",
    })
    // spotless:on
    @DisplayName("値が改修前と一致する")
    public void test値が一致する(String call, double expected) {
        double actual = ExpressionParser.parseExpression(call)
            .evaluateDouble(StubMachineContext.withMachine());

        assertEquals(expected, actual, () -> call + " が別の資源種・方向・名前指定を読んでいる");
    }

    // spotless:off
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "essentia('ignis')", "vis('ignis')", "fluid('water')", "gas('hydrogen')",
        "item('minecraft:stone')",
        "fluid_in('water')", "fluid_out('water')", "gas_in('hydrogen')", "gas_out('hydrogen')",
        "item_in('minecraft:stone')", "item_out('minecraft:stone')",
        "fluid_f_in('water')", "fluid_f_out('water')",
        "gas_f_in('hydrogen')", "gas_f_out('hydrogen')",
        "item_f('minecraft:stone')", "item_f_in('minecraft:stone')", "item_f_out('minecraft:stone')",
    })
    // spotless:on
    @DisplayName("toString を再パースすると同じ値になる")
    public void testtoStringが再パースできる(String call) {
        IExpression original = ExpressionParser.parseExpression(call);
        String text = original.toString();

        IExpression reparsed = ExpressionParser.parseExpression(text);

        assertEquals(
            original.evaluateDouble(StubMachineContext.withMachine()),
            reparsed.evaluateDouble(StubMachineContext.withMachine()),
            () -> call + " の toString が '" + text + "' になり、元と違う意味に読まれた");
    }

    /**
     * 引数の個数は 1 個ちょうど。
     *
     * 畳む前は essentia / vis だけ「空でなければよい」判定で、
     * 余分な引数を**黙って捨てていた**（fluid / gas / item は 2 個以上を弾いていた）。
     * 資源種ごとに検査が違う理由は無いので、厳しい側に揃えた。
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = { "essentia()", "essentia('a', 'b')", "vis()", "vis('a', 'b')", "fluid()", "fluid('a', 'b')",
            "item()", "item('a', 'b')" })
    @DisplayName("引数が 1 個でなければ弾く")
    public void test引数の個数を検査する(String call) {
        assertThrows(
            RuntimeException.class,
            () -> ExpressionParser.parseExpression(call),
            () -> call + " が通ってしまう。引数が捨てられている可能性がある");
    }
}
