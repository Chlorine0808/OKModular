package ruiseki.okmodular.selftest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// logContains must not pass on a line another scenario caused. A scenario sees the lines logged
// while the server loaded (before the first scenario started) plus the lines since its own setUp.
class LogCaptureSliceTest {

    private static final List<String> ALL = Arrays.asList("load1", "load2", "s1-a", "s1-b", "s2-a");

    @Test
    @DisplayName("読み込み時の行と自分の開始以降の行だけを見せる")
    void loadTimeLinesPlusOwnLines() {
        assertEquals(Arrays.asList("load1", "load2", "s2-a"), LogCapture.slice(ALL, 2, 4));
    }

    @Test
    @DisplayName("最初のシナリオでは境目が重なっても行を重複させない")
    void firstScenarioDoesNotRepeatLines() {
        assertEquals(ALL, LogCapture.slice(ALL, 2, 2));
    }

    @Test
    @DisplayName("範囲外の境目は丸める")
    void boundsAreClamped() {
        assertEquals(
            ALL,
            LogCapture.slice(ALL, 99, 99)
                .subList(0, 5));
        assertEquals(Arrays.asList("s1-b", "s2-a"), LogCapture.slice(ALL, -1, 3));
    }
}
