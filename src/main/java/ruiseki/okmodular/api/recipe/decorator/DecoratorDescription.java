package ruiseki.okmodular.api.recipe.decorator;

import java.util.Arrays;
import java.util.Objects;

/** One line describing a decorator: a lang key and its format arguments, any of which may be a description too. */
public final class DecoratorDescription {

    private final String key;
    private final Object[] args;

    public DecoratorDescription(String key, Object... args) {
        this.key = key;
        this.args = args;
    }

    public String getKey() {
        return key;
    }

    public Object[] getArgs() {
        return args.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DecoratorDescription that)) return false;
        return key.equals(that.key) && Arrays.deepEquals(args, that.args);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, Arrays.deepHashCode(args));
    }

    @Override
    public String toString() {
        return key + Arrays.deepToString(args);
    }
}
