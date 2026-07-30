package ruiseki.okmodular.api.condition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 条件の説明文が、実際に lang にあるキーを引いていることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `ICondition.getDescription()` の文は**プレイヤーが読む唯一の手がかり**になる。
 * B8 で機械の稼働条件を入れたので、条件を満たさない機械の GUI にこの文が出る
 * （`稼働条件を満たしていません: <条件>`）。**キーが無ければそこに生のキーが出る。**
 *
 * ============================================
 * このテストは一度失敗している。その理由が要点
 * ============================================
 *
 * 最初の版は期待キーを**自分で組み立てていた** — `"okmodular.condition.weather." + weather.name()`。
 * それは lang にあり、テストは緑になった。ところが実機に出たのは
 *
 * ```
 * conditions are not met: okmodular.condition.weather.rain
 * ```
 *
 * `WeatherCondition` は `weather.name().toLowerCase()` で**小文字のキー**を引いていた。
 * **テストは誰も要求しないキーの存在を確かめていた。**
 *
 * だから今の版は**キーを組み立てない**。`getDescription()` を呼び、
 * **返ってきた文字列がそのまま lang にあるか**を見る。
 * ゲーム外では `StatCollector` が訳を持たないキーを**そのまま返す**ので、
 * 戻り値 = 本番が要求したキーそのものになる。これで**コードと lang が食い違えなくなる。**
 *
 * 教訓は B8 全体を通して同じ: **期待値を本番と独立に作ると、両方が同時に間違える。**
 *
 * ============================================
 * 覆えないもの
 * ============================================
 *
 * `getDescription()` が**キー以外の文字を足す**条件は、この方法では見られない
 * （`BiomeCondition` はバイオーム名を連結し、`ComparisonCondition` は式をそのまま返す）。
 * ブロック系（`BlockCondition` / `BlockBelowCondition`）は純粋にキーを返すが、
 * **組み立てに実ブロックと `ItemStack` が要るのでゲーム外で作れない。**
 *
 * ============================================
 */
@DisplayName("条件の説明文の lang エントリ")
public class ConditionLangCoverageTest {

    private static final String[] LANGUAGES = { "en_US", "ja_JP" };

    /**
     * `getDescription()` が**キーだけ**を返す条件。
     * <p>
     * ゲーム外では訳が無いのでキーがそのまま返る。それを lang と突き合わせる。
     */
    private static Stream<ICondition> キーだけを返す条件() {
        Stream<ICondition> weather = Stream.of(WeatherCondition.Weather.values())
            .map(WeatherCondition::new);
        Stream<ICondition> dimension = Stream.of(new DimensionCondition(Arrays.asList(0)));
        return Stream.concat(weather, dimension);
    }

    private static Stream<Arguments> 説明文のキー() {
        return Stream.of(LANGUAGES)
            .flatMap(
                language -> キーだけを返す条件().map(
                    condition -> Arguments.of(
                        language,
                        condition.getClass()
                            .getSimpleName(),
                        condition.getDescription())));
    }

    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @MethodSource("説明文のキー")
    @DisplayName("説明文が引くキーが lang にある")
    public void test説明文のキーがある(String language, String conditionType, String key) throws IOException {
        Map<String, String> entries = load(language);
        assertTrue(
            entries.containsKey(key),
            () -> language + ".lang に '" + key + "' が無い（" + conditionType + " が要求したキー）。条件の説明に生のキーが出る");
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
