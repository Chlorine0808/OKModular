package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ICondition;

/**
 * 三項演算子 `c ? x : y` と、条件を含む式が保存を越えられること。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * **docs が三項演算子を機能として宣言していて、文法に無かった。**
 * `docs/{en,jp}/recipes/JSON_FORMAT.md` は「三項演算子 `? :` と論理演算子 `&&` / `||` が
 * 使えます」と書き、`EXPRESSION_EXAMPLES.md` は 4 例（丸ごとの JSON レシピを含む）を載せ、
 * `EXPRESSION_REFERENCE.md` は**ゼロ除算の回避方法として推奨**している
 * （`energy_p > 0 ? floor(1000 / energy_p) : 0`）。全部ロードで落ちていた。
 *
 * 三項の `toString()` は条件を埋め込むので、条件側が読み戻せるソースを吐くことが前提。
 * そちらは `ConditionRoundTripTest` が持っている。
 *
 * ============================================
 * 分岐を遅延させるのは最適化ではない
 * ============================================
 *
 * docs が推奨している形が `energy_p > 0 ? floor(1000 / energy_p) : 0` なので、
 * 両方を評価してから選ぶ実装では**その推奨が何も守らない**。
 *
 * 値が違うだけでは済まない。分岐には NBT 代入（`nbt('x') = 1`）や
 * 乱数の引き（`chance(0.5)`）が書ける。**式を評価することがそれらの実行そのもの**なので、
 * 先に両方を評価すると両方書き込まれ、1 回で済む抽選が 2 回になる。
 *
 * ============================================
 */
@DisplayName("三項演算子")
public class ConditionalExpressionTest {

    private static double evaluate(String expression) {
        return ExpressionParser.parseExpression(expression)
            .evaluateDouble(StubMachineContext.withMachine());
    }

    /** 往復させた式。NBT / JSON に書き出して読み直す経路そのもの。 */
    private static IExpression roundTrip(String expression) {
        IExpression once = ExpressionParser.parseExpression(expression);
        return assertDoesNotThrow(
            () -> ExpressionParser.parseExpression(once.toString()),
            () -> "書き出した文字列が読み戻せない: \"" + once + "\"");
    }

    // ============================================================
    // 選ぶ
    // ============================================================

    @Test
    @DisplayName("条件で枝を選ぶ")
    public void test枝を選ぶ() {
        assertEquals(1.0, evaluate("1 ? 1 : 2"), "真の枝");
        assertEquals(2.0, evaluate("0 ? 1 : 2"), "偽の枝");
        // StubMachineContext の tier は 3
        assertEquals(1.0, evaluate("tier >= 5 ? 2 : 1"), "tier 3 なので偽の枝");
        assertEquals(6.0, evaluate("tier >= 3 ? tier * 2 : tier"), "真の枝でも式が書ける");
    }

    @Test
    @DisplayName("条件に括弧が要らない（文法の最下段にある）")
    public void test条件が裸で書ける() {
        assertEquals(5.0, evaluate("tier == 3 ? 5 : 1"), "比較が `?` より強く結合していない");
        assertEquals(5.0, evaluate("tier > 1 && tier < 9 ? 5 : 1"), "`&&` が `?` より強く結合していない");
        assertEquals(5.0, evaluate("tier > 9 || tier == 3 ? 5 : 1"), "`||` が `?` より強く結合していない");
    }

    @Test
    @DisplayName("右結合で、入れ子にできる")
    public void test入れ子() {
        assertEquals(2.0, evaluate("0 ? 1 : 1 ? 2 : 3"), "a ? b : (c ? d : e)");
        assertEquals(3.0, evaluate("0 ? 1 : 0 ? 2 : 3"));
        assertEquals(2.0, evaluate("1 ? 0 ? 1 : 2 : 3"), "真の枝の中にも書ける");
    }

    @Test
    @DisplayName("枝の中は式として続く")
    public void test枝が式を取る() {
        assertEquals(7.0, evaluate("1 ? 3 + 4 : 0"), "枝が `+` を飲み込む");
        assertEquals(9.0, evaluate("1 ? tier ^ 2 : 0"), "累乗も書ける");
        assertEquals(2.0, evaluate("(1 ? 1 : 0) + 1"), "括弧の中に書ける（関数引数・配列も同じ経路）");
        assertEquals(3.0, evaluate("max(1 ? 3 : 0, 2)"), "関数の引数として書ける");
    }

    // ============================================================
    // 遅延
    // ============================================================

    /** 評価された回数を数える式。副作用のある式の代わり。 */
    private static final class Counting implements IExpression {

        private final double value;
        private int evaluations;

        Counting(double value) {
            this.value = value;
        }

        @Override
        public EvaluationValue evaluate(ConditionContext context) {
            evaluations++;
            return new EvaluationValue(value);
        }
    }

    @Test
    @DisplayName("取らない枝は評価されない")
    public void test取らない枝は動かない() {
        // 数えているのは「値が合っているか」ではなく「触ったか」。
        // 実際の副作用（NBT 代入・抽選）は IO 層が applyToNBT で適用するので、
        // 式を通した観測ができない。契約は ConditionalExpression にあるので直接組む。
        Counting whenTrue = new Counting(1);
        Counting whenFalse = new Counting(2);

        ConditionContext context = StubMachineContext.withoutMachine();
        IExpression always = ExpressionParser.parseExpression("1");

        new ConditionalExpression(alwaysTrue(), whenTrue, whenFalse).evaluate(context);

        assertEquals(1, whenTrue.evaluations, "取る枝が評価されていない");
        assertEquals(0, whenFalse.evaluations, "取らない枝が評価されている。両方の枝で NBT 代入と抽選が走る形");

        new ConditionalExpression(alwaysFalse(), whenTrue, whenFalse).evaluate(context);

        assertEquals(1, whenTrue.evaluations, "取らない枝が評価されている");
        assertEquals(1, whenFalse.evaluations, "取る枝が評価されていない");

        assertEquals(1.0, always.evaluateDouble(context), "テストの土台の確認");
    }

    private static ICondition alwaysTrue() {
        return condition(true);
    }

    private static ICondition alwaysFalse() {
        return condition(false);
    }

    private static ICondition condition(boolean answer) {
        return new ICondition() {

            @Override
            public boolean isMet(ConditionContext context) {
                return answer;
            }

            @Override
            public String getDescription() {
                return String.valueOf(answer ? 1 : 0);
            }

            @Override
            public void write(JsonObject json) {}

            @Override
            public String toString() {
                return getDescription();
            }
        };
    }

    @Test
    @DisplayName("docs が推奨しているゼロ除算の回避が成り立つ")
    public void testゼロ除算の回避() {
        // EXPRESSION_REFERENCE.md が落とし穴の回避策として案内している形そのまま。
        // 機械が無ければ energy_p は 0 になるので、偽の枝が選ばれる。
        assertEquals(
            0.0,
            ExpressionParser.parseExpression("energy_p > 0 ? floor(1000 / energy_p) : 0")
                .evaluateDouble(StubMachineContext.withoutMachine()),
            "分母が 0 のときに真の枝へ行っている");

        // StubMachineContext は energy 100 / capacity 1000 なので energy_p は 0.1
        assertEquals(10000.0, evaluate("energy_p > 0 ? floor(1000 / energy_p) : 0"));
    }

    // ============================================================
    // 保存を越える
    // ============================================================

    @Test
    @DisplayName("三項を含む式が往復する")
    public void test三項が往復する() {
        assertEquals(1.0, roundTrip("tier >= 5 ? 2 : 1").evaluateDouble(StubMachineContext.withMachine()));
        assertEquals(2.0, roundTrip("0 ? 1 : 1 ? 2 : 3").evaluateDouble(StubMachineContext.withMachine()));
    }

    // ============================================================
    // docs が約束している形
    // ============================================================

    /**
     * docs が三項の例として載せている式を、書かれているまま全部。
     * <p>
     * リテラルで書き写してあるのが仕様。docs から機械的に抜き出す形にすると、
     * **docs から例が消えたときに検証対象からも消えて緑のまま通る**
     * （`ExpressionNameFreezeTest` が凍結リストを手書きしているのと同じ理由）。
     */
    // spotless:off
    private static final String[] DOCUMENTED = {
        // recipes/EXPRESSION_EXAMPLES.md
        "tier >= 5 ? tier * 2 : tier",
        "energy_p < 0.2 ? 2 : 1",
        "time >= 0 && time < 12000 ? 5 : 1",
        "chance(0.1 + tier * 0.05) ? 1 : 0",
        // recipes/EXPRESSION_REFERENCE.md — ゼロ除算の回避策として案内されている形
        "energy_p > 0 ? floor(1000 / energy_p) : 0",
    };
    // spotless:on

    @Test
    @DisplayName("docs に載っている三項の例が全部通る")
    public void testドキュメントの例が動く() {
        for (String source : DOCUMENTED) {
            IExpression expr = assertDoesNotThrow(
                () -> ExpressionParser.parseExpression(source),
                () -> "docs に載っている \"" + source + "\" がパースできない");

            assertDoesNotThrow(
                () -> expr.evaluateDouble(StubMachineContext.withMachine()),
                () -> "docs に載っている \"" + source + "\" が評価できない");

            // 保存を越えられなければ、動いても稼働中のセーブで壊れる
            assertEquals(
                expr.evaluateDouble(StubMachineContext.withMachine()),
                roundTrip(source).evaluateDouble(StubMachineContext.withMachine()),
                "docs に載っている \"" + source + "\" が往復で値を変える");
        }
    }

    // ============================================================
    // 書き損じ
    // ============================================================

    @Test
    @DisplayName("`:` を書き忘れたら落ちる")
    public void test片方だけは落ちる() {
        assertThrows(RuntimeException.class, () -> ExpressionParser.parseExpression("1 ? 2"), "枝が 1 つで通る");
        assertThrows(RuntimeException.class, () -> ExpressionParser.parseExpression("1 ? 2 :"), "偽の枝が空で通る");
        assertThrows(RuntimeException.class, () -> ExpressionParser.parseExpression("1 : 2"), "`?` 無しの `:` が通る");
    }

    @Test
    @DisplayName("ブロック ID の `:` は巻き込まれない")
    public void test文字列の中のコロン() {
        // 'minecraft:stone' は文字列リテラルとして消費されるので、`:` は演算子に見えない
        assertEquals(
            StubMachineContext.ITEM_COUNT_NAMED,
            evaluate("item('minecraft:stone')"),
            "文字列の中の `:` が三項として読まれている");
        assertEquals(
            StubMachineContext.ITEM_COUNT_NAMED,
            evaluate("1 ? item('minecraft:stone') : 0"),
            "枝の中の文字列の `:` が枝の区切りとして読まれている");
    }
}
