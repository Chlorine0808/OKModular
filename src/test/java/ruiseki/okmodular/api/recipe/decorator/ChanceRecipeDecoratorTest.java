package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ICondition;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.expression.ConstantExpression;

/**
 * `{"chance": 0.6}` デコレータが**何を**左右するか。
 *
 * ============================================
 * なぜこのテストがあるか — 抽選は 2 回引っ越している
 * ============================================
 *
 * **1 回目**: `isConditionMet` で抽選していた。この呼び出しは**稼働中も毎 tick 走る**ので、
 * 確率 0.6 の 150 tick のレシピは 0.6 の 150 乗 ≒ 0 でしか完走できなかった。
 *
 * **2 回目**: `canStart` に移した。長いレシピは完走するようになったが、今度は
 * **外れても何も失わない**。機械は次の tick に新しい評価種でもう一度引くので、
 * 確率 0.25 のレシピは「数 tick 遅れて始まり、そして必ず完走する」。
 * 実機で `sample_05` の 0.25 レシピが**毎回完走した**のがこれ。
 * **外れが無料なら 0.25 と 1.0 は区別できない。**
 *
 * **3 回目（今）**: `producesOutput` に移した。レシピは走り、入力は消え、時間も経つ。
 * 抽選が決めるのは**出力を渡すかどうか**。docs が言う「成功率」はこれで、
 * 数字が観測できる置き場所はここしかない。
 *
 * ============================================
 * 1 回のレシピにつき 1 回であること
 * ============================================
 *
 * 機械の評価種はレシピ開始時に固定され、次のレシピが始まるまで動かない。
 * だから**完成時に引いても開始時に引いたのと同じ答え**になる。
 * ここでは「同じ context なら同じ答え」で縛る。
 *
 * ============================================
 * 抽選を決定的にした理由
 * ============================================
 *
 * `new Random()` のインスタンスを**レシピ定義が持っていた**ので、
 * 同じレシピを回す全機械が 1 本の乱数列を共有していた。
 * 評価の種から引けば機械ごと・レシピごとに独立し、同じ状況で同じ答えになる
 * （`random()` / `chance()` と同じ扱い）。
 *
 * ============================================
 */
@DisplayName("chance デコレータ")
public class ChanceRecipeDecoratorTest {

    private static ModularRecipe bare() {
        return ModularRecipe.builder()
            .registryName("test")
            .recipeGroup("test")
            .name("test")
            .duration(100)
            .build();
    }

    private static IModularRecipe withChance(double chance) {
        return new ChanceRecipeDecorator(bare(), new ConstantExpression(chance));
    }

    private static ConditionContext context(long seed) {
        return new ConditionContext(null, 0, 0, 0, null, seed);
    }

    private static ICondition never() {
        return new ICondition() {

            @Override
            public boolean isMet(ConditionContext context) {
                return false;
            }

            @Override
            public String getDescription() {
                return "never";
            }

            @Override
            public void write(JsonObject json) {}
        };
    }

    @Test
    @DisplayName("確率 1 なら必ず出力する")
    public void test確率1() {
        for (long seed = 0; seed < 200; seed++) {
            assertTrue(withChance(1.0).producesOutput(context(seed)), "seed=" + seed);
        }
    }

    @Test
    @DisplayName("確率 0 なら決して出力しない")
    public void test確率0() {
        for (long seed = 0; seed < 200; seed++) {
            assertFalse(withChance(0.0).producesOutput(context(seed)), "seed=" + seed);
        }
    }

    @Test
    @DisplayName("【回帰防止】稼働中は抽選しない")
    public void test稼働中は抽選しない() {
        // 1 回目の引っ越しの回帰防止。確率 0 でも、始まったレシピは条件として真であり続ける。
        IModularRecipe recipe = withChance(0.0);

        for (long seed = 0; seed < 200; seed++) {
            assertTrue(recipe.isConditionMet(context(seed)), "稼働中に抽選している。長いレシピが完走できなくなる");
        }
    }

    @Test
    @DisplayName("【回帰防止】開始は抽選で止めない")
    public void test開始では抽選しない() {
        // 2 回目の引っ越しの回帰防止。**ここが今回のバグの本体。**
        // 開始を止めると、機械は次の tick に新しい種で引き直すだけなので、
        // 外れが実質「数 tick の遅れ」にしかならず、確率が観測できなくなる。
        IModularRecipe recipe = withChance(0.0);

        for (long seed = 0; seed < 200; seed++) {
            assertTrue(recipe.canStart(context(seed)), "開始を抽選で止めている。外れが無料になるので確率が効かない");
        }
    }

    @Test
    @DisplayName("同じ状況なら同じ答え — レシピ 1 回につき 1 回であることの担保")
    public void test決定的である() {
        IModularRecipe recipe = withChance(0.5);

        assertEquals(recipe.producesOutput(context(12345)), recipe.producesOutput(context(12345)));
    }

    @Test
    @DisplayName("種が変われば答えも変わる")
    public void test種で変わる() {
        IModularRecipe recipe = withChance(0.5);
        Set<Boolean> seen = new HashSet<>();

        for (long seed = 0; seed < 200; seed++) {
            seen.add(recipe.producesOutput(context(seed)));
        }

        assertEquals(Set.of(true, false), seen, "全部同じ答えになっている");
    }

    @Test
    @DisplayName("確率どおりの割合で当たる")
    public void test割合が確率に従う() {
        // 「毎回完走する」を数で捕まえる。開始を止める実装だと、機械は外れた種を
        // 捨てて次の種で引き直せてしまうので、この比率が実機に出てこなかった。
        IModularRecipe recipe = withChance(0.25);

        int hits = 0;
        for (long seed = 0; seed < 4000; seed++) {
            if (recipe.producesOutput(context(seed))) hits++;
        }

        double rate = hits / 4000.0;
        assertTrue(rate > 0.22 && rate < 0.28, "0.25 のはずが " + rate);
    }

    @Test
    @DisplayName("中の条件が偽なら開始できない（抽選とは別の話）")
    public void test内側の条件が優先() {
        ModularRecipe blocked = ModularRecipe.builder()
            .registryName("test")
            .recipeGroup("test")
            .name("test")
            .duration(100)
            .addCondition(never())
            .build();

        assertFalse(new ChanceRecipeDecorator(blocked, new ConstantExpression(1.0)).canStart(context(1)));
    }

    @Test
    @DisplayName("内側が出力しないなら抽選に関係なく出力しない")
    public void test内側の出力可否が優先() {
        // 抽選だけ見て通してはいけない。chance を 2 枚重ねたときの片方が外れた場合など。
        IModularRecipe inner = withChance(0.0);

        assertFalse(new ChanceRecipeDecorator(inner, new ConstantExpression(1.0)).producesOutput(context(1)));
    }
}
