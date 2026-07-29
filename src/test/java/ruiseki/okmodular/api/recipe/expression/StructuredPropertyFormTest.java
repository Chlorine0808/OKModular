package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.JsonObject;

/**
 * 構造化 JSON 形式 `{"type": "machine_property", "property": "..."}` の凍結。
 *
 * ============================================
 * これは 2 つ目の公開面である
 * ============================================
 *
 * 機械プロパティを書く道は **2 本ある**:
 *
 * 1. 式の文字列: `"energy > 100"`
 * → ExpressionParser → ExpressionRegistry で名前を解決
 * 2. 構造化 JSON: `{"type": "machine_property", "property": "energy"}`
 * → ExpressionsParser → MachinePropertyExpression.fromJson で **名前をそのまま渡す**
 *
 * **2 は ExpressionRegistry を経由しない。** 名前の正本は
 * MachinePropertyExpression 側の definitions テーブルであり、登録の有無を見ない。
 *
 * つまり ExpressionRegistry に登録されていない名前でも、構造化形式なら通る。
 * `power` `essentia_free` `vis_free` などがその状態にある
 * （式の文字列としては書けないが、構造化形式では読める）。
 *
 * ExpressionsParser は条件（ComparisonCondition）と decorator の chance / count
 * から呼ばれているので、これは実際に使える経路。
 *
 * ============================================
 * なぜ凍結が必要か
 * ============================================
 *
 * 資源種を Kind のループで生成する改修（B9）は definitions の作り方を
 * 書き換える。**ループが出す名前の集合が今と違えば、この経路が黙って壊れる**。
 * 式の文字列側だけを守っても足りない。
 *
 * 名前の追加は許す。**減ることだけを禁止する。**
 *
 * ============================================
 * 検出方法
 * ============================================
 *
 * StubMachineContext の機械状態は全メソッドが非ゼロを返すので、
 * **0 が返れば definitions に無かった**ということ。
 *
 * ============================================
 */
@DisplayName("構造化プロパティ形式")
public class StructuredPropertyFormTest {

    /**
     * MachinePropertyExpression の definitions のキー。
     * register 51 件 + alias 54 件を書き写したもの（重複除去後 104 件）。
     *
     * world_seed だけは除外している。定義が ctx.getRecipeContext().getWorld() を
     * null チェック無しで辿るため、ワールドの無いテストコンテキストでは
     * 評価できない。この名前は world_property 形式（null 安全）で読むのが正で、
     * そちらは ExpressionNameFreezeTest が凍結している。
     */
    // spotless:off
    private static final String[] DEFINITION_KEYS = {
        "batch", "batch_size", "count_recipe", "count_recipe_type", "count_recipe_types",
        "current_batch",
        "energy", "energy_capacity", "energy_f", "energy_free", "energy_max",
        "energy_multi", "energy_multiplier", "energy_p", "energy_per_tick", "energy_percent",
        "energy_stored", "energy_total",
        "essentia", "essentia_capacity", "essentia_f", "essentia_free", "essentia_max",
        "essentia_p", "essentia_percent",
        "facing",
        "fluid", "fluid_capacity", "fluid_f", "fluid_f_in", "fluid_f_out", "fluid_free",
        "fluid_in", "fluid_max", "fluid_out", "fluid_p", "fluid_percent", "fluid_stored",
        "fluid_total",
        "gas", "gas_capacity", "gas_f", "gas_f_in", "gas_f_out", "gas_free", "gas_in",
        "gas_max", "gas_out", "gas_p", "gas_percent", "gas_total",
        "is_running", "is_waiting",
        "item", "item_capacity", "item_f", "item_free", "item_max", "item_p",
        "item_percent", "item_space", "item_total",
        "mana", "mana_capacity", "mana_f", "mana_free", "mana_max", "mana_p",
        "mana_percent", "mana_stored", "mana_total",
        "multiplier_energy", "multiplier_speed",
        "power", "power_p",
        "progress", "progress_percent",
        "recipe_count", "recipe_types_count", "recipeprocessed", "recipeprocessedtype",
        "speed_multi", "speed_multiplier",
        "tier", "timecontinue", "timeplaced",
        "total_energy", "total_energy_capacity", "total_energy_max",
        "total_fluid", "total_fluid_capacity", "total_fluid_max",
        "total_gas",
        "total_mana", "total_mana_capacity", "total_mana_max",
        "vis", "vis_capacity", "vis_f", "vis_free", "vis_max", "vis_p", "vis_percent",
    };
    // spotless:on

    private static List<String> definitionKeys() {
        return List.of(DEFINITION_KEYS);
    }

    private static JsonObject machineProperty(String name) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "machine_property");
        json.addProperty("property", name);
        return json;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("definitionKeys")
    @DisplayName("構造化形式で書いたプロパティは値を返す")
    public void test構造化形式で値が読める(String name) {
        IExpression expr = ExpressionsParser.parse(machineProperty(name));

        assertNotNull(expr, () -> "'" + name + "' の構造化形式がパースできない");

        assertNotEquals(
            0.0,
            expr.evaluateDouble(StubMachineContext.withMachine()),
            () -> "'" + name + "' が 0 を返した。definitions からキーが消えている");
    }
}
