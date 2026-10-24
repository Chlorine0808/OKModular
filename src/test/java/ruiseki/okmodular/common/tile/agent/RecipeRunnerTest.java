package ruiseki.okmodular.common.tile.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.PortColor;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.error.ErrorReason;
import ruiseki.okmodular.common.recipe.ProcessAgent;
import ruiseki.okmodular.common.tile.ProcessErrorState;

/**
 * レシピ進行が GUI へ出す「止まっている理由」の割り当て。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * この進行は `TEMachineController.processRecipe` / `startNextRecipe` にあり、
 * コントローラはユニットテストで組めないので、**実機でしか確かめられなかった**。
 * 理由の取り違えは例外にならず、GUI の文言が変わるだけなので気付きにくい。
 * 例えば条件で開始を断られたのに「入力が足りない」と出すと、プレイヤーは
 * 関係の無いハッチを見に行く。
 *
 * `MachineStateAgent` と同じく `Host` 越しに組めるクラスへ切り出し、
 * `ProcessAgent` は結果を台本どおりに返す subclass に置き換えて、
 * 経路ごとの理由をここで固定する。
 */
@DisplayName("レシピ進行のエラー理由")
public class RecipeRunnerTest {

    private static final List<IModularPort> NO_PORTS = Collections.emptyList();

    private ScriptedAgent agent;
    private ProcessErrorState errors;
    private FakeHost host;
    private FakeFinder finder;
    private RecipeRunner runner;

    @BeforeEach
    public void setUp() {
        agent = new ScriptedAgent();
        errors = new ProcessErrorState();
        host = new FakeHost();
        finder = new FakeFinder();
        runner = new RecipeRunner(host, agent, errors, finder);
    }

    private static ModularRecipe recipe() {
        return ModularRecipe.builder()
            .registryName("test")
            .recipeGroup("test")
            .name("test")
            .duration(1)
            .build();
    }

    // ========== Waiting for output ==========

    @Test
    @DisplayName("出力待ちでブロック出力が詰まっていれば BLOCK_OUTPUT_FULL")
    public void testブロック出力の詰まり() {
        agent.waiting = true;
        agent.blockOutputFull = true;

        runner.process();

        assertEquals(ErrorReason.BLOCK_OUTPUT_FULL, errors.getReason());
    }

    @Test
    @DisplayName("出力待ちでブロック以外なら WAITING_OUTPUT")
    public void test出力待ち() {
        agent.waiting = true;

        runner.process();

        assertEquals(ErrorReason.WAITING_OUTPUT, errors.getReason());
    }

    @Test
    @DisplayName("出力できたら完了を記録して次を探す")
    public void test出力できたら完了を記録() {
        ModularRecipe done = recipe();
        agent.waiting = true;
        agent.outputSucceeds = true;
        agent.current = done;

        runner.process();

        assertEquals(Collections.singletonList(done), host.completed);
        assertEquals(1, finder.calls, "完了したその tick で次のレシピを探していない");
    }

    // ========== Running ==========

    @Test
    @DisplayName("実行中に入力が尽きたら INPUT_MISSING")
    public void test実行中の入力切れ() {
        agent.running = true;
        agent.tickResult = ProcessAgent.TickResult.NO_INPUT;

        runner.process();

        assertEquals(ErrorReason.INPUT_MISSING, errors.getReason());
    }

    @Test
    @DisplayName("実行中にエネルギーが尽きたら NO_ENERGY")
    public void test実行中のエネルギー切れ() {
        agent.running = true;
        agent.tickResult = ProcessAgent.TickResult.NO_ENERGY;

        runner.process();

        assertEquals(ErrorReason.NO_ENERGY, errors.getReason());
    }

    @Test
    @DisplayName("実行中に条件で止まったら PAUSED で、止めた条件を詳細に出す")
    public void test実行中の条件停止() {
        agent.running = true;
        agent.tickResult = ProcessAgent.TickResult.PAUSED;
        agent.conditionFailure = "tier >= 2";

        runner.process();

        assertEquals(ErrorReason.PAUSED, errors.getReason());
        assertEquals("tier >= 2", errors.getDetail());
    }

    @Test
    @DisplayName("先読みは見つかったら実行中に 1 回だけ")
    public void test先読みは1回() {
        finder.answer = recipe();
        agent.running = true;
        agent.tickResult = ProcessAgent.TickResult.CONTINUE;

        runner.process();
        runner.process();

        assertEquals(1, finder.calls);
    }

    @Test
    @DisplayName("先読みは見つからなくても実行中に 1 回だけ")
    public void test見つからない先読みも1回() {
        agent.running = true;
        agent.tickResult = ProcessAgent.TickResult.CONTINUE;

        runner.process();
        runner.process();
        runner.process();

        assertEquals(1, finder.calls, "完了時に startNextRecipe が探し直すので、実行中に毎 tick 探す必要は無い");
    }

    // ========== Idle ==========

    @Test
    @DisplayName("何も合わず入力も無ければ INPUT_MISSING")
    public void test待機中の入力なし() {
        agent.idleDiagnosis = ProcessAgent.TickResult.NO_INPUT;

        runner.process();

        assertEquals(ErrorReason.INPUT_MISSING, errors.getReason());
    }

    @Test
    @DisplayName("何も合わず入力はあるなら NO_MATCHING_RECIPE")
    public void test待機中の不一致() {
        runner.process();

        assertEquals(ErrorReason.NO_MATCHING_RECIPE, errors.getReason());
    }

    @Test
    @DisplayName("【回帰防止】条件で開始を断られたら CONDITION_NOT_MET で、入力不足とは言わない")
    public void test条件で開始できない() {
        finder.answer = recipe();
        agent.blockedByCondition = true;
        agent.conditionFailure = "dimension == 1";

        runner.process();

        assertEquals(ErrorReason.CONDITION_NOT_MET, errors.getReason());
        assertEquals("dimension == 1", errors.getDetail());
    }

    @Test
    @DisplayName("条件以外で開始できなければ NO_INPUT")
    public void test開始できない() {
        finder.answer = recipe();

        runner.process();

        assertEquals(ErrorReason.NO_INPUT, errors.getReason());
    }

    @Test
    @DisplayName("開始できたら NONE にし、色と開始 tick を先に記録する")
    public void test開始できた() {
        errors.set(ErrorReason.NO_MATCHING_RECIPE);
        finder.answer = recipe();
        agent.startSucceeds = true;

        runner.process();

        assertEquals(ErrorReason.NONE, errors.getReason());
        assertEquals(Collections.singletonList(PortColor.NONE), host.startedColors);
        int handedOver = host.contexts.indexOf(agent.startContext);
        assertTrue(handedOver >= host.contextsBeforeStart, "開始 tick を記録する前に作った文脈で開始している。評価のシードが前回の開始 tick になる");
    }

    @Test
    @DisplayName("動き出したら描画の更新を 1 回だけ頼む")
    public void test描画の更新() {
        finder.answer = recipe();
        agent.startSucceeds = true;
        agent.runningAfterStart = true;

        runner.process();
        assertEquals(1, host.workingChanges);

        agent.running = false;
        agent.runningAfterStart = true;
        runner.process();
        assertEquals(1, host.workingChanges, "状態が変わっていないのに更新を頼んでいる");
    }

    @Test
    @DisplayName("起こすと先読みを捨てて探し直す")
    public void test先読みを捨てる() {
        finder.answer = recipe();
        agent.running = true;
        agent.tickResult = ProcessAgent.TickResult.CONTINUE;
        runner.process();

        runner.wake();
        runner.process();

        assertEquals(2, finder.calls);
    }

    // ========== Idle backoff ==========

    /** Runs the given number of idle ticks and returns the ticks on which a search ran. */
    private List<Integer> idleSearchTicks(int ticks) {
        List<Integer> searched = new ArrayList<>();
        for (int t = 0; t < ticks; t++) {
            int before = finder.calls;
            runner.process();
            if (finder.calls > before) searched.add(t);
        }
        return searched;
    }

    @Test
    @DisplayName("何も見つからない待機中は、検索の間隔を倍にしていく")
    public void test間隔を倍にする() {
        assertEquals(java.util.Arrays.asList(0, 2, 6, 14, 30), idleSearchTicks(31));
    }

    @Test
    @DisplayName("間隔は 20 tick で頭打ち")
    public void test間隔の上限() {
        List<Integer> searched = idleSearchTicks(200);

        int widest = 0;
        for (int i = 1; i < searched.size(); i++) {
            widest = Math.max(widest, searched.get(i) - searched.get(i - 1));
        }
        assertEquals(RecipeRunner.MAX_IDLE_INTERVAL, widest);
    }

    @Test
    @DisplayName("起こすと次の tick にすぐ探す")
    public void test起こすとすぐ探す() {
        idleSearchTicks(31);

        runner.wake();

        assertEquals(Collections.singletonList(0), idleSearchTicks(1));
    }

    @Test
    @DisplayName("開始できたら間隔を戻す")
    public void test開始できたら戻す() {
        idleSearchTicks(31);
        finder.answer = recipe();
        agent.startSucceeds = true;
        runner.wake();
        idleSearchTicks(1);

        agent.startSucceeds = false;
        finder.answer = null;

        assertEquals(Collections.singletonList(0), idleSearchTicks(1), "開始できた直後なのに、前の長い間隔を待っている");
    }

    // ========== Fakes ==========

    private static final class ScriptedAgent extends ProcessAgent {

        boolean waiting;
        boolean running;
        boolean runningAfterStart;
        boolean blockOutputFull;
        boolean outputSucceeds;
        boolean startSucceeds;
        boolean blockedByCondition;
        String conditionFailure;
        IModularRecipe current;
        ConditionContext startContext;
        TickResult tickResult = TickResult.CONTINUE;
        TickResult idleDiagnosis = TickResult.CONTINUE;

        ScriptedAgent() {
            super(null);
        }

        @Override
        public boolean isWaitingForOutput() {
            return waiting;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public IModularRecipe getCurrentRecipe() {
            return current;
        }

        @Override
        public boolean diagnoseBlockOutputFull(List<IModularPort> outputPorts, ConditionContext context) {
            return blockOutputFull;
        }

        @Override
        public boolean tryOutput(List<IModularPort> outputPorts, ConditionContext context) {
            if (outputSucceeds) waiting = false;
            return outputSucceeds;
        }

        @Override
        public TickResult tick(List<IModularPort> inputPorts, List<IModularPort> outputPorts,
            ConditionContext context) {
            return tickResult;
        }

        @Override
        public TickResult diagnoseIdle(List<IModularPort> inputPorts) {
            return idleDiagnosis;
        }

        @Override
        public boolean startRecipe(IModularRecipe recipe, List<IModularPort> inputPorts, List<IModularPort> outputPorts,
            ConditionContext context) {
            startContext = context;
            if (startSucceeds && runningAfterStart) running = true;
            return startSucceeds;
        }

        @Override
        public boolean wasBlockedByCondition() {
            return blockedByCondition;
        }

        @Override
        public String getConditionFailure() {
            return conditionFailure;
        }
    }

    private static final class FakeHost implements RecipeRunner.Host {

        final List<IModularRecipe> completed = new ArrayList<>();
        final List<PortColor> startedColors = new ArrayList<>();
        PortColor activeColor = PortColor.NONE;
        final List<ConditionContext> contexts = new ArrayList<>();
        int contextsBeforeStart = -1;
        int workingChanges;

        @Override
        public ConditionContext newConditionContext() {
            ConditionContext context = new ConditionContext(null, 0, 0, 0, null, 4649L);
            contexts.add(context);
            return context;
        }

        @Override
        public List<IModularPort> getContextualInputPorts() {
            return NO_PORTS;
        }

        @Override
        public List<IModularPort> getContextualOutputPorts() {
            return NO_PORTS;
        }

        @Override
        public List<IModularPort> getOutputPorts() {
            return NO_PORTS;
        }

        @Override
        public List<String> getRecipeGroups() {
            return Collections.singletonList("test");
        }

        @Override
        public PortColor getActiveRecipeColor() {
            return activeColor;
        }

        @Override
        public void onRecipeStarting(PortColor color) {
            activeColor = color;
            startedColors.add(color);
            contextsBeforeStart = contexts.size();
        }

        @Override
        public void recordRecipeCompletion(IModularRecipe recipe) {
            completed.add(recipe);
        }

        @Override
        public void onWorkingChanged() {
            workingChanges++;
        }
    }

    private static final class FakeFinder implements RecipeRunner.RecipeFinder {

        IModularRecipe answer;
        int calls;

        @Override
        public IModularRecipe findMatch(String[] groups, List<IModularPort> inputs,
            Predicate<IModularRecipe> canStart) {
            calls++;
            return answer;
        }

        @Override
        public int getRecipeVersion() {
            return 0;
        }
    }

}
