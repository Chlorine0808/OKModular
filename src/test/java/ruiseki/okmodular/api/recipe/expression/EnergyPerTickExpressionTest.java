package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;

/**
 * `energy_per_tick` をレシピの式から読めることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * この値の公開先は 2 つある:
 *
 * 1. IMachineState.getEnergyPerTick()
 * 2. レシピの式で使う `energy_per_tick` 変数
 *
 * 以前の修正（ProcessAgent に setEnergyPerTick の呼び出しが無く常に 0
 * だった件。PerTickReportingTest がその回帰テスト）で直ったのは **1 だけ**
 * だった。2 は ExpressionRegistry に registerMachineProperty("energy_per_tick")
 * として登録されているのでパースは通るが、MachinePropertyExpression 側に
 * 対応する定義が無く、**評価すると黙って 0 を返していた**。
 *
 * MachinePropertyExpression は定義が見つからないと ZERO を返す作りなので、
 * この種の欠落はエラーにならない。レシピ作者から見ると
 * 「式は書けるのに常に 0」という形で現れる。
 *
 * docs/{en,jp}/recipes/EXPRESSION_REFERENCE.md は `energy_per_tick` を
 * 使える式として記載しているので、これは仕様に対する実装漏れ。
 *
 * ============================================
 */
@DisplayName("energy_per_tick 式")
public class EnergyPerTickExpressionTest {

    private static double evaluate(String expression, ConditionContext context) {
        return ExpressionParser.parseExpression(expression)
            .evaluateDouble(context);
    }

    @Test
    @DisplayName("【回帰防止】機械の毎 tick エネルギーを返す")
    public void test毎tickエネルギーを返す() {
        assertEquals(
            StubMachineContext.ENERGY_PER_TICK,
            evaluate("energy_per_tick", StubMachineContext.withMachine()),
            "0 が返るなら MachinePropertyExpression に定義が無い");
    }

    @Test
    @DisplayName("式の中で計算に使える")
    public void test式の中で計算に使える() {
        assertEquals(
            StubMachineContext.ENERGY_PER_TICK * 2,
            evaluate("energy_per_tick * 2", StubMachineContext.withMachine()));
    }

    @Test
    @DisplayName("機械が無いコンテキストでは 0")
    public void test機械が無ければ0() {
        assertEquals(
            0.0,
            evaluate("energy_per_tick", StubMachineContext.withoutMachine()),
            "NEI はマシン無しでレシピを描くので、ここで落ちてはいけない");
    }
}
