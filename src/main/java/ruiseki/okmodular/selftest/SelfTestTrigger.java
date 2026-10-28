package ruiseki.okmodular.selftest;

import java.util.Map;
import java.util.Properties;

/** Decides whether the in-game selftest runs: the system property must be "true" in a deobfuscated dev run. */
public final class SelfTestTrigger {

    public static final String PROPERTY = "okmodular.selftest";
    private static final String DEOBF_KEY = "fml.deobfuscatedEnvironment";

    private SelfTestTrigger() {}

    public static boolean isRequested(Properties sys, Map<String, Object> blackboard) {
        if (sys == null || blackboard == null) return false;
        return "true".equals(sys.getProperty(PROPERTY)) && Boolean.TRUE.equals(blackboard.get(DEOBF_KEY));
    }
}
