package ruiseki.okmodular.api.recipe.expression;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import ruiseki.okmodular.api.condition.ConditionContext;

/**
 * Expression that evaluates vision-related functions (e.g., can_see_sky).
 */
public class VisionFunctionExpression implements IExpression {

    public enum Direction {
        SKY,
        VOID
    }

    /**
     * The argument-less forms, shared.
     * <p>
     * These are what the bare {@code can_see_sky} / {@code can_see_void} variables evaluate
     * to. The docs say the two spellings ask the same question, so one of them has to be the
     * implementation; {@link WorldPropertyExpression} used to answer separately, from the
     * chunk's height map:
     * <ul>
     * <li>{@code can_see_void} read {@code World.getHeightValue(x, z) < 0}. A height map
     * entry is never negative, so that variable was <b>a constant false</b>.
     * <li>{@code can_see_sky} read {@code World.canBlockSeeTheSky} at the controller's own
     * coordinates - {@code y >= heightMap}. Back then every block this mod registered
     * reported no light opacity, so a machine was invisible to the height map and the two
     * spellings happened to agree.
     * </ul>
     * Agreeing by accident is the thing being removed. The height map counts the block at
     * the asked-about coordinate, so now that a controller does stop light it would answer
     * false everywhere - which is exactly the silent constant this was blamed for before.
     * <p>
     * Safe to share because the class holds nothing but its direction and its argument list.
     */
    public static final VisionFunctionExpression SKY = new VisionFunctionExpression(
        Direction.SKY,
        Collections.emptyList());

    public static final VisionFunctionExpression VOID = new VisionFunctionExpression(
        Direction.VOID,
        Collections.emptyList());

    private final Direction direction;
    private final List<IExpression> arguments;

    public VisionFunctionExpression(Direction direction, List<IExpression> arguments) {
        this.direction = direction;
        this.arguments = arguments;
    }

    @Override
    public EvaluationValue evaluate(ConditionContext context) {
        if (context == null || context.getWorld() == null) return EvaluationValue.FALSE;

        int x = context.getX();
        int y = context.getY();
        int z = context.getZ();

        // Parse allowed-block arguments: "transparent", "strict", or "modid:block"
        boolean strict = false;
        Set<String> allowedBlockIds = new HashSet<>();

        if (arguments != null) {
            for (IExpression argExpr : arguments) {
                EvaluationValue eval = argExpr.evaluate(context);
                if (eval.isNbt() && eval.asNbt() instanceof NBTTagList list) {
                    for (int i = 0; i < list.tagCount(); i++) {
                        String arg = list.getStringTagAt(i);
                        if ("strict".equalsIgnoreCase(arg)) {
                            strict = true;
                        } else if (!"transparent".equalsIgnoreCase(arg) && arg != null && !arg.isEmpty()) {
                            allowedBlockIds.add(arg.toLowerCase());
                        }
                    }
                } else {
                    String arg = eval.asString();
                    if ("strict".equalsIgnoreCase(arg)) {
                        strict = true;
                    } else if (!"transparent".equalsIgnoreCase(arg) && arg != null && !arg.isEmpty()) {
                        allowedBlockIds.add(arg.toLowerCase());
                    }
                }
            }
        }

        World world = context.getWorld();
        boolean result = checkVision(world, x, y, z, direction, strict, allowedBlockIds);
        return result ? EvaluationValue.TRUE : EvaluationValue.FALSE;
    }

    private boolean checkVision(World world, int x, int y, int z, Direction dir, boolean strict,
        Set<String> allowedBlockIds) {
        if (dir == Direction.SKY) {
            for (int checkY = y + 1; checkY < 256; checkY++) {
                Block block = world.getBlock(x, checkY, z);
                if (block == null || block == Blocks.air || block.isAir(world, x, checkY, z)) continue;
                if (isAllowed(block, strict, allowedBlockIds)) continue;
                return false;
            }
            return true;
        } else { // VOID
            for (int checkY = y - 1; checkY >= 0; checkY--) {
                Block block = world.getBlock(x, checkY, z);
                if (block == null || block == Blocks.air || block.isAir(world, x, checkY, z)) continue;
                if (block == Blocks.bedrock) return true;
                if (isAllowed(block, strict, allowedBlockIds)) continue;
                return false;
            }
            return true;
        }
    }

    /**
     * Whether the scan sees past this block.
     * <p>
     * <b>Light opacity is the question, not whether the block renders as an opaque cube.</b>
     * It used to accept either, and {@code !isOpaqueCube()} lets through every block that
     * merely renders through a custom path while still being solid - a slab, a stair, and
     * this mod's own controller and ports, which turn that flag off so their overlay pass
     * renders. Opacity is what the chunk's height map and the world's skylight go by, so
     * going by it too is what makes {@code can_see_sky} mean what a player would read into
     * it. Glass and the like still pass: their opacity is zero.
     */
    private boolean isAllowed(Block block, boolean strict, Set<String> allowedBlockIds) {
        if (!allowedBlockIds.isEmpty()) {
            String blockId = Block.blockRegistry.getNameForObject(block);
            if (blockId != null) {
                String idLower = blockId.toLowerCase();
                if (allowedBlockIds.contains(idLower) || allowedBlockIds.contains("minecraft:" + idLower)) {
                    return true;
                }
            }
        }
        if (strict) return false;
        return block.getLightOpacity() == 0;
    }

    @Override
    public String toString() {
        return (direction == Direction.SKY ? "can_see_sky" : "can_see_void") + "()";
    }
}
