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
 * `{"chance": 0.6}` デコレータがいつ判定されるか。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `isConditionMet` を上書きして抽選していた。この呼び出しは
 * **稼働中も毎 tick 走る**ので、確率 0.6 の 150 tick のレシピは
 * 0.6 の 150 乗 ≒ 0 でしか完走できなかった。
 * docs が言う「成功率」は**レシピ 1 回に対する確率**なので、実装は仕様と別物だった。
 *
 * 開始時だけ判定する `canStart` に移した。`isConditionMet` は素通しになるので、
 * **一度始まったレシピは抽選のせいで止まらない。**
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

    @Test
    @DisplayName("確率 1 なら必ず開始できる")
    public void test確率1() {
        for (long seed = 0; seed < 200; seed++) {
            assertTrue(withChance(1.0).canStart(context(seed)), "seed=" + seed);
        }
    }

    @Test
    @DisplayName("確率 0 なら決して開始できない")
    public void test確率0() {
        for (long seed = 0; seed < 200; seed++) {
            assertFalse(withChance(0.0).canStart(context(seed)), "seed=" + seed);
        }
    }

    @Test
    @DisplayName("【回帰防止】稼働中は抽選しない")
    public void test稼働中は抽選しない() {
        // ここが本題。確率 0 でも、始まったレシピは条件として真であり続ける。
        IModularRecipe recipe = withChance(0.0);

        for (long seed = 0; seed < 200; seed++) {
            assertTrue(recipe.isConditionMet(context(seed)), "稼働中に抽選している。長いレシピが完走できなくなる");
        }
    }

    @Test
    @DisplayName("同じ状況なら同じ答え")
    public void test決定的である() {
        IModularRecipe recipe = withChance(0.5);

        assertEquals(recipe.canStart(context(12345)), recipe.canStart(context(12345)));
    }

    @Test
    @DisplayName("種が変われば答えも変わる")
    public void test種で変わる() {
        IModularRecipe recipe = withChance(0.5);
        Set<Boolean> seen = new HashSet<>();

        for (long seed = 0; seed < 200; seed++) {
            seen.add(recipe.canStart(context(seed)));
        }

        assertEquals(Set.of(true, false), seen, "全部同じ答えになっている");
    }

    @Test
    @DisplayName("中の条件が偽なら抽選に関係なく開始できない")
    public void test内側の条件が優先() {
        // 中の条件を見ずに抽選だけで通してはいけない。
        ModularRecipe blocked = ModularRecipe.builder()
            .registryName("test")
            .recipeGroup("test")
            .name("test")
            .duration(100)
            .addCondition(new ICondition() {

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
            })
            .build();

        assertFalse(new ChanceRecipeDecorator(blocked, new ConstantExpression(1.0)).canStart(context(1)));
    }
}
