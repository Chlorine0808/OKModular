package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 指数タワー `^^` / `tetrate()` と k 重階乗 `fact(n, k)`。
 *
 * ============================================
 * `^^` が pow() に脱糖できない理由
 * ============================================
 *
 * `^` は段数が 1 段と決まっているので、パース時に `pow()` の呼び出しへ書き換えられる。
 * `^^` は**段数が式**なので、何回冪を取るかが評価時まで決まらない。
 * だから本物の関数（`tetrate`）が要る — 演算子はその糖衣。
 *
 * ============================================
 * 反復上限は「大きな数を止めるため」ではない
 * ============================================
 *
 * 底が e^(1/e) ≈ 1.4447 より大きいタワーは数段で double を溢れるので、
 * Infinity 判定だけで勝手に止まる。**止まらないのは収束する側。**
 * `1 ^^ n` は永遠に 1、`1.4 ^^ n` は 1.887 付近に収束し、
 * 値を見ていても打ち切る理由が現れない。
 *
 * つまり tick スレッドを止めるのは `"2 ^^ 1000000000"` ではなく
 * **`"1.4 ^^ 1000000000"`**。そちらを @Timeout で縛る。
 *
 * ============================================
 * fact(n, 1) と fact(n) がビット一致すること
 * ============================================
 *
 * 階乗の項は 18! を超えると double で正確に表せない。
 * つまり**同じ鎖を違う順で掛けると下位ビットが変わる**。
 * k 重階乗を素直に `n, n-k, n-2k, …` と降順で書くと、k=1 のときに
 * 既存の `fact(n)`（昇順 1·2·3·…·n）と答えが食い違う。
 * 例外は出ず、レシピの数値が静かにずれるだけなので、値の一致をここで縛る。
 *
 * ============================================
 */
@DisplayName("指数タワーと k 重階乗")
public class TowerAndMultifactorialTest {

    private static double evaluate(String expression) {
        return ExpressionParser.parseExpression(expression)
            .evaluateDouble(StubMachineContext.withMachine());
    }

    // ============================================================
    // 指数タワー
    // ============================================================

    @Test
    @DisplayName("`^^` と `tetrate()` は同じもの")
    public void test演算子と関数が一致する() {
        assertEquals(16.0, evaluate("tetrate(2, 3)"), "tetrate() 自体が壊れている");
        assertEquals(16.0, evaluate("2 ^^ 3"), "`^^` が tetrate() に脱糖していない");
    }

    @Test
    @DisplayName("段数の意味")
    public void test段数() {
        assertEquals(1.0, evaluate("2 ^^ 0"), "0 段は空のタワー = 1");
        assertEquals(2.0, evaluate("2 ^^ 1"), "1 段は底そのもの");
        assertEquals(4.0, evaluate("2 ^^ 2"), "2 ^ 2");
        assertEquals(16.0, evaluate("2 ^^ 3"), "2 ^ (2 ^ 2) = 2 ^ 4。左結合なら (2^2)^2 = 16 と一致してしまうので下も見る");
        assertEquals(65536.0, evaluate("2 ^^ 4"), "2 ^ (2 ^ (2 ^ 2)) = 2 ^ 16。左結合だと 256 になる");
        assertEquals(27.0, evaluate("3 ^^ 2"), "3 ^ 3");
        // Math.pow の丸めまで縛る意味は無いので、同じ塔を明示的に書いたものと比べる
        assertEquals(Math.pow(3, 27), evaluate("3 ^^ 3"), "3 ^ (3 ^ 3) = 3 ^ 27");
    }

    @Test
    @DisplayName("すぐ double を溢れる（記法の性質）")
    public void test範囲が狭い() {
        assertTrue(Double.isInfinite(evaluate("2 ^^ 5")), "2 ^ 65536 は double に入らない");
        assertTrue(Double.isInfinite(evaluate("3 ^^ 4")));
        assertTrue(Double.isInfinite(evaluate("10 ^^ 3")), "10 ^ 10000000000");
    }

    @Test
    @DisplayName("`^` と同じ段・同じ右結合")
    public void test優先順位() {
        assertEquals(32.0, evaluate("2 * 2 ^^ 3"), "2 * (2 ^^ 3)。`*` より強い");
        assertEquals(134217728.0, evaluate("2 ^ 3 ^^ 2"), "2 ^ (3 ^^ 2) = 2 ^ 27。右結合が `^` を跨いで効く");
    }

    @Test
    @DisplayName("空白入りの `^ ^` も通る")
    public void test空白入りでも通る() {
        assertEquals(16.0, evaluate("2 ^ ^ 3"), "前置 `^` は存在しないので、これは `^^` 以外に読めない");
    }

    @Test
    @DisplayName("単項マイナスは `^` と同じく塔の外側")
    public void test単項マイナスは外側() {
        assertEquals(-16.0, evaluate("-2 ^^ 3"), "-(2 ^^ 3)。符号は塔の中に入らない");
        assertEquals(0.0, evaluate("(-2) ^^ 3"), "括弧で囲めば底が負になる（下の領域外を見よ）");
    }

    @Test
    @DisplayName("領域外は 0（このクラスの他の関数と同じ扱い）")
    public void test領域外は0() {
        assertEquals(0.0, evaluate("2 ^^ -1"), "負の段数");
        assertEquals(0.0, evaluate("(-2) ^^ 3"), "(-2) ^ ((-2) ^ (-2)) = (-2) ^ 0.25 は NaN。NaN を外へ出さない");
        assertEquals(4.0, evaluate("2 ^^ 2.9"), "小数の段数は切り捨て（2 段）");
    }

    @Test
    @Timeout(5)
    @DisplayName("収束するタワーでも有限時間で返る")
    public void test終わらない式が無い() {
        // 上限が無いと 10 億回まわる。Infinity 判定だけでは止まらない側。
        assertEquals(1.0, evaluate("1 ^^ 1000000000"), "1 のタワーは何段でも 1");

        double converging = evaluate("1.4 ^^ 1000000000");
        assertTrue(converging > 1.8 && converging < 1.9, "1.4 のタワーは 1.887 付近に収束する。実際の値: " + converging);
    }

    @Test
    @DisplayName("toString() が再パースできる")
    public void testタワーが往復する() {
        IExpression once = ExpressionParser.parseExpression("2 ^^ tier");
        IExpression twice = ExpressionParser.parseExpression(once.toString());

        assertEquals(
            once.evaluateDouble(StubMachineContext.withMachine()),
            twice.evaluateDouble(StubMachineContext.withMachine()),
            "書き出して読み直すと値が変わる: " + once);
    }

    // ============================================================
    // k 重階乗
    // ============================================================

    @Test
    @DisplayName("`fact(n, 1)` は `fact(n)` とビット一致する")
    public void test単階乗が変わっていない() {
        // 掛ける順を変えると 19! 以降で下位ビットが動く。== で比べているのが要点。
        for (int n = 0; n <= 30; n++) {
            assertEquals(
                evaluate("fact(" + n + ")"),
                evaluate("fact(" + n + ", 1)"),
                "n=" + n + " で単階乗と k=1 の答えが違う。掛ける順を変えている");
        }
    }

    @Test
    @DisplayName("既存の階乗の値そのまま")
    public void test階乗() {
        assertEquals(1.0, evaluate("fact(0)"));
        assertEquals(1.0, evaluate("fact(1)"));
        assertEquals(3628800.0, evaluate("fact(10)"));
        // 20! は double で正確に表せる（2^18 × 奇数、奇数部が 2^53 未満）。掛ける順を
        // 変えると 19! 以降で下位ビットが動くので、ここが順序そのものの検査になる。
        assertEquals(2432902008176640000.0, evaluate("fact(20)"), "1·2·…·20 の順で掛けていない");
        assertEquals(1.0, evaluate("fact(-5)"), "負の階乗は 1");
        assertTrue(Double.isInfinite(evaluate("fact(171)")), "171! は double に入らない");
    }

    @Test
    @DisplayName("k 重階乗")
    public void testk重階乗() {
        assertEquals(3840.0, evaluate("fact(10, 2)"), "10·8·6·4·2");
        assertEquals(945.0, evaluate("fact(9, 2)"), "9·7·5·3·1");
        assertEquals(280.0, evaluate("fact(10, 3)"), "10·7·4·1");
        assertEquals(880.0, evaluate("fact(11, 3)"), "11·8·5·2");
        assertEquals(10.0, evaluate("fact(10, 10)"), "項が 1 つだけ");
        assertEquals(10.0, evaluate("fact(10, 99)"), "刻みが n を超えても項は 1 つ");
    }

    @Test
    @Timeout(5)
    @DisplayName("刻み 0 は 0（無限ループにならない）")
    public void test刻み0() {
        assertEquals(0.0, evaluate("fact(10, 0)"), "刻み 0 は鎖が進まない");
        assertEquals(0.0, evaluate("fact(10, -2)"), "負の刻みも同じ");
    }

    @Test
    @Timeout(5)
    @DisplayName("巨大な n でも有限時間で返る")
    public void test巨大なnで止まる() {
        assertTrue(Double.isInfinite(evaluate("fact(1000000000, 2)")), "積が Infinity に達したところで打ち切る");
    }

    @Test
    @DisplayName("引数は 1 個か 2 個")
    public void test引数の個数() {
        assertThrows(RuntimeException.class, () -> ExpressionParser.parseExpression("fact()"), "引数無しが通る");
        assertThrows(
            RuntimeException.class,
            () -> ExpressionParser.parseExpression("fact(5, 2, 3)"),
            "3 個目が黙って捨てられている");
        assertThrows(RuntimeException.class, () -> ExpressionParser.parseExpression("tetrate(2)"), "tetrate は 2 個必要");
    }
}
