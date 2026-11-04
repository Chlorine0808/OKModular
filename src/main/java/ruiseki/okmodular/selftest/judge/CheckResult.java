package ruiseki.okmodular.selftest.judge;

/** Result of one expectation, with human-readable expected and actual text. */
public final class CheckResult {

    public final String kind;
    public final boolean passed;
    public final String expected;
    public final String actual;

    public CheckResult(String kind, boolean passed, String expected, String actual) {
        this.kind = kind;
        this.passed = passed;
        this.expected = expected;
        this.actual = actual;
    }
}
