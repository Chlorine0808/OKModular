package ruiseki.okmodular.api.modular;

public interface IPortType {

    /**
     * What kind of resource a port deals in.
     *
     * <h2>Adding a constant</h2>
     *
     * A constant can go anywhere in this list. External port configs are stored by
     * {@code name()} and old saves decode through frozen ordinal tables held by
     * {@code ExternalPortConfigCodec}, so the position of a constant is not part of
     * any stored format.
     *
     * <p>
     * Two things are still order- or name-sensitive:
     *
     * <ul>
     * <li><strong>{@link IPortType#SUPPORTED_TYPES} must only ever grow at the
     * end.</strong> The wrench keeps the player's selected kind as an index into
     * that array, in the item's own NBT, so reordering it points every wrench at a
     * different kind. Adding a constant here does not touch that array - it is
     * written out by hand precisely so the two can move independently.</li>
     * <li><strong>Renaming a constant is the breaking change now</strong>, not
     * inserting one. Names are what saves hold, so a rename orphans them.</li>
     * </ul>
     *
     * <p>
     * {@link #isStorable()} answers true for any new constant, which is what makes
     * a missing resource wiring fail loudly rather than return zero. It also means
     * a new constant immediately generates recipe-expression names from its own
     * name, so a kind whose name collides with an existing variable - {@code
     * redstone} being the obvious one - needs that thought through first.
     */
    enum Type {

        ITEM,
        FLUID,
        ENERGY,
        MANA,
        GAS,
        ESSENTIA,
        VIS,
        BLOCK,
        NONE;

        /**
         * Whether this type names a resource the machine holds an amount of.
         *
         * BLOCK addresses world blocks and NONE is the absence of a type, so
         * neither has a stored amount or a capacity. Everything else does.
         */
        public boolean isStorable() {
            return this != BLOCK && this != NONE;
        }

        /**
         * Whether input and output are separate storage for this kind, so that
         * asking about a direction gives a different answer than asking about the
         * machine as a whole.
         *
         * Fluids, gases and items are held in per-direction tanks and slots.
         * Energy, mana, essentia and vis are single pools, so a direction has
         * nothing to select and the total is the only answer.
         */
        public boolean hasDirectionalStorage() {
            return this == FLUID || this == GAS || this == ITEM;
        }
    }

    enum Direction {
        INPUT,
        OUTPUT,
        BOTH,
        NONE
    }

    /**
     * The kinds a wrench can assign to an external block, in the order it cycles
     * through them.
     *
     * <p>
     * <strong>Append only.</strong> The wrench stores the player's selection as an
     * index into this array in the item's NBT, so reordering it changes what every
     * existing wrench is set to. Written out by hand rather than derived from
     * {@code Type.values()} so that adding a constant to the enum cannot move these
     * indices by accident.
     */
    Type[] SUPPORTED_TYPES = { Type.ITEM, Type.FLUID, Type.ENERGY, Type.MANA, Type.GAS, Type.ESSENTIA, Type.VIS };

    Type getPortType();

    Direction getPortDirection();
}
