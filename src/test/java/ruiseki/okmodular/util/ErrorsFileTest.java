package ruiseki.okmodular.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Load errors land in errors.txt beside the JSON files they came from, so a pack author finds
// them where they edit. A clean load must remove the old file, or a fixed mistake keeps showing.
class ErrorsFileTest {

    @TempDir
    File dir;

    @Test
    @DisplayName("エラーを JSON と同じフォルダの errors.txt に書く")
    void writesEachErrorBesideTheJson() throws IOException {
        ErrorsFile.write(dir, "Recipe errors", Arrays.asList("bad one in a.json", "bad two in b.json"));

        String text = new String(Files.readAllBytes(new File(dir, "errors.txt").toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("Recipe errors"), text);
        assertTrue(text.contains("Error count: 2"), text);
        assertTrue(text.contains("bad one in a.json"), text);
        assertTrue(text.contains("bad two in b.json"), text);
    }

    @Test
    @DisplayName("エラーが無ければ前回の errors.txt を消す")
    void removesAStaleFileWhenThereAreNoErrors() throws IOException {
        File stale = new File(dir, "errors.txt");
        Files.write(stale.toPath(), "old".getBytes(StandardCharsets.UTF_8));

        ErrorsFile.write(dir, "Recipe errors", Collections.<String>emptyList());

        assertFalse(stale.exists());
    }
}
