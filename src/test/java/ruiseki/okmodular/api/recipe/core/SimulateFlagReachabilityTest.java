package ruiseki.okmodular.api.recipe.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * `simulate` で分岐するメソッドが、**両方の値で呼ばれている**ことの検証。
 *
 * ============================================
 * 何を捕まえるのか
 * ============================================
 *
 * 片方の値でしか呼ばれないフラグは、もう片方の分岐がデッドコード。
 * 型は合っているので、コンパイラも IDE も何も言わない。
 *
 * デコレータ 6 種が一度も動かなかったのはこの形だった。効果はすべて
 * `processOutputs` の `if (!simulate)` の中に書かれていたが、
 * `processOutputs` は `simulate = true` でしか呼ばれない。
 * **分岐の中身が正しいかをいくら検査しても、そこへ入らないことは分からない。**
 *
 * ============================================
 * どう数えるのか
 * ============================================
 *
 * 呼び出し先の `simulate` の位置に何が渡っているかで分ける。
 *
 * <pre>
 * f(..., true)      → true 側に到達できる
 * f(..., false)     → false 側に到達できる
 * f(..., simulate)  → 呼んでいる側が到達できる値を、そのまま渡す（辺を張る）
 * f(..., その他)     → 静的には決まらない。**両方に到達できるとみなす**
 * </pre>
 *
 * 最後の 1 行が大事で、ここを厳しくすると誤検出が出る。実際
 * `AbstractModularRecipeInput` は `simulate || !consume` を渡していて、
 * これは本当に両方の値を取りうる。**見逃す方向に倒す**のが正しい寄せ方で、
 * 「怪しい」を報告するテストは、いずれ誰も読まなくなる。
 *
 * ============================================
 * 見ている範囲と、その理由
 * ============================================
 *
 * 判定するのは `api/recipe` と `common/recipe` の宣言だけ。**呼び出し元は全体から探す。**
 *
 * エネルギーポートの `receiveEnergy(dir, amount, simulate)` などは CoFH の API 実装で、
 * **呼ぶのは他 mod のケーブル**。呼び出し元がこのソースに無いのは当たり前なので、
 * 一緒に数えると「外部 API を実装している」だけで毎回赤くなる。
 * ここで見たいのはレシピエンジンが自前で決めた `simulate` の規約の方。
 */
@DisplayName("simulate フラグの両値に呼ぶ側が居る")
public class SimulateFlagReachabilityTest {

    private static final String FLAG = "simulate";

    /** 判定する宣言の置き場所。呼び出し元はここに限らず main 全体から数える。 */
    private static final String[] SCOPE = { "api/recipe", "common/recipe" };

    /**
     * 片方の値でしか呼ばれていないと分かっているもの。**今は空**。
     *
     * このテストを書いた時点では `processInputs/2` と `processOutputs/2` が載っていた。
     * どちらも `simulate = true` でしか呼ばれず、呼び出し元は `ModularRecipe` の中の 2 箇所だけ。
     * 入力の消費は `RecipeExecutionVisitor` が、出力の払い出しは `ProcessAgent.cachedOutputs` が
     * 担うようになっていて、**あの 2 つの後半は visitor 導入前のエンジンの化石**だった。
     * `matchesInput` / `canOutput` に畳んで消したので、一覧も空になった。
     */
    private static final List<String> KNOWN_ONE_SIDED = Arrays.<String>asList();

    @Test
    @DisplayName("simulate で分岐するメソッドは両方の値で呼ばれている")
    public void test片側でしか呼ばれない分岐が無い() {
        Program program = Program.scan();
        List<String> offenders = new ArrayList<>();

        for (Method method : program.branchingMethods()) {
            if (KNOWN_ONE_SIDED.contains(method.key)) continue;
            if (program.isAmbiguous(method.key)) continue;

            Reach reach = program.reachOf(method.key);
            if (!reach.sawTrue || !reach.sawFalse) {
                offenders.add(method.label + " (" + reach + ")");
            }
        }

        assertTrue(
            offenders.isEmpty(),
            offenders + " は simulate で分岐しているのに、片方の値でしか呼ばれていない。" + "その分岐の中身はデッドコードで、型が合っているので何も言ってくれない");
    }

    @Test
    @DisplayName("片側でしか呼ばれない一覧が古くなっていない")
    public void test免除リストが正しい() {
        Program program = Program.scan();
        Set<String> branching = new LinkedHashSet<>();
        for (Method method : program.branchingMethods()) branching.add(method.key);

        for (String key : KNOWN_ONE_SIDED) {
            assertTrue(branching.contains(key), key + " はもう simulate で分岐していない。KNOWN_ONE_SIDED から消すこと");

            Reach reach = program.reachOf(key);
            assertTrue(
                !reach.sawTrue || !reach.sawFalse,
                key + " は両方の値で呼ばれるようになった。KNOWN_ONE_SIDED から消すこと（" + reach + "）");
        }
    }

    @Test
    @DisplayName("同じ名前と引数の数で simulate の位置が食い違っていない")
    public void test曖昧な署名が分岐を隠していない() {
        // 名前と引数の数だけで呼び出し先を決めているので、`process(shape, facing)` のように
        // 同名同数で simulate を取らないものがあると、どちらの宣言か決められない。
        // 決められないものは判定から外しているが、**外したものが分岐を持っていたら見逃しになる**。
        Program program = Program.scan();
        List<String> hidden = new ArrayList<>();

        for (Method method : program.branchingMethods()) {
            if (program.isAmbiguous(method.key)) hidden.add(method.label);
        }

        assertTrue(
            hidden.isEmpty(),
            hidden + " は simulate で分岐しているのに、同名同引数数の別宣言があって判定から外れている。" + "名前を変えるか、このテストに宣言の型まで見せること");
    }

    @Test
    @DisplayName("走査そのものが空振りしていない")
    public void test走査が効いている() {
        // 走査が何も見つけなくなると、上の 3 つは全部緑のまま無意味になる。
        Program program = Program.scan();

        assertTrue(
            program.branchingMethods()
                .size() >= 2,
            "simulate で分岐するメソッドが 2 個未満。宣言の走査が壊れている");

        Reach both = program.reachOf("process/4");
        assertTrue(
            both.sawTrue && both.sawFalse,
            "IModularRecipeInput.process(ports, multiplier, simulate, context) の両値が見えていない。"
                + "RecipeExecutionVisitor と ProcessAgent が実際に両方で呼んでいるので、見えないなら呼び出し元の走査が壊れている（"
                + both
                + "）");
    }

    // ============================================================
    // 走査
    // ============================================================

    /** 到達できる値。 */
    private static final class Reach {

        boolean sawTrue;
        boolean sawFalse;

        boolean absorb(Reach other) {
            boolean changed = (other.sawTrue && !sawTrue) || (other.sawFalse && !sawFalse);
            sawTrue |= other.sawTrue;
            sawFalse |= other.sawFalse;
            return changed;
        }

        @Override
        public String toString() {
            if (sawTrue && sawFalse) return "true と false";
            if (sawTrue) return "true のみ";
            if (sawFalse) return "false のみ";
            return "呼び出し元なし";
        }
    }

    /** 宣言 1 つ。 */
    private static final class Method {

        final String key;
        final String label;
        final int flagIndex;
        final int bodyStart;
        final int bodyEnd;
        final boolean branchesOnFlag;
        final boolean inScope;

        Method(String key, String label, int flagIndex, int bodyStart, int bodyEnd, boolean branchesOnFlag,
            boolean inScope) {
            this.key = key;
            this.label = label;
            this.flagIndex = flagIndex;
            this.bodyStart = bodyStart;
            this.bodyEnd = bodyEnd;
            this.branchesOnFlag = branchesOnFlag;
            this.inScope = inScope;
        }
    }

    private static final class Program {

        private final Map<String, Reach> reach = new LinkedHashMap<>();
        private final Map<String, Set<String>> forwards = new LinkedHashMap<>();
        private final Map<String, Integer> flagIndexByKey = new LinkedHashMap<>();
        private final Set<String> ambiguous = new TreeSet<>();
        private final List<Method> methods = new ArrayList<>();

        static Program scan() {
            Program program = new Program();
            Map<Path, String> sources = new LinkedHashMap<>();

            for (Path file : JavaSource.allMainSources()) {
                sources.put(file, JavaSource.stripComments(JavaSource.read(file)));
            }

            for (Map.Entry<Path, String> entry : sources.entrySet()) {
                program.collectDeclarations(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<Path, String> entry : sources.entrySet()) {
                program.collectCallSites(entry.getKey(), entry.getValue());
            }
            program.propagate();
            return program;
        }

        List<Method> branchingMethods() {
            List<Method> found = new ArrayList<>();
            for (Method method : methods) {
                if (method.branchesOnFlag && method.inScope) found.add(method);
            }
            return found;
        }

        boolean isAmbiguous(String key) {
            return ambiguous.contains(key);
        }

        Reach reachOf(String key) {
            Reach found = reach.get(key);
            return found != null ? found : new Reach();
        }

        // ------------------------------------------------------------

        private void collectDeclarations(Path file, String source) {
            for (Declaration declaration : Declaration.findAll(source)) {
                int flagIndex = declaration.params.indexOf(FLAG);
                String key = declaration.name + "/" + declaration.params.size();

                if (flagIndexByKey.containsKey(key)) {
                    if (flagIndexByKey.get(key) != flagIndex) ambiguous.add(key);
                } else {
                    flagIndexByKey.put(key, flagIndex);
                }
                if (flagIndex < 0) continue;

                String body = declaration.bodyStart < 0 ? ""
                    : source.substring(declaration.bodyStart, declaration.bodyEnd)
                        .replaceAll("\\s+", "");

                methods.add(
                    new Method(
                        key,
                        JavaSource.label(file) + "#" + declaration.name,
                        flagIndex,
                        declaration.bodyStart,
                        declaration.bodyEnd,
                        body.contains("if(!" + FLAG + ")") || body.contains("if(" + FLAG + ")"),
                        inScope(file)));
                reach.putIfAbsent(key, new Reach());
            }
        }

        private void collectCallSites(Path file, String source) {
            List<Declaration> declarations = Declaration.findAll(source);
            Set<Integer> declarationOffsets = new LinkedHashSet<>();
            for (Declaration declaration : declarations) declarationOffsets.add(declaration.nameStart);

            Matcher calls = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
                .matcher(source);

            while (calls.find()) {
                if (declarationOffsets.contains(calls.start(1))) continue;

                List<String> args = Arguments.split(source, calls.end() - 1);
                if (args == null) continue;

                String key = calls.group(1) + "/" + args.size();
                Integer flagIndex = flagIndexByKey.get(key);
                if (flagIndex == null || flagIndex < 0) continue;

                String passed = args.get(flagIndex)
                    .trim();
                Reach target = reach.computeIfAbsent(key, k -> new Reach());

                if ("true".equals(passed)) {
                    target.sawTrue = true;
                } else if ("false".equals(passed)) {
                    target.sawFalse = true;
                } else if (FLAG.equals(passed)) {
                    String caller = enclosingKey(declarations, calls.start(1));
                    if (caller != null) {
                        forwards.computeIfAbsent(caller, k -> new LinkedHashSet<>())
                            .add(key);
                    } else {
                        target.sawTrue = true;
                        target.sawFalse = true;
                    }
                } else {
                    // 静的には決まらない式。見逃す方向に倒す。
                    target.sawTrue = true;
                    target.sawFalse = true;
                }
            }
        }

        /** その位置を含む一番内側の、`simulate` を取るメソッド。 */
        private String enclosingKey(List<Declaration> declarations, int offset) {
            Declaration best = null;
            for (Declaration declaration : declarations) {
                if (declaration.bodyStart < 0) continue;
                if (offset < declaration.bodyStart || offset >= declaration.bodyEnd) continue;
                if (declaration.params.indexOf(FLAG) < 0) continue;
                if (best == null || declaration.bodyStart > best.bodyStart) best = declaration;
            }
            return best == null ? null : best.name + "/" + best.params.size();
        }

        private void propagate() {
            boolean changed = true;
            while (changed) {
                changed = false;
                for (Map.Entry<String, Set<String>> edge : forwards.entrySet()) {
                    Reach from = reach.get(edge.getKey());
                    if (from == null) continue;
                    for (String to : edge.getValue()) {
                        if (to.equals(edge.getKey())) continue;
                        changed |= reach.computeIfAbsent(to, k -> new Reach())
                            .absorb(from);
                    }
                }
            }
        }

        private static boolean inScope(Path file) {
            String label = JavaSource.label(file);
            for (String prefix : SCOPE) {
                if (label.startsWith(prefix + "/")) return true;
            }
            return false;
        }
    }

    /** ソース上のメソッド宣言 1 つ。 */
    private static final class Declaration {

        private static final Pattern SIGNATURE = Pattern.compile(
            "(?m)^[ \\t]*(?:(?:public|protected|private|static|final|abstract|default|synchronized|native)\\s+)*"
                + "([\\w.<>\\[\\],? ]+?)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)\\s*(?:throws [\\w., ]+)?\\s*[;{]");

        /** 宣言に見えるが宣言ではないもの。`return new ItemStack(a, b, c);` が代表。 */
        private static final Pattern NOT_A_TYPE = Pattern.compile("\\b(return|new|throw|assert|case|else)\\b|=");

        final String name;
        final List<String> params;
        final int nameStart;
        final int bodyStart;
        final int bodyEnd;

        Declaration(String name, List<String> params, int nameStart, int bodyStart, int bodyEnd) {
            this.name = name;
            this.params = params;
            this.nameStart = nameStart;
            this.bodyStart = bodyStart;
            this.bodyEnd = bodyEnd;
        }

        static List<Declaration> findAll(String source) {
            List<Declaration> found = new ArrayList<>();
            Matcher matcher = SIGNATURE.matcher(source);

            while (matcher.find()) {
                if (NOT_A_TYPE.matcher(matcher.group(1))
                    .find()) continue;

                int bodyStart = -1;
                int bodyEnd = -1;
                if (source.charAt(matcher.end() - 1) == '{') {
                    bodyStart = matcher.end() - 1;
                    bodyEnd = closingBrace(source, bodyStart);
                    if (bodyEnd < 0) continue;
                }

                found.add(
                    new Declaration(
                        matcher.group(2),
                        parameterNames(matcher.group(3)),
                        matcher.start(2),
                        bodyStart,
                        bodyEnd));
            }
            return found;
        }

        private static List<String> parameterNames(String declared) {
            List<String> names = new ArrayList<>();
            if (declared.trim()
                .isEmpty()) return names;

            int depth = 0;
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < declared.length(); i++) {
                char c = declared.charAt(i);
                if (c == '<' || c == '[') depth++;
                if (c == '>' || c == ']') depth--;
                if (c == ',' && depth == 0) {
                    names.add(lastWord(current.toString()));
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
            names.add(lastWord(current.toString()));
            return names;
        }

        private static String lastWord(String parameter) {
            String trimmed = parameter.trim();
            int space = trimmed.lastIndexOf(' ');
            return space < 0 ? trimmed : trimmed.substring(space + 1);
        }

        private static int closingBrace(String source, int open) {
            int depth = 0;
            for (int i = open; i < source.length(); i++) {
                if (source.charAt(i) == '{') depth++;
                if (source.charAt(i) == '}') {
                    depth--;
                    if (depth == 0) return i + 1;
                }
            }
            return -1;
        }
    }

    /** 呼び出しの実引数。文字列リテラルの中の括弧やカンマで区切らない。 */
    private static final class Arguments {

        static List<String> split(String source, int open) {
            int depth = 0;
            List<String> args = new ArrayList<>();
            StringBuilder current = new StringBuilder();

            for (int i = open; i < source.length(); i++) {
                char c = source.charAt(i);

                if (c == '"' || c == '\'') {
                    int end = skipLiteral(source, i);
                    current.append(source, i, end);
                    i = end - 1;
                    continue;
                }
                if (c == '(' || c == '[' || c == '{') {
                    depth++;
                    if (depth == 1) continue;
                }
                if (c == ')' || c == ']' || c == '}') {
                    depth--;
                    if (depth == 0) {
                        if (current.toString()
                            .trim()
                            .isEmpty() && args.isEmpty()) return args;
                        args.add(current.toString());
                        return args;
                    }
                }
                if (c == ',' && depth == 1) {
                    args.add(current.toString());
                    current.setLength(0);
                    continue;
                }
                current.append(c);
            }
            return null; // 閉じ括弧が見つからない
        }

        private static int skipLiteral(String source, int start) {
            char quote = source.charAt(start);
            int i = start + 1;
            while (i < source.length()) {
                char c = source.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                i++;
                if (c == quote) break;
            }
            return i;
        }
    }
}
