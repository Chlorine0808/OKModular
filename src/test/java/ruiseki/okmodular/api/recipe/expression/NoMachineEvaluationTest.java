package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;

/**
 * 機械が居ない文脈で式を評価しても**落ちない**ことの検証。
 *
 * ============================================
 * NEI は機械を持っていない
 * ============================================
 *
 * NEI はレシピを一覧として描くだけなので、`ConditionContext` の `recipeContext` が null。
 * 検証パスや、コマンドから式を試すときも同じ。
 *
 * そこで例外が飛ぶと**レシピ一覧が開かなくなる**。1 つの式のせいで
 * その機械のレシピが全部見えなくなるので、被害が式の外へ出る。
 *
 * ここで確かめるのは値ではなく**落ちないこと**。
 * 「機械が無いなら 0」は `MachinePropertyExpression.evaluate` が既に決めている契約で、
 * 値そのものは `MachinePropertyCoverageTest` が機械つきで見ている。
 *
 * ============================================
 * 名簿は生かしておく
 * ============================================
 *
 * 対象は `MachinePropertyExpression.propertyNames()` から取る。
 * 手で並べると、**新しく足したプロパティだけが素通しになる**。
 * 素通しになったものが NEI を落とす、というのがここで防ぎたい形そのもの。
 */
@DisplayName("機械が無い文脈での評価")
public class NoMachineEvaluationTest {

    private static final ConditionContext NO_MACHINE = new ConditionContext(null, 0, 0, 0);

    @Test
    @DisplayName("すべての機械プロパティが機械無しで評価できる")
    public void test機械無しで落ちない() {
        List<String> thrown = new ArrayList<>();

        for (String name : MachinePropertyExpression.propertyNames()) {
            try {
                ExpressionParser.parseExpression(name)
                    .evaluateDouble(NO_MACHINE);
            } catch (RuntimeException e) {
                thrown.add(
                    name + ": "
                        + e.getClass()
                            .getSimpleName());
            }
        }

        assertTrue(thrown.isEmpty(), thrown + " が機械の無い文脈で例外を投げる。NEI はこの文脈でレシピを描くので、" + "1 つ落ちるとその機械のレシピ一覧ごと開かなくなる");
    }

    @Test
    @DisplayName("機械が無ければ 0 を返す")
    public void test機械無しは0() {
        // 落ちないことと 0 を返すことは別。例外を握り潰して別の値を返す実装だと、
        // NEI に嘘の数字が並ぶ。
        List<String> lying = new ArrayList<>();

        for (String name : MachinePropertyExpression.propertyNames()) {
            double value = ExpressionParser.parseExpression(name)
                .evaluateDouble(NO_MACHINE);
            if (value != 0.0) lying.add(name + " = " + value);
        }

        assertTrue(lying.isEmpty(), lying + " が機械の無い文脈で 0 以外を返した。NEI にその数字がそのまま並ぶ");
    }

    @Test
    @DisplayName("文脈そのものが null でも落ちない")
    public void test文脈が_null_でも落ちない() {
        // 単体で式を評価する経路（検証・コマンド）は文脈を持たないことがある。
        List<String> thrown = new ArrayList<>();

        for (String name : MachinePropertyExpression.propertyNames()) {
            try {
                ExpressionParser.parseExpression(name)
                    .evaluateDouble(null);
            } catch (RuntimeException e) {
                thrown.add(
                    name + ": "
                        + e.getClass()
                            .getSimpleName());
            }
        }

        assertTrue(thrown.isEmpty(), thrown + " が文脈 null で例外を投げる");
    }

    @Test
    @DisplayName("走査そのものが空振りしていない")
    public void test走査が効いている() {
        assertTrue(
            MachinePropertyExpression.propertyNames()
                .size() >= 40,
            "機械プロパティが 40 個未満。名簿の取り方が壊れている（" + MachinePropertyExpression.propertyNames()
                .size() + " 個）");

        // 資源種のループが生やす名前まで入っている証拠。凍結リストに戻ると、
        // ループが増やした名前だけが素通しになる。
        assertTrue(
            MachinePropertyExpression.propertyNames()
                .contains("fluid_f_in"),
            "資源種のループが生やす名前が名簿に入っていない。凍結リストを見ていないか");
    }
}
