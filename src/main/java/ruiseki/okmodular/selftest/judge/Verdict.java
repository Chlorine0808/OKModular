package ruiseki.okmodular.selftest.judge;

/** Outcome of a scenario; VISUAL means the checks passed but a human must look at the figures. */
public enum Verdict {

    PASS("pass"),
    FAIL("fail"),
    VISUAL("要目視");

    public final String label;

    Verdict(String label) {
        this.label = label;
    }
}
