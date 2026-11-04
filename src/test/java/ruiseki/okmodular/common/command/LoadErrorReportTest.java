package ruiseki.okmodular.common.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// The reload message pointed at OKCore's json_errors.txt at the config root. Errors now sit in
// errors.txt beside the JSON they came from, so the message names each folder that has some.
class LoadErrorReportTest {

    @Test
    @DisplayName("エラーのあるフォルダごとに errors.txt の場所と件数を出す")
    void namesEachFolderWithErrors() {
        List<String> lines = LoadErrorReport.lines(2, 0, "structure_io", 3);

        assertEquals(
            Arrays.asList("config/okmodular/structures/errors.txt (2)", "config/okmodular/recipes/errors.txt (3)"),
            lines);
    }

    @Test
    @DisplayName("パターンのフォルダは設定の名前で出す")
    void usesTheConfiguredPatternFolder() {
        List<String> lines = LoadErrorReport.lines(0, 1, "io_patterns", 0);

        assertEquals(1, lines.size());
        assertTrue(
            lines.get(0)
                .startsWith("config/okmodular/io_patterns/errors.txt"),
            lines.get(0));
    }
}
