package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;

/**
 * バッチ倍率を式に織り込む処理の検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * 出力は copy(batchSize) されてから apply(..., 1, ...) で呼ばれる。
 * 静的な量は copy の中で stackSize *= multiplier されるが、式は
 * そのまま引き継がれていたため、**式で書いた量だけ batch が効かなかった**。
 *
 * 定数を定数のまま保つ点が重要: BlockInput / BlockOutput の NBT 書き込みは
 * `amountExpr instanceof ConstantExpression` で「数値として書けるか」を
 * 判定している。ここで定数が掛け算に化けると、単なる数値が式文字列として
 * 保存されるようになる。
 *
 * ============================================
 */
@DisplayName("式のバッチスケーリング")
public class ExpressionScalingTest {

    private static ConditionContext emptyContext() {
        return new ConditionContext(null, 0, 0, 0);
    }

    @Test
    @DisplayName("式に倍率が掛かる")
    public void test式に倍率が掛かる() {
        IExpression expr = ExpressionParser.parseExpression("2 + 3");

        assertEquals(
            15.0,
            ArithmeticExpression.scaled(expr, 3)
                .evaluateDouble(emptyContext()));
    }

    @Test
    @DisplayName("倍率 1 なら同じインスタンスを返す")
    public void test倍率1なら変わらない() {
        IExpression expr = ExpressionParser.parseExpression("2 + 3");

        assertSame(expr, ArithmeticExpression.scaled(expr, 1), "無駄なラップをしないべき");
    }

    @Test
    @DisplayName("null は null のまま")
    public void testNullはNullのまま() {
        assertNull(ArithmeticExpression.scaled(null, 5));
    }

    @Test
    @DisplayName("【重要】定数は定数のまま保たれる")
    public void test定数は定数のまま() {
        IExpression scaled = ArithmeticExpression.scaled(new ConstantExpression(4), 3);

        assertInstanceOf(
            ConstantExpression.class,
            scaled,
            "定数が掛け算に化けると instanceof ConstantExpression で分岐している NBT 書き込みが壊れる");
        assertEquals(12.0, scaled.evaluateDouble(null));
    }

    @Test
    @DisplayName("スケールした式は再パースできる形で表現される")
    public void testスケール後も再パースできる() {
        IExpression scaled = ArithmeticExpression.scaled(ExpressionParser.parseExpression("2 + 3"), 3);

        String text = scaled.toString();
        assertFalse(text.contains("@"), "Object.toString() になっていないべき: " + text);

        // NBT / JSON に書いて読み直す経路と同じ
        IExpression reparsed = ExpressionParser.parseExpression(text);
        assertEquals(15.0, reparsed.evaluateDouble(emptyContext()), "往復して同じ値になるべき");
    }

    @Test
    @DisplayName("スケールを重ねても評価順序が壊れない")
    public void test括弧が優先順位を守る() {
        // (2 + 3) * 3 が 2 + 3 * 3 = 11 になってはいけない
        IExpression scaled = ArithmeticExpression.scaled(ExpressionParser.parseExpression("2 + 3"), 3);
        assertEquals(15.0, scaled.evaluateDouble(emptyContext()));

        IExpression twice = ArithmeticExpression.scaled(scaled, 2);
        assertEquals(30.0, twice.evaluateDouble(emptyContext()));
        assertEquals(
            30.0,
            ExpressionParser.parseExpression(twice.toString())
                .evaluateDouble(emptyContext()),
            "二重にスケールした式も往復できるべき");
    }
}
