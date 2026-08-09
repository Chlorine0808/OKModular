package ruiseki.okmodular.api.recipe.decorator;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.IShearable;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.expression.ConstantExpression;
import ruiseki.okmodular.api.recipe.expression.ExpressionsParser;
import ruiseki.okmodular.api.recipe.expression.IExpression;
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
 *
 * <h2>Three moments, not one</h2>
 *
 * The other five decorators all fit on {@link RecipeDecorator#produceExtraOutputs}, which runs
 * after the cached outputs land. This one cannot: by then a {@code BlockOutput} has already
 * replaced the block it was supposed to collect. It needs three separate moments, and getting
 * them wrong is what left it dead for so long.
 *
 * <pre>
 * canStart              レシピが始まる前   硬すぎるブロックがあれば断る
 * beforeInputsConsumed  入力を消す直前     BlockInput が持っていくブロックを採る
 * beforeOutputsApplied  出力を書く直前     BlockOutput が上書きするブロックを採る
 * </pre>
 *
 * <h2>Why the gate is at the start and only at the start</h2>
 *
 * The harvest-level check has to be able to refuse, and start is the only moment where
 * refusing is honest. Refusing while consuming would take the inputs and stop; refusing while
 * producing would take the inputs, finish the work, and hand back nothing while the machine
 * waits forever for output room it does not need.
 *
 * So {@link #canStart} looks at <em>both</em> sets of positions - the ones an input will
 * consume and the ones an output will overwrite - and answers once. Both are resolvable at
 * start, so nothing is guessed. The two harvest hooks after it never refuse.
 *
 * <h2>What {@code harvestLevel} is</h2>
 *
 * An expression, defaulting to <b>0</b> - what a wooden pick manages - so a recipe that leaves
 * it out refuses to start the moment its structure contains iron ore. Writing
 * {@code "harvestLevel": "tier"} lets a better machine mine harder blocks; it is evaluated
 * once in {@link #canStart}, so both sides are judged against the same number.
 */
public class HarvestBlockDecorator extends RecipeDecorator {

    /** What {@link IShearable} is handed as the tool. Nothing reads its damage or enchants. */
    private static final ItemStack SHEARS = new ItemStack(Items.shears);

    private final int fortune;
    private final boolean silkTouch;
    private final boolean shear;
    private final IExpression harvestLevelExpr;

    public HarvestBlockDecorator(IModularRecipe internal, int fortune, boolean silkTouch, boolean shear,
        IExpression harvestLevelExpr) {
        super(internal);
        this.fortune = fortune;
        this.silkTouch = silkTouch;
        this.shear = shear;
        this.harvestLevelExpr = harvestLevelExpr;
    }

    /**
     * Refuses the run when any block it would collect is too hard for {@code harvestLevel}.
     * <p>
     * Both sides are checked here because this is the only moment refusing costs nothing.
     * See the class javadoc.
     */
    @Override
    public boolean canStart(ConditionContext context) {
        if (!super.canStart(context)) return false;

        IRecipeContext recipe = context != null ? context.getRecipeContext() : null;
        // No machine means no world to look at - validation and NEI take this path. Refusing
        // here would make the recipe look impossible everywhere it is merely unevaluated.
        if (recipe == null || recipe.getWorld() == null) return true;

        int allowed = (int) harvestLevelExpr.evaluateDouble(context);
        return levelAllows(recipe, positionsFrom(recipe, true), allowed)
            && levelAllows(recipe, positionsFrom(recipe, false), allowed);
    }

    @Override
    public void beforeInputsConsumed(List<IModularPort> inputPorts, ConditionContext context) {
        super.beforeInputsConsumed(inputPorts, context);
        harvest(context, inputPorts, true);
    }

    @Override
    public void beforeOutputsApplied(List<IModularPort> outputPorts, ConditionContext context) {
        super.beforeOutputsApplied(outputPorts, context);
        harvest(context, outputPorts, false);
    }

    /**
     * The blocks this recipe is about to remove on one side.
     *
     * @param fromInputs true for the blocks a consuming {@code BlockInput} takes, false for
     *                   the ones a {@code BlockOutput} overwrites
     */
    private List<ChunkCoordinates> positionsFrom(IRecipeContext context, boolean fromInputs) {
        List<ChunkCoordinates> targetPositions = new ArrayList<>();

        if (fromInputs) {
            for (IRecipeInput input : internal.getInputs()) {
                if (input instanceof BlockInput bi && bi.isConsume()) {
                    targetPositions.addAll(
                        context.getSymbolPositions(
                            bi.toJson()
                                .get("symbol")
                                .getAsString()
                                .charAt(0)));
                }
            }
        } else {
            for (IRecipeOutput output : internal.getOutputs()) {
                if (output instanceof BlockOutput bo) {
                    targetPositions.addAll(bo.getPositions(context));
                }
            }
        }
        return targetPositions;
    }

    /**
     * Whether every block at these positions is soft enough. Air and toolless blocks pass.
     *
     * @param allowed the evaluated {@code harvestLevel}, settled once by the caller so both
     *                sides are judged against the same number
     */
    private boolean levelAllows(IRecipeContext context, List<ChunkCoordinates> positions, int allowed) {
        World world = context.getWorld();

        for (ChunkCoordinates pos : positions) {
            Block block = world.getBlock(pos.posX, pos.posY, pos.posZ);
            if (block.isAir(world, pos.posX, pos.posY, pos.posZ)) continue;

            int meta = world.getBlockMetadata(pos.posX, pos.posY, pos.posZ);
            if (block.getHarvestTool(meta) == null) continue;
            if (block.getHarvestLevel(meta) > allowed) return false;
        }
        return true;
    }

    /** Collects one side's blocks into the ports. {@link #canStart} already allowed it. */
    private void harvest(ConditionContext context, List<IModularPort> ports, boolean fromInputs) {
        IRecipeContext recipe = context != null ? context.getRecipeContext() : null;
        if (recipe == null || recipe.getWorld() == null) return;

        for (ChunkCoordinates pos : positionsFrom(recipe, fromInputs)) {
            harvestAt(recipe.getWorld(), pos, ports);
        }
    }

    private void harvestAt(World world, ChunkCoordinates pos, List<IModularPort> ports) {
        int x = pos.posX;
        int y = pos.posY;
        int z = pos.posZ;
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockMetadata(x, y, z);

        if (block.isAir(world, x, y, z)) return;

        List<ItemStack> drops = new ArrayList<>();

        // Silk touch first when both are set: it promises the block itself for anything that
        // allows it, while shears only speak for blocks that implement IShearable. For leaves
        // the two agree anyway.
        if (silkTouch && block.canSilkHarvest(world, null, x, y, z, meta)) {
            drops.add(new ItemStack(block, 1, meta));
        } else if (shear && block instanceof IShearable shearable && shearable.isShearable(SHEARS, world, x, y, z)) {
            drops.addAll(shearable.onSheared(SHEARS, world, x, y, z, fortune));
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
        IExpression harvestLevel = json.has("harvestLevel") ? ExpressionsParser.parse(json.get("harvestLevel"))
            : new ConstantExpression(0);

        if (!json.has("harvestLevel")) {
            Logger.warn(
                "harvest_block with no harvestLevel defaults to 0, which is what a wooden pick "
                    + "manages. Any block in the structure that needs better - iron ore needs 1 - "
                    + "stops the recipe from starting at all. Write \"harvestLevel\": \"tier\" to "
                    + "let the machine decide instead of a fixed number.");
        }

        return new HarvestBlockDecorator(recipe, fortune, silkTouch, shear, harvestLevel);
    }
}
