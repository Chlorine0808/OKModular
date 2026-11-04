package ruiseki.okmodular.selftest.scenario;

/** Thrown when a scenario file cannot be parsed at all, and used internally for per-scenario problems. */
public class ScenarioParseException extends RuntimeException {

    public ScenarioParseException(String message) {
        super(message);
    }

    public ScenarioParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
