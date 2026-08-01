package ruiseki.okmodular.api.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.recipe.expression.ExpressionParser;
import ruiseki.okmodular.api.recipe.expression.StubMachineContext;

/**
 * 別の座標で条件を評価するために作り直す文脈が、何を引き継ぐか。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `offset` と `pattern` は**中の条件を別の座標で評価する**ので、
 * 座標だけ差し替えた `ConditionContext` を作り直す。
 * `ConditionContext` は 3 つのコンストラクタを持ち、**短い方を選ぶと黙って情報が落ちる**:
 *
 * <pre>
 * (world, x, y, z, recipeContext, evaluationSeed)   全部持つ
 * (world, x, y, z, recipeContext)                   evaluationSeed = 0 に固定
 * (world, x, y, z)                                  機械も種も無い
 * </pre>
 *
 * どちらも 5 引数を使っていたので、**機械は引き継ぐのに評価の種だけが 0 に落ちていた**。
 * 種を使うのは `random()` と `chance()` の 2 つで（`MathFunctionExpression:85,149`）、
 * 種が常に 0 なら `new Random(0)` / `new Random(1)` の 1 個目が毎回返る。
 * つまり `offset` の中の `chance(0.5)` は**常に false**、`chance(0.9)` は**常に true**で、
 * 全機械・全 tick で同じ。確率になっていない。
 *
 * これは `TEMachineController` が素の文脈を渡していた件とは**別の欠陥**で、
 * こちらは機械そのものは正しく繋がっていた。落ちるのは種だけ。
 *
 * ============================================
 * 何を縛るのか
 * ============================================
 *
 * 「座標だけ差し替える」を**座標以外は同じ**として縛る。
 * 引き継ぐもの（機械・種）と変えるもの（座標）を両方書いてあるので、
 * 将来また短いコンストラクタに戻せばここで落ちる。
 *
 * ============================================
 */
@DisplayName("派生文脈が引き継ぐもの")
public class DerivedContextTest {

    /** 0 と紛れない種。落ちれば 0 になるので、0 でないことに意味がある。 */
    private static final long SEED = 0x5EEDL;

    private static final int X = 100;
    private static final int Y = 64;
    private static final int Z = -200;

    /** 評価された文脈を順に覚える条件。 */
    private static final class RecordingCondition implements ICondition {

        private final List<ConditionContext> seen = new ArrayList<>();

        @Override
        public boolean isMet(ConditionContext context) {
            seen.add(context);
            return true;
        }

        @Override
        public String getDescription() {
            return "recording";
        }

        @Override
        public void write(JsonObject json) {}
    }

    /** 機械が繋がっていて、種も持っている外側の文脈。 */
    private static ConditionContext outerContext() {
        IRecipeContext machine = StubMachineContext.withMachine()
            .getRecipeContext();
        return new ConditionContext(null, X, Y, Z, machine, SEED);
    }

    private static double evaluate(String expression, ConditionContext context) {
        return ExpressionParser.parseExpression(expression)
            .evaluateDouble(context);
    }

    // ========== offset ==========

    @Test
    @DisplayName("offset は座標をずらす")
    public void testoffsetは座標をずらす() {
        RecordingCondition inner = new RecordingCondition();
        ConditionContext outer = outerContext();

        new OffsetCondition(inner, 1, -2, 3).isMet(outer);

        ConditionContext derived = inner.seen.get(0);
        assertEquals(X + 1, derived.getX());
        assertEquals(Y - 2, derived.getY());
        assertEquals(Z + 3, derived.getZ());
    }

    @Test
    @DisplayName("offset は機械を引き継ぐ")
    public void testoffsetは機械を引き継ぐ() {
        // これは元から通っていた。座標以外が同じであることの片方。
        RecordingCondition inner = new RecordingCondition();
        ConditionContext outer = outerContext();

        new OffsetCondition(inner, 1, 0, 0).isMet(outer);

        assertSame(
            outer.getRecipeContext(),
            inner.seen.get(0)
                .getRecipeContext());
    }

    @Test
    @DisplayName("offset は評価の種を引き継ぐ")
    public void testoffsetは種を引き継ぐ() {
        RecordingCondition inner = new RecordingCondition();
        ConditionContext outer = outerContext();

        new OffsetCondition(inner, 1, 0, 0).isMet(outer);

        assertEquals(
            SEED,
            inner.seen.get(0)
                .getEvaluationSeed(),
            "種が 0 に落ちている。offset の中の chance() が確率でなくなる");
    }

    @Test
    @DisplayName("offset の中の random() は外と同じ答えを出す")
    public void testoffsetの中の乱数() {
        // 種が落ちた形での実害。同じ 1 回の判定なので、同じ種から同じ答えが出るのが正しい。
        RecordingCondition inner = new RecordingCondition();
        ConditionContext outer = outerContext();

        new OffsetCondition(inner, 1, 0, 0).isMet(outer);

        assertEquals(evaluate("random()", outer), evaluate("random()", inner.seen.get(0)));
    }

    // ========== pattern ==========

    /** 3 × 3 の全マスに同じ条件を置いたもの。中心が基準になる。 */
    private static BiomePatternCondition pattern(ICondition cell) {
        Map<Character, ICondition> keys = new HashMap<>();
        keys.put('A', cell);
        return new BiomePatternCondition(new String[] { "AAA", "AAA", "AAA" }, keys);
    }

    @Test
    @DisplayName("pattern は中心を基準に水平だけずらす")
    public void testpatternは水平にずらす() {
        RecordingCondition inner = new RecordingCondition();

        pattern(inner).isMet(outerContext());

        assertEquals(9, inner.seen.size());
        ConditionContext first = inner.seen.get(0);
        assertEquals(X - 1, first.getX());
        assertEquals(Y, first.getY(), "pattern は 2D なので高さは動かさない");
        assertEquals(Z - 1, first.getZ());
    }

    @Test
    @DisplayName("pattern は機械を引き継ぐ")
    public void testpatternは機械を引き継ぐ() {
        RecordingCondition inner = new RecordingCondition();
        ConditionContext outer = outerContext();

        pattern(inner).isMet(outer);

        for (ConditionContext derived : inner.seen) {
            assertSame(outer.getRecipeContext(), derived.getRecipeContext());
        }
    }

    @Test
    @DisplayName("pattern は評価の種を引き継ぐ")
    public void testpatternは種を引き継ぐ() {
        RecordingCondition inner = new RecordingCondition();

        pattern(inner).isMet(outerContext());

        for (ConditionContext derived : inner.seen) {
            assertEquals(SEED, derived.getEvaluationSeed(), "種が 0 に落ちている");
        }
    }

    @Test
    @DisplayName("空のパターンは何も評価しない")
    public void test空のパターン() {
        RecordingCondition inner = new RecordingCondition();
        Map<Character, ICondition> keys = new HashMap<>();
        keys.put('A', inner);

        new BiomePatternCondition(new String[0], keys).isMet(outerContext());

        assertEquals(Collections.emptyList(), inner.seen);
    }
}
