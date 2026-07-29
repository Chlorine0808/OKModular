package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import ruiseki.okmodular.api.condition.Conditions;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.io.EnergyInput;
import ruiseki.okmodular.api.recipe.io.IRecipeInput;
import ruiseki.okmodular.api.recipe.parser.DecoratorParser;
import ruiseki.okmodular.api.recipe.visitor.IRecipeVisitor;

/**
 * requirement decorator の検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * decorator の `requirements` は decorator_comprehensive_test.json が
 * 要求していたのに実装が無く、レシピ 2 件が捨てられていた。
 *
 * 「requirement」は同名で別物が 2 つある点に注意:
 * - 構造 JSON の requirements = ポート数の指定（IStructureRequirement）
 * - decorator の requirements = 消費しないリソース要件（こちら）
 *
 * 実装は「非消費入力を getInputs() に足す」形。エンジンは既に
 * consume=false の入力を開始時と毎 tick チェックするので、その経路に乗る。
 * 成立には RecipeDecorator.accept が visitor に自分を渡す必要がある
 * （internal を渡すと decorator が足した入力が見えない）。
 *
 * ============================================
 */
@DisplayName("requirement decorator")
public class RequirementDecoratorTest {

    @BeforeAll
    public static void setUpAll() {
        Conditions.registerDefaults();
    }

    private static IModularRecipe decorate(String json) {
        IModularRecipe base = ModularRecipe.builder()
            .registryName("test_recipe")
            .recipeGroup("test")
            .addInput(new EnergyInput(100, true))
            .build();
        return DecoratorParser.parse(base, new JsonParser().parse(json));
    }

    /** visitor から見えた入力を数える。エンジンが入力を辿る経路と同じ。 */
    private static int countInputsSeenByVisitor(IModularRecipe recipe) {
        int[] seen = { 0 };
        recipe.accept(new IRecipeVisitor() {

            @Override
            public void visit(IRecipeInput input) {
                seen[0]++;
            }
        });
        return seen[0];
    }

    @Test
    @DisplayName("【回帰防止】requirements がレシピの入力に追加される")
    public void testRequirementsが入力に追加される() {
        IModularRecipe recipe = decorate("{ \"type\": \"requirement\", \"requirements\": [ { \"energy\": 10000 } ] }");

        assertInstanceOf(RequirementDecorator.class, recipe);
        assertEquals(
            2,
            recipe.getInputs()
                .size(),
            "元の入力 1 件 + requirement 1 件");
    }

    @Test
    @DisplayName("requirements は消費されない（触媒）")
    public void testRequirementsは消費されない() {
        IModularRecipe recipe = decorate("{ \"type\": \"requirement\", \"requirements\": [ { \"energy\": 10000 } ] }");

        IRecipeInput requirement = ((RequirementDecorator) recipe).getRequirements()
            .get(0);
        assertFalse(requirement.isConsume(), "触媒なので消費されてはいけない");
    }

    @Test
    @DisplayName("requirements に consume: true と書いても消費されない")
    public void testConsumeTrueを書いても消費されない() {
        // 消費する追加入力が欲しいなら inputs に書くべきで、そちらなら
        // 読んだときに消費されると分かる。ここで許すと紛らわしい
        IModularRecipe recipe = decorate(
            "{ \"type\": \"requirement\", \"requirements\": [ { \"energy\": 10000, \"consume\": true } ] }");

        assertFalse(
            ((RequirementDecorator) recipe).getRequirements()
                .get(0)
                .isConsume(),
            "requirements 側の consume 指定は無視されるべき");
    }

    @Test
    @DisplayName("【要】visitor が追加された入力を見られる")
    public void testVisitorが追加入力を見られる() {
        // RecipeDecorator.accept が internal を渡していると、ここが 1 になる。
        // エンジンは visitor 経由で入力をチェックするので、見えなければ
        // requirement は存在しないのと同じ
        IModularRecipe recipe = decorate("{ \"type\": \"requirement\", \"requirements\": [ { \"energy\": 10000 } ] }");

        assertEquals(2, countInputsSeenByVisitor(recipe), "decorator が足した入力も visitor に見えるべき");
    }

    @Test
    @DisplayName("requirements を持たない decorator は入力を変えない")
    public void test条件だけなら入力は変わらない() {
        IModularRecipe recipe = decorate("{ \"type\": \"requirement\", \"condition\": { \"dimension\": 0 } }");

        assertEquals(
            1,
            recipe.getInputs()
                .size());
        assertEquals(1, countInputsSeenByVisitor(recipe), "他の decorator の visitor 経路を壊していないべき");
    }

    @Test
    @DisplayName("condition と requirements は併用できる")
    public void testConditionとRequirementsを併用できる() {
        IModularRecipe recipe = decorate(
            "{ \"type\": \"requirement\", \"condition\": { \"dimension\": 0 },"
                + " \"requirements\": [ { \"energy\": 10000 } ] }");

        RequirementDecorator decorator = (RequirementDecorator) recipe;
        assertNotNull(decorator.getExtraCondition());
        assertEquals(
            1,
            decorator.getRequirements()
                .size());
        assertEquals(
            2,
            recipe.getInputs()
                .size());
    }

    @Test
    @DisplayName("複数の requirements を書ける")
    public void test複数のRequirements() {
        IModularRecipe recipe = decorate(
            "{ \"type\": \"requirement\", \"requirements\": [ { \"energy\": 10000 }, { \"mana\": 500 } ] }");

        assertEquals(
            2,
            ((RequirementDecorator) recipe).getRequirements()
                .size());
        assertEquals(3, countInputsSeenByVisitor(recipe));
    }

    @Test
    @DisplayName("type 省略でも requirements から推論できる")
    public void testTypeを省略しても推論される() {
        IModularRecipe recipe = decorate("{ \"requirements\": [ { \"energy\": 10000 } ] }");

        assertInstanceOf(RequirementDecorator.class, recipe);
    }

    @Test
    @DisplayName("condition も requirements も無ければ例外")
    public void test両方無ければ例外() {
        assertThrows(IllegalArgumentException.class, () -> decorate("{ \"type\": \"requirement\" }"));
    }

    @Test
    @DisplayName("requirements が配列でなければ例外")
    public void test配列でなければ例外() {
        assertThrows(
            IllegalArgumentException.class,
            () -> decorate("{ \"type\": \"requirement\", \"requirements\": { \"energy\": 10000 } }"));
    }

    @Test
    @DisplayName("元のレシピの入力リストは変更されない")
    public void test元の入力リストを壊さない() {
        ModularRecipe base = ModularRecipe.builder()
            .registryName("test_recipe")
            .recipeGroup("test")
            .addInput(new EnergyInput(100, true))
            .build();
        List<IRecipeInput> before = base.getInputs();

        DecoratorParser.parse(
            base,
            new JsonParser().parse("{ \"type\": \"requirement\", \"requirements\": [ { \"energy\": 10000 } ] }"));

        assertEquals(1, before.size(), "decorator は元のレシピの入力を書き換えてはいけない");
        assertEquals(
            1,
            base.getInputs()
                .size());
    }
}
