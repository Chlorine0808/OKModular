package ruiseki.okmodular.structure.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 構造 IO パターンのファイルローダの検証。
 *
 * ============================================
 * なぜ別ディレクトリなのか
 * ============================================
 *
 * n×n×n のパターンをレシピ JSON に並べるとレシピが読めなくなる。別ファイルにしたことで
 * **`StructureMigrationRegistry` に乗せられる** — `migrate(JsonElement)` は `modVersion` しか
 * 見ないので構造 JSON 専用ではなく、パターンのスキーマを後から migrator で直せる。
 * レシピ JSON 側に置いていたら、スキーマ版管理の無い側に永久に固定されていた。
 *
 * ただし**仕組みがあることと呼ばれることは別**で、この mod は「機能は書かれていたのに
 * 使う側が無い」を 10 件出している。だから最後のテストは `migrate()` の呼び出しそのものを見る。
 *
 * ============================================
 * なぜ 1 ファイルの失敗で全部止めないのか
 * ============================================
 *
 * 構造もレシピも「壊れたファイルだけ捨てて残りは読む」形で、収集したエラーは
 * `errors.txt` に出る。パターンだけ全滅させると、**1 文字の打ち間違いで機械が全部消える**。
 *
 * ============================================
 * なぜローダがエラーを溜めて返すのか
 * ============================================
 *
 * `StructureErrorCollector.collect` は OKCore の `JsonErrorCollector` へ流し、その先が
 * `OKCore.instance` を触る。**ゲーム外では null なので、報告した瞬間に NPE で落ちる**
 * （OKCore は D-2 で 1 行も変えない）。つまり収集器を直接呼ぶ設計だと、
 * **ローダの失敗経路がテストから一切触れない**。
 *
 * 溜めて呼び出し側に渡す形は `StructureValidationVisitor` が既に採っている形でもある。
 */
@DisplayName("構造 IO パターンのローダ")
public class StructurePatternLoaderTest {

    @TempDir
    File dir;

    private StructurePatternLoader loader;

    @BeforeEach
    public void setUp() {
        loader = StructurePatternLoader.getInstance();
        loader.clear();
    }

    private void write(String fileName, String json) {
        try {
            Files.write(
                new File(dir, fileName).toPath(),
                json.replace('\'', '"')
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("ディレクトリのパターンを名前で引ける")
    public void test名前で引ける() {
        write("altar.json", "{ 'name': 'altar', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] }");

        loader.loadFrom(dir);

        assertNotNull(loader.get("altar"), "読み込んだパターンが引けない");
        assertEquals(
            1,
            loader.getNames()
                .size());
    }

    @Test
    @DisplayName("1 ファイルに複数のパターンを書ける")
    public void test複数まとめて書ける() {
        write(
            "pack.json",
            "{ 'patterns': [" + "{ 'name': 'a', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] },"
                + "{ 'name': 'b', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] } ] }");

        loader.loadFrom(dir);

        assertEquals(
            2,
            loader.getNames()
                .size(),
            "patterns 配列の 2 件目が読まれていない: " + loader.getNames());
    }

    @Test
    @DisplayName("裸の配列でも書ける")
    public void test配列形式() {
        write("arr.json", "[ { 'name': 'a', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] } ]");

        loader.loadFrom(dir);

        assertNotNull(loader.get("a"));
    }

    @Test
    @DisplayName("壊れたファイルは残りの読み込みを止めない")
    public void test壊れたファイルで全滅しない() {
        write("broken.json", "{ 'name': 'broken', 'mappings': {}, 'layers': [ ['X'] ] }");
        write("good.json", "{ 'name': 'good', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] }");

        loader.loadFrom(dir);

        assertNull(loader.get("broken"), "検証を通らないパターンが登録されている");
        assertNotNull(loader.get("good"), "1 ファイルの失敗で他のファイルまで捨てている");
        assertEquals(
            1,
            loader.getErrors()
                .size(),
            "捨てただけで報告していない。黙って消えると errors.txt に何も出ない");
    }

    @Test
    @DisplayName("json 以外のファイルは見ない")
    public void testjson以外は無視() {
        write("notes.txt", "これは JSON ではない");

        loader.loadFrom(dir);

        assertEquals(
            0,
            loader.getNames()
                .size());
    }

    @Test
    @DisplayName("読み直すと前回の内容は残らない")
    public void test再読込で消える() {
        write("a.json", "{ 'name': 'a', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] }");
        loader.loadFrom(dir);

        assertTrue(new File(dir, "a.json").delete(), "テストの前提が崩れている: 消せなかった");
        loader.loadFrom(dir);

        assertNull(loader.get("a"), "消したファイルのパターンが残っている。reload で古い定義が生き延びる");
    }

    @Test
    @DisplayName("ディレクトリが無ければ作るだけで落ちない")
    public void test無いディレクトリ() {
        File missing = new File(dir, "not_yet");

        loader.loadFrom(missing);

        assertTrue(missing.exists(), "初回起動でディレクトリが作られない。置き場が無いと誰も気づけない");
    }

    /**
     * `migrate()` は登録された migrator が 0 件の今は何もしない。**だから呼び出しを
     * 振る舞いで確かめられない** — 呼んでも呼ばなくても結果が同じになる。
     *
     * それを理由に確かめないでおくと、migrator を足した日に「仕組みはあるのに動かない」になる。
     * `ConditionsRegistrationTest` などと同じくソースを見て捕まえる。
     */
    @Test
    @DisplayName("読み込み経路がマイグレーションを呼んでいる")
    public void testマイグレーションを呼んでいる() {
        Path source = Paths.get("src/main/java/ruiseki/okmodular/structure/pattern/StructurePatternLoader.java");
        String text;
        try {
            text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + source.toAbsolutePath(), e);
        }

        assertTrue(
            text.contains("StructureMigrationRegistry.migrate("),
            "ローダが migrate() を呼んでいない。別ファイルにしてまで手に入れた" + "「後から migrator で直せる」性質が、呼ばないだけで無くなる");
    }
}
