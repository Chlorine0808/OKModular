package ruiseki.okmodular.api.recipe.io;

/**
 * Turns a computed amount into the int the game's own APIs take.
 **/

public final class Amounts {

    private Amounts() {}

    /**
     * @param amount an amount computed in long; may be negative, or past what an int holds
     * @return the same amount, held inside {@code [0, Integer.MAX_VALUE]}
     */
    public static int toInt(long amount) {
        if (amount <= 0) return 0;
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
    }
}
