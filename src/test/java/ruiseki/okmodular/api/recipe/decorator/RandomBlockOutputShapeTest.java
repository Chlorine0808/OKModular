package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.util.ChunkCoordinates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.decorator.RandomBlockOutputDecorator.WeightedBlockOutput;
import ruiseki.okmodular.api.recipe.expression.ConstantExpression;
import ruiseki.okmodular.api.recipe.io.BlockOutput;

/**
 * P24 (C): random_block_output takes one symbol and several weighted outputs, and draws the
 * output per selected cell. The pick uses its own stream, so which cells are selected (the
 * ticket stream) says nothing about which output they get.
 */
@DisplayName("random_block_output の形（記号 1 つ + 重み付き出力）")
public class RandomBlockOutputShapeTest {

    private static WeightedBlockOutput entry(int weight) {
        return new WeightedBlockOutput(new BlockOutput('O', "minecraft:stone", null, 1), weight);
    }

    private static List<ChunkCoordinates> grid(int size) {
        List<ChunkCoordinates> cells = new ArrayList<>();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) cells.add(new ChunkCoordinates(x, 0, z));
        }
        return cells;
    }

    private static RandomBlockOutputDecorator decorator() {
        return new RandomBlockOutputDecorator(null, 'O', new ConstantExpression(1), new ArrayList<>());
    }

    @Test
    @DisplayName("引きは重みに比例する")
    public void pickFollowsWeights() {
        List<WeightedBlockOutput> entries = Arrays.asList(entry(3), entry(1));
        int first = 0;
        List<ChunkCoordinates> cells = grid(20);
        for (ChunkCoordinates cell : cells) {
            if (RandomBlockOutputDecorator.pickFor(entries, 42L, cell) == entries.get(0)) first++;
        }
        double share = first / (double) cells.size();
        assertTrue(share > 0.65 && share < 0.85, "share=" + share);
    }

    @Test
    @DisplayName("重み 0 は選ばれない")
    public void pickSkipsZeroWeight() {
        List<WeightedBlockOutput> entries = Arrays.asList(entry(0), entry(2));
        for (ChunkCoordinates cell : grid(10)) {
            assertEquals(entries.get(1), RandomBlockOutputDecorator.pickFor(entries, 7L, cell));
        }
    }

    @Test
    @DisplayName("正の重みが無ければ null")
    public void pickReturnsNullWhenNothingWeighs() {
        assertNull(RandomBlockOutputDecorator.pickFor(Arrays.asList(entry(0), entry(-1)), 7L, grid(1).get(0)));
        assertNull(RandomBlockOutputDecorator.pickFor(new ArrayList<>(), 7L, grid(1).get(0)));
    }

    @Test
    @DisplayName("引きはチケットと独立（チケットの低いマスが片寄らない）")
    public void pickIsIndependentOfTicket() {
        List<WeightedBlockOutput> entries = Arrays.asList(entry(1), entry(1));
        long seed = 42L;
        List<ChunkCoordinates> cells = grid(20);
        ConditionContext context = new ConditionContext(null, 0, 0, 0, null, seed);

        // select(count = all) orders every cell by ticket
        List<ChunkCoordinates> byTicket = decorator().select(cells, cells.size(), context);
        List<ChunkCoordinates> lowest = byTicket.subList(0, 100);

        int first = 0;
        for (ChunkCoordinates cell : lowest) {
            if (RandomBlockOutputDecorator.pickFor(entries, seed, cell) == entries.get(0)) first++;
        }
        double share = first / 100.0;
        assertTrue(share > 0.35 && share < 0.65, "share=" + share);
    }

    @Test
    @DisplayName("count を省くと全マス")
    public void countOmittedMeansEveryCell() {
        RandomBlockOutputDecorator all = new RandomBlockOutputDecorator(null, 'O', null, new ArrayList<>());
        assertEquals(Integer.MAX_VALUE, all.totalCount(null));
    }
}
