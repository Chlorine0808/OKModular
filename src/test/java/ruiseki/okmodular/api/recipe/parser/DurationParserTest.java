package ruiseki.okmodular.api.recipe.parser;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonPrimitive;

import ruiseki.okmodular.api.recipe.core.DurationPolicy;
import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.parser.impl.DurationParser;

/**
 * duration の読み取りと、式を静的値に畳む処理の検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * DurationParser は渡されたものに無条件で getAsInt() をかけていたので、
 * 文字列が来ると NumberFormatException でレシピが丸ごと捨てられていた。
 * json_errors.txt の 5 件中 3 件がこれ（sample_03_alloy_forge.json）。
 *
 * 畳み込みの成否が NEI の表示を分ける:
 * - 定数に畳めた → 式を捨てて静的値だけ持つ → NEI は秒数を出せる
 * - 畳めなかった → 式を保持する → NEI は式をそのまま出す
 *
 * ============================================
 */
@DisplayName("duration: 数値と式")
public class DurationParserTest {

    private static ModularRecipe parse(JsonPrimitive value) {
        ModularRecipe.Builder builder = ModularRecipe.builder()
            .registryName("test_recipe")
            .recipeGroup("test");
        new DurationParser().parse(builder, value);
        return builder.build();
    }

    @Test
    @DisplayName("数値の duration はそのまま読まれる")
    public void test数値() {
        ModularRecipe recipe = parse(new JsonPrimitive(200));

        assertEquals(200, recipe.getDuration());
        assertNull(recipe.getDurationExpression(), "数値に式は要らない");
    }

    @Test
    @DisplayName("【回帰防止】文字列の duration でレシピが捨てられない")
    public void test文字列でも例外にならない() {
        // 以前はここで NumberFormatException が出て、レシピごと捨てられていた
        ModularRecipe recipe = parse(new JsonPrimitive("floor(200 / 2)"));

        assertNotNull(recipe);
        assertEquals(100, recipe.getDuration(), "定数式は畳まれて静的値になるべき");
    }

    @Test
    @DisplayName("定数に畳める式は式を持たない")
    public void test定数式は畳まれる() {
        ModularRecipe recipe = parse(new JsonPrimitive("50 * 4"));

        assertEquals(200, recipe.getDuration());
        assertNull(recipe.getDurationExpression(), "定数に畳めたなら式を保持する意味がない（NEI が秒数を出せる）");
    }

    @Test
    @DisplayName("マシン依存の式は式として保持される")
    public void testマシン依存式は保持される() {
        ModularRecipe recipe = parse(new JsonPrimitive("floor(200 / tier)"));

        assertNotNull(recipe.getDurationExpression(), "評価に context が要る式は保持されるべき");
        assertEquals(
            "floor((200 / tier))",
            recipe.getDurationExpression()
                .toString(),
            "式は再パースできる形で表現されるべき");
    }

    @Test
    @DisplayName("マシン依存の式でも静的な控え値を持つ")
    public void testマシン依存式でも静的値がある() {
        ModularRecipe recipe = parse(new JsonPrimitive("floor(200 / tier)"));

        // NEI や RecipeValidationVisitor は context を持たずに getDuration() を呼ぶ。
        // 0 だとレシピが即完成扱いになるので、1 以上でなければならない
        assertTrue(recipe.getDuration() > 0, "context 無しでも 0 より大きい値を返すべき");
    }

    @Test
    @DisplayName("context が無ければ静的値を返す")
    public void testcontextなしでは静的値() {
        ModularRecipe recipe = parse(new JsonPrimitive(200));

        assertEquals(200, recipe.getDuration(null));
    }

    @Test
    @DisplayName("policy 名は表記ゆれを吸収する")
    public void testPolicy名の表記ゆれ() {
        assertEquals(DurationPolicy.PER_TICK, DurationPolicy.fromString("perTick", DurationPolicy.ON_START));
        assertEquals(DurationPolicy.PER_TICK, DurationPolicy.fromString("per_tick", DurationPolicy.ON_START));
        assertEquals(DurationPolicy.PER_TICK, DurationPolicy.fromString("PER_TICK", DurationPolicy.ON_START));
        assertEquals(DurationPolicy.ON_START, DurationPolicy.fromString("onStart", DurationPolicy.PER_TICK));
    }

    @Test
    @DisplayName("未知の policy 名は既定値に落ちる")
    public void test未知のPolicy名() {
        assertEquals(DurationPolicy.ON_START, DurationPolicy.fromString("nonsense", DurationPolicy.ON_START));
        assertEquals(DurationPolicy.ON_START, DurationPolicy.fromString(null, DurationPolicy.ON_START));
        assertNull(DurationPolicy.fromString("nonsense", null), "既定値に null を渡せば判別できるべき");
    }
}
