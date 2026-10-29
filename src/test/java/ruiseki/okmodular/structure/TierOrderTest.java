package ruiseki.okmodular.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The blueprint's stack size counts tiers from the lowest, so the candidates of a tiered part must
 * come out in tier order instead of the HashMap order they are stored in. Ties fall back to the
 * block id so the n-th candidate never changes between loads.
 */
class TierOrderTest {

    @Test
    void candidatesComeOutLowestTierFirst() {
        Map<String, Integer> tiers = new HashMap<>();
        tiers.put("okmodular:casing:2", 3);
        tiers.put("okmodular:casing:0", 1);
        tiers.put("okmodular:casing:1", 2);
        assertEquals(
            Arrays.asList("okmodular:casing:0", "okmodular:casing:1", "okmodular:casing:2"),
            BlockResolver.orderByTier(tiers));
    }

    @Test
    void sameTierFallsBackToTheBlockId() {
        Map<String, Integer> tiers = new HashMap<>();
        tiers.put("minecraft:glass:0", 1);
        tiers.put("b:glass:0", 1);
        tiers.put("a:glass:0", 1);
        assertEquals(Arrays.asList("a:glass:0", "b:glass:0", "minecraft:glass:0"), BlockResolver.orderByTier(tiers));
    }
}
