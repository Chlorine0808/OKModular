package ruiseki.okmodular.selftest.scenario;

import java.util.List;
import java.util.Map;

/** Which structure to build and how to place it. */
public final class MachineSpec {

    public final String structure;
    public final String facing;
    public final String rotation;
    public final String flip;
    /** Authored cell key ("C*" or "P#0") to "modid:name:meta". */
    public final Map<String, String> cells;
    /** How many blueprints are stacked when the machine is built; the count picks the tier of tiered parts. */
    public final int blueprintCount;
    /** Built the way a player does in survival, from carried items, instead of placed outright. */
    public final boolean survival;
    /** Survival only: blocks already in the world before the build, by authored cell key. */
    public final Map<String, String> standing;
    /** Survival only: the items the building player carries. */
    public final List<InputSpec> carry;

    public MachineSpec(String structure, String facing, String rotation, String flip, Map<String, String> cells,
        int blueprintCount, boolean survival, Map<String, String> standing, List<InputSpec> carry) {
        this.structure = structure;
        this.facing = facing;
        this.rotation = rotation;
        this.flip = flip;
        this.cells = cells;
        this.blueprintCount = blueprintCount;
        this.survival = survival;
        this.standing = standing;
        this.carry = carry;
    }
}
