package ruiseki.okmodular.api.modular;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * enum の定数に対応する lang エントリが揃っていることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `PortColor` / `IPortType.Type` は名前から lang キーを組み立てる
 * （`gui.port_color.RED` など）。**キーが無いと例外は出ず、生のキー文字列が
 * そのまま画面に出る**という形で壊れる。
 *
 * 実際に前例がある。`gui.port_type.*` は en/ja のどちらにもエントリが無く、
 * レンチと GUI が生のキーを表示していた（lang ファイルのコメントに残っている）。
 * 手で 17 個追加するような作業では特に落としやすいので、列挙側から突き合わせる。
 *
 * 訳の**中身**は見ない。キーが存在するかだけを見る。
 *
 * ============================================
 */
@DisplayName("lang エントリの網羅")
public class PortLangCoverageTest {

    private static final String[] LANGUAGES = { "en_US", "ja_JP" };

    private static Stream<Arguments> 色のキー() {
        return keys("gui.port_color.", names(PortColor.values()));
    }

    private static Stream<Arguments> 種別のキー() {
        return keys("gui.port_type.", names(IPortType.Type.values()));
    }

    private static Stream<Arguments> 塗ったときのメッセージ() {
        return keys(
            "",
            new String[] { "chat.okmodular.port_color_set", "chat.okmodular.port_color_cleared",
                "tooltip.okmodular.port_color" });
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("色のキー")
    @DisplayName("すべての色に名前がある")
    public void test色の名前がある(String language, String key) throws IOException {
        assertHasKey(language, key);
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("種別のキー")
    @DisplayName("すべてのポート種別に名前がある")
    public void test種別の名前がある(String language, String key) throws IOException {
        assertHasKey(language, key);
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("塗ったときのメッセージ")
    @DisplayName("塗ったときのメッセージがある")
    public void testメッセージがある(String language, String key) throws IOException {
        assertHasKey(language, key);
    }

    // ========== 補助 ==========

    private static void assertHasKey(String language, String key) throws IOException {
        Map<String, String> entries = load(language);
        assertTrue(entries.containsKey(key), () -> language + ".lang に '" + key + "' が無い。訳が無いと画面にキーがそのまま出る");
        assertTrue(
            !entries.get(key)
                .isEmpty(),
            () -> language + ".lang の '" + key + "' が空");
    }

    private static Stream<Arguments> keys(String prefix, String[] suffixes) {
        return Stream.of(LANGUAGES)
            .flatMap(
                language -> Stream.of(suffixes)
                    .map(suffix -> Arguments.of(language, prefix + suffix)));
    }

    private static String[] names(Enum<?>[] constants) {
        String[] names = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
            names[i] = constants[i].name();
        }
        return names;
    }

    private static Map<String, String> load(String language) throws IOException {
        String path = "/assets/okmodular/lang/" + language + ".lang";
        Map<String, String> entries = new HashMap<>();

        try (InputStream in = PortLangCoverageTest.class.getResourceAsStream(path)) {
            assertNotNull(in, () -> path + " が読めない。テストのクラスパスに resources が乗っていない");

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int split = line.indexOf('=');
                if (split <= 0) continue;
                entries.put(line.substring(0, split), line.substring(split + 1));
            }
        }
        return entries;
    }
}
