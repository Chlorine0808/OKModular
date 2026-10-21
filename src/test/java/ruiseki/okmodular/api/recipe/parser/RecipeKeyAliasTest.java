package ruiseki.okmodular.api.recipe.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.structure.migration.StructureMigrationRegistry;
import ruiseki.okmodular.testsupport.JavaSource;

/**
 * レシピのキーの別名と、構造 JSON の版の刻印の検証。
 *
 * ============================================
 * 別名は同じ所へ行かなければ意味が無い
 * ============================================
 *
 * `duration` と `time`、`inputs` と `input` のように、レシピのキーには単複や
 * 言い換えの別名がある。**別名は同じパーサに結ばれてはじめて別名**で、
 * 片方だけ別の関数に結ぶと「`input` では動くのに `inputs` では動かない」になる。
 *
 * 書いた人は綴りを疑う。パーサは知らないキーを黙って無視するので、
 * どちらが正しいのかを教えてくれるものが無い。
 *
 * ============================================
 * 版の刻印は「後から直せる」の根拠
 * ============================================
 *
 * 構造 JSON は書き出すときに `modVersion` を刻む。読むときはその版を見て、
 * まだ当てていない migrator だけを適用する。
 *
 * 刻む値が **mod の版**だと、migrator を 1 つも持たない今でも
 * 「この版まで移行済み」と宣言してしまい、**あとから足した migrator が二度と走らない**。
 * だから刻むのは `getLatestDataVersion()` ── **登録済み migrator から導いた版**でなければならない。
 *
 * 今は migrator が 0 個なので `0.0.0` が刻まれる。
 * 「まだ何も移行していない」が正しく、将来の migrator は全部走れる。
 */
@DisplayName("レシピのキーの別名と版の刻印")
public class RecipeKeyAliasTest {

    private static final Path REGISTRY = JavaSource.MAIN.resolve("api/recipe/parser/RecipeParserRegistry.java");
    private static final Path WRITER = JavaSource.MAIN.resolve("api/structure/io/StructureJsonWriter.java");

    /** 同じ所へ行かなければならない別名の組。 */
    private static final List<String[]> ALIASES = Arrays.asList(
        new String[] { "duration", "time" },
        new String[] { "inputs", "input" },
        new String[] { "outputs", "output" },
        new String[] { "conditions", "condition" });

    @Test
    @DisplayName("別名は同じパーサに結ばれている")
    public void test別名が別の所へ行っていない() {
        Map<String, String> targets = registeredTargets();
        List<String> split = new ArrayList<>();

        for (String[] pair : ALIASES) {
            String first = targets.get(pair[0]);
            String second = targets.get(pair[1]);

            if (first == null || second == null) {
                split.add(pair[0] + " / " + pair[1] + " のどちらかが登録されていない");
            } else if (!first.equals(second)) {
                split.add(pair[0] + " → " + first + " だが " + pair[1] + " → " + second);
            }
        }

        assertTrue(split.isEmpty(), split + "。別名のつもりが別物になっている。片方でしか動かないキーは、書いた人に綴りを疑わせる");
    }

    @Test
    @DisplayName("版は登録済み migrator から導いている")
    public void test刻む版が_mod_の版でない() {
        String writer = JavaSource.stripComments(JavaSource.read(WRITER));

        assertTrue(
            writer.contains("StructureMigrationRegistry.getLatestDataVersion()"),
            "書き出しが getLatestDataVersion() 以外の版を刻んでいる。mod の版を刻むと、"
                + "migrator を 1 つも持たないうちから「移行済み」を宣言することになり、あとから足した migrator が走らなくなる");
    }

    @Test
    @DisplayName("migrator が無ければ版は 0.0.0")
    public void test何も移行していない版() {
        // ここが上がると、まだ書いていない migrator が「もう当たった」ことにされる。
        assertEquals("0.0.0", StructureMigrationRegistry.getLatestDataVersion(), "migrator を登録したなら、この値と一緒にこのテストも直すこと");
    }

    @Test
    @DisplayName("走査そのものが空振りしていない")
    public void test走査が効いている() {
        Map<String, String> targets = registeredTargets();

        assertTrue(targets.size() >= 12, "登録されたレシピのキーが 12 個未満。走査が壊れている: " + targets.keySet());
        assertTrue(targets.containsKey("duration"), "duration すら拾えていない");
    }

    /** キー名 → 結ばれている先の名前（関数の変数名か、ラムダなら "lambda"）。 */
    private static Map<String, String> registeredTargets() {
        Map<String, String> found = new LinkedHashMap<>();
        Matcher hits = Pattern.compile("register\\(\\s*\"([a-zA-Z_0-9]+)\"\\s*,\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\)")
            .matcher(JavaSource.stripComments(JavaSource.read(REGISTRY)));

        while (hits.find()) found.put(hits.group(1), hits.group(2));

        // ラムダで直接書かれたものも「登録されている」ことだけは数える。
        Matcher inline = Pattern.compile("register\\(\\s*\"([a-zA-Z_0-9]+)\"\\s*,\\s*\\(")
            .matcher(JavaSource.stripComments(JavaSource.read(REGISTRY)));
        while (inline.find()) found.putIfAbsent(inline.group(1), "lambda:" + inline.group(1));

        return found;
    }
}
