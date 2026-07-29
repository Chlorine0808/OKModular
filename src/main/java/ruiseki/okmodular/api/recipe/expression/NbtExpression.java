package ruiseki.okmodular.api.recipe.expression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;

/**
 * Reads a value out of a TileEntity's NBT.
 * <p>
 * This is the single way to reach NBT from a recipe script:
 *
 * <pre>
 * nbt('energy')             the machine's own NBT
 * nbt('display.Name')       a nested path, dots inside the string
 * nbt('S', 'stored')        the TileEntity at structure symbol S
 * nbt('S', 'a.b.c')         a nested path on that TileEntity
 * </pre>
 *
 * The target goes in the arguments, never in the surrounding syntax. Bare dot
 * notation (<code>display.Name</code> as an expression of its own) is not
 * accepted, because a bare <code>S.energy</code> cannot be told apart from a
 * two-level path on the machine itself.
 */
public class NbtExpression implements IExpression {

    private final String nbtKey;
    private final List<String> pathSegments;
    private final EvaluationValue defaultValue;
    private final char symbol;

    public NbtExpression(String nbtKey, EvaluationValue defaultValue, char symbol) {
        this.nbtKey = nbtKey;
        this.pathSegments = splitPath(nbtKey);
        this.defaultValue = defaultValue;
        this.symbol = symbol;
    }

    public NbtExpression(String nbtKey, double defaultValue) {
        this(nbtKey, new EvaluationValue(defaultValue), '\0');
    }

    /**
     * Splits a key on dots. The dots live inside the argument string, so the
     * expression parser never has to decide what a dot means.
     */
    private static List<String> splitPath(String key) {
        if (key == null || key.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(key.split("\\."))));
    }

    @Override
    public EvaluationValue evaluate(ConditionContext context) {
        NBTBase found = resolveTag(context);
        return found != null ? new EvaluationValue(found) : defaultValue;
    }

    /**
     * Resolves the tag this expression points at, or null if it is not there.
     * <p>
     * Separate from {@link #evaluate} because "absent" and "zero" are different
     * questions, and {@link NbtPresenceExpression} needs to tell them apart.
     */
    public NBTBase resolveTag(ConditionContext context) {
        if (context == null || context.getWorld() == null) return null;

        ChunkCoordinates pos = null;
        if (symbol != '\0' && context.getRecipeContext() != null) {
            List<ChunkCoordinates> positions = context.getRecipeContext()
                .getSymbolPositions(symbol);
            if (positions != null && !positions.isEmpty()) {
                pos = positions.get(0);
            }
        } else {
            pos = new ChunkCoordinates(context.getX(), context.getY(), context.getZ());
        }

        if (pos == null) return null;

        String teCacheKey = "te_nbt_" + pos.posX + "_" + pos.posY + "_" + pos.posZ;
        EvaluationValue teNbtValue = context.getCachedValue(teCacheKey);
        NBTTagCompound nbt;

        if (teNbtValue != null && teNbtValue.isNbt() && teNbtValue.asNbt() instanceof NBTTagCompound nbttagcompound) {
            nbt = nbttagcompound;
        } else {
            TileEntity te = context.getWorld()
                .getTileEntity(pos.posX, pos.posY, pos.posZ);
            if (te == null) return null;
            nbt = new NBTTagCompound();
            te.writeToNBT(nbt);
            context.setCachedValue(teCacheKey, new EvaluationValue(nbt));
        }

        return navigatePath(nbt, pathSegments);
    }

    /**
     * Walks a compound down the given segments. A single segment is the flat case,
     * so both are handled here.
     */
    private static NBTBase navigatePath(NBTTagCompound root, List<String> segments) {
        NBTBase current = root;
        for (String segment : segments) {
            if (!(current instanceof NBTTagCompound)) return null;

            NBTTagCompound compound = (NBTTagCompound) current;
            if (!compound.hasKey(segment)) return null;
            current = compound.getTag(segment);
        }
        return current == root ? null : current;
    }

    /**
     * Reproduces the call as a recipe script.
     * <p>
     * Expressions are persisted by writing this string and parsing it back, so the
     * result has to read as the same target it started as. The old form wrote a
     * symbol as <code>S.key</code>, which came back as a two-level path on the
     * machine itself — a different TileEntity entirely.
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("nbt('");
        if (symbol != '\0') {
            text.append(symbol)
                .append("', '");
        }
        return text.append(nbtKey)
            .append("')")
            .toString();
    }

    public String getNbtKey() {
        return nbtKey;
    }

    public List<String> getPathSegments() {
        return pathSegments;
    }

    public char getSymbol() {
        return symbol;
    }

    public static IExpression fromJson(JsonObject json) {
        String key = json.get("key")
            .getAsString();
        EvaluationValue def = json.has("default") ? new EvaluationValue(
            json.get("default")
                .getAsDouble())
            : EvaluationValue.ZERO;
        char sym = json.has("symbol") ? json.get("symbol")
            .getAsString()
            .charAt(0) : '\0';
        return new NbtExpression(key, def, sym);
    }
}
