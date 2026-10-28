package ruiseki.okmodular.selftest.scenario;

import com.google.gson.JsonObject;

/** One check; the full JSON object (including "kind") is kept in args. */
public final class Expectation {

    public final String kind;
    public final JsonObject args;

    public Expectation(String kind, JsonObject args) {
        this.kind = kind;
        this.args = args;
    }
}
