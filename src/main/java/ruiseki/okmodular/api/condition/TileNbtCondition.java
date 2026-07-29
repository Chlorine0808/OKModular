package ruiseki.okmodular.api.condition;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ruiseki.okmodular.api.recipe.expression.EvaluationValue;

/**
 * Condition that checks an NBT value of the TileEntity at the current position.
 */
public class TileNbtCondition implements ICondition {

    /**
     * Two-character symbols come first so <code>&gt;=</code> is never read as
     * <code>&gt;</code> with a stray <code>=</code> left over.
     */
    private static final ComparisonOp[] OPS_LONGEST_FIRST = { ComparisonOp.GREATER_OR_EQUAL, ComparisonOp.LESS_OR_EQUAL,
        ComparisonOp.EQUAL, ComparisonOp.GREATER_THAN, ComparisonOp.LESS_THAN };

    private final String key;
    private final ComparisonOp op;
    private final double value;

    public TileNbtCondition(String key, ComparisonOp op, double value) {
        this.key = key;
        this.op = op;
        this.value = value;
    }

    @Override
    public boolean isMet(ConditionContext context) {
        if (context == null || context.getWorld() == null) return false;

        String teCacheKey = "te_nbt_" + context.getX() + "_" + context.getY() + "_" + context.getZ();
        EvaluationValue teNbtValue = context.getCachedValue(teCacheKey);
        NBTTagCompound nbt;

        if (teNbtValue != null && teNbtValue.isNbt() && teNbtValue.asNbt() instanceof NBTTagCompound nbtTagCompound) {
            nbt = nbtTagCompound;
        } else {
            TileEntity te = context.getWorld()
                .getTileEntity(context.getX(), context.getY(), context.getZ());
            if (te == null) return false;
            nbt = new NBTTagCompound();
            te.writeToNBT(nbt);
            context.setCachedValue(teCacheKey, new EvaluationValue(nbt));
        }

        if (!nbt.hasKey(key)) return false;

        double actualValue = nbt.getDouble(key);

        switch (op) {
            case GREATER_THAN:
                return actualValue > value;
            case GREATER_OR_EQUAL:
                return actualValue >= value;
            case LESS_THAN:
                return actualValue < value;
            case LESS_OR_EQUAL:
                return actualValue <= value;
            case EQUAL:
                return Math.abs(actualValue - value) < 0.0001;
            default:
                return false;
        }
    }

    @Override
    public String getDescription() {
        return StatCollector.translateToLocalFormatted("okmodular.condition.tile_nbt", key, op.symbol, value);
    }

    @Override
    public void write(JsonObject json) {
        json.addProperty("type", "tile_nbt");
        json.addProperty("key", key);
        json.addProperty(
            "op",
            op.name()
                .toLowerCase());
        json.addProperty("value", value);
    }

    /**
     * Reads either the spelled-out form or the shorthand.
     *
     * <pre>
     * { "key": "energy", "op": "greater_or_equal", "value": 1000 }
     * { "tile_nbt": "energy >= 1000" }
     * </pre>
     */
    public static ICondition fromJson(JsonObject json) {
        JsonElement shorthand = json.get("tile_nbt");
        if (shorthand != null && shorthand.isJsonPrimitive()) {
            return fromShorthand(shorthand.getAsString());
        }

        String key = json.get("key")
            .getAsString();
        ComparisonOp op = ComparisonOp.valueOf(
            json.get("op")
                .getAsString()
                .toUpperCase());
        double value = json.get("value")
            .getAsDouble();
        return new TileNbtCondition(key, op, value);
    }

    private static ICondition fromShorthand(String text) {
        for (ComparisonOp op : OPS_LONGEST_FIRST) {
            int at = text.indexOf(op.symbol);
            // An operator at position 0 would leave no key in front of it.
            if (at <= 0) continue;

            String key = text.substring(0, at)
                .trim();
            String value = text.substring(at + op.symbol.length())
                .trim();
            if (key.isEmpty() || value.isEmpty()) continue;

            try {
                return new TileNbtCondition(key, op, Double.parseDouble(value));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "A tile_nbt condition compares against a number, but read \"" + value + "\" in: " + text);
            }
        }
        throw new IllegalArgumentException(
            "A tile_nbt condition reads <key> <op> <number>, as in \"energy >= 1000\", but got: " + text);
    }

    public enum ComparisonOp {

        GREATER_THAN(">"),
        GREATER_OR_EQUAL(">="),
        LESS_THAN("<"),
        LESS_OR_EQUAL("<="),
        EQUAL("==");

        public final String symbol;

        ComparisonOp(String symbol) {
            this.symbol = symbol;
        }
    }
}
