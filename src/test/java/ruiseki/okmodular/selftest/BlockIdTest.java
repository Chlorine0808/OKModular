package ruiseki.okmodular.selftest;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Scenario files name blocks and items as "modid:name" or "modid:name:meta", the same spelling
// BlockGrid uses for what it saw (meta written only when non-zero). Both directions must agree,
// or a blockAt check compares "minecraft:stone:0" with "minecraft:stone" and fails for nothing.
class BlockIdTest {

    @Test
    @DisplayName("メタの無い ID はメタ 0")
    void noMetaMeansZero() {
        BlockId id = BlockId.parse("minecraft:stone");
        assertEquals("minecraft:stone", id.name);
        assertEquals(0, id.meta);
    }

    @Test
    @DisplayName("3 つ目の区切りはメタ")
    void thirdPartIsMeta() {
        BlockId id = BlockId.parse("okmodular:casing_plain:3");
        assertEquals("okmodular:casing_plain", id.name);
        assertEquals(3, id.meta);
    }

    @Test
    @DisplayName("書き出しはメタ 0 を省く")
    void formatOmitsMetaZero() {
        assertEquals("minecraft:stone", BlockId.format("minecraft:stone", 0));
        assertEquals("minecraft:wool:3", BlockId.format("minecraft:wool", 3));
        assertEquals("minecraft:stone", BlockId.format(BlockId.parse("minecraft:stone:0").name, 0));
    }

    @Test
    @DisplayName("数でないメタは理由つきで弾く")
    void badMetaIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> BlockId.parse("a:b:*"));
        assertTrue(
            e.getMessage()
                .contains("a:b:*"));
        assertThrows(IllegalArgumentException.class, () -> BlockId.parse(""));
    }
}
