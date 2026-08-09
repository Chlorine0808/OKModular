package ruiseki.okmodular;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * docs に載っている JSON の例が、**そのまま書ける形**であることの検証。
 *
 * ============================================
 * 例は仕様書より読まれる
 * ============================================
 *
 * レシピや構造を書く人が最初に見るのは表ではなく**動く例**で、コピーして直して使う。
 * 例が壊れていると、書いた人は自分の書き方を疑い、直そうとして別の形に崩す。
 * しかもパーサは知らないキーを黙って無視するので、**どこが悪いのか出力から分からない**。
 *
 * 実際に踏んだ形が 2 つある。ブロック ID の `:meta` を書き落とすと
 * `BlockOutput` はメタ 0 として読むので**別のブロックが置かれ**、
 * 構造が成立しなくなって機械が壊れる。`minecraft:iron_nugget` のように
 * **1.7.10 に存在しない ID** を書いても、JSON としては何も間違っていない。
 *
 * ============================================
 * どこまで確かめられるか
 * ============================================
 *
 * ID が実在するかは `GameRegistry` が要るのでゲームの外では見られない（D-2）。
 * だが**構文と書式**は見られるので、そこまでを常設にする。
 *
 * `run/` の実サンプルを読まないのは、あそこが gitignore だから。
 * 手元にしか無いファイルに依存するテストは、他の誰の環境でも落ちる。
 */
@DisplayName("docs の JSON 例")
public class DocumentedJsonExampleTest {

    private static final Path DOCS = Paths.get("docs");

    /**
     * ブロック ID を書く位置のキー。`mod:name` だけだとメタが 0 に落ちる。
     * <p>
     * これが効くのは**メタを持つブロックだけ**なので、`minecraft:glowstone` のように
     * メタが 1 種類しか無いものまで縛ると例が読みにくくなる。
     * だから「この mod のブロックで、メタを持つと分かっているもの」だけを見る。
     */
    private static final List<String> METAED = Arrays.asList("okmodular:casing_reinforced", "okmodular:glass");

    @Test
    @DisplayName("すべての JSON 例が構文として妥当")
    public void test壊れた例が無い() {
        List<String> broken = new ArrayList<>();
        int checked = 0;

        for (Path file : markdownFiles()) {
            int index = 0;
            for (String block : jsonBlocksOf(JavaSource.read(file))) {
                index++;
                if (block.contains("...")) continue; // 省略記法。JSON としては壊れているのが正しい
                checked++;
                String failure = firstFailure(block);
                if (failure != null) broken.add(DOCS.relativize(file) + " の " + index + " 個目: " + failure);
            }
        }

        assertTrue(checked >= 20, "JSON の例が 20 個未満しか見つからない。走査が壊れている（" + checked + " 個）");
        assertTrue(broken.isEmpty(), broken + "。コピーして使う人が最初に踏む");
    }

    @Test
    @DisplayName("メタを持つブロックの例がメタを省略していない")
    public void testメタ落ちが無い() {
        List<String> bare = new ArrayList<>();

        for (Path file : markdownFiles()) {
            for (String block : jsonBlocksOf(JavaSource.read(file))) {
                for (String id : METAED) {
                    Matcher hits = Pattern.compile("\"" + Pattern.quote(id) + "\"")
                        .matcher(block);
                    if (hits.find()) bare.add(DOCS.relativize(file) + " の " + id);
                }
            }
        }

        assertTrue(
            bare.isEmpty(),
            bare + " がメタ無しで書かれている。読む側は 3 つ目のコロン区切りが無ければメタ 0 とみなすので、" + "**別のブロックが置かれる**。構造が成立しなくなると、機械が解けて直す手段も消える");
    }

    @Test
    @DisplayName("例に使う mod 接頭辞が実在するものだけ")
    public void test知らない_mod_の_ID_が無い() {
        // ID の実在は確かめられないが、**接頭辞の綴り**なら確かめられる。
        // okmodular が okmachinery になっているような取り違えはここで落ちる。
        List<String> unknown = new ArrayList<>();
        // `modid` は「あなたの mod の ID」を表す docs の書き方。実在しないのが正しい。
        List<String> known = Arrays.asList(
            "minecraft",
            "okmodular",
            "okcore",
            "modid",
            "Thaumcraft",
            "Botania",
            "Mekanism",
            "appliedenergistics2");

        for (Path file : markdownFiles()) {
            for (String block : jsonBlocksOf(JavaSource.read(file))) {
                Matcher ids = Pattern.compile("\"([a-zA-Z][a-zA-Z0-9_]*):[a-zA-Z0-9_.]+(?::[0-9*]+)?\"")
                    .matcher(block);

                while (ids.find()) {
                    if (!known.contains(ids.group(1))) unknown.add(DOCS.relativize(file) + " の " + ids.group());
                }
            }
        }

        assertTrue(unknown.isEmpty(), unknown + " の接頭辞が知らない mod。綴り違いなら黙って解決に失敗する");
    }

    // ============================================================

    /**
     * 断片は `{ }` で包む。docs には `"tierMap": { ... }` のように
     * **キーから始まる抜粋**が多く、それ自体は JSON として不完全だが、
     * 例としては正しい。包めば構文だけは確かめられる。
     */
    /**
     * ブロック全体として読めなければ、**1 行ずつの抜粋**として読み直す。
     * <p>
     * docs には「こう書けます」を 3 行並べたブロックがある。
     *
     * <pre>
     * "nbt": "nbt('customData.level') = 7"
     * "nbt": "nbt('temperature') += 50"
     * </pre>
     *
     * 3 つで 1 つのオブジェクトではないので、まとめて包むとカンマが無くて壊れる。
     * かといって行ごとに割ると、複数行にまたがる抜粋が壊れる。**先に全体、駄目なら行ごと**。
     *
     * @return 壊れている理由。読めたなら null
     */
    private static String firstFailure(String block) {
        try {
            readAll(wrapFragment(block));
            return null;
        } catch (IOException | JsonSyntaxException whole) {
            for (String line : block.split("\r?\n")) {
                if (line.trim()
                    .isEmpty()) continue;
                try {
                    readAll(wrapFragment(line));
                } catch (IOException | JsonSyntaxException single) {
                    return whole.getMessage();
                }
            }
            return null;
        }
    }

    /**
     * 先頭から末尾まで、値がいくつ並んでいても読み切る。
     * <p>
     * docs には「こういう書き方もできます」を並べたブロックがあり、
     * 独立したオブジェクトがカンマ無しで積まれている。それ自体は 1 つの JSON ではないが、
     * 例としては正しい。**括弧の対応や `:` の抜け**という本当に困る壊れ方は、
     * 緩めた読み方でもそのまま落ちる。
     */
    private static void readAll(String json) throws IOException {
        try (com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(
            new java.io.StringReader(json))) {
            reader.setLenient(true);
            while (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) {
                new JsonParser().parse(reader);
            }
        }
    }

    private static String wrapFragment(String block) {
        String trimmed = block.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed;
        return "{" + trimmed.replaceAll(",\\s*$", "") + "}";
    }

    private static List<String> jsonBlocksOf(String markdown) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inside = false;

        for (String line : markdown.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                if (inside) {
                    blocks.add(current.toString());
                    current.setLength(0);
                    inside = false;
                } else {
                    inside = trimmed.substring(3)
                        .trim()
                        .equalsIgnoreCase("json");
                }
                continue;
            }
            if (inside) current.append(line)
                .append('\n');
        }
        return blocks;
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
}
