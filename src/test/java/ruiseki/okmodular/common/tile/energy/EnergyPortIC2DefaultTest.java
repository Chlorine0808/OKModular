package ruiseki.okmodular.common.tile.energy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A freshly placed energy port registers with IC2, so it takes EU without a trip to its GUI.
 * Saved ports keep the value they were stored with.
 *
 * The port class implements IC2 and Mekanism interfaces that are absent from the test
 * classpath, so this reads the field initializer from the source instead of constructing it.
 */
@DisplayName("新しく置いたエネルギーポートは IC2 互換がオン")
public class EnergyPortIC2DefaultTest {

    private static final String SOURCE = "src/main/java/ruiseki/okmodular/common/tile/energy/AbstractEnergyIOPortTE.java";

    @Test
    @DisplayName("useIC2Compat の初期値が true")
    public void testDefaultsToIC2() throws Exception {
        String code = new String(Files.readAllBytes(Paths.get(SOURCE)), StandardCharsets.UTF_8);
        assertTrue(code.contains("useIC2Compat = true;"), "useIC2Compat must default to true");
    }
}
