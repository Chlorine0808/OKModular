package ruiseki.okmodular.common.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ICondition;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.structure.core.ConditionPolicy;

/**
 * レシピ自身の `conditions` が、開始時と稼働中にどう効くか。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * **レシピの条件は開始時に一度もチェックされていなかった。**
 * `RecipeLoader.findMatch` → `matchesInput` は入力の有無しか見ず、
 * `startRecipe` の visitor も入出力しか歩かない。条件は稼働中の
 * `checkContinuousConditions` **だけ**で見られ、崩れると `abort()` していた。
 *
 * 結果、条件が偽でも機械はレシピを開始し、**材料を消費してから**次の tick で破棄し、
 * また開始する。実機では `"conditions": [{ "expression": "energy > 1000" }]` を
 * 書いたレシピが **2 tick に 1 個ずつ砂を溶かし続けた**。
 *
 * ============================================
 * 2 つの直し方を両方入れている
 * ============================================
 *
 * 1. **開始時に条件を見る。** これだけで無限ループは止まる（消費する前に断る）
 * 2. **`conditionPolicy` をレシピにも与える。** 既定は `pause`
 *
 * 2 が要るのは、1 だけでは「稼働中に条件が崩れたら消費済みの材料が消える」が残るから。
 * 構造体の機械条件は前から `pause` / `abort` を選べたのに、レシピ側は
 * **常に `abort`** で、しかも選べなかった。docs の「`abort` はこの mod で唯一
 * 消費済みレシピを破棄する」という記述もそのせいで嘘になっていた。
 *
 * ============================================
 */
@DisplayName("レシピ条件と conditionPolicy")
public class RecipeConditionPolicyTest {

    private static final List<IModularPort> NO_PORTS = Collections.emptyList();

    private ProcessAgent agent;

    /** 答えを外から切り替えられる条件。 */
    private static final class Switchable implements ICondition {

        private boolean answer;

        Switchable(boolean answer) {
            this.answer = answer;
        }

        @Override
        public boolean isMet(ConditionContext context) {
            return answer;
        }

        @Override
        public String getDescription() {
            return "switchable";
        }

        @Override
        public void write(JsonObject json) {}
    }

    private static ConditionContext context() {
        return new ConditionContext(null, 0, 0, 0);
    }

    private static ModularRecipe recipe(ICondition condition, ConditionPolicy policy) {
        ModularRecipe.Builder builder = ModularRecipe.builder()
            .registryName("test")
            .recipeGroup("test")
            .name("test")
            .duration(100);
        if (condition != null) builder.addCondition(condition);
        if (policy != null) builder.conditionPolicy(policy);
        return builder.build();
    }

    @BeforeEach
    public void setUp() {
        // 式の評価にしか context を使わないので IRecipeContext は不要。
        agent = new ProcessAgent(null);
    }

    // ========== 開始時 ==========

    @Test
    @DisplayName("【回帰防止】条件が偽なら開始しない")
    public void test条件が偽なら開始しない() {
        // ここが通っていなかったせいで、材料を消費してから毎 tick 破棄していた。
        boolean started = agent.startRecipe(recipe(new Switchable(false), null), NO_PORTS, NO_PORTS, context());

        assertFalse(started, "条件が偽なのに開始している");
        assertFalse(agent.isRunning(), "消費が走る前に断らなければならない");
    }

    @Test
    @DisplayName("条件が真なら開始する")
    public void test条件が真なら開始する() {
        assertTrue(agent.startRecipe(recipe(new Switchable(true), null), NO_PORTS, NO_PORTS, context()));
        assertTrue(agent.isRunning());
    }

    @Test
    @DisplayName("条件を書いていないレシピは今までどおり")
    public void test条件が無ければ開始する() {
        assertTrue(agent.startRecipe(recipe(null, null), NO_PORTS, NO_PORTS, context()));
    }

    // ========== 稼働中 ==========

    @Test
    @DisplayName("既定は pause — 崩れても破棄しない")
    public void test既定はpause() {
        Switchable condition = new Switchable(true);
        agent.startRecipe(recipe(condition, null), NO_PORTS, NO_PORTS, context());
        agent.tick(NO_PORTS, NO_PORTS, context());
        long progressBefore = agent.getProgress();

        condition.answer = false;
        ProcessAgent.TickResult result = agent.tick(NO_PORTS, NO_PORTS, context());

        assertEquals(ProcessAgent.TickResult.PAUSED, result);
        assertTrue(agent.isRunning(), "破棄してはいけない。消費済みの材料が消える");
        assertEquals(progressBefore, agent.getProgress(), "止まっている間は進んではいけない");
    }

    @Test
    @DisplayName("pause は条件が戻れば続きから再開する")
    public void testpauseは再開する() {
        Switchable condition = new Switchable(true);
        agent.startRecipe(recipe(condition, ConditionPolicy.PAUSE), NO_PORTS, NO_PORTS, context());
        agent.tick(NO_PORTS, NO_PORTS, context());
        long progressBefore = agent.getProgress();

        condition.answer = false;
        agent.tick(NO_PORTS, NO_PORTS, context());
        condition.answer = true;
        agent.tick(NO_PORTS, NO_PORTS, context());

        assertTrue(agent.getProgress() > progressBefore, "条件が戻ったのに進んでいない");
    }

    @Test
    @DisplayName("abort を選べば従来どおり破棄する")
    public void testabortは破棄する() {
        Switchable condition = new Switchable(true);
        agent.startRecipe(recipe(condition, ConditionPolicy.ABORT), NO_PORTS, NO_PORTS, context());
        agent.tick(NO_PORTS, NO_PORTS, context());

        condition.answer = false;
        agent.tick(NO_PORTS, NO_PORTS, context());

        assertFalse(agent.isRunning(), "abort を指定したのに走り続けている");
    }

    @Test
    @DisplayName("条件が満たされている間は普通に進む")
    public void test条件が真なら進む() {
        agent.startRecipe(recipe(new Switchable(true), null), NO_PORTS, NO_PORTS, context());

        agent.tick(NO_PORTS, NO_PORTS, context());
        agent.tick(NO_PORTS, NO_PORTS, context());

        assertEquals(2, agent.getProgress());
    }
}
