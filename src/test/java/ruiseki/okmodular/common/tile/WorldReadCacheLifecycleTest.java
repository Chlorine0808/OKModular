package ruiseki.okmodular.common.tile;

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
 * When the controller throws its world reads away.
 *
 * The controller cannot be built in a unit test (see SymbolCellRecordingTest), so the wiring
 * is checked in the source. A cache that outlives a re-formed structure answers for the old
 * shape until it expires; one that outlives the controller holds its reads for nobody.
 */
@DisplayName("ワールド読み取りのキャッシュを捨てる時機")
public class WorldReadCacheLifecycleTest {

    private static final Path CONTROLLER = Paths
        .get("src/main/java/ruiseki/okmodular/common/tile/TEMachineController.java");

    @Test
    @DisplayName("コントローラがキャッシュを渡している")
    public void testキャッシュを渡す() {
        String body = methodBody(read(CONTROLLER), "public WorldReadCache getWorldReadCache(");

        assertTrue(body.contains("worldReadCache"), "getWorldReadCache が自分のキャッシュを返していない");
    }

    @Test
    @DisplayName("構造の形成で捨てる")
    public void test形成() {
        assertClears("public void onFormed(");
    }

    @Test
    @DisplayName("機械の破壊で捨てる")
    public void test破壊() {
        assertClears("public void invalidate(");
    }

    @Test
    @DisplayName("チャンクのアンロードで捨てる")
    public void testアンロード() {
        assertClears("public void onChunkUnload(");
    }

    private static void assertClears(String signature) {
        String body = methodBody(read(CONTROLLER), signature);
        assertTrue(body.contains("worldReadCache.clear()"), signature + " がキャッシュを捨てていない");
    }

    /** Cuts the method body out by brace matching, so a call elsewhere does not count. */
    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "メソッドが見つからない: " + signature);

        int brace = source.indexOf('{', start);
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
