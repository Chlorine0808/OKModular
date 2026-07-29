package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * レシピ JSON から使える式名の凍結リスト（characterization test）。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * 資源種ごとの写像が API・実装・式レジストリの 3 層に手書きで重複しており、
 * 資源種を 1 つ足すたびに 3 層すべてに手が入る。これを Kind のループ生成に
 * 畳む改修（ロードマップ B9）を安全に行うための **足場** がこのテスト。
 *
 * 式名は docs/{en,jp}/recipes/EXPRESSION_REFERENCE.md が仕様の正本として
 * 公開するので、**改修で 1 つでも消えると既存レシピ JSON が黙って壊れる**。
 * 「気をつける」ではなくテストで担保する。
 *
 * ============================================
 * なぜ実行時列挙ではなく「凍結した名前の羅列」なのか
 * ============================================
 *
 * レジストリを実行時に列挙して検証する形にすると、**名前が消えたことを
 * 検出できない**。列挙は「その時点で登録されているもの」を返すだけなので、
 * 登録が消えれば検証対象からも消えて緑のまま通る。
 *
 * よって、ここには改修前の名前を **リテラルとして書き写す**。
 * これがスナップショットの本体であり、リストが手書きであること自体が仕様。
 *
 * 名前の追加は許す（B9 のループ生成は、手書きで抜けていた別名を埋めるため
 * 名前が増える方向に動く）。**減ることだけを禁止する。**
 *
 * ============================================
 * このテストが見ているもの / 見ていないもの
 * ============================================
 *
 * 見ているのは「**パースが通るか**」だけ。値が正しいかは見ない。
 * 未登録の識別子は ExpressionParser が `Unknown variable` / `Unknown function`
 * で throw するので、登録の消失はここで確実に落ちる。
 *
 * 「パースは通るが評価すると黙って 0 になる」種類の欠落は別のテストが持つ
 * （MachinePropertyExpression 側に定義が無い場合。energy_per_tick が実例）。
 *
 * ============================================
 */
@DisplayName("式名の凍結リスト")
public class ExpressionNameFreezeTest {

    /**
     * 変数として使える機械プロパティ。
     * ExpressionRegistry の registerMachineProperty 87 件を書き写したもの。
     */
    // spotless: off
    private static final String[] MACHINE_PROPERTIES = {
        "batch", "batch_size", "count_recipe", "count_recipe_type", "count_recipe_types",
        "current_batch",
        "energy", "energy_capacity", "energy_f", "energy_free", "energy_max",
        "energy_multi", "energy_multiplier", "energy_p", "energy_per_tick", "energy_percent",
        "energy_stored", "energy_total",
        "essentia", "essentia_capacity", "essentia_f", "essentia_max", "essentia_p",
        "fluid", "fluid_capacity", "fluid_f", "fluid_f_in", "fluid_f_out", "fluid_free",
        "fluid_in", "fluid_max", "fluid_out", "fluid_p", "fluid_percent", "fluid_stored",
        "fluid_total",
        "gas", "gas_capacity", "gas_f", "gas_f_in", "gas_f_out", "gas_free", "gas_in",
        "gas_max", "gas_out", "gas_p", "gas_percent", "gas_total",
        "is_running", "is_waiting",
        "mana", "mana_capacity", "mana_f", "mana_free", "mana_max", "mana_p",
        "mana_percent", "mana_stored", "mana_total",
        "multiplier_energy", "multiplier_speed",
        "progress", "progress_percent",
        "recipe_count", "recipe_types_count", "recipeprocessed", "recipeprocessedtype",
        "speed_multi", "speed_multiplier",
        "tier", "timecontinue", "timeplaced",
        "total_energy", "total_energy_capacity", "total_energy_max",
        "total_fluid", "total_fluid_capacity", "total_fluid_max",
        "total_gas",
        "total_mana", "total_mana_capacity", "total_mana_max",
        "vis", "vis_capacity", "vis_f", "vis_max", "vis_p",
    };

    /**
     * 変数として使えるワールドプロパティ。
     * ExpressionRegistry の registerWorldProperty 22 件。
     */
    private static final String[] WORLD_PROPERTIES = {
        "day", "dimension", "humidity",
        "is_day", "is_night", "is_raining", "is_thundering",
        "light", "light_block", "light_sky",
        "moon_phase", "progress_tick", "recipe_tick", "redstone", "seed",
        "temp", "tick", "time", "total_days",
        "x", "y", "z",
    };

    /** 個別に registerVariable された変数。 */
    private static final String[] OTHER_VARIABLES = { "e", "moon", "pi", };

    /**
     * 関数。**呼び出しの形ごと**凍結する（引数の個数も契約の一部なので）。
     *
     * 資源系関数は「引数 1 個」を registerResourceFunction が検証している。
     * fluid_in などは変数としても関数としても存在し、意味が違う
     * （変数 = 入力側の総量、関数 = 指定した種類の量）。両方を凍結する。
     */
    private static final String[] FUNCTION_CALLS = {
        // 資源量（種類を指定する形）
        "essentia('ignis')", "vis('ignis')",
        "gas('hydrogen')", "gas_in('hydrogen')", "gas_out('hydrogen')",
        "gas_f_in('hydrogen')", "gas_f_out('hydrogen')",
        "fluid('water')", "fluid_in('water')", "fluid_out('water')",
        "fluid_f_in('water')", "fluid_f_out('water')",
        "item('minecraft:stone')", "item_in('minecraft:stone')", "item_out('minecraft:stone')",
        "item_f('minecraft:stone')", "item_f_in('minecraft:stone')", "item_f_out('minecraft:stone')",
        // スロット
        "item_slot('minecraft:stone')", "item_slot_in('minecraft:stone')",
        "item_slot_out('minecraft:stone')", "item_slot_empty('minecraft:stone')",
        // NBT アクセス（D-10 で nbt() / has_nbt() に一本化した記法）
        "nbt('display.Name')", "has_nbt('display.Name')",
        "nbt('C', 'display.Name')", "has_nbt('C', 'display.Name')",
        // 環境
        "can_see_sky()", "can_see_void()", "count_blocks('minecraft:stone')",
    };
    // spotless: on

    private static List<String> allVariables() {
        List<String> names = new ArrayList<>();
        for (String n : MACHINE_PROPERTIES) names.add(n);
        for (String n : WORLD_PROPERTIES) names.add(n);
        for (String n : OTHER_VARIABLES) names.add(n);
        return names;
    }

    private static List<String> allFunctionCalls() {
        return List.of(FUNCTION_CALLS);
    }

    @ParameterizedTest(name = "変数 {0}")
    @MethodSource("allVariables")
    @DisplayName("凍結した変数名はすべてパースできる")
    public void test変数がパースできる(String name) {
        IExpression expr = assertDoesNotThrow(
            () -> ExpressionParser.parseExpression(name),
            () -> "変数 '" + name + "' が登録から消えている。B9 の改修で名前を落とした可能性がある");

        assertNotNull(expr, "変数 '" + name + "' のパース結果が null");
    }

    @ParameterizedTest(name = "関数 {0}")
    @MethodSource("allFunctionCalls")
    @DisplayName("凍結した関数呼び出しはすべてパースできる")
    public void test関数がパースできる(String call) {
        IExpression expr = assertDoesNotThrow(
            () -> ExpressionParser.parseExpression(call),
            () -> "関数呼び出し '" + call + "' が通らない。登録名か引数の契約が変わった可能性がある");

        assertNotNull(expr, "関数呼び出し '" + call + "' のパース結果が null");
    }
}
