package ruiseki.okmodular.core.helper;

import static com.gtnewhorizon.gtnhlib.blockstate.registry.BlockPropertyRegistry.getBlockState;
import static com.gtnewhorizon.gtnhlib.blockstate.registry.BlockPropertyRegistry.registerProperty;
import static net.minecraftforge.common.util.ForgeDirection.EAST;
import static net.minecraftforge.common.util.ForgeDirection.NORTH;
import static net.minecraftforge.common.util.ForgeDirection.SOUTH;
import static net.minecraftforge.common.util.ForgeDirection.WEST;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.blockstate.core.BlockState;
import com.gtnewhorizon.gtnhlib.blockstate.properties.DirectionBlockProperty;

public class BlockStateHelpers {

    public static final DirectionBlockProperty FACING = DirectionBlockProperty.facing(0b0011, dir -> switch (dir) {
        case SOUTH -> 0;
        case EAST -> 1;
        case NORTH -> 2;
        case WEST -> 3;
        default -> 0;
    }, meta -> switch (meta & 0b0011) {
        case 0 -> SOUTH;
        case 1 -> EAST;
        case 2 -> NORTH;
        case 3 -> WEST;
        default -> NORTH;
    });

    public static void registerFacingProp(Class<? extends Block> clazz) {
        registerProperty(clazz, FACING);
    }

    /** {@code World.setBlock} flags: notify neighbours and send the change to clients. */
    private static final int PLACE_FLAGS = 3;

    public static void setFacingProp(World world, int x, int y, int z, ForgeDirection facing) {
        try (BlockState state = getBlockState(world, x, y, z)) {
            state.setPropertyValue("facing", facing);
            state.place(world, x, y, z, PLACE_FLAGS);
        }
    }

    public static ForgeDirection getFacingProp(World world, int x, int y, int z) {
        try (BlockState state = getBlockState(world, x, y, z)) {
            if (state != null) {
                ForgeDirection facing = state.getPropertyValue("facing");
                return facing != null ? facing : NORTH;
            }
        } catch (Exception e) {}
        return NORTH;
    }

    public static float getFacingAngle(ForgeDirection facing) {
        return switch (facing) {
            case SOUTH -> 270f;
            case EAST -> 90f;
            case NORTH -> 0f;
            case WEST -> 180f;
            default -> 0f;
        };
    }

}
