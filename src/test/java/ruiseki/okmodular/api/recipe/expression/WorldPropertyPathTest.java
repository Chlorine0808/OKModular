package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * 世界のプロパティで、**書ける名前と答えられる名前が同じ**ことの検証。
 *
 * ============================================
 * 二重に静かなバグ
 * ============================================
 *
 * `WorldPropertyExpression` は名前で `switch` して値を出す。この形は両側から壊れる。
 *
 * <b>登録されているのに `case` が無い</b>名前は `default: ZERO` に落ちる。
 * レシピに書けて、パースも通って、**答えだけが黙って 0** になる。
 * 「夜だけ動く機械」を書いたつもりが常に止まっている、という形で出る。
 *
 * <b>`case` があるのに登録されていない</b>名前は、書いても変数として認識されない。
 * 中身は正しく書かれているのに一度も呼ばれない。読むと機能に見えるので、
 * 「実装してあるはずだが動かない」を生む。
 *
 * ============================================
 * 見つかった 3 つ
 * ============================================
 *
 * <pre>
 * total_time   tick と同じ値を返す。登録も docs も無かった。
 *              1 つのものに綴りが 2 つある形なので、片方（生きている tick）を残した。
 * moon         レジストリが moon → moon_phase に変換してから作るので、
 *              この label には制御が来ない。
 * facing       これは機械のプロパティで、レジストリは MachinePropertyExpression に送る。
 *              ここのコピーは死んでいたが、**null 検査だけは向こうに無かった**ので移した。
 * </pre>
 *
 * 3 つとも「実行経路に見える死んだコード」で、デコレータが 6 種まとめて死んだのと同じ形。
 */
@DisplayName("世界のプロパティの登録と case")
public class WorldPropertyPathTest {

    private static final Path EXPRESSION = JavaSource.MAIN
        .resolve("api/recipe/expression/WorldPropertyExpression.java");
    private static final Path REGISTRY = JavaSource.MAIN.resolve("api/recipe/expression/ExpressionRegistry.java");

    /**
     * レジストリが別の名前に読み替えてから渡すもの。`case` を持つ側だけが正しい。
     * <p>
     * `registerVariable("moon", name -> new WorldPropertyExpression("moon_phase"))` の形。
     * 書ける名前としては生きているので、`case "moon"` が無いことは穴ではない。
     */
    private static final List<String> ALIASED = Arrays.asList("moon");

    @Test
    @DisplayName("登録された名前にはすべて case がある")
    public void test黙って0を返す名前が無い() {
        Set<String> labels = caseLabels();
        List<String> silent = new ArrayList<>();

        for (String name : registeredNames()) {
            if (!labels.contains(name)) silent.add(name);
        }

        assertTrue(
            silent.isEmpty(),
            silent + " は変数として書けるのに case が無い。default に落ちて 0 を返すので、" + "本当に 0 なのか名前が届いていないのか、書いた側からは区別できない");
    }

    @Test
    @DisplayName("case にはすべて到達できる")
    public void test到達できない_case_が無い() {
        Set<String> registered = registeredNames();
        List<String> unreachable = new ArrayList<>();

        for (String label : caseLabels()) {
            if (ALIASED.contains(label)) continue;
            if (!registered.contains(label)) unreachable.add(label);
        }

        assertTrue(
            unreachable.isEmpty(),
            unreachable + " に対応する registerWorldProperty が無い。その case には制御が来ないので、"
                + "中身がどれだけ正しくても動かない。読むと機能に見えるぶん、無いより悪い");
    }

    @Test
    @DisplayName("読み替えられる名前の一覧が正しい")
    public void test別名の一覧が正しい() {
        String registry = JavaSource.stripComments(JavaSource.read(REGISTRY));

        for (String alias : ALIASED) {
            assertTrue(
                Pattern.compile("registerVariable\\(\\s*\"" + Pattern.quote(alias) + "\"")
                    .matcher(registry)
                    .find(),
                alias + " をレジストリが読み替えていない。ALIASED から消すこと");
            assertTrue(!caseLabels().contains(alias), alias + " の case が戻ってきた。読み替え先に届くので、この case には制御が来ない");
        }
    }

    @Test
    @DisplayName("走査そのものが空振りしていない")
    public void test走査が効いている() {
        Set<String> labels = caseLabels();
        Set<String> registered = registeredNames();

        assertTrue(labels.size() >= 20, "case が 20 個未満。走査が壊れている（" + labels.size() + " 個）");
        assertTrue(registered.size() >= 20, "登録が 20 個未満。走査が壊れている（" + registered.size() + " 個）");
        assertEquals(registered.size(), labels.size(), "登録と case の数が食い違っている。上の 2 つのどちらかが取りこぼしている");
    }

    /** `case "x":` の x。 */
    private static Set<String> caseLabels() {
        return matches(Pattern.compile("case\\s+\"([a-z_0-9]+)\"\\s*:"), JavaSource.read(EXPRESSION));
    }

    /** `registerWorldProperty("x")` の x。 */
    private static Set<String> registeredNames() {
        Set<String> found = new TreeSet<>();
        for (Path file : JavaSource.allMainSources()) {
            found.addAll(
                matches(
                    Pattern.compile("registerWorldProperty\\(\\s*\"([a-z_0-9]+)\"\\s*\\)"),
                    JavaSource.stripComments(JavaSource.read(file))));
        }
        return found;
    }

    private static Set<String> matches(Pattern pattern, String source) {
        Set<String> found = new TreeSet<>();
        Matcher hits = pattern.matcher(JavaSource.stripComments(source));
        while (hits.find()) found.add(hits.group(1));
        return found;
    }
}
