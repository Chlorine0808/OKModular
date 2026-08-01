package ruiseki.okmodular.api.recipe.io;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import com.google.gson.JsonObject;

import cpw.mods.fml.common.registry.GameRegistry;
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
 * Writes a whole arrangement of blocks, the mirror of {@link StructureInput}.
 * <p>
 * The named pattern is laid over each block recorded for {@code symbol} and every cell is set to
 * what the pattern's mappings say, {@code _} included -- so the same file both builds an
 * arrangement and, drawn as air, clears one.
 * <p>
 * <b>It overwrites.</b> There is no per-cell "only if replaceable" filter, because a pattern is
 * placed as a unit: refusing half of it would leave the machine having produced something that is
 * not the arrangement the recipe promised. Guard with a {@link StructureInput} when the recipe
 * should only run against particular ground.
 */
public class StructureOutput extends AbstractRecipeOutput implements IModularRecipeOutput {

    /** The JSON {@code type}, the NBT {@code id} and the parser registry key. All one word. */
    public static final String TYPE = "structure";

    private char symbol;
    private String patternName;
    private int amount;
    private IExpression amountExpr;
    private boolean optional;
    private int index = -1;

    public StructureOutput(char symbol, String patternName, int amount, boolean optional) {
        this.symbol = symbol;
        this.patternName = patternName;
        this.amount = amount;
        this.amountExpr = new ConstantExpression(amount);
        this.optional = optional;
    }

    /** NBT reconstruction constructor. */
    public StructureOutput() {
        this('\0', null, 1, false);
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

    /**
     * Whether enough anchors exist to write the pattern at.
     * <p>
     * The pattern itself always fits -- it overwrites -- so what is checked is that the machine
     * actually has the blocks to anchor on and that their cells resolve. A recipe whose anchor
     * symbol is absent has nowhere to put its output, and saying so here stops it from starting
     * and consuming its inputs for nothing.
     */
    @Override
    public boolean checkCapacity(List<IModularPort> ports, int multiplier, ConditionContext context) {
        if (optional) return true;

        IRecipeContext recipeContext = (context != null) ? context.getRecipeContext() : findRecipeContext(ports);
        if (recipeContext == null) return false;

        int required = (int) (getRequiredAmount(context) * multiplier);
        return anchors(recipeContext, required).size() >= required;
    }

    @Override
    public void apply(List<IModularPort> ports, int multiplier, ConditionContext context) {
        IRecipeContext recipeContext = (context != null) ? context.getRecipeContext() : findRecipeContext(ports);
        if (recipeContext == null) return;

        apply(recipeContext, multiplier, context);
    }

    @Override
    public void apply(List<IModularPort> ports, int multiplier) {
        apply(ports, multiplier, null);
    }

    public void apply(IRecipeContext context, int multiplier, ConditionContext condContext) {
        StructurePattern pattern = StructurePatternLoader.getInstance()
            .get(patternName);
        if (pattern == null) return;

        World world = context.getWorld();
        List<StructurePattern.Cell> cells = pattern.cellsFor(machineFacing(context));
        List<int[]> written = new ArrayList<>();

        for (int[] anchorCell : anchors(context, (int) (getRequiredAmount(condContext) * multiplier))) {
            for (StructurePattern.Cell cell : cells) {
                int[] pos = context
                    .getCellPosition(anchorCell[0] + cell.a, anchorCell[1] + cell.b, anchorCell[2] + cell.c);
                if (pos == null) continue;
                if (setBlockAt(world, pos, cell.blockId)) written.add(pos);
            }
        }

        // Neighbour notifications are held back until the whole arrangement is down. Notifying
        // as each block lands lets a half-written pattern trigger updates against itself, which
        // is how BlockOutput ended up recursing through blocks that react in invalidate().
        for (int[] pos : written) {
            world.func_147453_f(pos[0], pos[1], pos[2], world.getBlock(pos[0], pos[1], pos[2]));
        }
    }

    /**
     * The anchor cells this output will write at, up to {@code limit}.
     */
    private List<int[]> anchors(IRecipeContext context, int limit) {
        List<int[]> found = new ArrayList<>();
        List<ChunkCoordinates> positions = context.getSymbolPositions(symbol);
        if (positions == null) return found;

        World world = context.getWorld();
        for (ChunkCoordinates pos : positions) {
            if (found.size() >= limit) break;
            if (!indexMatches(world, pos)) continue;

            int[] cell = context.getSymbolCell(pos.posX, pos.posY, pos.posZ);
            if (cell != null) found.add(cell);
        }
        return found;
    }

    private boolean setBlockAt(World world, int[] pos, String blockId) {
        String[] parts = blockId.split(":");
        if (parts.length < 2) return false;

        Block block = GameRegistry.findBlock(parts[0], parts[1]);
        if (block == null) return false;

        int meta = 0;
        if (parts.length >= 3 && !parts[2].equals("*")) {
            try {
                meta = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {}
        }

        // Flag 2: send to clients without notifying neighbours. The notification happens once the
        // whole pattern is placed.
        world.setBlock(pos[0], pos[1], pos[2], block, meta, 2);
        world.markBlockForUpdate(pos[0], pos[1], pos[2]);
        return true;
    }

    /**
     * The pattern is transformed with the <b>machine's</b> facing, never one of its own, so a
     * placed arrangement lines up with the machine that placed it.
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

    private IRecipeContext findRecipeContext(List<IModularPort> ports) {
        for (IModularPort port : ports) {
            if (port instanceof IRecipeContext) return (IRecipeContext) port;
        }
        return null;
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
    public IRecipeOutput copy() {
        return copy(1);
    }

    @Override
    public IRecipeOutput copy(int multiplier) {
        StructureOutput result = new StructureOutput(symbol, patternName, amount * multiplier, optional);
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
        if (optional) json.addProperty("optional", true);
        if (index != -1) json.addProperty("index", index);
        if (interval > 0) json.addProperty("pertick", interval);
    }

    public static IRecipeOutput fromJson(JsonObject json) {
        char symbol = json.has("symbol") ? json.get("symbol")
            .getAsString()
            .charAt(0) : '\0';
        String pattern = json.has("pattern") ? json.get("pattern")
            .getAsString() : null;
        boolean optional = json.has("optional") && json.get("optional")
            .getAsBoolean();

        StructureOutput output = new StructureOutput(symbol, pattern, 1, optional);
        output.read(json);

        if (pattern == null) {
            Logger.error("A structure output has no 'pattern' - it can never place anything");
        } else if (StructurePatternLoader.getInstance()
            .get(pattern) == null) {
                Logger.warn("Structure output references an unknown pattern: {}", pattern);
            }
        return output;
    }

    @Override
    public void accept(IRecipeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public RecipeTickResult getFailureResult(boolean perTick) {
        return RecipeTickResult.BLOCK_OUTPUT_FULL;
    }
}
