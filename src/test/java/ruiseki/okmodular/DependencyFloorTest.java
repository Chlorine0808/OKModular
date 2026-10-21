package ruiseki.okmodular;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 宣言した依存の下限が、ビルドに使った版と一致すること。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * GTNHLib は 0.11.21 と 0.11.41 の間で `BlockState.place` の形を変え、両方を持つ版は無い。
 * ビルドの版だけ上げて `Reference.DEPENDENCIES` の下限を古いまま残すと、
 * 古いライブラリの環境でも起動してしまい、ブロックを置いた瞬間に NoSuchMethodError で落ちる。
 * 下限をビルドの版にそろえておけば、Forge が起動時に依存不足として止める。
 *
 * 下限がビルドの版より低くても動く場合はあるが、それを確かめる手段がこのリポジトリに無い。
 * 確かめていない版を許すより、確かめた版だけを許す。
 */
@DisplayName("依存の下限とビルドの版")
public class DependencyFloorTest {

    private static String buildVersion(String artifact) throws IOException {
        String gradle = new String(Files.readAllBytes(Paths.get("dependencies.gradle")), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile(":" + Pattern.quote(artifact) + ":([^:\"]+)[:\"]")
            .matcher(gradle);
        assertNotNull(m.find() ? m.group(1) : null, artifact + " が dependencies.gradle に見つからない");
        return m.group(1);
    }

    private static String declaredFloor(String modId) {
        Matcher m = Pattern.compile("required-after:" + Pattern.quote(modId) + "@\\[([^,\\]]+),")
            .matcher(Reference.DEPENDENCIES);
        return m.find() ? m.group(1) : null;
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({ "okcore, OKCore", "gtnhlib, GTNHLib", "structurelib, StructureLib", "modularui2, ModularUI2" })
    @DisplayName("下限はビルドに使った版")
    public void test下限がビルドの版(String modId, String artifact) throws IOException {
        assertEquals(buildVersion(artifact), declaredFloor(modId), modId + " の下限がビルドの版とずれている");
    }
}
