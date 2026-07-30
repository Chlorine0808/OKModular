package ruiseki.okmodular.api.condition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 条件の説明文に使う lang エントリの網羅。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `ICondition.getDescription()` の文は**プレイヤーが読む唯一の手がかり**になる。
 * B8 で機械の稼働条件を入れたので、条件を満たさない機械の GUI にこの文が出る
 * （`稼働条件を満たしていません: <条件>`）。**キーが無ければそこに生のキーが出る。**
 *
 * `WeatherCondition` は `"okmodular.condition.weather." + 天候名` と**組み立てる**ので、
 * ソースのリテラルを拾う `LangKeyCoverageTest` では見つからない。
 * あちらの `RUNTIME_PREFIXES` に「覆うテストは無い」と書いてあった 4 件のうちの 1 件で、
 * **enum を回せば覆えると書いてあったものを、実際に覆いに来た**。
 *
 * 残る 3 件（`gui.craftingState.` / `machinery.design.` / `okmodular.component.` / `structure.`
 * のうちブロックデザインと構造名）は集合が JSON 由来なので、**静的テストでは原理的に覆えない**。
 *
 * ============================================
 */
@DisplayName("条件の説明文の lang エントリ")
public class ConditionLangCoverageTest {

    private static final String[] LANGUAGES = { "en_US", "ja_JP" };

    private static Stream<Arguments> 天候のキー() {
        return Stream.of(LANGUAGES)
            .flatMap(
                language -> Stream.of(WeatherCondition.Weather.values())
                    .map(weather -> Arguments.of(language, "okmodular.condition.weather." + weather.name())));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("天候のキー")
    @DisplayName("すべての天候に文言がある")
    public void test天候の文言がある(String language, String key) throws IOException {
        Map<String, String> entries = load(language);
        assertTrue(entries.containsKey(key), () -> language + ".lang に '" + key + "' が無い。条件の説明に生のキーが出る");
        assertFalse(
            entries.get(key)
                .isEmpty(),
            () -> language + ".lang の '" + key + "' が空");
    }

    private static Map<String, String> load(String language) throws IOException {
        String path = "/assets/okmodular/lang/" + language + ".lang";
        Map<String, String> entries = new HashMap<>();

        try (InputStream in = ConditionLangCoverageTest.class.getResourceAsStream(path)) {
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
