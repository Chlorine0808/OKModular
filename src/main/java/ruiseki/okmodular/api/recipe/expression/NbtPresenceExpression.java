package ruiseki.okmodular.api.recipe.expression;

import ruiseki.okmodular.api.condition.ConditionContext;

/**
 * Whether an NBT key is present at all, as opposed to what it holds.
 *
 * <pre>
 * has_nbt('energy')          does the machine have this key
 * has_nbt('S', 'stored')     does the block at symbol S have it
 * has_nbt('a.b.c')           does this nested path resolve
 * </pre>
 *
 * <code>nbt('energy')</code> answers 0 for a key that does not exist, which reads
 * the same as a key holding 0. That is fine for <code>&gt;=</code> comparisons and
 * wrong for <code>&lt;=</code> ones, where an absent key would otherwise satisfy the
 * condition. Pair the two when the distinction matters:
 *
 * <pre>
 * has_nbt('heat') &amp;&amp; nbt('heat') &lt;= 100
 * </pre>
 */
public class NbtPresenceExpression implements IExpression {

    private final NbtExpression access;

    public NbtPresenceExpression(NbtExpression access) {
        this.access = access;
    }

    @Override
    public EvaluationValue evaluate(ConditionContext context) {
        return access.resolveTag(context) != null ? EvaluationValue.TRUE : EvaluationValue.FALSE;
    }

    @Override
    public String toString() {
        // nbt('S', 'key') -> has_nbt('S', 'key')
        String accessText = access.toString();
        return "has_" + accessText;
    }

    public NbtExpression getAccess() {
        return access;
    }
}
