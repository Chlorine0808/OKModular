package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * デコレータが override したメソッドに、**そこへ届く呼び出し元が居る**ことの検証。
 *
 * ============================================
 * 何を一般化しているのか
 * ============================================
 *
 * デコレータ 6 種が一度も動いていなかった件（`DecoratorWiringTest`）を、
 * **その 1 つのメソッドではなく override 全部**に広げたもの。
 * 当時手で数えたらこうだった:
 *
 * <pre>
 * isConditionMet    : 2 件
 * producesOutput    : 1 件
 * getInputs         : 14 件
 * processOutputs    : 0 件   ← これ
 * </pre>
 *
 * 0 件のメソッドを override したデコレータは、型は合うのに一生動かない。
 * 例外もログも出ないし、効果が確率的だと「確率が低い」と見分けが付かない。
 *
 * ============================================
 * なぜ「修飾された呼び出し」だけを数えるのか
 * ============================================
 *
 * ここが今回このテストを書いて分かった一番大事なところ。
 *
 * `ModularRecipe.matchesInput` は `processInputs(inputPorts, true)` と**無修飾で**呼ぶ。
 * 無修飾呼び出しは `this` に束縛される。そして `this` は
 * **デコレータが包んでいる中身の `ModularRecipe`** であって、デコレータ自身ではない。
 * デコレータ側の `matchesInput` は `internal.matchesInput(...)` に委譲するだけなので、
 * 制御が外側に戻ってくることは無い。
 *
 * つまり `HarvestBlockDecorator.processInputs` は、
 * **`simulate = true` ですら一度も呼ばれない**。`!simulate` の中が死んでいるのではなく、
 * メソッドごと到達不能。`.processInputs(` の形で数えるとこれが出る。
 *
 * <pre>
 * 呼ばれ方              override に届くか
 * ---------------------------------------
 * ref.method()          届く
 * ref::method           届く
 * method()              届かない（this = 中身）
 * </pre>
 *
 * ============================================
 * このテストの弱点（分かった上で受け入れている）
 * ============================================
 *
 * `getName` のようにありふれた名前は、無関係な型に対する呼び出しも数えてしまう。
 * 数えすぎる方向なので**見逃しは起きるが誤検出は起きない**。
 * 実際に問題になる名前（`produceExtraOutputs` / `processOutputs` / `checkOutputCapacity`）は
 * 十分に固有なので、今のところ実害は無い。
 */
@DisplayName("デコレータの override に呼ぶ側が居る")
public class DecoratorOverrideReachabilityTest {

    private static final Path DECORATORS = Paths.get("src/main/java/ruiseki/okmodular/api/recipe/decorator");

    /**
     * 修飾された呼び出し元がどこにも居ないメソッド。**空にするのが目標**で、増やすものではない。
     *
     * この 2 つは visitor 導入前のエンジンの化石。入力の消費は `RecipeExecutionVisitor` が、
     * 出力の払い出しは `ProcessAgent.cachedOutputs` が担っていて、
     * 残っているのは `ModularRecipe` が自分で自分を呼ぶ検査だけになっている。
     *
     * 消すのではなく畳む予定。読むと「ここが実行経路」に見えるのが問題の本体で、
     * 実際デコレータを書いた人はここに効果を足した。
     */
    private static final List<String> KNOWN_UNREACHABLE = Arrays.asList("processInputs", "processOutputs");

    @Test
    @DisplayName("override したメソッドに修飾された呼び出し元がある")
    public void test到達できない_override_が無い() {
        TreeMap<String, Integer> callers = countCallers(overriddenMethodNames());
        List<String> unreachable = new ArrayList<>();

        for (String name : callers.keySet()) {
            if (KNOWN_UNREACHABLE.contains(name)) continue;
            if (callers.get(name) == 0) unreachable.add(name);
        }

        assertTrue(
            unreachable.isEmpty(),
            unreachable + " を override しているデコレータがあるが、decorator パッケージの外に "
                + "`ref.メソッド名(` の形の呼び出しが 1 件も無い。無修飾呼び出しは this に束縛されるので "
                + "デコレータには届かない。型が合うだけで一生動かないので、例外もログも出ない");
    }

    @Test
    @DisplayName("到達できない一覧が古くなっていない")
    public void test免除リストが正しい() {
        // 免除リストを検証しない免除リストは、直したあとも免除し続ける。
        TreeMap<String, Integer> callers = countCallers(new LinkedHashSet<>(KNOWN_UNREACHABLE));

        for (String name : KNOWN_UNREACHABLE) {
            assertTrue(overriddenMethodNames().contains(name), name + " をもう誰も override していない。KNOWN_UNREACHABLE から消すこと");
            assertEquals(
                0,
                callers.get(name)
                    .intValue(),
                name + " に呼び出し元ができた。KNOWN_UNREACHABLE から消すこと");
        }
    }

    @Test
    @DisplayName("無修飾の自己呼び出しを到達とみなさない")
    public void test無修飾呼び出しは数えない() {
        // このテスト自身の判定基準の検査。ここが緩むと上の 2 つが黙って通るようになる。
        String source = JavaSource.read(JavaSource.MAIN.resolve("api/recipe/core/ModularRecipe.java"));
        String stripped = JavaSource.stripComments(source);

        assertTrue(
            stripped.contains("processInputs(inputPorts, true)"),
            "ModularRecipe が自分で processInputs を呼ぶ形が変わった。このテストの前提が崩れている");
        assertFalse(
            qualifiedCallPattern("processInputs").matcher(stripped)
                .find(),
            "ModularRecipe の中の呼び出しが修飾された形になった。" + "それならデコレータに届くので KNOWN_UNREACHABLE を見直すこと");
    }

    /** decorator パッケージで `@Override` が付いているメソッドの名前。 */
    private static Set<String> overriddenMethodNames() {
        Set<String> names = new LinkedHashSet<>();

        for (Path file : JavaSource.javaFilesUnder(DECORATORS)) {
            String source = JavaSource.stripComments(JavaSource.read(file));
            Matcher marker = Pattern.compile("@Override\\b")
                .matcher(source);

            while (marker.find()) {
                String name = methodNameAfter(source, marker.end());
                if (name != null) names.add(name);
            }
        }
        return names;
    }

    /**
     * `@Override` の直後から読んで、宣言されているメソッドの名前を取る。
     * `@Override` に続く他のアノテーション（引数付きも）を跨ぐので、
     * `@SuppressWarnings("unused")` の丸括弧を掴んでしまうことはない。
     */
    private static String methodNameAfter(String source, int from) {
        int i = skipAnnotations(source, from);

        int paren = source.indexOf('(', i);
        if (paren < 0) return null;

        int end = paren;
        while (end > 0 && Character.isWhitespace(source.charAt(end - 1))) end--;

        int start = end;
        while (start > 0 && Character.isJavaIdentifierPart(source.charAt(start - 1))) start--;

        return start < end ? source.substring(start, end) : null;
    }

    private static int skipAnnotations(String source, int from) {
        int i = from;
        while (i < source.length()) {
            while (i < source.length() && Character.isWhitespace(source.charAt(i))) i++;
            if (i >= source.length() || source.charAt(i) != '@') return i;

            i++; // '@'
            while (i < source.length() && (Character.isJavaIdentifierPart(source.charAt(i)) || source.charAt(i) == '.'))
                i++;

            int probe = i;
            while (probe < source.length() && Character.isWhitespace(source.charAt(probe))) probe++;
            if (probe < source.length() && source.charAt(probe) == '(') {
                int depth = 0;
                for (int j = probe; j < source.length(); j++) {
                    if (source.charAt(j) == '(') depth++;
                    if (source.charAt(j) == ')') {
                        depth--;
                        if (depth == 0) {
                            i = j + 1;
                            break;
                        }
                    }
                }
            }
        }
        return i;
    }

    /** decorator パッケージの外にある、修飾された呼び出しの件数。 */
    private static TreeMap<String, Integer> countCallers(Set<String> names) {
        TreeMap<String, Integer> counts = new TreeMap<>();
        for (String name : names) counts.put(name, 0);

        for (Path file : JavaSource.allMainSources()) {
            if (file.startsWith(DECORATORS)) continue;

            String source = JavaSource.stripComments(JavaSource.read(file));
            for (String name : names) {
                Matcher hits = qualifiedCallPattern(name).matcher(source);
                while (hits.find()) counts.put(name, counts.get(name) + 1);
            }
        }
        return counts;
    }

    /** `ref.name(` と `ref::name`。どちらも実際の型に従って解決されるのでデコレータに届く。 */
    private static Pattern qualifiedCallPattern(String name) {
        return Pattern.compile("(\\.|::)\\s*" + Pattern.quote(name) + "\\s*[(\\s]");
    }
}
