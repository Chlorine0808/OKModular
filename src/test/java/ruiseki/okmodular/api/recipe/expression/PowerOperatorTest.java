package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 累乗演算子 `^` / `**` の結合と優先順位。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * **docs が先に `^` を書いていて、実装が無かった。**
 * `docs/{en,jp}/recipes/JSON_FORMAT.md` の「Tier で二乗に増える」の行が
 * `"energy": "100 * tier ^ 2"` を動くものとして例示していたが、
 * 文法に冪乗の段が無く、`parse()` の末尾検査が `Unexpected token: '^'` を投げていた。
 * docs をコピーして書いたレシピはロードに失敗する。
 *
 * ============================================
 * なぜ値で縛るのか
 * ============================================
 *
 * **優先順位と結合を間違えても例外は出ない。数が変わるだけ。**
 * 落ちる形の間違いは自分で気づけるが、`-2 ^ 2` が 4 になる間違いは
 * レシピの数値が静かにずれるだけで、テストが無ければ誰も気づかない。
 *
 * 4 通りの外し方それぞれに、区別できる値を選んである:
 *
 * <pre>
 * 2 * 3 ^ 2   18 ではなく 36 → `*` より弱い位置に置いた
 * 2 ^ 3 ^ 2   512 ではなく 64 → 左結合になっている
 * -2 ^ 2      -4 ではなく 4 → 単項マイナスを冪の内側に置いた
 * 2 ^ -1      0.5 ではなくパース失敗 → 指数側を primary にした
 * </pre>
 *
 * ============================================
 * `*=` が生き残っていること
 * ============================================
 *
 * `**` を探すには `*` を 1 つ食ってから 2 つ目を見る。`eat()` は消費するので、
 * `nbt('k') *= 2` で 1 つ目を食ったあと `=` を見て失敗したときに
 * **位置を戻さないと既存の複合代入が丸ごと壊れる**。
 * 演算子を足した副作用が別の機能を殺す形なので、ここで縛る。
 *
 * なお `2 * * 3` は `2 ** 3` として通る。前置 `*` が無いので他の読み方が存在せず、
 * 空白を禁じても新しく書けるようになるものが無い。
 *
 * ============================================
 */
@DisplayName("累乗演算子")
public class PowerOperatorTest {

    private static double evaluate(String expression) {
        return ExpressionParser.parseExpression(expression)
            .evaluateDouble(StubMachineContext.withMachine());
    }

    @Test
    @DisplayName("`^` / `**` / `pow()` は同じもの")
    public void test三つの綴りが一致する() {
        assertEquals(1024.0, evaluate("pow(2, 10)"), "pow() 自体が壊れている");
        assertEquals(1024.0, evaluate("2 ^ 10"), "`^` が pow() の呼び出しに展開されていない");
        assertEquals(1024.0, evaluate("2 ** 10"), "`**` が pow() の呼び出しに展開されていない");
    }

    @Test
    @DisplayName("`*` `/` `%` より強く結合する")
    public void test乗除より強い() {
        assertEquals(18.0, evaluate("2 * 3 ^ 2"), "2 * (3 ^ 2) と読まれていない");
        assertEquals(32.0, evaluate("2 ^ 3 * 4"), "(2 ^ 3) * 4 と読まれていない");
        assertEquals(2.0, evaluate("18 / 3 ^ 2"), "18 / (3 ^ 2) と読まれていない");
        assertEquals(4.0, evaluate("4 % 3 ^ 2"), "4 % (3 ^ 2) と読まれていない");
    }

    @Test
    @DisplayName("`+` `-` より強く結合する")
    public void test加減より強い() {
        assertEquals(10.0, evaluate("1 + 3 ^ 2"), "1 + (3 ^ 2) と読まれていない");
    }

    @Test
    @DisplayName("右結合")
    public void test右結合() {
        assertEquals(512.0, evaluate("2 ^ 3 ^ 2"), "左結合になっている（64 が出るなら (2^3)^2）");
        assertEquals(512.0, evaluate("2 ** 3 ** 2"), "`**` だけ結合が違う");
    }

    @Test
    @DisplayName("単項マイナスは冪乗より弱い")
    public void test単項マイナスは外側() {
        assertEquals(-4.0, evaluate("-2 ^ 2"), "(-2) ^ 2 と読まれている。符号を冪の内側に置いた");
        assertEquals(-4.0, evaluate("-2 ** 2"), "`**` だけ符号の扱いが違う");
        assertEquals(4.0, evaluate("(-2) ^ 2"), "括弧で囲めば符号が底に付く");
    }

    @Test
    @DisplayName("符号を付けた数は数のまま")
    public void test負のリテラルは定数() {
        // `-8` は単項マイナスなので、素直に組むと (0 - 8) という**式**になる。
        // 呼ぶ側は `instanceof ConstantExpression` で「そのまま int として書き出せるか」
        // 「書かれた値が範囲外か」を判定するので、そこから -8 が見えなくなる。
        // noise(-8) のロード時検査がそれで素通りしていた。
        assertInstanceOf(ConstantExpression.class, ExpressionParser.parseExpression("-8"), "-8 が式になっている");
        assertInstanceOf(ConstantExpression.class, ExpressionParser.parseExpression("-0.5"));
        assertEquals(-8.0, evaluate("-8"));

        // 畳むのは数に付いた符号だけ。式に付いた符号は式のまま
        assertEquals(-3.0, evaluate("-tier"), "tier 3 の符号反転");
        assertEquals(-4.0, evaluate("-(1 + 3)"));
    }

    @Test
    @DisplayName("指数側に符号と括弧を書ける")
    public void test指数が単項を取れる() {
        assertEquals(0.5, evaluate("2 ^ -1"), "指数側を primary にすると負の指数が書けない");
        assertEquals(0.25, evaluate("2 ^ -(1 + 1)"));
        assertEquals(8.0, evaluate("2 ^ +3"), "単項プラス");
    }

    @Test
    @DisplayName("2 文字演算子の間に空白を挟んでもよい")
    public void test空白入りでも通る() {
        assertEquals(8.0, evaluate("2 * * 3"), "前置 `*` は存在しないので、これは `**` 以外に読めない");
    }

    @Test
    @DisplayName("`*=` は代入のまま（`**` の探索に食われない）")
    public void test複合代入が生きている() {
        assertInstanceOf(
            NBTAssignmentExpression.class,
            ExpressionParser.parseExpression("nbt('k') *= 2"),
            "`**` を探して食った `*` を戻していない。複合代入が丸ごと壊れる");

        assertInstanceOf(
            NBTAssignmentExpression.class,
            ExpressionParser.parseExpression("nbt('k') = 4"),
            "単純代入も巻き込まれていないこと");
    }

    @Test
    @DisplayName("底も指数も式でよい")
    public void test式を取れる() {
        // StubMachineContext の tier は 3
        assertEquals(9.0, evaluate("tier ^ 2"));
        assertEquals(8.0, evaluate("2 ^ tier"));
        assertEquals(900.0, evaluate("100 * tier ^ 2"), "docs（JSON_FORMAT の Tier 二乗の行）がそのまま動くこと");
    }

    @Test
    @DisplayName("toString() が再パースできる（NBT 往復)")
    public void test往復する() {
        // 式は toString() で NBT / JSON に書き出して読み直される。`^` は pow() を吐くので、
        // 往復は既存の関数の経路をそのまま通る。
        IExpression once = ExpressionParser.parseExpression("100 * tier ^ 2");
        IExpression twice = ExpressionParser.parseExpression(once.toString());

        assertEquals(
            once.evaluateDouble(StubMachineContext.withMachine()),
            twice.evaluateDouble(StubMachineContext.withMachine()),
            "書き出して読み直すと値が変わる: " + once);
    }

    @Test
    @DisplayName("指数を書き忘れたら落ちる")
    public void test指数無しは落ちる() {
        assertThrows(RuntimeException.class, () -> ExpressionParser.parseExpression("2 ^"), "指数が無いのに通る");
        assertThrows(RuntimeException.class, () -> ExpressionParser.parseExpression("2 **"), "指数が無いのに通る");
    }
}
