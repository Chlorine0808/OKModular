package ruiseki.okmodular.api.recipe.expression;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import ruiseki.okmodular.api.condition.ConditionContext;

/**
 * Expression that counts blocks in a cubic range around the controller.
 * Usage: count_blocks(distance, [filter])
 * <p>
 * The distance is capped at {@link #MAX_DISTANCE}, and chunks that are not loaded are skipped
 * rather than loaded, because a condition may run this on every recipe search.
 */
public class CountBlocksFunctionExpression implements IExpression {

    /** The largest distance scanned; (2 * 16 + 1)^3 is about 36 000 reads. */
    public static final int MAX_DISTANCE = 16;

    /** What the scan asks of the world. */
    interface Probe {

        boolean isChunkLoaded(int chunkX, int chunkZ);

        boolean counts(int x, int y, int z);
    }

    private final List<IExpression> arguments;

    public CountBlocksFunctionExpression(List<IExpression> arguments) {
        this.arguments = arguments;
    }

    @Override
    public EvaluationValue evaluate(ConditionContext context) {
        if (context == null || context.getWorld() == null || arguments.isEmpty()) {
            return EvaluationValue.ZERO;
        }

        double distanceDouble = arguments.get(0)
            .evaluate(context)
            .asDouble();
        int distance = (int) Math.floor(distanceDouble);
        if (distance < 0) return EvaluationValue.ZERO;

        Set<Block> allowedBlocks = new HashSet<>();
        boolean hasFilter = false;

        if (arguments.size() > 1) {
            hasFilter = true;
            for (int i = 1; i < arguments.size(); i++) {
                IExpression arg = arguments.get(i);
                EvaluationValue eval = arg.evaluate(context);
                if (eval.isNbt() && eval.asNbt() instanceof NBTTagList list) {
                    for (int j = 0; j < list.tagCount(); j++) {
                        addBlockToFilter(list.getStringTagAt(j), allowedBlocks);
                    }
                } else {
                    addBlockToFilter(eval.asString(), allowedBlocks);
                }
            }
        }

        World world = context.getWorld();
        ChunkCoordinates pos = context.getRecipeContext()
            .getControllerPos();
        int cx = pos.posX;
        int cy = pos.posY;
        int cz = pos.posZ;

        final boolean filtered = hasFilter;
        long count = scan(cx, cy, cz, distance, new Probe() {

            @Override
            public boolean isChunkLoaded(int chunkX, int chunkZ) {
                return world.getChunkProvider()
                    .chunkExists(chunkX, chunkZ);
            }

            @Override
            public boolean counts(int x, int y, int z) {
                Block block = world.getBlock(x, y, z);
                return filtered ? allowedBlocks.contains(block) : !block.isAir(world, x, y, z);
            }
        });

        return new EvaluationValue((double) count);
    }

    /** Counts the cube around the centre, excluding the centre itself. */
    static long scan(int cx, int cy, int cz, int distance, Probe probe) {
        int reach = Math.min(distance, MAX_DISTANCE);
        int minY = Math.max(cy - reach, 0);
        int maxY = Math.min(cy + reach, 255);

        long count = 0;
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int z = cz - reach; z <= cz + reach; z++) {
                if (!probe.isChunkLoaded(x >> 4, z >> 4)) continue;
                for (int y = minY; y <= maxY; y++) {
                    if (x == cx && y == cy && z == cz) continue;
                    if (probe.counts(x, y, z)) count++;
                }
            }
        }
        return count;
    }

    private void addBlockToFilter(String name, Set<Block> allowedBlocks) {
        if (name == null || name.isEmpty()) return;
        Block block = (Block) Block.blockRegistry.getObject(name);
        if (block != null) {
            allowedBlocks.add(block);
        }
    }

    @Override
    public String toString() {
        return "count_blocks(" + arguments.get(0) + ")";
    }
}
