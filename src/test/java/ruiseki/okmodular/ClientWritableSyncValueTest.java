package ruiseki.okmodular;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every sync value that a GUI widget writes from the client must call {@code allowC2S()}.
 *
 * ModularUI2 drops client-to-server updates from a sync handler that has not opted in.
 * The client logs a SecurityException, the server keeps its value and sends it back,
 * so the button appears to do nothing. This hit the redstone mode buttons, the RF/EU
 * switch and the IC2 toggle of the ports.
 *
 * A value counts as client-written when it is passed to {@code RedstoneModeWidget} or
 * {@code ToggleWidget}, or when {@code .setValue(} is called on it. The check misses a
 * value handed to a widget through another variable; none exists today.
 */
class ClientWritableSyncValueTest {

    private static final Path TILE_ROOT = Paths.get("src/main/java/ruiseki/okmodular/common/tile");

    private static final Pattern WIDGET_ARG = Pattern.compile("new(?:RedstoneModeWidget|ToggleWidget)\\((\\w+)\\)");
    private static final Pattern SET_VALUE = Pattern.compile("(\\w+)\\.setValue\\(");

    @Test
    @DisplayName("client-written sync values allow client-to-server updates")
    void clientWrittenValuesAllowC2S() {
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (Path file : javaFiles()) {
            String code = read(file).replaceAll("\\s+", "");
            for (String name : clientWritten(code)) {
                checked++;
                if (!code.contains(name + ".allowC2S()")) {
                    missing.add(TILE_ROOT.relativize(file) + ": " + name);
                }
            }
        }
        assertTrue(checked > 0, "the scan found no client-written sync values");
        assertTrue(missing.isEmpty(), "missing allowC2S(): " + missing);
    }

    private static Set<String> clientWritten(String code) {
        Set<String> names = new LinkedHashSet<>();
        for (Pattern pattern : new Pattern[] { WIDGET_ARG, SET_VALUE }) {
            Matcher m = pattern.matcher(code);
            while (m.find()) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    private static List<Path> javaFiles() {
        try (Stream<Path> files = Files.walk(TILE_ROOT)) {
            return files.filter(
                p -> p.toString()
                    .endsWith(".java"))
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
