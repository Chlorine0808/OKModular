package ruiseki.okmodular.api.condition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `registerDefaults()` を本番コードが実際に呼んでいることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * **`Conditions.registerDefaults()` は呼び出し元が 0 件だった。** 定義されているだけで
 * 一度も呼ばれず、`ConditionParserRegistry` は**実行時ずっと空**だった。
 *
 * 結果として、**レシピと構造の JSON に書かれた条件がすべて読み捨てられていた**。
 * 2026-07-30 の実機ログにその跡が残っている:
 *
 * ```
 * Unknown or non-inferable condition type: {"weather":"RAIN"}
 * Unknown or non-inferable condition type: {"expression":"is_night == 1 && can_see_sky == 1"}
 * ```
 *
 * `future_roadmap.md` が `[X]`（完了）としていた「天候・時間帯・バイオーム・月齢による
 * レシピ制限」は、**一度も効いていなかった**。B8（機械の稼働条件）を実機で試したときに
 * 「条件を無視して動き続ける」形で初めて表に出た。
 *
 * ============================================
 * なぜテストが気づけなかったのか — ここが教訓
 * ============================================
 *
 * `ConditionParserRegistryTest` も `MachineConditionsParserTest` も、
 * **`@BeforeAll` で自分で `registerDefaults()` を呼んでいる**。
 * 条件のパースは正しく動くので緑になる。
 *
 * **テストのセットアップが本番の配線漏れを隠していた。**
 * 「テストが緑」は「本番で初期化されている」を意味しない。
 * セットアップで補ったものは、本番でも誰かが補っているかを別に確かめる必要がある。
 *
 * ============================================
 * なぜ static 初期化にしないのか
 * ============================================
 *
 * `RecipeParserRegistry` は `static {}` ブロックで自己登録するので呼び出しが要らない。
 * 同じ手を `Conditions` に使うことはできない — **クラスがどこからも参照されないので
 * 読み込まれず、読み込まれないクラスの static 初期化は走らない。**
 * `WrenchOverlayRenderer` が GTNHLib の annotation で登録されなかったのと同じ理由。
 * だから明示的に呼ぶ形にして、呼んでいることをこのテストで縛る。
 *
 * ============================================
 */
@DisplayName("既定パーサの登録")
public class ConditionsRegistrationTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");

    @Test
    @DisplayName("registerDefaults を持つクラスは、本番コードから呼ばれている")
    public void test登録メソッドが呼ばれている() {
        List<Path> definitions = new ArrayList<>();
        for (Path file : sources()) {
            if (read(file).contains("public static void registerDefaults()")) definitions.add(file);
        }
        assertFalse(definitions.isEmpty(), "registerDefaults を持つクラスが見つからない。走査が壊れている");

        List<String> uncalled = new ArrayList<>();
        for (Path definition : definitions) {
            String className = simpleName(definition);
            String needle = className + ".registerDefaults()";

            boolean called = false;
            for (Path file : sources()) {
                if (file.equals(definition)) continue; // 自分自身の中の宣言は呼び出しではない
                if (read(file).contains(needle)) {
                    called = true;
                    break;
                }
            }
            if (!called) uncalled.add(className);
        }

        assertTrue(
            uncalled.isEmpty(),
            "registerDefaults() を呼ぶ本番コードが無い: " + uncalled + " — 定義しただけではレジストリは空のまま。JSON を読む前に呼ぶこと");
    }

    @Test
    @DisplayName("登録すれば条件が読める（レジストリが効いている証拠）")
    public void test登録すれば読める() {
        Conditions.registerDefaults();

        assertTrue(
            ConditionParserRegistry.parse(
                new com.google.gson.JsonParser().parse("{ \"weather\": \"RAIN\" }")
                    .getAsJsonObject())
                != null,
            "登録済みなのに読めない");
    }

    // ========== 走査 ==========

    private static List<Path> sources() {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(SOURCE_ROOT)) {
            walk.filter(
                path -> path.toString()
                    .endsWith(".java"))
                .forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException("ソースツリーが読めない: " + SOURCE_ROOT.toAbsolutePath(), e);
        }
        return files;
    }

    private static String simpleName(Path file) {
        return file.getFileName()
            .toString()
            .replace(".java", "");
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file, e);
        }
    }
}
