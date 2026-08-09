package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * 数学関数について、**書ける・答えられる・載っている**の 3 つが揃っていることの検証。
 *
 * ============================================
 * 3 つある名簿
 * ============================================
 *
 * <pre>
 * SUPPORTED_FUNCTIONS  ExpressionRegistry がここを回して名前を登録する = 書ける名前
 * case ラベル           実際に値を出す = 答えられる名前
 * docs の関数リファレンス  書く人が知っている名前
 * </pre>
 *
 * 3 つが一致していないと、それぞれ違う壊れ方をする。
 *
 * <b>書けるのに答えられない</b>と、その関数だけ既定値になる。
 * <b>答えられるのに書けない</b>と、実装が丸ごと届かない。
 * <b>どちらもできるのに載っていない</b>と、誰にも使われないまま腐る。
 *
 * ============================================
 * 見つかったもの
 * ============================================
 *
 * `cbrt` / `ceiling` / `ln` は **case はあるのに SUPPORTED_FUNCTIONS に無かった**。
 * レジストリは SUPPORTED_FUNCTIONS しか登録しないので、
 * レシピに `cbrt(8)` と書いても関数として認識されない。実装は正しく、誰にも届かなかった。
 *
 * さらに 13 個が docs に無かった（`atan2` `fact` `sign` `log10` `perm` 系 `combi` 系ほか）。
 * 書けて動くのに、書く人が存在を知る手段が無い。
 *
 * ============================================
 * random だけ扱いが違う
 * ============================================
 *
 * `random` は switch に入る前に処理されるので `case` を持たない。
 * 「case が無い = 壊れている」とは限らない例なので、ここだけ名指しで除く。
 */
@DisplayName("数学関数の名簿が 3 つとも揃っている")
public class MathFunctionCoverageTest {

    private static final Path SOURCE = JavaSource.MAIN.resolve("api/recipe/expression/MathFunctionExpression.java");
    private static final Path DOCS = Paths.get("docs");

    /** switch に入る前に答えを返すもの。`case` が無いのが正しい。 */
    private static final List<String> BEFORE_THE_SWITCH = Arrays.asList("random");

    @Test
    @DisplayName("書ける関数はすべて答えを持っている")
    public void test既定値に落ちる関数が無い() {
        Set<String> labels = caseLabels();
        List<String> silent = new ArrayList<>();

        for (String name : supportedFunctions()) {
            if (BEFORE_THE_SWITCH.contains(name)) continue;
            if (!labels.contains(name)) silent.add(name);
        }

        assertTrue(silent.isEmpty(), silent + " は書けるのに値を出す case が無い。関数の形をした定数になる");
    }

    @Test
    @DisplayName("答えを持つ関数はすべて書ける")
    public void test呼べない関数が無い() {
        Set<String> supported = supportedFunctions();
        List<String> unreachable = new ArrayList<>();

        for (String label : caseLabels()) {
            if (!supported.contains(label)) unreachable.add(label);
        }

        assertTrue(
            unreachable.isEmpty(),
            unreachable + " は SUPPORTED_FUNCTIONS に無い。ExpressionRegistry はその一覧しか登録しないので、"
                + "レシピに書いても関数として認識されない。実装は正しいまま誰にも届かない");
    }

    @Test
    @DisplayName("書ける関数はすべて docs に載っている")
    public void test隠れた関数が無い() {
        String docs = allDocs();
        List<String> hidden = new ArrayList<>();

        for (String name : supportedFunctions()) {
            if (!docs.contains("`" + name + "(")) hidden.add(name);
        }

        assertTrue(hidden.isEmpty(), hidden + " が docs の関数リファレンスに無い。書けて動くのに、書く人が存在を知る手段が無い");
    }

    @Test
    @DisplayName("switch の前で答える関数の一覧が正しい")
    public void test例外の一覧が正しい() {
        String source = JavaSource.stripComments(JavaSource.read(SOURCE));

        for (String name : BEFORE_THE_SWITCH) {
            assertTrue(
                source.contains("functionName.equals(\"" + name + "\")"),
                name + " はもう switch の前で処理されていない。BEFORE_THE_SWITCH から消すこと");
            assertTrue(supportedFunctions().contains(name), name + " はもう書けない。BEFORE_THE_SWITCH から消すこと");
        }
    }

    @Test
    @DisplayName("走査そのものが空振りしていない")
    public void test走査が効いている() {
        assertTrue(supportedFunctions().size() >= 30, "書ける関数が 30 個未満。SUPPORTED_FUNCTIONS の走査が壊れている");
        assertTrue(caseLabels().size() >= 30, "case が 30 個未満。switch の走査が壊れている");
        assertEquals(
            supportedFunctions().size(),
            caseLabels().size() + BEFORE_THE_SWITCH.size(),
            "書ける関数と答えられる関数の数が食い違っている");
        assertNotNull(allDocs());
        assertTrue(allDocs().contains("`sqrt("), "docs の関数リファレンスが読めていない。sqrt すら見つからない");
    }

    // ============================================================

    /** `SUPPORTED_FUNCTIONS` に並んでいる名前。 */
    private static Set<String> supportedFunctions() {
        String source = JavaSource.stripComments(JavaSource.read(SOURCE));
        int start = source.indexOf("SUPPORTED_FUNCTIONS");
        int end = source.indexOf(";", start);
        return literals(source.substring(start, end));
    }

    /** `case "x":` の x。 */
    private static Set<String> caseLabels() {
        Set<String> found = new TreeSet<>();
        Matcher hits = Pattern.compile("case\\s+\"([a-z_0-9]+)\"\\s*:")
            .matcher(JavaSource.stripComments(JavaSource.read(SOURCE)));
        while (hits.find()) found.add(hits.group(1));
        return found;
    }

    private static Set<String> literals(String source) {
        Set<String> found = new TreeSet<>();
        Matcher hits = Pattern.compile("\"([a-z_0-9]+)\"")
            .matcher(source);
        while (hits.find()) found.add(hits.group(1));
        return found;
    }

    private static String allDocs() {
        StringBuilder joined = new StringBuilder();
        try (Stream<Path> files = Files.walk(DOCS)) {
            files.filter(
                p -> p.toString()
                    .endsWith(".md"))
                .forEach(
                    p -> joined.append(JavaSource.read(p))
                        .append('\n'));
        } catch (IOException e) {
            throw new UncheckedIOException("辿れない: " + DOCS, e);
        }
        return joined.toString();
    }
}
