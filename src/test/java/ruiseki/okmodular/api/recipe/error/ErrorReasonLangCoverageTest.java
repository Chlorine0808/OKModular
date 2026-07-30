package ruiseki.okmodular.api.recipe.error;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * `ErrorReason` の全定数に lang エントリがあることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `ErrorReason.getUnlocalizedName()` は id から `gui.status.<id>` を**組み立てる**。
 * キーが無くても例外は出ず、**機械の GUI に生のキー文字列がそのまま出る**。
 *
 * 実際に出ていた。2026-07-30 の実機（**親 mod 無しの 48 mod 構成**）で確認したところ、
 * 18 定数のうち OKModular 側の lang にあるのは `idle` と `output_full` の 2 個だけで、
 * 残りは**親 mod (OmoshiroiKamo) の lang にしか無かった**。
 *
 * ところが OKModular の依存は `required-after:okcore;required-after:gtnhlib` で、
 * **親 mod を要求していない**。つまり単体で入れると `gui.status.no_energy` のような
 * 文字列が画面に出る。**分離の落とし物。**
 *
 * ============================================
 * なぜ enum から列挙するのか
 * ============================================
 *
 * キーはリテラルではなく `"gui.status." + id` で組み立てられるので、
 * **ソースから文字列を拾う方法では見つからない。** enum を回して
 * `getUnlocalizedName()` を実際に呼ぶのが唯一の確実な列挙。
 *
 * **新しい定数を足したら自動的に落ちる**のが要点。手書きの一覧を並べても、
 * 一覧を更新し忘れるという同じ失敗を繰り返すだけ。
 *
 * 訳の**中身**は見ない。キーが存在して空でないことだけを見る。
 *
 * ============================================
 */
@DisplayName("ErrorReason の lang エントリ")
public class ErrorReasonLangCoverageTest {

    private static final String[] LANGUAGES = { "en_US", "ja_JP" };

    /**
     * lang エントリを要求しない定数と、その理由。
     * <p>
     * **理由が書けないなら訳を用意する。** 未使用の lang エントリは無害なので、
     * 「今は表示していない」は除外の理由にならない — 表示するようになった瞬間に生のキーが出る。
     */
    private static final Map<String, String> NEEDS_NO_TRANSLATION;
    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("NONE", "「エラーが無い」状態を表す番兵で、文言そのものが存在しない（defaultMessage も空）。" + "表示経路に乗ることがないため訳を持たない。");
        NEEDS_NO_TRANSLATION = Collections.unmodifiableMap(map);
    }

    private static Stream<Arguments> 状態のキー() {
        return Stream.of(LANGUAGES)
            .flatMap(
                language -> Stream.of(ErrorReason.values())
                    .filter(reason -> !NEEDS_NO_TRANSLATION.containsKey(reason.name()))
                    .map(reason -> Arguments.of(language, reason.name(), reason.getUnlocalizedName())));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("状態のキー")
    @DisplayName("すべての状態に文言がある")
    public void test状態の文言がある(String language, String constant, String key) throws IOException {
        Map<String, String> entries = load(language);
        assertTrue(
            entries.containsKey(key),
            () -> language + ".lang に '" + key + "' が無い（" + constant + "）。訳が無いと画面にキーがそのまま出る");
        assertFalse(
            entries.get(key)
                .isEmpty(),
            () -> language + ".lang の '" + key + "' が空");
    }

    @Test
    @DisplayName("除外リストに実在しない定数が残っていない")
    public void test除外リストが古くなっていない() {
        for (String excluded : NEEDS_NO_TRANSLATION.keySet()) {
            boolean exists = Stream.of(ErrorReason.values())
                .anyMatch(
                    reason -> reason.name()
                        .equals(excluded));
            assertTrue(exists, excluded + " という定数は存在しない。除外リストから消すこと");
        }
    }

    @Test
    @DisplayName("除外には理由が書かれている")
    public void test除外に理由がある() {
        for (Map.Entry<String, String> entry : NEEDS_NO_TRANSLATION.entrySet()) {
            assertTrue(
                entry.getValue()
                    .length() >= 20,
                entry.getKey() + " の除外理由が短すぎる: " + entry.getValue());
        }
    }

    // ========== 補助 ==========

    private static Map<String, String> load(String language) throws IOException {
        String path = "/assets/okmodular/lang/" + language + ".lang";
        Map<String, String> entries = new HashMap<>();

        try (InputStream in = ErrorReasonLangCoverageTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, () -> path + " が読めない");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int split = line.indexOf('=');
                    if (split <= 0) continue;
                    entries.put(line.substring(0, split), line.substring(split + 1));
                }
            }
        }
        return entries;
    }
}
