package ruiseki.okmodular.selftest.scenario;

/** One thing fed into a machine: an item stack, a fluid or energy. */
public final class InputSpec {

    public enum Kind {
        ITEM,
        FLUID,
        ENERGY
    }

    public final Kind kind;
    /** Null for ENERGY. */
    public final String id;
    public final int amount;

    public InputSpec(Kind kind, String id, int amount) {
        this.kind = kind;
        this.id = id;
        this.amount = amount;
    }
}
