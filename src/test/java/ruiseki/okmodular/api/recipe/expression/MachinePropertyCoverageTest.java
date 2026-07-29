package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 登録された機械プロパティに、評価する実装が付いていることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * 機械プロパティは **2 箇所に登録しないと動かない**:
 *
 * 1. ExpressionRegistry.registerMachineProperty(name) — パースできるようになる
 * 2. MachinePropertyExpression の register(name, getter) — 値が読めるようになる
 *
 * 1 だけだと **パースは通り、評価は黙って 0 を返す**（定義が見つからないときの
 * MachinePropertyExpression の戻り値が ZERO なので、例外もログも出ない）。
 * レシピ作者から見ると「式は書けるのに常に 0」という形でしか現れない。
 *
 * energy_per_tick が実際にこれで落ちていた。同じ穴が他にも空いていないかを
 * 名前の一覧に対して機械的に確かめる。
 *
 * ============================================
 * どうやって「定義が無い」を検出するか
 * ============================================
 *
 * StubMachineContext の機械状態は全メソッドが非ゼロを返す。
 * よって **評価結果が 0 なら、定義が無かった**ということ。
 *
 * 値そのものは検証しない（それは各プロパティ固有の話で、ここの関心ではない）。
 * 見ているのは「実装に到達したか」だけ。
 *
 * ============================================
 * B9 との関係
 * ============================================
 *
 * 2 箇所への登録が手書きで並んでいる限り、この種の食い違いは入り続ける。
 * 資源種を Kind のループで生成する改修（B9）が済めば、
 * **登録漏れは構造的に起こらなくなる**。このテストはその前後で緑のまま通る。
 *
 * ============================================
 */
@DisplayName("機械プロパティの実装カバレッジ")
public class MachinePropertyCoverageTest {

    private static List<String> machineProperties() {
        return List.of(ExpressionNameFreezeTest.MACHINE_PROPERTIES);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("machineProperties")
    @DisplayName("登録された機械プロパティは評価すると値を返す")
    public void testプロパティが値を返す(String name) {
        double value = ExpressionParser.parseExpression(name)
            .evaluateDouble(StubMachineContext.withMachine());

        assertNotEquals(
            0.0,
            value,
            () -> "'" + name
                + "' が 0 を返した。ExpressionRegistry には登録されているが "
                + "MachinePropertyExpression に定義が無い（= 黙って 0 になる）可能性が高い");
    }
}
