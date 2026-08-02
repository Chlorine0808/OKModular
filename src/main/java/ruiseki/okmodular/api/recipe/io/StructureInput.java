package ruiseki.okmodular.api.recipe.io;

import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.recipe.core.RecipeTickResult;
import ruiseki.okmodular.api.recipe.expression.ArithmeticExpression;
import ruiseki.okmodular.api.recipe.expression.ConstantExpression;
import ruiseki.okmodular.api.recipe.expression.ExpressionParser;
import ruiseki.okmodular.api.recipe.expression.ExpressionsParser;
import ruiseki.okmodular.api.recipe.expression.IExpression;
import ruiseki.okmodular.api.recipe.visitor.IRecipeVisitor;
import ruiseki.okmodular.api.structure.core.IStructureEntry;
import ruiseki.okmodular.structure.pattern.StructurePattern;
import ruiseki.okmodular.structure.pattern.StructurePatternLoader;
import ruiseki.okmodular.util.Logger;

/**
 * A recipe input satisfied by a whole arrangement of blocks rather than a single one.
 * <p>
 * {@code symbol} names a structure symbol; every block the formation check recorded for it is a
 * candidate anchor, and the named {@link StructurePattern} is laid over each in turn. An anchor
 * counts only when <b>all</b> of the pattern's cells match, which is what makes this one input
 * instead of n&sup3; block inputs.
 * <p>
 * The pattern extends in structure-local coordinates, so it turns with the machine and may reach
 * cells the formation check never visited -- {@link IRecipeContext#getCellPosition} resolves those
 * too.
 * <p>
 * Where {@code BlockInput} is "n blocks of this kind somewhere under this symbol", this is "this
 * exact shape, in this exact orientation, at this block".
 */
public class StructureInput extends AbstractRecipeInput implements IModularRecipeInput {

    /** The JSON {@code type}, the NBT {@code id} and the parser registry key. All one word. */
    public static final String TYPE = "structure";

    private char symbol;
    private String patternName;
    private int amount;
    private IExpression amountExpr;
    private boolean optional;
    private int index = -1;

    public StructureInput(char symbol, String patternName, int amount, boolean consume, boolean optional) {
        this.symbol = symbol;
        this.patternName = patternName;
        this.amount = amount;
        this.amountExpr = new ConstantExpression(amount);
        this.consume = consume;
        this.optional = optional;
    }

    /** NBT reconstruction constructor. */
    public StructureInput() {
        this('\0', null, 1, false, false);
    }

    @Override
    public IPortType.Type getPortType() {
        return IPortType.Type.BLOCK;
    }

    @Override
    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public char getSymbol() {
        return symbol;
    }

    public String getPatternName() {
        return patternName;
    }

    public int getAmount() {
        return amount;
    }

    public boolean isOptional() {
        return optional;
    }

    @Override
    public boolean process(List<IModularPort> ports, int multiplier, boolean simulate, ConditionContext context) {
        IRecipeContext recipeContext = (context != null) ? context.getRecipeContext() : IRecipeContext.findIn(ports);
        if (recipeContext == null) return false;

        return check(recipeContext, multiplier, simulate, context);
    }

    @Override
    public boolean process(List<IModularPort> ports, int multiplier, boolean simulate) {
        return process(ports, multiplier, simulate, null);
    }

    public boolean check(IRecipeContext context, int multiplier, boolean simulate, ConditionContext condContext) {
        if (optional && simulate) return true;

        StructurePattern pattern = StructurePatternLoader.getInstance()
            .get(patternName);
        if (pattern == null) return optional;

        List<ChunkCoordinates> anchors = context.getSymbolPositions(symbol);
        if (anchors == null) return optional;

        List<StructurePattern.Cell> cells = pattern.cellsFor(machineFacing(context));
        World world = context.getWorld();
        int required = (int) (getRequiredAmount(condContext) * multiplier);
        int found = 0;

        for (ChunkCoordinates anchor : anchors) {
            if (found >= required) break;
            if (!indexMatches(world, anchor)) continue;

            int[] anchorCell = context.getSymbolCell(anchor.posX, anchor.posY, anchor.posZ);
            if (anchorCell == null) continue;
            if (!allCellsMatch(context, world, cells, anchorCell)) continue;

            found++;
            if (!simulate && consume) {
                clear(context, world, cells, anchorCell);
            }
        }

        return optional || found >= required;
    }

    private boolean allCellsMatch(IRecipeContext context, World world, List<StructurePattern.Cell> cells,
        int[] anchorCell) {
        for (StructurePattern.Cell cell : cells) {
            int[] pos = context.getCellPosition(anchorCell[0] + cell.a, anchorCell[1] + cell.b, anchorCell[2] + cell.c);
            if (pos == null) return false;

            String id = BlockIdMatcher
                .idOf(world.getBlock(pos[0], pos[1], pos[2]), world.getBlockMetadata(pos[0], pos[1], pos[2]));
            if (!BlockIdMatcher.matches(id, cell.blockId)) return false;
        }
        return true;
    }

    /**
     * Removes the matched arrangement.
     * <p>
     * The whole pattern goes, not a count of blocks: a partially consumed arrangement is not a
     * smaller arrangement, it is a broken one, and the next check would not match it anyway.
     */
    private void clear(IRecipeContext context, World world, List<StructurePattern.Cell> cells, int[] anchorCell) {
        for (StructurePattern.Cell cell : cells) {
            int[] pos = context.getCellPosition(anchorCell[0] + cell.a, anchorCell[1] + cell.b, anchorCell[2] + cell.c);
            if (pos == null) continue;
            world.setBlockToAir(pos[0], pos[1], pos[2]);
        }
    }

    /**
     * The pattern is transformed with the <b>machine's</b> facing, never one of its own, so it
     * cannot end up rotated relative to the structure it sits in.
     */
    private String machineFacing(IRecipeContext context) {
        IStructureEntry entry = context.getCurrentStructure();
        return entry == null ? null : entry.getDefaultFacing();
    }

    private boolean indexMatches(World world, ChunkCoordinates pos) {
        if (index == -1) return true;
        TileEntity te = world.getTileEntity(pos.posX, pos.posY, pos.posZ);
        return !(te instanceof IModularPort) || ((IModularPort) te).getAssignedIndex() == index;
    }

    @Override
    public long getRequiredAmount(ConditionContext context) {
        return amountExpr != null ? (long) amountExpr.evaluateDouble(context) : amount;
    }

    @Override
    public long getRequiredAmount() {
        return amount;
    }

    @Override
    public IRecipeInput copy() {
        return copy(1);
    }

    @Override
    public IRecipeInput copy(int multiplier) {
        StructureInput result = new StructureInput(symbol, patternName, amount * multiplier, consume, optional);
        result.amountExpr = ArithmeticExpression.scaled(this.amountExpr, multiplier);
        result.interval = this.interval;
        result.index = this.index;
        return result;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setString("id", TYPE);
        nbt.setString("symbol", String.valueOf(symbol));
        if (patternName != null) nbt.setString("pattern", patternName);
        if (amountExpr instanceof ConstantExpression) {
            nbt.setInteger("amount", amount);
        } else {
            nbt.setString("amountExpr", amountExpr.toString());
        }
        nbt.setBoolean("consume", consume);
        nbt.setBoolean("optional", optional);
        nbt.setInteger("interval", interval);
        nbt.setInteger("index", index);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.symbol = nbt.getString("symbol")
            .isEmpty() ? '\0'
                : nbt.getString("symbol")
                    .charAt(0);
        this.patternName = nbt.hasKey("pattern") ? nbt.getString("pattern") : null;
        this.consume = nbt.getBoolean("consume");
        this.optional = nbt.getBoolean("optional");
        this.interval = nbt.getInteger("interval");
        this.index = nbt.hasKey("index") ? nbt.getInteger("index") : -1;

        this.amount = nbt.getInteger("amount");
        this.amountExpr = nbt.hasKey("amountExpr") ? ExpressionParser.parseExpression(nbt.getString("amountExpr"))
            : new ConstantExpression(amount);
    }

    @Override
    public void read(JsonObject json) {
        readPerTick(json, 0);
        if (json.has("index")) {
            this.index = json.get("index")
                .getAsInt();
        }
        if (json.has("amount")) {
            this.amountExpr = ExpressionsParser.parse(json.get("amount"));
            if (amountExpr instanceof ConstantExpression) {
                this.amount = (int) amountExpr.evaluateDouble(null);
            }
        } else {
            this.amount = 1;
            this.amountExpr = new ConstantExpression(1);
        }
    }

    @Override
    public void write(JsonObject json) {
        json.addProperty("type", TYPE);
        json.addProperty("symbol", String.valueOf(symbol));
        if (patternName != null) json.addProperty("pattern", patternName);
        if (amountExpr instanceof ConstantExpression) {
            json.addProperty("amount", amount);
        } else {
            json.addProperty("amount", amountExpr.toString());
        }
        if (consume) json.addProperty("consume", true);
        if (optional) json.addProperty("optional", true);
        if (index != -1) json.addProperty("index", index);
        if (interval > 0) json.addProperty("pertick", interval);
    }

    public static IRecipeInput fromJson(JsonObject json) {
        char symbol = json.has("symbol") ? json.get("symbol")
            .getAsString()
            .charAt(0) : '\0';
        String pattern = json.has("pattern") ? json.get("pattern")
            .getAsString() : null;
        boolean consume = json.has("consume") && json.get("consume")
            .getAsBoolean();
        boolean optional = json.has("optional") && json.get("optional")
            .getAsBoolean();

        StructureInput input = new StructureInput(symbol, pattern, 1, consume, optional);
        input.read(json);

        // Reported at load rather than at craft time: a name that resolves to nothing produces a
        // recipe that simply never fires, which reads to the author as their JSON being ignored.
        if (pattern == null) {
            Logger.error("A structure input has no 'pattern' - it can never match");
        } else if (StructurePatternLoader.getInstance()
            .get(pattern) == null) {
                Logger.warn("Structure input references an unknown pattern: {}", pattern);
            }
        return input;
    }

    @Override
    public void accept(IRecipeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public RecipeTickResult getFailureResult(boolean perTick) {
        return RecipeTickResult.BLOCK_MISSING;
    }
}
