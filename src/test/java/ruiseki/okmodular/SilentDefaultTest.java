package ruiseki.okmodular;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * 「答えの代わりに 0 を返す `default`」に、**本当の実装が居る**ことの検証。
 *
 * ============================================
 * なぜこの形が危ないのか
 * ============================================
 *
 * interface に `default` を置いて 0 や null を返させると、
 * **実装し忘れても何も起きない**。例外も出ないし、コンパイラも黙っている。
 * 呼んだ側は「この機械には流体が 1 も入っていない」という答えを受け取り、それを信じる。
 *
 * `_SSOT.md` の「文脈を間違えて渡す（silent zero）」と同じ型で、
 * この mod が 11 回踏んでいる「機能は書かれていたのに使う側が無い」の親戚。
 * `default` は abstract と違って**未完成のまま緑になれる**ぶん、たちが悪い。
 *
 * ============================================
 * 何を数えるのか
 * ============================================
 *
 * 値を返す `default` のうち、中身が `return 0;` `return null;` のような
 * **空の答え**だけのものを拾い、同じ名前と引数の数の実装が
 * どこかのクラスに 1 つでもあるかを見る。
 *
 * 0 個なら、その `default` が**唯一の実装**。永久に空の答えしか返さない。
 *
 * 値を返さない `default`（`setAssignedIndex` や Waila のフック）は対象外。
 * 「何もしない」が正しい既定である場合が多く、空の答えを配る問題とは別の話。
 *
 * ============================================
 * 弱点
 * ============================================
 *
 * 名前と引数の数だけで実装を探すので、無関係な同名メソッドを実装と数えることがある。
 * `IProgressTile.getProgress()` と `IMachineState.getProgress()` のような組。
 * **見逃す方向**なので、赤くなったときは本物。
 */
@DisplayName("空の答えを返す default に実装が居る")
public class SilentDefaultTest {

    /** 空の答え。空白を潰した本体がこのどれかなら、その `default` は何も知らない。 */
    private static final List<String> EMPTY_ANSWERS = Arrays.asList(
        "{returnnull;}",
        "{return0;}",
        "{return0L;}",
        "{return0l;}",
        "{return0.0;}",
        "{return0.0f;}",
        "{return0f;}",
        "{return0d;}",
        "{returnfalse;}",
        "{return\"\";}",
        "{returnCollections.emptyList();}",
        "{returnCollections.emptyMap();}",
        "{returnCollections.emptySet();}",
        "{returnnewArrayList<>();}",
        "{returnnewHashMap<>();}");

    /**
     * 実装が無くても正しい `default`。**理由が書けるものだけ**を置く。
     *
     * WAILA の `getWailaStack` は「表示するアイテムを差し替えたければどうぞ」という口で、
     * **null が『差し替えない』という本当の答え**。`BlockProvider` はその null を
     * そのまま WAILA に返し、WAILA は既定のアイテムを出す。
     * 空の答えを配っているのではなく、意見が無いことを表明している。
     *
     * このテストを書いたときはここに 17 個並んでいた。
     * `IMachineState` の流体・ガス・エッセンシア・vis の合計 16 個は
     * **`MachineStateAgent` が実装しておらず、`getAmount` / `getSpace` 経由で
     * 実機だけが 0 を返していた**（テストのスタブは実装していたので緑だった）。
     * `IModularBlockTint.getStructureTintColor` は綴りが 2 つある方の死んでいる側だった。
     * 残っているのがこの 2 つだけ、という状態を保つのが目的。
     */
    private static final List<String> KNOWN_UNIMPLEMENTED = Arrays
        .asList("IWailaBlockInfoProvider#getWailaStack/2", "IWailaTileInfoProvider#getWailaStack/2");

    @Test
    @DisplayName("空の答えを返す default には実装クラスが居る")
    public void test実装の無い_default_が無い() {
        List<String> orphans = new ArrayList<>();

        for (Default silent : silentDefaults()) {
            if (KNOWN_UNIMPLEMENTED.contains(silent.label)) continue;
            if (implementationCount(silent) == 0) orphans.add(silent.label);
        }

        assertTrue(
            orphans.isEmpty(),
            orphans + " は空の答えを返す default で、実装しているクラスが 1 つも無い。"
                + "呼んだ側は 0 や null を本当の答えだと思って受け取る。実装するか、interface から消すこと");
    }

    @Test
    @DisplayName("実装が無い一覧が古くなっていない")
    public void test免除リストが正しい() {
        Set<String> silent = new LinkedHashSet<>();
        for (Default found : silentDefaults()) silent.add(found.label);

        for (String label : KNOWN_UNIMPLEMENTED) {
            assertTrue(silent.contains(label), label + " はもう空の答えを返す default ではない。KNOWN_UNIMPLEMENTED から消すこと");
        }
    }

    @Test
    @DisplayName("走査そのものが空振りしていない")
    public void test走査が効いている() {
        // 空の答えの書き方が変わって 1 つも拾えなくなると、上の 2 つは緑のまま無意味になる。
        List<Default> silent = silentDefaults();
        assertTrue(silent.size() >= 5, "空の答えを返す default が 5 個未満。EMPTY_ANSWERS の書き方が実装とずれている（" + silent.size() + " 個）");

        List<String> labels = new ArrayList<>();
        for (Default found : silent) labels.add(found.label);
        assertTrue(
            labels.contains("IRecipeContext#getMachineState/0"),
            "IRecipeContext.getMachineState は null を返す default のはず。拾えていないなら走査が壊れている: " + labels);
    }

    // ============================================================
    // 走査
    // ============================================================

    private static final class Default {

        final String label;
        final String name;
        final int arity;

        Default(String label, String name, int arity) {
            this.label = label;
            this.name = name;
            this.arity = arity;
        }
    }

    private static final Pattern DEFAULT_METHOD = Pattern
        .compile("(?m)^[ \\t]*default\\s+([\\w.<>\\[\\],? ]+?)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)\\s*\\{");

    private static List<Default> silentDefaults() {
        List<Default> found = new ArrayList<>();

        for (Path file : JavaSource.allMainSources()) {
            String source = JavaSource.stripComments(JavaSource.read(file));
            Matcher declarations = DEFAULT_METHOD.matcher(source);

            while (declarations.find()) {
                if (declarations.group(1)
                    .trim()
                    .equals("void")) continue;

                String body = bodyAt(source, declarations.end() - 1).replaceAll("\\s+", "");
                if (!EMPTY_ANSWERS.contains(body)) continue;

                String owner = JavaSource.label(file)
                    .replaceAll(".*/", "")
                    .replace(".java", "");
                int arity = arityOf(declarations.group(3));
                found.add(new Default(owner + "#" + declarations.group(2) + "/" + arity, declarations.group(2), arity));
            }
        }
        return found;
    }

    /**
     * その名前と引数の数を、本体つきで、`default` でなく宣言しているものの数。
     * <p>
     * `default` を除くのが肝で、最初に書いたときは除いていなかった。戻り値の型を
     * `[\w ]+?` で拾うので **`default long` がまるごと型として通り**、
     * どの `default` も自分自身を実装として数え、テストは何も見つけないまま緑になった。
     * 「探しているものが自分だった」は、走査するテストの一番ありふれた壊れ方。
     */
    private static int implementationCount(Default silent) {
        Pattern declaration = Pattern.compile(
            "(?m)^[ \\t]*(?:(?:public|protected|private|static|final|synchronized)\\s+)*" + "([\\w.<>\\[\\],? ]+?)\\s+"
                + Pattern.quote(silent.name)
                + "\\s*\\(([^)]*)\\)\\s*\\{");
        int found = 0;

        for (Path file : JavaSource.allMainSources()) {
            Matcher hits = declaration.matcher(JavaSource.stripComments(JavaSource.read(file)));
            while (hits.find()) {
                if (hits.group(1)
                    .matches(".*\\bdefault\\b.*")) continue;
                if (arityOf(hits.group(2)) == silent.arity) found++;
            }
        }
        return found;
    }

    private static int arityOf(String parameters) {
        if (parameters.trim()
            .isEmpty()) return 0;

        int depth = 0;
        int count = 1;
        for (int i = 0; i < parameters.length(); i++) {
            char c = parameters.charAt(i);
            if (c == '<' || c == '[') depth++;
            if (c == '>' || c == ']') depth--;
            if (c == ',' && depth == 0) count++;
        }
        return count;
    }

    private static String bodyAt(String source, int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            if (source.charAt(i) == '}') {
                depth--;
                if (depth == 0) return source.substring(open, i + 1);
            }
        }
        return "";
    }
}
