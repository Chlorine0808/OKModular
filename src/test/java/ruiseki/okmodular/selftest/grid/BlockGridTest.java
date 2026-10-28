package ruiseki.okmodular.selftest.grid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockGridTest {

    @Test
    @DisplayName("未設定のマスは空気")
    void unsetCellsAreAir() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 2, 2, 2 });
        assertEquals("minecraft:air", g.get(1, 1, 1));
        assertEquals(27, g.cells());
    }

    @Test
    @DisplayName("meta は ID の一部")
    void metaIsPartOfTheId() {
        BlockGrid g = new BlockGrid(new int[] { 0, 0, 0 }, new int[] { 1, 1, 1 });
        g.set(0, 0, 0, "minecraft:wool:3");
        assertEquals(0, g.count("minecraft:wool"));
        assertEquals(1, g.count("minecraft:wool:3"));
        assertEquals("minecraft:wool:3", g.get(0, 0, 0));
    }

    @Test
    @DisplayName("sub は相対座標を保つ")
    void subKeepsRelativeCoordinates() {
        BlockGrid g = new BlockGrid(new int[] { -2, 0, -2 }, new int[] { 2, 3, 2 });
        g.set(1, 2, -1, "minecraft:stone");
        g.set(-2, 0, -2, "minecraft:dirt");
        g.mark(1, 2, -1, BlockGrid.Marker.CONTROLLER);
        BlockGrid s = g.sub(new int[] { 0, 1, -2 }, new int[] { 2, 3, 0 });
        assertEquals("minecraft:stone", s.get(1, 2, -1));
        assertEquals(0, s.count("minecraft:dirt"));
        assertArrayEquals(new int[] { 0, 1, -2 }, s.from());
        assertArrayEquals(new int[] { 2, 3, 0 }, s.to());
        assertEquals(
            BlockGrid.Marker.CONTROLLER,
            s.markers()
                .get(java.util.Arrays.asList(1, 2, -1)));
        assertEquals(27, s.cells());
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("minecraft:stone", "minecraft:air")), s.blocks());
    }
}
