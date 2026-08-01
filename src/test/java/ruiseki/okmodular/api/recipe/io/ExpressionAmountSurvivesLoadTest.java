package ruiseki.okmodular.api.recipe.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.expression.StubMachineContext;

/**
 * 式で書いた資源量が**ロードを生き延びる**ことの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `validate()` が **`amount > 0`** を見ていた。量を式で書くと読み取り側は
 * `amountExpr` に式を入れて `amount` は 0 のままにするので、**`fromJson` が null を返し、
 * その入出力がレシピから丸ごと消えていた**。
 *
 * **エラーもログも出ない。** 「毎 tick エネルギーを食うはずのレシピが無料で動く」
 * という形で現れる。`fix-expression` で直した silent-zero（機械プロパティが 0 になる）と
 * 系列は同じだが、こちらは **silent-drop**。
 *
 * docs は `EXPRESSION_REFERENCE.md` の「式が書けるフィールド」に
 * 「`energy` / `mana` などのリソース量」を挙げているので、**仕様に対する実装漏れ**。
 * `release_freeze.md` F-5（docs が約束して実装が無い）と同じ失敗の仕方。
 *
 * ============================================
 * なぜ 10 件を表で回すのか
 * ============================================
 *
 * **同じ判定が 10 クラスに写経されていた**のが本体で、1 クラスだけ直しても同じ穴が 9 個残る。
 * 判定は `AbstractModularRecipe{Input,Output}.hasAmount()` に 1 本上げた。
 * ここを表で回しておけば、**資源種を足した人が写経しても**このテストに行が増えないことで気づける。
 *
 * ============================================
 * ここに fluid と item が居ない理由
 * ============================================
 *
 * どちらも `validate()` が識別子（`required` / `rawItemName` / `fluidName`）を見ているので
 * 最初から影響が無い。`fluid` はさらに `FluidRegistry` を引くのでゲーム外では組めない。
 * item 側の式は `ItemOutputAmountTest` が持っている。
 *
 * ============================================
 */
@DisplayName("式で書いた資源量がロードを生き延びる")
public class ExpressionAmountSurvivesLoadTest {

    /** `StubMachineContext` の機械は tier 3 なので、"50 * tier" は 150 になる。 */
    private static final long EXPECTED = 150L;

    private static JsonObject json(String text) {
        return new JsonParser().parse(text)
            .getAsJsonObject();
    }

    private static ConditionContext machine() {
        return StubMachineContext.withMachine();
    }

    // ========== 入力 ==========

    private static Stream<Arguments> 式で量を書いた入力() {
        return Stream.of(
            Arguments.of(
                "energy",
                (Supplier<IModularRecipeInput>) () -> EnergyInput
                    .fromJson(json("{ \"energy\": \"50 * tier\", \"pertick\": true }"))),
            Arguments.of(
                "mana",
                (Supplier<IModularRecipeInput>) () -> ManaInput.fromJson(json("{ \"mana\": \"50 * tier\" }"))),
            Arguments.of(
                "gas",
                (Supplier<IModularRecipeInput>) () -> GasInput
                    .fromJson(json("{ \"gas\": \"oxygen\", \"amount\": \"50 * tier\" }"))),
            Arguments.of(
                "essentia",
                (Supplier<IModularRecipeInput>) () -> EssentiaInput
                    .fromJson(json("{ \"essentia\": \"ignis\", \"amount\": \"50 * tier\" }"))),
            Arguments.of(
                "vis",
                (Supplier<IModularRecipeInput>) () -> VisInput
                    .fromJson(json("{ \"vis\": \"ignis\", \"amount\": \"50 * tier\" }"))));
    }

    @ParameterizedTest(name = "{0} 入力")
    @MethodSource("式で量を書いた入力")
    @DisplayName("入力: 式で書いた量が捨てられない")
    public void test入力が生き延びる(String kind, Supplier<IModularRecipeInput> load) {
        IModularRecipeInput input = load.get();

        assertNotNull(input, kind + " 入力が丸ごと消えている。validate() が amount > 0 を見ていないか");
        assertEquals(EXPECTED, input.getRequiredAmount(machine()), kind + " 入力の式が評価されていない");
    }

    // ========== 出力 ==========

    private static Stream<Arguments> 式で量を書いた出力() {
        return Stream.of(
            Arguments.of(
                "energy",
                (Supplier<IRecipeOutput>) () -> EnergyOutput.fromJson(json("{ \"energy\": \"50 * tier\" }"))),
            Arguments
                .of("mana", (Supplier<IRecipeOutput>) () -> ManaOutput.fromJson(json("{ \"mana\": \"50 * tier\" }"))),
            Arguments.of(
                "gas",
                (Supplier<IRecipeOutput>) () -> GasOutput
                    .fromJson(json("{ \"gas\": \"oxygen\", \"amount\": \"50 * tier\" }"))),
            Arguments.of(
                "essentia",
                (Supplier<IRecipeOutput>) () -> EssentiaOutput
                    .fromJson(json("{ \"essentia\": \"ignis\", \"amount\": \"50 * tier\" }"))),
            Arguments.of(
                "vis",
                (Supplier<IRecipeOutput>) () -> VisOutput
                    .fromJson(json("{ \"vis\": \"ignis\", \"amount\": \"50 * tier\" }"))));
    }

    @ParameterizedTest(name = "{0} 出力")
    @MethodSource("式で量を書いた出力")
    @DisplayName("出力: 式で書いた量が捨てられない")
    public void test出力が生き延びる(String kind, Supplier<IRecipeOutput> load) {
        IRecipeOutput output = load.get();

        assertNotNull(output, kind + " 出力が丸ごと消えている。validate() が amount > 0 を見ていないか");
        assertEquals(EXPECTED, output.getRequiredAmount(machine()), kind + " 出力の式が評価されていない");
    }

    // ========== 数値で書いた側を壊していないこと ==========

    private static Stream<Arguments> 数値で量を書いた入力() {
        return Stream.of(
            Arguments
                .of("energy", (Supplier<IModularRecipeInput>) () -> EnergyInput.fromJson(json("{ \"energy\": 150 }"))),
            Arguments.of("mana", (Supplier<IModularRecipeInput>) () -> ManaInput.fromJson(json("{ \"mana\": 150 }"))),
            Arguments.of(
                "gas",
                (Supplier<IModularRecipeInput>) () -> GasInput
                    .fromJson(json("{ \"gas\": \"oxygen\", \"amount\": 150 }"))),
            Arguments.of(
                "essentia",
                (Supplier<IModularRecipeInput>) () -> EssentiaInput
                    .fromJson(json("{ \"essentia\": \"ignis\", \"amount\": 150 }"))),
            Arguments.of(
                "vis",
                (Supplier<IModularRecipeInput>) () -> VisInput
                    .fromJson(json("{ \"vis\": \"ignis\", \"amount\": 150 }"))));
    }

    @ParameterizedTest(name = "{0} 入力")
    @MethodSource("数値で量を書いた入力")
    @DisplayName("【回帰防止】数値で書いた量は今までどおり")
    public void test数値はそのまま(String kind, Supplier<IModularRecipeInput> load) {
        IModularRecipeInput input = load.get();

        assertNotNull(input, kind + " 入力が読めなくなっている");
        assertEquals(EXPECTED, input.getRequiredAmount(machine()));
    }
}
