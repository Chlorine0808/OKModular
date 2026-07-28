package ruiseki.okmodular.common.recipe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.recipe.io.EnergyInput;
import ruiseki.okmodular.api.recipe.io.EnergyOutput;
import ruiseki.okmodular.api.recipe.io.ManaInput;
import ruiseki.okmodular.api.recipe.io.ManaOutput;

/**
 * 1 tick あたりのリソース量の報告値の検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * ProcessAgent.energyPerTick / manaPerTick を設定する呼び出しが
 * どこにも存在せず、稼働中のマシンでも常に 0 だった。
 * 公開先は 2 つあり、どちらも 0 を見ていた:
 * - IMachineState.getEnergyPerTick()
 * - レシピスクリプトの energy_per_tick 変数（ExpressionRegistry に登録済み）
 *
 * **消費自体は正常だった**。perTick 入力が毎 tick 消費する経路は動いており、
 * 壊れていたのは「いま何を消費しているか」という報告値だけ。
 * このテストはその報告値を対象にする。
 *
 * ============================================
 */
@DisplayName("perTick リソースの報告値")
public class PerTickReportingTest {

    private ProcessAgent agent;

    @BeforeEach
    public void setUp() {
        // IRecipeContext は集計に不要（context を使うのは式の評価だけ）
        agent = new ProcessAgent(null);
    }

    @Test
    @DisplayName("【回帰防止】perTick エネルギー入力が報告される")
    public void testエネルギー入力が報告される() {
        agent.addPerTickInput(new EnergyInput(500, true));

        agent.recomputePerTickTotals(null);

        assertEquals(500, agent.getEnergyPerTick(), "0 のままであってはいけない");
    }

    @Test
    @DisplayName("複数の perTick 入力は合計される")
    public void test複数入力が合計される() {
        agent.addPerTickInput(new EnergyInput(500, true));
        agent.addPerTickInput(new EnergyInput(300, true));

        agent.recomputePerTickTotals(null);

        assertEquals(800, agent.getEnergyPerTick());
    }

    @Test
    @DisplayName("エネルギーとマナが混ざらない")
    public void testエネルギーとマナが振り分けられる() {
        agent.addPerTickInput(new EnergyInput(500, true));
        agent.addPerTickInput(new ManaInput(120, true));

        agent.recomputePerTickTotals(null);

        assertEquals(500, agent.getEnergyPerTick());
        assertEquals(120, agent.getManaPerTick());
    }

    @Test
    @DisplayName("入力と出力が別々に集計される")
    public void test入力と出力が分かれる() {
        agent.addPerTickInput(new EnergyInput(500, true));
        agent.addPerTickOutput(new EnergyOutput(200, true));
        agent.addPerTickOutput(new ManaOutput(40, true));

        agent.recomputePerTickTotals(null);

        assertEquals(500, agent.getEnergyPerTick());
        assertEquals(200, agent.getEnergyOutputPerTick());
        assertEquals(40, agent.getManaOutputPerTick());
        assertEquals(0, agent.getManaPerTick(), "マナ入力は無いので 0");
    }

    @Test
    @DisplayName("再集計で前回の値が累積しない")
    public void test再集計で累積しない() {
        agent.addPerTickInput(new EnergyInput(500, true));
        agent.recomputePerTickTotals(null);
        assertEquals(500, agent.getEnergyPerTick());

        // 動的構造では毎 tick 呼ばれる。足し込みになっていたら際限なく増える
        agent.recomputePerTickTotals(null);
        agent.recomputePerTickTotals(null);

        assertEquals(500, agent.getEnergyPerTick(), "何度呼んでも同じ値であるべき");
    }

    @Test
    @DisplayName("perTick 入力が無ければ 0")
    public void test入力が無ければ0() {
        agent.recomputePerTickTotals(null);

        assertEquals(0, agent.getEnergyPerTick());
        assertEquals(0, agent.getManaPerTick());
    }
}
