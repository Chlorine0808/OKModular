package ruiseki.okmodular.structure.pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * パターンローダに呼ぶ側が居ることの検証。
 *
 * ============================================
 * なぜソース走査なのか
 * ============================================
 *
 * `MachineryModule` は FML のイベントを受ける入口で、ユニットテストからは組めない。
 * ローダ自身の振る舞いは `StructurePatternLoaderTest` が縛っているので、
 * ここで見るのは**そのローダが起動経路とリロード経路の両方から呼ばれているか**だけ。
 *
 * この mod は「機能は書かれていたのに、それを使う側が無い」を 10 件出している
 * （`Conditions.registerDefaults()` は preInit から呼ばれておらず、
 * **条件が一度も効いていなかった**）。同じ形を作らないために静的に捕まえる。
 *
 * ============================================
 * なぜ順序まで見るのか
 * ============================================
 *
 * レシピの `structure` 入出力はパターン名を解決する。**レシピを先に読むと、
 * 参照先がまだ 1 つも登録されていない**ので、書いた本人には「名前を間違えた」ように見える
 * 警告が全件に出る。順序は仕様であって好みではない。
 */
@DisplayName("パターンローダの配線")
public class StructurePatternWiringTest {

    private static final Path MODULE = Paths.get("src/main/java/ruiseki/okmodular/MachineryModule.java");

    @Test
    @DisplayName("postInit がパターンを読んでいる")
    public void test起動時に読まれる() {
        String body = methodBody(read(MODULE), "public void postInit(");

        assertTrue(
            body.contains("StructurePatternLoader"),
            "postInit がパターンを読んでいない。ファイルを置いても誰も読まないので、" + "レシピからは常に「そんなパターンは無い」に見える");
    }

    @Test
    @DisplayName("reload がパターンも読み直している")
    public void testリロードでも読まれる() {
        String body = methodBody(read(MODULE), "public void reload(");

        assertTrue(
            body.contains("StructurePatternLoader"),
            "/ok reload がパターンを読み直していない。構造とレシピだけ新しくなり、" + "パターンだけ起動時のまま取り残される");
    }

    @Test
    @DisplayName("パターンはレシピより先に読まれる")
    public void testレシピより先() {
        String source = read(MODULE);

        assertOrder(methodBody(source, "public void postInit("), "postInit");
        assertOrder(methodBody(source, "public void reload("), "reload");
    }

    private static void assertOrder(String body, String where) {
        int patterns = body.indexOf("StructurePatternLoader");
        int recipes = body.indexOf("RecipeLoader");

        assertTrue(patterns >= 0, where + " がパターンを読んでいない");
        assertTrue(recipes >= 0, where + " がレシピを読んでいない — このテストの前提が変わったなら書き直すこと");
        assertTrue(patterns < recipes, where + " でレシピのほうが先に読まれている。レシピが参照するパターンがまだ 1 つも無い");
    }

    /**
     * メソッド本体を波括弧の対応で切り出す。ファイル全体への `contains` だと
     * **別のメソッドにある呼び出しを拾って**配線されていないのに緑になる。
     */
    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "メソッドが見つからない: " + signature + " — 改名されたならこのテストの参照も直すこと");

        int brace = source.indexOf('{', start);
        assertTrue(brace >= 0, "メソッド本体の開始が見つからない: " + signature);

        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(brace, i + 1);
            }
        }
        throw new AssertionError("メソッド本体の終端が見つからない: " + signature);
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file.toAbsolutePath(), e);
        }
    }
}
