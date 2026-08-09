package ruiseki.okmodular.api.recipe.decorator;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.io.BlockInput;
import ruiseki.okmodular.api.recipe.io.BlockOutput;
import ruiseki.okmodular.api.recipe.io.IRecipeInput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.client.gui.handler.ItemStackHandlerBase;
import ruiseki.okmodular.core.item.ItemUtils;
import ruiseki.okmodular.util.Logger;

/**
 * Decorator that harvests blocks affected by the recipe's BlockInputs and
 * BlockOutputs.
 * Supports Fortune, Silk Touch, Shears, and Harvest Level requirements.
 * <p>
 * <b>This one does not run.</b> Like the other decorators it hung its work off
 * {@code processInputs} / {@code processOutputs} with a {@code !simulate} guard, and the
 * engine only ever called those to simulate - see {@code DecoratorWiringTest}. Those two
 * methods are gone now, folded into the check-only {@code matchesInput} and {@code canOutput}
 * so nothing else mistakes them for the execution path, and the overrides went with them.
 * <p>
 * The rest of the decorators moved to {@link RecipeDecorator#produceExtraOutputs}, which runs
 * after the cached outputs land. Harvesting cannot: it has to happen <em>before</em> the
 * blocks it collects are overwritten, and it also needs a hook on the input side, so it needs
 * its own two call sites rather than that one. Item S in {@code run/StructureIO_todos.md}.
 * <p>
 * {@link #checkAndHarvest} is therefore parked - written, correct as far as anyone knows, and
 * called by nobody. {@code DecoratorWiringTest.KNOWN_DEAD} names it so, and asserts it stays
 * uncalled until item S gives it somewhere to hang; the day it gets a call site, that list
 * fails until the entry is removed. Parked code a test points at is survivable. Parked code
 * that looks like it runs is what cost six decorators.
 */
public class HarvestBlockDecorator extends RecipeDecorator {

    private final int fortune;
    private final boolean silkTouch;
    @SuppressWarnings("unused")
    private final boolean shear;
    private final int harvestLevel;

    public HarvestBlockDecorator(IModularRecipe internal, int fortune, boolean silkTouch, boolean shear,
        int harvestLevel) {
        super(internal);
        this.fortune = fortune;
        this.silkTouch = silkTouch;
        this.shear = shear;
        this.harvestLevel = harvestLevel;
    }

    /**
     * Harvest blocks from either inputs or outputs.
     * <p>
     * <b>Nothing calls this.</b> See the class javadoc: the two hooks it used to hang off are
     * gone, and item S owes it a pair of real ones. Package-private rather than private so
     * the wiring test can name it without reflection.
     *
     * @param context    Recipe context
     * @param ports      Available ports (for item insertion)
     * @param fromInputs True if checking inputs, false for outputs
     * @return true if all harvest level requirements are met
     */
    boolean checkAndHarvest(IRecipeContext context, List<IModularPort> ports, boolean fromInputs) {
        List<ChunkCoordinates> targetPositions = new ArrayList<>();
        World world = context.getWorld();

        // 1. Identify all affected positions
        if (fromInputs) {
            for (IRecipeInput input : internal.getInputs()) {
                if (input instanceof BlockInput) {
                    BlockInput bi = (BlockInput) input;
                    if (bi.isConsume()) {
                        targetPositions.addAll(
                            context.getSymbolPositions(
                                bi.toJson()
                                    .get("symbol")
                                    .getAsString()
                                    .charAt(0)));
                    }
                }
            }
        } else {
            for (IRecipeOutput output : internal.getOutputs()) {
                if (output instanceof BlockOutput) {
                    BlockOutput bo = (BlockOutput) output;
                    targetPositions.addAll(bo.getPositions(context));
                }
            }
        }

        // 2. Validate harvest level for all positions first
        for (ChunkCoordinates pos : targetPositions) {
            Block block = world.getBlock(pos.posX, pos.posY, pos.posZ);
            if (block.isAir(world, pos.posX, pos.posY, pos.posZ)) continue;

            String harvestTool = block.getHarvestTool(world.getBlockMetadata(pos.posX, pos.posY, pos.posZ));
            if (harvestTool != null) {
                int level = block.getHarvestLevel(world.getBlockMetadata(pos.posX, pos.posY, pos.posZ));
                if (level > harvestLevel) {
                    return false; // Requirement not met, abort recipe
                }
            }
        }

        // 3. Perform harvesting
        for (ChunkCoordinates pos : targetPositions) {
            harvestAt(world, pos, ports);
        }

        return true;
    }

    private void harvestAt(World world, ChunkCoordinates pos, List<IModularPort> ports) {
        int x = pos.posX;
        int y = pos.posY;
        int z = pos.posZ;
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);

        if (block.isAir(world, x, y, z)) return;

        List<ItemStack> drops = new ArrayList<>();

        if (silkTouch && block.canSilkHarvest(world, null, x, y, z, meta)) {
            drops.add(new ItemStack(block, 1, meta));
        } else {
            // Standard drop with fortune
            drops.addAll(block.getDrops(world, x, y, z, meta, fortune));
        }

        if (drops.isEmpty()) return;

        // Try to insert into item ports
        List<IModularPort> itemPorts = filterByType(ports, IPortType.Type.ITEM);
        for (ItemStack drop : drops) {
            ItemStack remaining = insertIntoPorts(itemPorts, drop);
            if (remaining != null && remaining.stackSize > 0) {
                // Drop into world if port full
                ItemStackHandlerBase.dropStack(world, x, y, z, remaining);
            }
        }
    }

    private ItemStack insertIntoPorts(List<IModularPort> itemPorts, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (IModularPort port : itemPorts) {
            if (port instanceof IInventory) {
                IInventory inv = (IInventory) port;
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    ItemStack inSlot = inv.getStackInSlot(i);
                    if (inSlot == null) {
                        inv.setInventorySlotContents(i, remaining);
                        return null;
                    } else if (ItemUtils.areStackMergable(inSlot, remaining)) {
                        int canAdd = Math.min(remaining.stackSize, inv.getInventoryStackLimit() - inSlot.stackSize);
                        inSlot.stackSize += canAdd;
                        remaining.stackSize -= canAdd;
                        if (remaining.stackSize <= 0) return null;
                    }
                }
            }
        }
        return remaining;
    }

    private List<IModularPort> filterByType(List<IModularPort> ports, IPortType.Type type) {
        List<IModularPort> filtered = new ArrayList<>();
        for (IModularPort port : ports) {
            if (port.getPortType() == type) {
                filtered.add(port);
            }
        }
        return filtered;
    }

    public static IModularRecipe fromJson(IModularRecipe recipe, JsonObject json) {
        int fortune = json.has("fortune") ? json.get("fortune")
            .getAsInt() : 0;
        boolean silkTouch = json.has("silkTouch") && json.get("silkTouch")
            .getAsBoolean();
        boolean shear = json.has("shear") && json.get("shear")
            .getAsBoolean();
        int harvestLevel = json.has("harvestLevel") ? json.get("harvestLevel")
            .getAsInt() : 0;

        Logger.warn(
            "harvest_block is parsed but does nothing yet: its effects hang off "
                + "processInputs/processOutputs, which the engine only ever calls to simulate. "
                + "The other decorators moved to produceExtraOutputs; this one cannot, because it "
                + "has to run BEFORE the blocks it harvests are overwritten. Item S in "
                + "run/StructureIO_todos.md. Saying so is better than a recipe that silently "
                + "drops its drops.");

        return new HarvestBlockDecorator(recipe, fortune, silkTouch, shear, harvestLevel);
    }
}
