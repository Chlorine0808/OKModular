package ruiseki.okmodular.api.recipe.expression;

import net.minecraft.nbt.NBTBase;

import ruiseki.okmodular.api.condition.ConditionContext;

/**
 * A numeric literal written with an NBT type suffix — <code>127b</code>,
 * <code>32767s</code>, <code>100i</code>, <code>100L</code>, <code>1.5f</code>,
 * <code>1.5d</code>.
 * <p>
 * Without a suffix a literal is a plain double, and an assignment writes it as one.
 * The suffix exists for the cases where the tag type matters — a byte flag, a long
 * timestamp, a float the receiving code reads with getFloat.
 */
public class NbtLiteralExpression implements IExpression {

    private final NBTBase tag;
    private final String source;

    public NbtLiteralExpression(NBTBase tag, String source) {
        this.tag = tag;
        this.source = source;
    }

    @Override
    public EvaluationValue evaluate(ConditionContext context) {
        return new EvaluationValue(tag);
    }

    /**
     * Reproduces the literal with its suffix, so a persisted expression parses back
     * to the same tag type.
     */
    @Override
    public String toString() {
        return source;
    }

    /**
     * Whether this character pins a numeric literal to an NBT type.
     * <p>
     * Long is uppercase only: a lowercase <code>l</code> is too easily read as a
     * <code>1</code>, and Minecraft's own SNBT spells it <code>L</code>.
     */
    public static boolean isTypeSuffix(int ch) {
        switch (ch) {
            case 'b':
            case 'B':
            case 's':
            case 'S':
            case 'i':
            case 'I':
            case 'L':
            case 'f':
            case 'F':
            case 'd':
            case 'D':
                return true;
            default:
                return false;
        }
    }
}
