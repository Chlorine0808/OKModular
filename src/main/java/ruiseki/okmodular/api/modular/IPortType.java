package ruiseki.okmodular.api.modular;

public interface IPortType {

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
         *
         * <p>
         * Note on adding types: the ordinal of these constants is written to world
         * NBT (see TEMachineController's external port configs), so a new type has
         * to be <em>appended</em>. Inserting one shifts the ordinals of everything
         * after it and existing saves decode to the wrong type.
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

    Type[] SUPPORTED_TYPES = { Type.ITEM, Type.FLUID, Type.ENERGY, Type.MANA, Type.GAS, Type.ESSENTIA, Type.VIS };

    Type getPortType();

    Direction getPortDirection();
}
