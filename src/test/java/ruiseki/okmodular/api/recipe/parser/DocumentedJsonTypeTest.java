package ruiseki.okmodular.api.recipe.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * docs が JSON の `type` として約束している名前に、実際にパーサが登録されていることの検証
 * （release_freeze F-5）。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `block_nbt` は「docs に書いてあるのに実装が無い」名前だった。見つかったのは
 * **雑な grep でたまたま 1 件出たから**で、docs 全体を照合したわけではなかった。
 * 残りが何件あるのかは分かっていなかった。
 *
 * docs はレシピ・構造 JSON の**仕様の正本**なので、そこに載っていて動かない名前は
 * 「書いたのに黙って無視される」形でユーザーに届く。**登録の有無は静的に照合できる**。
 *
 * `DocumentedVariableTest` が式名について行っていることの、JSON の型名版。
 *
 * ============================================
 * 何を照合しているか
 * ============================================
 *
 * <pre>
 * ① JSON 例の中の type: "X"     → いずれかのレジストリに X がある
 * ② 条件の一覧表の 1 列目        → ConditionParserRegistry に登録がある
 * ③ デコレータの一覧表の 1 列目  → DecoratorParser に登録がある
 * ④ 「利用可能なタイプ:」の列挙  → RequirementRegistry に登録がある
 * </pre>
 *
 * ① を**レジストリ横断**で見るのは、docs 上の `type` がレシピ入出力・条件・デコレータ・
 * 構造 requirement のどれにも現れるから。どこにも無い名前だけを落とす。
 *
 * ============================================
 * 走査が壊れたときに黙って緑にならないこと
 * ============================================
 *
 * 抽出が 0 件になれば「照合対象が無い」ので全部通ってしまう。**それは
 * 「docs が正しい」ではなく「テストが何も見ていない」。** よって各抽出について
 * **件数が 0 でないこと**を先に確かめる。`ConditionsRegistrationTest` が
 * `registerDefaults` の走査に対して同じ自己検査を持っているのと同じ理由。
 *
 * ============================================
 * なぜレジストリを実行時に引かずソースを読むのか
 * ============================================
 *
 * どのレジストリも「その名前を知っているか」を訊く公開メソッドを持たない
 * （`parse()` に本物の JSON を渡すしかなく、パーサ側が中身を要求する）。
 * テストのために本番へ問い合わせ口を足すより、**登録が書いてある場所を読む**ほうが
 * 変更に対して素直で、この mod の既存のソース走査テストとも揃う。
 */
@DisplayName("docs が約束している JSON の型名")
public class DocumentedJsonTypeTest {

    private static final Path DOCS = Paths.get("docs");
    private static final Path SOURCE = Paths.get("src/main/java");

    private static final Path INPUT_REGISTRY = SOURCE
        .resolve("ruiseki/okmodular/api/recipe/parser/InputParserRegistry.java");
    private static final Path OUTPUT_REGISTRY = SOURCE
        .resolve("ruiseki/okmodular/api/recipe/parser/OutputParserRegistry.java");
    private static final Path DECORATOR_REGISTRY = SOURCE
        .resolve("ruiseki/okmodular/api/recipe/parser/DecoratorParser.java");
    private static final Path CONDITION_REGISTRY = SOURCE.resolve("ruiseki/okmodular/api/condition/Conditions.java");
    private static final Path REQUIREMENT_REGISTRY = SOURCE
        .resolve("ruiseki/okmodular/api/structure/io/RequirementRegistry.java");

    /**
     * `register("x", ...)` / `alias("x", ...)` の第 1 引数。
     * 名前が次の行に来る書き方（Conditions の comparison など）があるので改行をまたぐ。
     */
    private static final Pattern REGISTRATION = Pattern.compile("(?:register|alias)\\(\\s*\"([^\"]+)\"");

    /** `"type": "x"` と、表のセルに出てくる `type: "x"` の両方。 */
    private static final Pattern DOCUMENTED_TYPE = Pattern.compile("\"?type\"?\\s*:\\s*\"([A-Za-z_][A-Za-z0-9_]*)\"");

    /** 「利用可能なタイプ: `a`, `b`, ...」の行。 */
    private static final Pattern AVAILABLE_TYPES = Pattern.compile("(?:Available types|利用可能なタイプ)\\s*[:：](.*)");

    private static final Pattern BACKTICKED = Pattern.compile("`([^`]+)`");

    /**
     * 一覧表のヘッダ 1 列目に出る綴り。en / jp の両方。
     * ここに合致する表の 1 列目だけを型名として読む。
     */
    private static final Set<String> TYPE_COLUMN_HEADERS = new LinkedHashSet<>(
        Arrays.asList("type", "type name", "type 名"));

    /**
     * `InputParserRegistry.parse` が `register(...)` ではなく分岐で受けている名前。
     * BlockInput は JSON に `"type": "block"` と書き出すのに、登録名は `symbol` になっている。
     */
    private static final Set<String> INPUT_SPECIAL_CASES = new LinkedHashSet<>(Arrays.asList("block"));

    // ========== ① JSON 例の type ==========

    @Test
    @DisplayName("JSON 例に書かれた type は、どこかのレジストリが知っている")
    public void testJSON例の型名にパーサがある() {
        Set<String> documented = new LinkedHashSet<>();
        for (Path doc : docs()) {
            Matcher matcher = DOCUMENTED_TYPE.matcher(read(doc));
            while (matcher.find()) documented.add(matcher.group(1));
        }

        assertFalse(documented.isEmpty(), "docs から type が 1 件も抽出できていない。走査か正規表現が壊れている");

        Set<String> known = new LinkedHashSet<>();
        known.addAll(registered(INPUT_REGISTRY));
        known.addAll(INPUT_SPECIAL_CASES);
        known.addAll(registered(OUTPUT_REGISTRY));
        known.addAll(registered(DECORATOR_REGISTRY));
        known.addAll(registered(CONDITION_REGISTRY));
        known.addAll(registered(REQUIREMENT_REGISTRY));

        List<String> missing = missingFrom(known, documented);

        assertTrue(
            missing.isEmpty(),
            "docs が type として書いているのに、どのレジストリにも登録が無い: " + missing + " — 書いても黙って無視されるので、実装するか docs から消すこと");
    }

    // ========== ② 条件の一覧表 ==========

    @Test
    @DisplayName("条件の一覧表に載っている型は登録されている")
    public void test条件の一覧表が実装と揃っている() {
        Set<String> documented = typeTableEntries("CONDITIONS.md");
        assertFalse(documented.isEmpty(), "CONDITIONS.md から条件の型が抽出できていない。表の見出しが変わった可能性がある");

        List<String> missing = missingFrom(registered(CONDITION_REGISTRY), documented);

        assertTrue(missing.isEmpty(), "CONDITIONS.md が載せているのに ConditionParserRegistry に登録が無い: " + missing);
    }

    // ========== ③ デコレータの一覧表 ==========

    @Test
    @DisplayName("デコレータの一覧表に載っている型は登録されている")
    public void testデコレータの一覧表が実装と揃っている() {
        Set<String> documented = typeTableEntries("DECORATORS.md");
        assertFalse(documented.isEmpty(), "DECORATORS.md からデコレータの型が抽出できていない。表の見出しが変わった可能性がある");

        List<String> missing = missingFrom(registered(DECORATOR_REGISTRY), documented);

        assertTrue(missing.isEmpty(), "DECORATORS.md が載せているのに DecoratorParser に登録が無い: " + missing);
    }

    // ========== ④ 構造 requirement の列挙 ==========

    @Test
    @DisplayName("「利用可能なタイプ」に並んでいる requirement は登録されている")
    public void test構造の要求タイプが実装と揃っている() {
        Set<String> documented = new LinkedHashSet<>();
        for (Path doc : docs()) {
            Matcher line = AVAILABLE_TYPES.matcher(read(doc));
            while (line.find()) {
                Matcher token = BACKTICKED.matcher(line.group(1));
                while (token.find()) documented.add(token.group(1));
            }
        }

        assertFalse(documented.isEmpty(), "「利用可能なタイプ」の行が見つからない。docs の書き方が変わった可能性がある");

        List<String> missing = missingFrom(registered(REQUIREMENT_REGISTRY), documented);

        assertTrue(
            missing.isEmpty(),
            "docs が列挙しているのに RequirementRegistry に登録が無い: " + missing + " — 構造 JSON にそう書いても requirement が null になって落ちる");
    }

    // ========== 抽出 ==========

    /** 型名の一覧表（ヘッダ 1 列目が type 系）の 1 列目を集める。 */
    private static Set<String> typeTableEntries(String fileName) {
        Set<String> entries = new LinkedHashSet<>();

        for (Path doc : docs()) {
            if (!doc.getFileName()
                .toString()
                .equals(fileName)) continue;

            String[] lines = read(doc).split("\\R");
            boolean inTypeTable = false;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();

                if (!line.startsWith("|")) {
                    inTypeTable = false;
                    continue;
                }

                String first = firstCell(line);

                if (!inTypeTable) {
                    // ヘッダ行か。次の行が区切り（|---|）であることまで見る
                    boolean isHeader = TYPE_COLUMN_HEADERS.contains(
                        first.toLowerCase()
                            .trim());
                    boolean separatorFollows = i + 1 < lines.length && lines[i + 1].trim()
                        .startsWith("|-");
                    if (isHeader && separatorFollows) {
                        inTypeTable = true;
                        i++; // 区切り行を飛ばす
                    }
                    continue;
                }

                Matcher token = BACKTICKED.matcher(first);
                if (token.find()) entries.add(token.group(1));
            }
        }

        return entries;
    }

    private static String firstCell(String tableLine) {
        String body = tableLine.substring(1);
        int end = body.indexOf('|');
        return (end < 0 ? body : body.substring(0, end)).trim();
    }

    /** レジストリのソースに書かれている登録名。 */
    private static Set<String> registered(Path registrySource) {
        assertTrue(Files.exists(registrySource), "レジストリのソースが見つからない: " + registrySource + " — 移動したならこのテストの参照も直すこと");

        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = REGISTRATION.matcher(read(registrySource));
        while (matcher.find()) names.add(matcher.group(1));

        assertFalse(names.isEmpty(), "登録名が 1 件も読み取れない: " + registrySource + " — 登録の書き方が変わった可能性がある");
        return names;
    }

    private static List<String> missingFrom(Set<String> known, Set<String> documented) {
        List<String> missing = new ArrayList<>();
        for (String name : documented) {
            if (!known.contains(name)) missing.add(name);
        }
        return missing;
    }

    private static List<Path> docs() {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(DOCS)) {
            walk.filter(
                path -> path.toString()
                    .endsWith(".md"))
                .forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException("docs が読めない: " + DOCS.toAbsolutePath(), e);
        }
        assertFalse(files.isEmpty(), "docs に .md が 1 件も無い。走査の起点が間違っている");
        return files;
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file, e);
        }
    }
}
