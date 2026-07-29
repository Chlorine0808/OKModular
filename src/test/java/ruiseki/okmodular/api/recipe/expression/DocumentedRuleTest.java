package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import ruiseki.okmodular.api.modular.IPortType;

/**
 * docs が書いている「規則」が実際に成り立つことの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * docs/{en,jp}/recipes/EXPRESSION_REFERENCE.md は資源プロパティを
 * **名前の一覧ではなく規則の表**として書いている。
 * 「7 資源種はすべて同じ接尾辞を持つ」「方向つきの名前は入出力が別格納の種だけ」など。
 *
 * 一覧だと書き漏れが起きる（実際に起きていた: gas に `gas_stored` が無い、
 * essentia / vis に総量系が無い、docs が約束する 12 名前が Unknown variable で
 * 弾かれる、など）。規則で書けば漏れないが、**規則が実装と食い違えば
 * 一覧より広範囲に嘘になる**。
 *
 * そこでここでは docs の表を**独立に書き写して**名前を組み立て、
 * すべて動くことを確かめる。実装側の生成ループとは別に書いてあるので、
 * どちらかが変わればここで食い違いが出る。
 *
 * ============================================
 * docs の表（このテストが写しているもの）
 * ============================================
 *
 * <pre>
 * K                            = item / fluid / gas / energy / mana / essentia / vis
 * K, K_stored, K_total, total_K                                    保持量
 * K_max, K_capacity, total_K_max, total_K_capacity                 容量
 * K_f, K_free, K_space                                             空き
 * K_p, K_percent                                                   充填率
 *
 * 入出力が別格納の種（item / fluid / gas）のみ:
 * K_in, K_out                                                      方向別の量
 * K_f_in, K_f_out                                                  方向別の空き
 * </pre>
 *
 * ============================================
 */
@DisplayName("docs の規則")
public class DocumentedRuleTest {

    /** docs の「保持量 / 容量 / 空き / 充填率」の表を書き写したもの。 */
    private static final String[] SUFFIXES = { "", "_stored", "_total", "_max", "_capacity", "_f", "_free", "_space",
        "_p", "_percent" };

    /** docs の「total_K」の形。 */
    private static final String[] PREFIXED = { "total_", };

    /** docs の「total_K_max / total_K_capacity」の形。 */
    private static final String[] PREFIXED_SUFFIXED = { "_max", "_capacity" };

    /** docs の方向つきの表。入出力が別格納の資源種だけが持つ。 */
    private static final String[] DIRECTIONAL_SUFFIXES = { "_in", "_out", "_f_in", "_f_out" };

    private static List<String> documentedNames() {
        List<String> names = new ArrayList<>();

        for (IPortType.Type kind : IPortType.Type.values()) {
            if (!kind.isStorable()) continue;

            String k = kind.name()
                .toLowerCase();

            for (String suffix : SUFFIXES) names.add(k + suffix);
            for (String prefix : PREFIXED) names.add(prefix + k);
            for (String suffix : PREFIXED_SUFFIXED) names.add("total_" + k + suffix);

            if (kind.hasDirectionalStorage()) {
                for (String suffix : DIRECTIONAL_SUFFIXES) names.add(k + suffix);
            }
        }

        return names;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentedNames")
    @DisplayName("規則から組み立てた名前はすべて使える")
    public void test規則どおりの名前が使える(String name) {
        assertDoesNotThrow(
            () -> ExpressionParser.parseExpression(name),
            () -> "docs は '" + name + "' が使えると書いているが、パースできない");

        double value = ExpressionParser.parseExpression(name)
            .evaluateDouble(StubMachineContext.withMachine());

        assertNotEquals(0.0, value, () -> "'" + name + "' はパースできるが評価が 0。定義に繋がっていない");
    }

    /**
     * 方向つきの名前は、方向が意味を持つ資源種にだけ存在する。
     *
     * docs は「energy / mana / essentia / vis は単一のプールなので方向つきの名前は無い」と
     * 書いている。**無いことも仕様**なので、あったら docs が嘘になる。
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("singlePoolDirectionalNames")
    @DisplayName("単一プールの資源種に方向つきの名前は無い")
    public void test単一プールに方向つきの名前は無い(String name) {
        boolean defined = MachinePropertyExpression.propertyNames()
            .contains(name);

        assertFalse(defined, () -> "'" + name + "' が定義されている。docs は方向つきの名前は無いと書いている");
    }

    /**
     * 引数を取れるのは複数の種類を持つ資源種だけ。
     *
     * docs は「energy / mana は単一の種類の単一プールなので名指しする対象が無い」と書いている。
     * `energy("x")` は関数として登録されていないので Unknown function で弾かれるのが正しい。
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "energy('x')", "mana('x')", "energy_f('x')", "mana_max('x')" })
    @DisplayName("energy と mana に引数形は無い")
    public void test単一種の資源に引数形は無い(String call) {
        assertThrows(
            RuntimeException.class,
            () -> ExpressionParser.parseExpression(call),
            () -> call + " が通ってしまう。docs は引数形が無いと書いている");
    }

    private static List<String> singlePoolDirectionalNames() {
        List<String> names = new ArrayList<>();

        for (IPortType.Type kind : IPortType.Type.values()) {
            if (!kind.isStorable() || kind.hasDirectionalStorage()) continue;

            String k = kind.name()
                .toLowerCase();
            for (String suffix : DIRECTIONAL_SUFFIXES) names.add(k + suffix);
        }

        return names;
    }
}
