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
     * coordinates. That is {@code y >= heightMap}, and the height map is built from
     * {@link net.minecraft.block.Block#getLightOpacity()} - which is <b>zero for every block
     * this mod registers</b>, casings included (see {@link #isAllowed}). So the machine is
     * invisible to the height map and the two spellings happened to agree.
     * </ul>
     * Agreeing by accident is the thing being removed. The height map answer rests on an
     * opacity value that is zero for a reason nobody chose, and it counts the block at the
     * asked-about coordinate, so a controller that ever became a light-blocking cube would
     * silently start answering false everywhere.
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
     * <b>Every block this mod registers passes, and only one of the two reasons was chosen.</b>
     * The controller and the ports set {@code isOpaque = false} deliberately, so
     * {@code isOpaqueCube()} is false for them. A casing does not - but
     * {@link Block#getLightOpacity()} answers zero for it all the same, because
     * {@code Block(Material)} runs {@code lightOpacity = isOpaqueCube() ? 255 : 0} while
     * {@code BlockOK.isOpaque} still holds its default {@code false}: that field's
     * initialiser only runs once the superclass constructor has returned. So the casing is
     * an opaque cube that reports no light opacity.
     * <p>
     * A machine is therefore see-through to this check, and to the chunk's height map with
     * it. That is a defensible behaviour - a machine should not block its own view of the
     * sky - but it is not one this code asked for, and it is not confined to expressions.
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
        if (!block.isOpaqueCube() || block.getLightOpacity() == 0) return true;
        return false;
    }

    @Override
    public String toString() {
        return (direction == Direction.SKY ? "can_see_sky" : "can_see_void") + "()";
    }
}
