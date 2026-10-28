package ruiseki.okmodular.structure.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

/**
 * Every block a pattern can place, which is what NEI looks a structure output up by.
 */
@DisplayName("パターンが置きうるブロック")
public class PatternPlacedBlocksTest {

    private static StructurePattern parse(String json) {
        return StructurePatternParser.parse(
            new JsonParser().parse(json.replace('\'', '"'))
                .getAsJsonObject(),
            "test.json");
    }

    @Test
    @DisplayName("描いた記号のブロックと重み表の候補をすべて返す")
    public void testListsEveryChoice() {
        StructurePattern pattern = parse(
            "{ 'name': 'p', 'mappings': { 'S': 'minecraft:stone', "
                + "'O': { 'weights': { 'minecraft:iron_ore': 1, 'minecraft:gold_ore': 2 } }, "
                + "'U': 'minecraft:dirt' }, 'layers': [ ['SOQ'] ] }");

        // U is declared but never drawn, and the anchor Q has no mapping
        assertEquals(
            new HashSet<>(Arrays.asList("minecraft:stone", "minecraft:iron_ore", "minecraft:gold_ore")),
            pattern.placedBlocks());
    }

    @Test
    @DisplayName("空気は置くブロックに数えない")
    public void testSkipsAir() {
        StructurePattern pattern = parse(
            "{ 'name': 'p', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S_Q'] ] }");

        assertEquals(new HashSet<>(Arrays.asList("minecraft:stone")), pattern.placedBlocks());
    }
}
