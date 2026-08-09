package ruiseki.okmodular.api.recipe.parser;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
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
 * JSON のキーについて、**docs と実装が同じ集合を指している**ことの検証。
 *
 * ============================================
 * 片方向ずつ、別々のバグになる
 * ============================================
 *
 * <b>docs にあって実装に無い</b>キーは、書いた人を疑わせる。
 * パーサは知らないキーを黙って無視するので、エラーもログも出ない。
 * 実際 `tierMap` がそうだった。構造 JSON の表にも説明にも例にも載っているのに
 * **実装のどこにも無い**綴りで、正しくは `mappings` の中の `component` と `tiers`。
 * 書いた人からは「Tier を設定したのにレシピが通らない」としか見えない。
 *
 * <b>実装にあって docs に無い</b>キーは、誰にも使われない機能になる。
 * `_SSOT.md` の「機能は書かれていたのに使う側が無い」の、使う側が**人間**の版。
 * `amountExpr` は全ての入出力型が読むのに docs に一言も無かった。
 *
 * ============================================
 * キーの集め方
 * ============================================
 *
 * 実装側は 2 通りある。`json.get("x")` の形で直接読むものと、
 * `register("x", ...)` でレジストリに預けるもの。`duration` や `inputs` は後者なので、
 * 前者だけを見ていると**実装にあるのに無いことになる**。
 *
 * docs 側は ```json で囲まれたブロックの中の `"x":` だけを見る。
 * 表や本文にはキー以外の名前（型名・変数名・関数名）も並ぶので、
 * 全文から拾うと実装に無い名前だらけになる。
 */
@DisplayName("JSON のキーが docs と実装で一致する")
public class DocumentedJsonKeyTest {

    private static final Path DOCS = Paths.get("docs");

    /**
     * JSON の中では**書く人が名前を決める**位置にあるキー。
     *
     * 構造の記号（`mappings` の `"G"`）、部品の名前（`component` の値に対応する `casing`）、
     * 外部ポートの名前（`externalPorts` の `energyInput`）。
     * どれもパーサが名前で探すものではないので、実装側に現れないのが正しい。
     *
     * 1 文字の大文字は記号として機械で除くが、それ以外はここに書く。
     */
    private static final List<String> AUTHOR_NAMED = Arrays
        .asList("casing", "glass", "energyInput", "fluidInput", "itemOutput");

    /**
     * パーサが読むのに docs に一言も無いキー。**空にするのが目標**。
     *
     * 全部レシピ / 構造 JSON に書ける本物の項目で、書けば効く。
     * 効くのに誰も知らないので、**使われないまま壊れていても気づけない**。
     *
     * <pre>
     * amountExpr                     全入出力型の量を式で書く
     * id                             入出力を特定のポート番号に結びつける
     * nbtmatch / nbtlist / op / ops  item / block の NBT 照合と書き換え
     * maxIn / maxOut / minIn / minOut  map_range 式の四隅
     * data                           block / block_below 条件のメタデータ
     * modifierKey                    bonus デコレータが読む機械修飾子
     * controllerOffset / offsets / externalPorts / tierStructures  構造の配置
     * inputOnly / outputOnly / bothOnly  構造要件が許すポートの向き
     * modVersion                     構造 JSON の移行に使う版
     * </pre>
     *
     * レジストリに預ける名前もここに来る。式の構造化形式 4 つ
     * （`machine_property` / `component_tier` / `nbt_assignment` / `string_literal`）は
     * `ExpressionsParser` が受け付けるのに docs に無い。
     *
     * `recipeprocessed` / `recipeprocessedtype` だけは事情が違う。
     * **別名の `recipe_count` / `recipe_types_count` が docs に載っている**ので書く人には届いている。
     * 正規名の方を載せる理由が無いので、ここに置く。
     * この 2 つを調べたついでに `is_waiting` と `recipe_types_count` が
     * 本当にどこにも無いことが分かったので、そちらは docs に足した。
     */
    private static final List<String> UNDOCUMENTED = Arrays.asList(
        "amountExpr",
        "bothOnly",
        "component_tier",
        "controllerOffset",
        "data",
        "externalPorts",
        "id",
        "inputOnly",
        "machine_property",
        "maxIn",
        "maxOut",
        "minIn",
        "minOut",
        "modVersion",
        "modifierKey",
        "nbt_assignment",
        "nbtlist",
        "nbtmatch",
        "offsets",
        "op",
        "ops",
        "outputOnly",
        "recipeprocessed",
        "recipeprocessedtype",
        "string_literal",
        "tierStructures");

    @Test
    @DisplayName("docs の JSON 例に出るキーを実装が読んでいる")
    public void test誰も読まないキーを書かせていない() {
        Set<String> parsed = parserKeys();
        List<String> phantom = new ArrayList<>();

        for (String key : documentedKeys()) {
            if (key.length() == 1 && Character.isUpperCase(key.charAt(0))) continue;
            if (AUTHOR_NAMED.contains(key)) continue;
            if (!parsed.contains(key)) phantom.add(key);
        }

        assertTrue(
            phantom.isEmpty(),
            phantom + " は docs の JSON 例に出てくるが、読む側が実装に居ない。" + "パーサは知らないキーを黙って無視するので、書いた人には自分が間違えたようにしか見えない");
    }

    @Test
    @DisplayName("実装が読むキーが docs のどこかに載っている")
    public void test隠しキーが増えていない() {
        String docs = allDocs();
        List<String> hidden = new ArrayList<>();

        for (String key : parserKeys()) {
            if (UNDOCUMENTED.contains(key)) continue;
            if (!Pattern.compile("\\b" + Pattern.quote(key) + "\\b")
                .matcher(docs)
                .find()) hidden.add(key);
        }

        assertTrue(hidden.isEmpty(), hidden + " をパーサが読むのに docs に一言も無い。書けば効くのに誰も知らないので、" + "壊れても気づかれない");
    }

    @Test
    @DisplayName("隠しキーの一覧が古くなっていない")
    public void test免除リストが正しい() {
        Set<String> parsed = parserKeys();
        String docs = allDocs();

        for (String key : UNDOCUMENTED) {
            assertTrue(parsed.contains(key), key + " はもうパーサが読んでいない。UNDOCUMENTED から消すこと");
            assertTrue(
                !Pattern.compile("\\b" + Pattern.quote(key) + "\\b")
                    .matcher(docs)
                    .find(),
                key + " は docs に書かれた。UNDOCUMENTED から消すこと");
        }
    }

    @Test
    @DisplayName("走査そのものが空振りしていない")
    public void test走査が効いている() {
        Set<String> parsed = parserKeys();
        Set<String> documented = documentedKeys();

        assertTrue(parsed.size() >= 50, "パーサが読むキーが 50 個未満。走査が壊れている（" + parsed.size() + " 個）");
        assertTrue(documented.size() >= 20, "docs の JSON 例のキーが 20 個未満。走査が壊れている（" + documented.size() + " 個）");

        // レジストリ経由で登録されるキーは `.get("x")` の形で現れない。
        // ここが漏れると「docs にあって実装に無い」が大量に出る。
        assertTrue(parsed.contains("duration"), "register(\"duration\", ...) を拾えていない。レジストリ側の走査が壊れている");
        assertTrue(parsed.contains("inputs"), "register(\"inputs\", ...) を拾えていない。レジストリ側の走査が壊れている");
    }

    // ============================================================

    /** 実装が読むキー。直接読むものと、レジストリに預けるものの両方。 */
    private static Set<String> parserKeys() {
        Set<String> found = new TreeSet<>();
        Pattern direct = Pattern
            .compile("\\.(?:has|get|getAsJsonObject|getAsJsonArray|getAsJsonPrimitive)\\(\"([a-zA-Z_]\\w*)\"\\)");
        Pattern registered = Pattern.compile("\\bregister\\w*\\(\\s*\"([a-zA-Z_]\\w*)\"");

        for (Path file : JavaSource.allMainSources()) {
            String source = JavaSource.stripComments(JavaSource.read(file));
            if (!source.contains("com.google.gson")) continue;

            collect(found, direct.matcher(source));
            collect(found, registered.matcher(source));
        }
        return found;
    }

    /** docs の ```json ブロックに `"x":` の形で出てくるキー。 */
    private static Set<String> documentedKeys() {
        Set<String> found = new LinkedHashSet<>();
        Pattern key = Pattern.compile("\"([a-zA-Z_]\\w*)\"\\s*:");

        for (Path file : markdownFiles()) {
            collect(found, key.matcher(jsonBlocksOf(JavaSource.read(file))));
        }
        return found;
    }

    private static String allDocs() {
        StringBuilder joined = new StringBuilder();
        for (Path file : markdownFiles()) joined.append(JavaSource.read(file))
            .append('\n');
        return joined.toString();
    }

    private static List<Path> markdownFiles() {
        List<Path> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(DOCS)) {
            files.filter(
                p -> p.toString()
                    .endsWith(".md"))
                .forEach(found::add);
        } catch (IOException e) {
            throw new UncheckedIOException("辿れない: " + DOCS, e);
        }
        Collections.sort(found);
        return found;
    }

    /** ```json で囲まれた部分だけを繋げたもの。 */
    private static String jsonBlocksOf(String markdown) {
        StringBuilder blocks = new StringBuilder();
        boolean inside = false;

        for (String line : markdown.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inside = !inside && trimmed.substring(3)
                    .trim()
                    .equalsIgnoreCase("json");
                continue;
            }
            if (inside) blocks.append(line)
                .append('\n');
        }
        return blocks.toString();
    }

    private static void collect(Set<String> into, Matcher hits) {
        while (hits.find()) into.add(hits.group(1));
    }
}
