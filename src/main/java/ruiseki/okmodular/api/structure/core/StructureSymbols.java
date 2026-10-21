package ruiseki.okmodular.api.structure.core;

/**
 * The symbols a structure's layers reserve, and which of them a recipe may point at.
 *
 * <h2>Pointing at a symbol means asking for its positions</h2>
 *
 * A recipe reaches structure cells through {@code getSymbolPositions(symbol)}, and that map is
 * filled by {@code CustomStructureRegistry.wrapTracking} as the structure check walks the
 * shape. A symbol registered without the wrapper is <b>never recorded</b>, so its position
 * list comes back empty - and an empty list is not an error anywhere. A decorator pointed at
 * such a symbol acts on nothing and reports success; a probability decorator is then
 * indistinguishable from bad luck.
 * <p>
 * {@link #CONTROLLER} and {@link #AIR} used to be registered bare, for the reasonable-sounding
 * reason that a machine knows where its own controller is and that air holds nothing worth
 * collecting. That reasoning only covers <b>blocks</b>. An effect placed at a cell - a
 * particle, a sound, a spawn, a check on what is standing there - wants the position whatever
 * the cell holds, and the controller's cell and the empty ones are as real as any other. Both
 * are recorded now.
 *
 * <h2>The one that is refused</h2>
 *
 * {@link #ANYTHING} is not part of the structure at all. The shape reader skips it, the
 * builder never sees it, and no check ever visits those coordinates - so there is nothing to
 * record, and nothing that could be recorded without making the engine walk cells it has
 * deliberately been told to ignore. A recipe naming it is refused when it loads rather than
 * left to do nothing forever.
 */
public final class StructureSymbols {

    /** The controller block's own cell. Recorded, and a recipe may point at it. */
    public static final char CONTROLLER = 'Q';

    /** A cell that has to be empty. Recorded, and a recipe may point at it. */
    public static final char AIR = '_';

    /** A cell outside the structure. Never visited, so never recorded, so not addressable. */
    public static final char ANYTHING = ' ';

    private StructureSymbols() {}

    /**
     * Whether this symbol may be given a meaning in a structure's {@code mappings}.
     * <p>
     * All three are defined by the engine, so a mapping for one of them would be read and then
     * quietly dropped.
     */
    public static boolean isReserved(char symbol) {
        return symbol == CONTROLLER || symbol == AIR || symbol == ANYTHING;
    }

    /**
     * Whether a recipe may point at this symbol - that is, whether its positions are recorded.
     */
    public static boolean isAddressable(char symbol) {
        return symbol != ANYTHING;
    }

    /**
     * Refuse a recipe that points at a symbol whose positions are never recorded.
     * <p>
     * This is a load-time refusal because the alternative is a run-time silence: the position
     * list is empty, every caller treats empty as "nothing to do", and the recipe reports
     * success having done nothing.
     *
     * @param symbol what the recipe wrote
     * @param where  the key or feature that wrote it, for the message
     * @return the symbol, so this can wrap the read
     * @throws IllegalArgumentException if the symbol has no positions to give
     */
    public static char requireAddressable(char symbol, String where) {
        if (isAddressable(symbol)) return symbol;

        throw new IllegalArgumentException(
            where + " points at ' ', which marks a cell outside the structure. Those cells are never"
                + " checked and so have no recorded positions - the recipe would act on nothing and"
                + " report success. Give the cell a symbol in the structure's mappings first.");
    }
}
