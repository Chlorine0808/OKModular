package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 機械プロパティが返す**値**の凍結。
 *
 * ============================================
 * なぜ名前の凍結だけでは足りないのか
 * ============================================
 *
 * ExpressionNameFreezeTest は「名前がパースできるか」、
 * MachinePropertyCoverageTest は「0 以外を返すか」しか見ていない。
 * どちらも **値が別の資源種のものに入れ替わったこと**を検出できない。
 *
 * 資源種を Kind のループに畳む改修（B9）は 50 個の定義を書き換える。
 * そのとき `gas_f` が誤って fluid の空きを返すようになっても、
 * 名前は残り、値は非ゼロなので、上の 2 本は緑のまま通ってしまう。
 *
 * よってここで **改修前の値をそのまま書き写す**。
 * スタブは資源種・方向・名前指定ごとに違う値を返すので、
 * 取り違えは必ず数値の不一致として出る。
 *
 * ============================================
 * 表の読み方
 * ============================================
 *
 * 期待値は StubMachineContext の定数から決まる:
 *
 * <pre>
 * energy 100/1000   mana 200/2000   fluid 300/3000   gas 400/4000
 * essentia 500/5000  vis 600/6000    item 700 個・空き 710・20 スロット
 * </pre>
 *
 * 下 1 桁が方向と名前指定を表す（+2 入力 / +4 出力、+1 で名前指定）。
 * たとえば `fluid_in` = 302 は「入力側の合計」を読んでいる証拠であり、
 * ここが 303 になったら「名前で引くメソッド」を呼んでいる。
 *
 * `*_p` が 0.1 に揃うのは、残量と容量を 1:10 に置いたから。
 * `item_p` だけ 0.546875 なのは、アイテムの容量がスロット数 × 64 で決まるため。
 *
 * ============================================
 * この表を書き換えてよいとき
 * ============================================
 *
 * **値が変わったら、まず疑うのは改修の側。** 表を実測値に合わせて
 * 書き換えるのは、意図した仕様変更だと確認できたときだけ。
 *
 * ============================================
 */
@DisplayName("機械プロパティの値の凍結")
public class MachinePropertyValueFreezeTest {

    // spotless:off
    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
        "batch, 4.0",
        "batch_size, 4.0",
        "count_recipe, 5.0",
        "count_recipe_type, 2.0",
        "count_recipe_types, 2.0",
        "current_batch, 4.0",
        "energy, 100.0",
        "energy_capacity, 1000.0",
        "energy_f, 900.0",
        "energy_free, 900.0",
        "energy_max, 1000.0",
        "energy_multi, 2.5",
        "energy_multiplier, 2.5",
        "energy_p, 0.1",
        "energy_per_tick, 7.0",
        "energy_percent, 0.1",
        "energy_stored, 100.0",
        "energy_total, 100.0",
        "essentia, 500.0",
        "essentia_capacity, 5000.0",
        "essentia_f, 4500.0",
        "essentia_max, 5000.0",
        "essentia_p, 0.1",
        "facing, 2.0",
        "fluid, 300.0",
        "fluid_capacity, 3000.0",
        "fluid_f, 2700.0",
        "fluid_f_in, 310.0",
        "fluid_f_out, 312.0",
        "fluid_free, 2700.0",
        "fluid_in, 302.0",
        "fluid_max, 3000.0",
        "fluid_out, 304.0",
        "fluid_p, 0.1",
        "fluid_percent, 0.1",
        "fluid_stored, 300.0",
        "fluid_total, 300.0",
        "gas, 400.0",
        "gas_capacity, 4000.0",
        "gas_f, 3600.0",
        "gas_f_in, 410.0",
        "gas_f_out, 412.0",
        "gas_free, 3600.0",
        "gas_in, 402.0",
        "gas_max, 4000.0",
        "gas_out, 404.0",
        "gas_p, 0.1",
        "gas_percent, 0.1",
        "gas_total, 400.0",
        "is_running, 1.0",
        "is_waiting, 1.0",
        "item, 700.0",
        "item_capacity, 1280.0",
        "item_f, 710.0",
        "item_free, 710.0",
        "item_max, 1280.0",
        "item_p, 0.546875",
        "item_percent, 0.546875",
        "item_space, 710.0",
        "item_total, 700.0",
        "mana, 200.0",
        "mana_capacity, 2000.0",
        "mana_f, 1800.0",
        "mana_free, 1800.0",
        "mana_max, 2000.0",
        "mana_p, 0.1",
        "mana_percent, 0.1",
        "mana_stored, 200.0",
        "mana_total, 200.0",
        "multiplier_energy, 2.5",
        "multiplier_speed, 1.5",
        "progress, 0.5",
        "progress_percent, 0.5",
        "recipe_count, 5.0",
        "recipe_types_count, 2.0",
        "recipeprocessed, 5.0",
        "recipeprocessedtype, 2.0",
        "speed_multi, 1.5",
        "speed_multiplier, 1.5",
        "tier, 3.0",
        "timecontinue, 800.0",
        "timeplaced, 1000.0",
        "total_energy, 100.0",
        "total_energy_capacity, 1000.0",
        "total_energy_max, 1000.0",
        "total_fluid, 300.0",
        "total_fluid_capacity, 3000.0",
        "total_fluid_max, 3000.0",
        "total_gas, 400.0",
        "total_mana, 200.0",
        "total_mana_capacity, 2000.0",
        "total_mana_max, 2000.0",
        "vis, 600.0",
        "vis_capacity, 6000.0",
        "vis_f, 5400.0",
        "vis_max, 6000.0",
        "vis_p, 0.1",
    })
    // spotless:on
    @DisplayName("値が改修前と一致する")
    public void test値が一致する(String name, double expected) {
        double actual = ExpressionParser.parseExpression(name)
            .evaluateDouble(StubMachineContext.withMachine());

        assertEquals(expected, actual, () -> "'" + name + "' の値が変わった。別の資源種・方向・名前指定の値を読んでいる可能性がある");
    }
}
