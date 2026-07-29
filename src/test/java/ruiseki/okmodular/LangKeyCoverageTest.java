package ruiseki.okmodular;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ソースがリテラルで引く lang キーが、両言語に存在することの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `post_split_issues.md` が「分離の不具合は起動前に静的に洗える」として挙げる 3 本のうちの
 * **lang の差分**（コードが引くキー集合 − lang が持つキー集合）をテストにしたもの。
 * この方法で過去に見つかったもの:
 *
 * - GUI / NEI が生キー表示になっていた件（移植漏れ 20 件）
 * - 本体の lang キーを上書きしていた件
 * - `gui.status.*` の 15 件（訳が親 mod にしか無く、単体導入で生キーが出ていた）
 * - **`waila.me.*` の 3 件**（このテストが見つけた。ME ポートの WAILA に生キーが出ていた）
 *
 * キーが無くても例外は出ない。**生のキー文字列が画面に出る**という形で壊れる。
 *
 * ============================================
 * 実行時に組み立てるキーは扱えない
 * ============================================
 *
 * `"gui.status." + id` のように連結で作られるキーは、**リテラルとして存在しない**ので
 * この方法では見つからない。そちらは enum を回す形の別テストが受け持つ
 * （`ErrorReasonLangCoverageTest` / `PortLangCoverageTest`）。
 *
 * 末尾が `.` のリテラルは連結の断片なので除外する。**どの断片がどのテストに
 * 覆われているかを {@link #RUNTIME_PREFIXES} に書き出してある** — 覆われていないものも
 * 正直に「無い」と書く。そこが次に足すべきテストの一覧になる。
 *
 * ============================================
 */
@DisplayName("lang キーの網羅")
public class LangKeyCoverageTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");
    private static final String[] LANGUAGES = { "en_US", "ja_JP" };

    /** 翻訳を引く呼び出しの第 1 引数。 */
    private static final Pattern LOOKUP = Pattern.compile(
        "(?:localize|translateToLocal|translateToLocalFormatted|ChatComponentTranslation)\\s*\\(\\s*\"([^\"]+)\"");

    /**
     * 実行時に連結して使われる断片と、それを覆っているテスト。
     * <p>
     * **「無い」と書いてあるものは、次にテストを足す候補。** 断片だけを見て
     * 「覆われている」と思い込まないために、意図的に列挙して書き出す。
     */
    private static final Map<String, String> RUNTIME_PREFIXES;
    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("gui.port_type.", "PortLangCoverageTest が IPortType.Type を回して覆っている");
        map.put("okmodular.condition.weather.", "覆うテストは無い。WeatherCondition の enum を回せば覆える");
        map.put("gui.craftingState.", "覆うテストは無い");
        map.put("machinery.design.", "覆うテストは無い。ケーシングのデザイン名は JSON 由来で、集合が静的に決まらない");
        map.put("okmodular.component.", "覆うテストは無い。コンポーネント名は構造 JSON 由来");
        map.put("structure.", "覆うテストは無い。構造名は JSON 由来");
        RUNTIME_PREFIXES = Collections.unmodifiableMap(map);
    }

    /**
     * 訳を持たないと決めたキーと、その理由。
     * <p>
     * **他 mod が提供するキーはここに書く。** 空のままにしてあるのは、
     * 現状 OKModular が引くキーは全て自前で持っているべきものだから。
     */
    private static final Map<String, String> PROVIDED_ELSEWHERE = Collections.unmodifiableMap(new LinkedHashMap<>());

    @Test
    @DisplayName("リテラルで引くキーは両言語に存在する")
    public void testリテラルのキーが揃っている() throws IOException {
        Set<String> referenced = referencedKeys();
        assertFalse(referenced.isEmpty(), "キーが 1 つも見つからない。走査が壊れている");

        List<String> problems = new ArrayList<>();
        for (String language : LANGUAGES) {
            Map<String, String> entries = load(language);
            for (String key : referenced) {
                if (PROVIDED_ELSEWHERE.containsKey(key)) continue;
                if (!entries.containsKey(key)) {
                    problems.add(language + ": " + key + " が無い");
                } else if (entries.get(key)
                    .isEmpty()) {
                        problems.add(language + ": " + key + " が空");
                    }
            }
        }

        assertTrue(problems.isEmpty(), "訳が無いキーは画面に生のまま出る。lang に足すか、理由つきで PROVIDED_ELSEWHERE に足すこと: " + problems);
    }

    @Test
    @DisplayName("連結で作られる断片はすべて把握されている")
    public void test断片が把握されている() {
        List<String> unknown = new ArrayList<>();
        for (String fragment : rawLiterals()) {
            if (!fragment.endsWith(".")) continue;
            if (!RUNTIME_PREFIXES.containsKey(fragment)) unknown.add(fragment);
        }
        assertTrue(unknown.isEmpty(), "実行時に組み立てるキーの断片が増えている。何が覆うのかを RUNTIME_PREFIXES に書くこと: " + unknown);
    }

    @Test
    @DisplayName("把握リストに実在しない断片が残っていない")
    public void test把握リストが古くなっていない() {
        Set<String> literals = rawLiterals();
        for (String fragment : RUNTIME_PREFIXES.keySet()) {
            assertTrue(literals.contains(fragment), fragment + " はもうコードに無い。RUNTIME_PREFIXES から消すこと");
        }
    }

    // ========== 走査 ==========

    /** 末尾が `.` でない = そのままキーとして使われるもの。 */
    private static Set<String> referencedKeys() {
        Set<String> keys = new TreeSet<>();
        for (String literal : rawLiterals()) {
            if (!literal.endsWith(".")) keys.add(literal);
        }
        return keys;
    }

    private static Set<String> rawLiterals() {
        Set<String> literals = new TreeSet<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            files.filter(
                path -> path.toString()
                    .endsWith(".java"))
                .forEach(path -> {
                    Matcher matcher = LOOKUP.matcher(read(path));
                    while (matcher.find()) {
                        String literal = matcher.group(1);
                        // ドットを含まないものと空白を含むものは lang キーではない
                        // （デバッグ用の生文字列を ChatComponentTranslation に渡している箇所がある）。
                        if (literal.contains(".") && !literal.contains(" ")) literals.add(literal);
                    }
                });
        } catch (IOException e) {
            throw new UncheckedIOException("ソースツリーが読めない: " + SOURCE_ROOT.toAbsolutePath(), e);
        }
        return literals;
    }

    private static Map<String, String> load(String language) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        Path path = Paths.get("src/main/resources/assets/okmodular/lang/" + language + ".lang");
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int split = line.indexOf('=');
            if (split <= 0) continue;
            entries.put(line.substring(0, split), line.substring(split + 1));
        }
        return entries;
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file, e);
        }
    }
}
