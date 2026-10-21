package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.core.MachinePerformance;
import ruiseki.okmodular.testsupport.TunableMachine;

/**
 * 写しを持った文脈で、式が性能を写しから読むこと。
 *
 * 開始時に写したあと機械を組み替え、式が組み替え前の値を答えるかを見る。
 * 残量（energy）は写しの対象ではないので、組み替え後の値を答えるのが正しい。
 */
@DisplayName("写しを持った文脈での性能の読み方")
public class StartedPerformanceReadTest {

    private TunableMachine machine;
    private ConditionContext started;

    private static double eval(String expression, ConditionContext context) {
        return ExpressionParser.parseExpression(expression)
            .evaluateDouble(context);
    }

    @BeforeEach
    public void setUp() {
        machine = new TunableMachine();
        machine.tier = 1;
        machine.speed = 1.0;
        machine.energyMultiplier = 1.0;
        machine.energy = 100;
        machine.components.put("casing", 1);

        MachinePerformance taken = MachinePerformance.capture(machine.context());

        machine.tier = 5;
        machine.speed = 3.0;
        machine.energyMultiplier = 4.0;
        machine.energy = 900;
        machine.components.put("casing", 5);

        started = machine.context()
            .withPerformance(taken);
    }

    @Test
    @DisplayName("tier は開始時の値")
    public void testTier() {
        assertEquals(1, eval("tier", started));
    }

    @Test
    @DisplayName("速度係数とエネルギー係数は開始時の値")
    public void test係数() {
        assertEquals(1.0, eval("speed_multi", started));
        assertEquals(1.0, eval("energy_multi", started));
    }

    @Test
    @DisplayName("部品 Tier は開始時の値")
    public void test部品Tier() {
        assertEquals(1, eval("tier.casing", started));
    }

    @Test
    @DisplayName("残量は読んだ時点の値")
    public void test残量は生きている() {
        assertEquals(900, eval("energy", started));
    }

    @Test
    @DisplayName("写しが無ければ今の機械を読む")
    public void test写しが無い() {
        assertEquals(5, eval("tier", machine.context()));
        assertEquals(
            5,
            eval(
                "tier",
                machine.context()
                    .withPerformance(null)));
    }

    @Test
    @DisplayName("バッチの抽選に分けても写しは残る")
    public void test抽選ごと() {
        // bonus や random_block_output は forDraw で抽選を分ける。そこで写しが落ちると、
        // 2 回目以降の抽選だけが組み替え後の機械を読む。
        assertEquals(1, eval("tier", started.forDraw(2)));
    }

    @Test
    @DisplayName("パターンのマスに分けても写しは残る")
    public void testマスごと() {
        assertEquals(1, eval("tier", ConditionContext.forCell(started, 1, 2, 3, 0, 0, 0)));
    }
}
