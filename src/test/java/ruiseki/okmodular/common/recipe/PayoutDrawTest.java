package ruiseki.okmodular.common.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.decorator.RecipeDecorator;
import ruiseki.okmodular.api.recipe.io.IModularRecipeOutput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.visitor.IRecipeVisitor;

/**
 * 「このレシピは成果物を渡すか」の抽選が、**1 回のレシピにつき 1 回**であることの検証。
 *
 * ============================================
 * 抽選が、やり直す側のメソッドの中にある
 * ============================================
 *
 * `produceOutputs` は 2 つの仕事をしています。
 *
 * <pre>
 * produceOutputs()
 *   1. 成果物を渡すか抽選する        ← chance デコレータ
 *   2. 出力先に空きがあるか見る       ← 満杯なら false を返す
 *   3. 置く
 * </pre>
 *
 * **2 で失敗すると、次の tick に 1 からやり直します。** 出力先が詰まっている間ずっと。
 * 誰もそう設計したわけではなく、呼び出しがそこに置かれているだけです。
 *
 * ============================================
 * 固定値の chance は助かっていた。式は助からない
 * ============================================
 *
 * 評価の種はレシピ実行中は動かないので、`"chance": 0.6` は何度聞いても同じ答えでした。
 * **`"chance": "energy_p"` は違います。** 待っている間もエネルギーは動くので、
 * 完成した tick では勝っていた実行が、次の tick では負けうる。
 *
 * そして**負けは「何も出さずに完了」**です。入力はとっくに消えている。
 * 詰まっている時間が長いほど当たりにくくなる、という誰も設計していない挙動でした。
 *
 * ============================================
 * ここで縛ること
 * ============================================
 *
 * 1 回だけ聞くこと。そして**聞いた答えを覚えていること**（答えが後から変わっても揺らがない）。
 */
@DisplayName("成果物を渡すかの抽選")
public class PayoutDrawTest {

    private static final List<IModularPort> NO_PORTS = Collections.emptyList();

    private ProcessAgent agent;

    /**
     * 答えを外から変えられるレシピ。**式で書かれた `chance` の模型**で、
     * 待っている間に機械の状態が動いたのと同じことが起きる。
     */
    private static final class Flipping extends RecipeDecorator {

        int asked;
        boolean answer = true;

        Flipping(IModularRecipe internal) {
            super(internal);
        }

        @Override
        public boolean producesOutput(ConditionContext context) {
            asked++;
            return answer;
        }
    }

    /** 決して空きが無い出力。これが `produceOutputs` をやり直させる。 */
    private static final class AlwaysFull implements IModularRecipeOutput {

        @Override
        public IPortType.Type getPortType() {
            return IPortType.Type.ITEM;
        }

        @Override
        public boolean checkCapacity(List<IModularPort> ports, int multiplier, ConditionContext context) {
            return false;
        }

        @Override
        public void apply(List<IModularPort> ports, int multiplier, ConditionContext context) {}

        @Override
        public int getInterval() {
            return 0;
        }

        @Override
        public IRecipeOutput copy() {
            return new AlwaysFull();
        }

        @Override
        public IRecipeOutput copy(int multiplier) {
            return new AlwaysFull();
        }

        @Override
        public void writeToNBT(NBTTagCompound nbt) {}

        @Override
        public void readFromNBT(NBTTagCompound nbt) {}

        @Override
        public long getRequiredAmount() {
            return 1;
        }

        @Override
        public void accept(IRecipeVisitor visitor) {}

        @Override
        public void read(JsonObject json) {}

        @Override
        public void write(JsonObject json) {}
    }

    private static ConditionContext context() {
        return new ConditionContext(null, 0, 0, 0, null, 4649L);
    }

    private static ModularRecipe bare() {
        return ModularRecipe.builder()
            .registryName("test")
            .recipeGroup("test")
            .name("test")
            .duration(1)
            .build();
    }

    @BeforeEach
    public void setUp() {
        agent = new ProcessAgent(null);
    }

    /** 完成させて、出力先が詰まった状態に持っていく。 */
    private Flipping runUntilBlocked() {
        Flipping recipe = new Flipping(bare());
        assertTrue(agent.startRecipe(recipe, NO_PORTS, NO_PORTS, context()), "レシピが開始できていない");

        agent.tick(NO_PORTS, NO_PORTS, context());
        assertTrue(agent.isWaitingForOutput(), "duration 1 で完成していない。模型が本物とずれている");

        agent.addCachedOutput(new AlwaysFull());
        return recipe;
    }

    @Test
    @DisplayName("【回帰防止】詰まっている間に引き直さない")
    public void test引き直さない() {
        Flipping recipe = runUntilBlocked();

        for (int tick = 0; tick < 10; tick++) {
            assertFalse(agent.tryOutput(NO_PORTS, context()), "空きが無いのに払い出している");
        }

        assertEquals(1, recipe.asked, "詰まっている tick の数だけ抽選し直している");
    }

    @Test
    @DisplayName("【回帰防止】待っている間に答えが変わっても覆らない")
    public void test答えを覚えている() {
        // 式で書いた chance が、待機中に動いた機械の状態を読んで負けに転じた形。
        // 覚えていないと「詰まっていたせいで成果物が消えた」になる。
        Flipping recipe = runUntilBlocked();

        agent.tryOutput(NO_PORTS, context()); // 当たりで確定
        recipe.answer = false; // 機械の状態が動いた

        for (int tick = 0; tick < 10; tick++) {
            assertFalse(agent.tryOutput(NO_PORTS, context()), "まだ空きは無いので払い出せないはず");
        }

        assertTrue(agent.isWaitingForOutput(), "外れに転んで「何も出さずに完了」している。入力はもう戻らない");
        assertEquals(1, recipe.asked, "答えを覚えていない");
    }

    @Test
    @DisplayName("外れた実行は待たずに終わる")
    public void test外れれば完了する() {
        // 外れは「渡すものが無い」なので、空きが無くても待ってはいけない。
        // ここで待つと、要りもしない空きを永久に待つ機械ができる。
        Flipping recipe = new Flipping(bare());
        recipe.answer = false;
        agent.startRecipe(recipe, NO_PORTS, NO_PORTS, context());
        agent.tick(NO_PORTS, NO_PORTS, context());
        agent.addCachedOutput(new AlwaysFull());

        assertTrue(agent.tryOutput(NO_PORTS, context()), "外れた実行が完了扱いになっていない");
        assertFalse(agent.isWaitingForOutput(), "渡すものが無いのに待っている");
    }

    @Test
    @DisplayName("次のレシピは引き直す")
    public void test次の実行では引き直す() {
        // 覚えるのは 1 回の実行の中だけ。持ち越すと確率が 1 回目で固定される。
        Flipping first = new Flipping(bare());
        first.answer = false;
        agent.startRecipe(first, NO_PORTS, NO_PORTS, context());
        agent.tick(NO_PORTS, NO_PORTS, context());
        agent.tryOutput(NO_PORTS, context());

        Flipping second = new Flipping(bare());
        agent.startRecipe(second, NO_PORTS, NO_PORTS, context());
        agent.tick(NO_PORTS, NO_PORTS, context());
        agent.tryOutput(NO_PORTS, context());

        assertEquals(1, second.asked, "次の実行で抽選していない。前回の答えを持ち越している");
    }
}
