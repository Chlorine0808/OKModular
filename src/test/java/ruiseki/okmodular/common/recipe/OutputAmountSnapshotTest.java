package ruiseki.okmodular.common.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.io.EnergyOutput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.io.ItemOutput;

/**
 * 完成時に出す量が**いつ決まるか**。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * 入力はレシピ開始時に消費され、出力は完成時に評価されていた。
 * 途中で `tier` が変われば、**入力は開始時の Tier・出力は完成時の Tier**という非対称になる。
 * 実機で「途中で構造体を変えて Tier を切り替えると、切り替えた**後**の数だけ出力される」
 * として観測された。長いレシピを低 Tier で始めて完成直前に高 Tier へ差し替える採取が成立する。
 *
 * **完成時の出力は開始時に決まる**ことにした。
 *
 * ============================================
 * どこで畳むか
 * ============================================
 *
 * `cachedOutputs` は**もともと開始時に作られる出力のスナップショット**で、
 * `copy(batchSize)` した実体を持ち、NBT にも保存される。
 * 「何を出すか」は既にそこで固まっていて、**「いくつ出すか」だけが式のまま残っていた**。
 * 量もそこで畳めば、新しい層も新しい保存形式も要らず、ワールドの再読込も越える。
 *
 * ============================================
 * perTick 出力は畳まない
 * ============================================
 *
 * `perTick` は「毎 tick 出す」と書いてあるものなので、毎 tick 評価する。
 * 畳むのは**完成時にまとめて出す分**だけ。この線引きは docs に書いてある。
 *
 * ============================================
 */
@DisplayName("完成時の出力量は開始時に決まる")
public class OutputAmountSnapshotTest {

    private static final List<IModularPort> NO_PORTS = Collections.emptyList();

    private ProcessAgent agent;

    private static JsonObject json(String text) {
        return new JsonParser().parse(text)
            .getAsJsonObject();
    }

    private static ConditionContext context() {
        return new ConditionContext(null, 0, 0, 0);
    }

    private static ModularRecipe recipeWith(IRecipeOutput output) {
        return ModularRecipe.builder()
            .registryName("test")
            .recipeGroup("test")
            .name("test")
            .duration(100)
            .addOutput(output)
            .build();
    }

    @BeforeEach
    public void setUp() {
        agent = new ProcessAgent(null);
    }

    // ========== 畳む操作そのもの ==========

    @Test
    @DisplayName("畳んだ後は文脈なしで量が読める")
    public void test畳むと文脈が要らなくなる() {
        ItemOutput output = ItemOutput.fromJson(json("{ \"item\": \"minecraft:gold_nugget\", \"amount\": \"2 + 3\" }"));
        assertNotEquals(5, output.getRequiredAmount(), "前提: 畳む前は控え値を返す");

        output.resolveAmount(context());

        assertEquals(5, output.getRequiredAmount(), "畳んだのに式のままになっている");
        assertEquals(5, output.getRequiredAmount(context()), "文脈つきでも同じ値であるべき");
    }

    @Test
    @DisplayName("式で書いていない量は畳んでも変わらない")
    public void test数値の量は変わらない() {
        ItemOutput output = ItemOutput.fromJson(json("{ \"item\": \"minecraft:gold_nugget\", \"amount\": 3 }"));

        output.resolveAmount(context());

        assertEquals(3, output.getRequiredAmount());
    }

    // ========== レシピ開始時の配線 ==========

    @Test
    @DisplayName("【本題】開始した時点で完成時の量が確定する")
    public void test開始時に確定する() {
        ItemOutput output = ItemOutput.fromJson(json("{ \"item\": \"minecraft:gold_nugget\", \"amount\": \"2 + 3\" }"));

        assertTrue(agent.startRecipe(recipeWith(output), NO_PORTS, NO_PORTS, context()));

        IRecipeOutput cached = agent.getCachedOutputs()
            .get(0);
        assertEquals(5, cached.getRequiredAmount(), "完成時まで式が残っている。途中で状態が変われば結果も変わる");
    }

    @Test
    @DisplayName("元のレシピの出力は畳まれない")
    public void test元のレシピは変わらない() {
        // cachedOutputs は copy() なので、レシピそのもの（全機械で共有）を書き換えてはいけない。
        ItemOutput output = ItemOutput.fromJson(json("{ \"item\": \"minecraft:gold_nugget\", \"amount\": \"2 + 3\" }"));

        agent.startRecipe(recipeWith(output), NO_PORTS, NO_PORTS, context());

        assertNotEquals(5, output.getRequiredAmount(), "レシピ定義そのものを畳んでしまっている");
    }

    @Test
    @DisplayName("perTick 出力は畳まない")
    public void testperTickは畳まない() {
        EnergyOutput output = EnergyOutput.fromJson(json("{ \"energy\": \"2 + 3\", \"pertick\": true }"));
        assertTrue(output.isPerTick(), "前提: perTick として読めていること");

        agent.startRecipe(recipeWith(output), NO_PORTS, NO_PORTS, context());

        IRecipeOutput perTick = agent.getPerTickOutputs()
            .get(0);
        assertEquals(5, perTick.getRequiredAmount(context()), "毎 tick 評価できなくなっている");
        assertNotEquals(5, perTick.getRequiredAmount(), "perTick まで畳んでしまっている");
    }
}
