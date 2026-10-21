package ruiseki.okmodular.common.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.decorator.RecipeDecorator;
import ruiseki.okmodular.api.recipe.expression.ExpressionParser;
import ruiseki.okmodular.api.recipe.expression.IExpression;
import ruiseki.okmodular.testsupport.TunableMachine;

/**
 * 払い出しの式が**開始時の性能**を読むこと。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * 完成時に評価する式（レシピの chance、デコレータ、出力の nbt）は完成時の機械を読んでいた。
 * 低 Tier でレシピを始め、完成直前に構造体を高 Tier へ組み替えると、
 * 低 Tier の入力で高 Tier の払い出しが得られる。
 *
 * 払い出しの入口は `produceOutputs` の 1 か所なので、そこで文脈に開始時の写しを付ける。
 * 成功の抽選（`producesOutput`）とデコレータ（`produceExtraOutputs`）の両方が
 * その文脈を受け取ることを、式を記録するデコレータで確かめる。
 */
@DisplayName("払い出しは開始時の性能を読む")
public class PayoutReadsStartedPerformanceTest {

    private static final String GROUP = "payout-reads-started-performance";
    private static final List<IModularPort> NO_PORTS = Collections.emptyList();

    private TunableMachine machine;
    private Recording recipe;

    /** 払い出しの各段で `tier` を評価して記録するデコレータ。 */
    private static final class Recording extends RecipeDecorator {

        private static final IExpression TIER = ExpressionParser.parseExpression("tier");

        final List<Double> atDraw = new ArrayList<>();
        final List<Double> atExtras = new ArrayList<>();

        Recording(IModularRecipe internal) {
            super(internal);
        }

        @Override
        public boolean producesOutput(ConditionContext context) {
            atDraw.add(TIER.evaluateDouble(context));
            return super.producesOutput(context);
        }

        @Override
        public void produceExtraOutputs(List<IModularPort> outputPorts, ConditionContext context) {
            atExtras.add(TIER.evaluateDouble(context));
            super.produceExtraOutputs(outputPorts, context);
        }
    }

    @BeforeEach
    public void setUp() {
        machine = new TunableMachine();
        recipe = new Recording(
            ModularRecipe.builder()
                .registryName(GROUP + ":run")
                .recipeGroup(GROUP)
                .name("run")
                .duration(1)
                .build());
        RecipeLoader.getInstance()
            .addRecipe(GROUP, recipe);
    }

    @AfterEach
    public void tearDown() {
        RecipeLoader.getInstance()
            .clearGroup(GROUP);
    }

    private static void finish(ProcessAgent agent, ConditionContext context) {
        agent.tick(NO_PORTS, NO_PORTS, context);
        assertTrue(agent.isWaitingForOutput(), "前提: 1 tick で完成していること");
        assertTrue(agent.tryOutput(NO_PORTS, context), "前提: 払い出しが済むこと");
    }

    @Test
    @DisplayName("【本題】完成前に組み替えても開始時の Tier で払い出す")
    public void test組み替えても開始時の値() {
        ProcessAgent agent = new ProcessAgent(null);
        machine.tier = 1;
        assertTrue(agent.startRecipe(recipe, NO_PORTS, NO_PORTS, machine.context()));

        machine.tier = 5;
        finish(agent, machine.context());

        assertEquals(List.of(1.0), recipe.atDraw, "成功の抽選が組み替え後の機械を読んだ");
        assertEquals(List.of(1.0), recipe.atExtras, "デコレータが組み替え後の機械を読んだ");
    }

    @Test
    @DisplayName("セーブとロードを跨いでも開始時の Tier で払い出す")
    public void testロードを跨ぐ() {
        ProcessAgent before = new ProcessAgent(null);
        machine.tier = 1;
        assertTrue(before.startRecipe(recipe, NO_PORTS, NO_PORTS, machine.context()));

        NBTTagCompound saved = new NBTTagCompound();
        before.writeToNBT(saved);
        ProcessAgent after = new ProcessAgent(null);
        after.readFromNBT(saved);
        assertTrue(after.isRunning(), "前提: ロード後も実行中であること");

        machine.tier = 5;
        finish(after, machine.context());

        assertEquals(List.of(1.0), recipe.atExtras, "ロードで写しが消え、組み替え後の機械を読んだ");
    }

    @Test
    @DisplayName("次のレシピは次の開始時の Tier で払い出す")
    public void test次のレシピ() {
        ProcessAgent agent = new ProcessAgent(null);
        machine.tier = 1;
        agent.startRecipe(recipe, NO_PORTS, NO_PORTS, machine.context());
        finish(agent, machine.context());

        machine.tier = 5;
        assertTrue(agent.startRecipe(recipe, NO_PORTS, NO_PORTS, machine.context()));
        finish(agent, machine.context());

        assertEquals(List.of(1.0, 5.0), recipe.atExtras, "前のレシピの写しが残っている");
    }
}
