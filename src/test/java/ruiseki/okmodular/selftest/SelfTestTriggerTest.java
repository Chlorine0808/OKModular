package ruiseki.okmodular.selftest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// The selftest replaces the normal server run (it builds machines and shuts the server down), so it
// must only start when both the explicit system property and the dev environment hold. Anything
// short of the literal "true" stays off, and a production (obfuscated) environment never starts it.
@DisplayName("実機テストの合図")
class SelfTestTriggerTest {

    private static Properties sys(String value) {
        Properties p = new Properties();
        if (value != null) p.setProperty(SelfTestTrigger.PROPERTY, value);
        return p;
    }

    private static Map<String, Object> blackboard(Object deobf) {
        Map<String, Object> m = new HashMap<>();
        if (deobf != null) m.put("fml.deobfuscatedEnvironment", deobf);
        return m;
    }

    @Test
    @DisplayName("プロパティが無ければ動かない")
    void offWithoutTheProperty() {
        assertFalse(SelfTestTrigger.isRequested(sys(null), blackboard(Boolean.TRUE)));
    }

    @Test
    @DisplayName("開発環境の外では動かない")
    void offOutsideTheDevEnvironment() {
        assertFalse(SelfTestTrigger.isRequested(sys("true"), blackboard(Boolean.FALSE)));
        assertFalse(SelfTestTrigger.isRequested(sys("true"), blackboard(null)));
        assertFalse(SelfTestTrigger.isRequested(sys("true"), null));
    }

    @Test
    @DisplayName("両方そろえば動く")
    void onWhenBothHold() {
        assertTrue(SelfTestTrigger.isRequested(sys("true"), blackboard(Boolean.TRUE)));
    }

    @Test
    @DisplayName("文字どおりの true だけが合図になる")
    void onlyTheLiteralTrueTurnsItOn() {
        assertFalse(SelfTestTrigger.isRequested(sys("1"), blackboard(Boolean.TRUE)));
        assertFalse(SelfTestTrigger.isRequested(sys("yes"), blackboard(Boolean.TRUE)));
    }
}
