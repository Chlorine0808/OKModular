package ruiseki.okmodular.api.recipe.parser;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import ruiseki.okmodular.api.condition.Conditions;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.decorator.BonusOutputDecorator;
import ruiseki.okmodular.api.recipe.decorator.ChanceRecipeDecorator;
import ruiseki.okmodular.api.recipe.decorator.HarvestBlockDecorator;
import ruiseki.okmodular.api.recipe.decorator.WeightedRandomDecorator;
import ruiseki.okmodular.api.recipe.expression.MapRangeExpression;

/**
 * DecoratorParser が受け付ける 3 つの記述形式の検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * ドキュメント（JSON_FORMAT.md / SAMPLE_MACHINES.md）はネスト形式
 * { "bonus": { ... } } を仕様として書いているのに、パーサは "type" 明示と
 * 平坦なプロパティ推論しか見ていなかった。サンプルレシピが丸ごと捨てられ、
 * それを捕まえるテストも無かった。
 *
 * 特に重要なのは「ネスト形式と、値がオブジェクトである平坦形の区別」。
 * { "chance": { "type": "map_range", ... } } は chance decorator に式を
 * 渡す形であって、ネスト宣言ではない。ここを取り違えると式が黙って失われる。
 *
 * ============================================
 */
@DisplayName("Decorator パーサ: 3 つの記述形式")
public class DecoratorParserTest {

    @BeforeAll
    public static void setUpAll() {
        // requirement decorator が条件をパースするのに必要
        Conditions.registerDefaults();
    }

    private static IModularRecipe decorate(String json) {
        IModularRecipe base = ModularRecipe.builder()
            .registryName("test_recipe")
            .recipeGroup("test")
            .build();
        return DecoratorParser.parse(base, new JsonParser().parse(json));
    }

    // ========================================
    // 3 つの記述形式
    // ========================================

    @Test
    @DisplayName("【形式1】type を明示した形式")
    public void testType明示形式() {
        IModularRecipe result = decorate("{ \"type\": \"chance\", \"chance\": 0.5 }");
        assertInstanceOf(ChanceRecipeDecorator.class, result);
    }

    @Test
    @DisplayName("【形式2】キーで型を名指しするネスト形式（ドキュメントの記法）")
    public void testネスト形式() {
        IModularRecipe result = decorate(
            "{ \"bonus\": { \"chance\": 0.3, \"outputs\": [ { \"item\": \"minecraft:gold_nugget\", \"amount\": 3 } ] } }");

        assertInstanceOf(BonusOutputDecorator.class, result, "ネスト形式が bonus として解決されるべき");
        assertEquals(
            1,
            ((BonusOutputDecorator) result).getBonusOutputs()
                .size(),
            "内側の outputs が読まれているべき");
    }

    @Test
    @DisplayName("【形式3】プロパティからの推論")
    public void test平坦推論() {
        IModularRecipe result = decorate("{ \"chance\": 0.6 }");
        assertInstanceOf(ChanceRecipeDecorator.class, result);
    }

    // ========================================
    // ネスト形式と紛らわしいもの（回帰防止）
    // ========================================

    @Test
    @DisplayName("【回帰防止】chance に式オブジェクトを渡してもネスト宣言と誤認しない")
    public void testChanceに式オブジェクトを渡してもネストと誤認しない() {
        // "chance" は登録済みの型名で値もオブジェクトなので、内側が chance の
        // detector を満たすかを見ないとネスト宣言と読み違える
        IModularRecipe result = decorate(
            "{ \"chance\": { \"type\": \"map_range\", \"input\": { \"type\": \"constant\", \"value\": 50 },"
                + " \"minIn\": 0, \"maxIn\": 100, \"minOut\": 0, \"maxOut\": 1, \"clamp\": true } }");

        assertInstanceOf(ChanceRecipeDecorator.class, result, "chance decorator であるべき");
        assertInstanceOf(
            MapRangeExpression.class,
            ((ChanceRecipeDecorator) result).getChanceExpression(),
            "式が失われていないべき");
    }

    @Test
    @DisplayName("【回帰防止】weight を持たない outputs は bonus として読まれる")
    public void testWeightのないOutputsはBonusになる() {
        // weighted_random は bonus より先に detector を試されるので、
        // weight の有無で区別できていないと bonus が weighted_random に化ける
        IModularRecipe result = decorate(
            "{ \"chance\": 0.3, \"outputs\": [ { \"item\": \"minecraft:gold_nugget\", \"amount\": 1 } ] }");

        assertInstanceOf(BonusOutputDecorator.class, result);
    }

    // ========================================
    // type 名の解決
    // ========================================

    @Test
    @DisplayName("snake_case と旧 camelCase が同じ decorator に解決する")
    public void testSnakeCaseとCamelCaseが同じDecoratorに解決する() {
        String properties = "\"fortune\": 3, \"harvestLevel\": 2";

        assertInstanceOf(
            HarvestBlockDecorator.class,
            decorate("{ \"type\": \"harvest_block\", " + properties + " }"),
            "snake_case が正名");
        assertInstanceOf(
            HarvestBlockDecorator.class,
            decorate("{ \"type\": \"harvest\", " + properties + " }"),
            "旧 camelCase もエイリアスとして残っているべき");
    }

    @Test
    @DisplayName("未知の type 名でもプロパティから推論する")
    public void test未知のType名でもプロパティから推論する() {
        IModularRecipe result = decorate("{ \"type\": \"no_such_decorator\", \"chance\": 0.5 }");
        assertInstanceOf(ChanceRecipeDecorator.class, result, "型名が誤っていても内容から解決されるべき");
    }

    @Test
    @DisplayName("どの形式でも解決できなければ例外を投げる")
    public void test解決できなければ例外() {
        assertThrows(IllegalArgumentException.class, () -> decorate("{ \"no_such_property\": 1 }"));
    }

    // ========================================
    // weighted_random の 2 形式
    // ========================================

    @Test
    @DisplayName("【weighted_random】outputs 形式（レシピが実際に使う記法）")
    public void testWeightedRandomのOutputs形式() {
        IModularRecipe result = decorate(
            "{ \"type\": \"weighted_random\", \"outputs\": [ { \"weight\": 70, \"item\": \"minecraft:flint\", \"amount\": 1 },"
                + " { \"weight\": 30, \"item\": \"minecraft:gravel\", \"amount\": 1 } ] }");

        assertInstanceOf(WeightedRandomDecorator.class, result);
        WeightedRandomDecorator weighted = (WeightedRandomDecorator) result;
        assertEquals(
            2,
            weighted.getPool()
                .size(),
            "2 エントリが読まれるべき");
        assertEquals(1, weighted.getRolls(), "rolls 省略時は 1 回");
    }

    @Test
    @DisplayName("【weighted_random】旧 pool 形式も読める")
    public void testWeightedRandomのPool形式() {
        IModularRecipe result = decorate(
            "{ \"type\": \"weighted_random\", \"rolls\": 2,"
                + " \"pool\": [ { \"weight\": 1, \"output\": { \"item\": \"minecraft:flint\", \"amount\": 1 } } ] }");

        assertInstanceOf(WeightedRandomDecorator.class, result);
        WeightedRandomDecorator weighted = (WeightedRandomDecorator) result;
        assertEquals(
            1,
            weighted.getPool()
                .size());
        assertEquals(2, weighted.getRolls());
    }

    @Test
    @DisplayName("【weighted_random】type 省略でも weight があれば推論できる")
    public void testWeightedRandomは推論もできる() {
        IModularRecipe result = decorate(
            "{ \"outputs\": [ { \"weight\": 50, \"item\": \"minecraft:coal\", \"amount\": 1 } ] }");

        assertInstanceOf(WeightedRandomDecorator.class, result);
    }

    // ========================================
    // 複数 decorator の積み重ね
    // ========================================

    @Test
    @DisplayName("配列で渡すと順に積み重なる")
    public void test配列で積み重なる() {
        // 外側が最後に適用されるので、結果の型は配列の末尾のもの
        IModularRecipe result = decorate(
            "[ { \"chance\": 0.25 },"
                + " { \"bonus\": { \"chance\": 0.05, \"outputs\": [ { \"item\": \"minecraft:nether_star\", \"amount\": 1 } ] } } ]");

        assertInstanceOf(BonusOutputDecorator.class, result, "末尾の bonus が最も外側になるべき");
    }
}
