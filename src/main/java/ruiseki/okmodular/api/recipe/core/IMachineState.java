package ruiseki.okmodular.api.recipe.core;

import ruiseki.okmodular.api.modular.IPortType;

/**
 * Interface for accessing machine state in expressions.
 * Allows the API layer to access energy, progress, etc., without depending on
 * the implementation.
 */
public interface IMachineState {

    /**
     * Number of items a full slot holds. Used to express an item capacity as an
     * amount rather than a slot count, so that items answer getCapacity in the
     * same unit as every other resource.
     */
    int ITEMS_PER_SLOT = 64;

    /**
     * Get the capacity for a resource kind.
     *
     * This is the kind-parameterized form of the per-kind capacity getters below.
     * New resource kinds should only need a branch here rather than a new pair of
     * interface methods.
     *
     * @param kind the resource kind. Kinds that hold no amount ({@code BLOCK},
     *             {@code NONE}) answer 0.
     */
    default long getCapacity(IPortType.Type kind) {
        switch (kind) {
            case ENERGY:
                return getEnergyCapacity();
            case MANA:
                return getManaCapacity();
            case FLUID:
                return getFluidCapacity();
            case GAS:
                return getGasCapacity();
            case ESSENTIA:
                return getEssentiaCapacity();
            case VIS:
                return getVisCapacity();
            case ITEM:
                return (long) getItemSlotCount(IPortType.Direction.BOTH, false) * ITEMS_PER_SLOT;
            default:
                return 0L;
        }
    }

    /**
     * Get the amount held for a resource kind.
     *
     * This is the kind-parameterized form of the per-kind amount getters below.
     * {@code getItemCount} already has this shape; the other kinds spell the same
     * three questions out as separate methods.
     *
     * @param kind the resource kind. Kinds that hold no amount answer 0.
     * @param dir  {@code BOTH} asks about the machine as a whole. Kinds that keep
     *             no per-direction figures (energy, mana, essentia, vis) answer
     *             the total for any direction - having no directional split is not
     *             the same as holding nothing.
     * @param name a specific fluid / gas / aspect / item, or null or empty to ask
     *             about the kind as a whole.
     */
    default long getAmount(IPortType.Type kind, IPortType.Direction dir, String name) {
        boolean byName = name != null && !name.isEmpty();

        switch (kind) {
            case ENERGY:
                return getStoredEnergy();
            case MANA:
                return getStoredMana();
            case FLUID:
                switch (dir) {
                    case INPUT:
                        return byName ? getFluidInput(name) : getTotalFluidInput();
                    case OUTPUT:
                        return byName ? getFluidOutput(name) : getTotalFluidOutput();
                    default:
                        return byName ? getStoredFluid(name) : getStoredFluid();
                }
            case GAS:
                switch (dir) {
                    case INPUT:
                        return byName ? getGasInput(name) : getTotalGasInput();
                    case OUTPUT:
                        return byName ? getGasOutput(name) : getTotalGasOutput();
                    default:
                        return byName ? getStoredGas(name) : getTotalStoredGas();
                }
            case ESSENTIA:
                return byName ? getStoredEssentia(name) : getTotalStoredEssentia();
            case VIS:
                return byName ? getStoredVis(name) : getTotalStoredVis();
            case ITEM:
                return getItemCount(dir, byName ? name : null);
            default:
                return 0L;
        }
    }

    /**
     * Get the remaining room for a resource kind.
     *
     * Space is not one formula for every kind, and the difference is deliberate:
     *
     * <ul>
     * <li>Items ask the machine directly. Stack limits differ per item, so
     * "slots * 64 minus count" is not the number that actually fits.</li>
     * <li>Fluids and gases keep separate input and output tanks, so a direction
     * has its own figure and must not be derived from the shared capacity.</li>
     * <li>Everything else is capacity minus what is held.</li>
     * </ul>
     *
     * {@code BOTH} with a name is a combination the per-kind getters never
     * offered. Input and output are separate storage, so it is their sum.
     * {@code BOTH} without a name keeps the capacity-minus-held form that the
     * existing {@code *_f} properties compute, so their values do not move.
     *
     * @param kind the resource kind. Kinds that hold no amount answer 0.
     * @param dir  {@code BOTH} asks about the machine as a whole.
     * @param name a specific fluid / gas / aspect / item, or null or empty to ask
     *             about the kind as a whole.
     */
    default long getSpace(IPortType.Type kind, IPortType.Direction dir, String name) {
        boolean byName = name != null && !name.isEmpty();

        switch (kind) {
            case ITEM:
                return getItemSpace(dir, byName ? name : null);
            case FLUID:
                switch (dir) {
                    case INPUT:
                        return byName ? getFluidInputSpace(name) : getTotalFluidInputSpace();
                    case OUTPUT:
                        return byName ? getFluidOutputSpace(name) : getTotalFluidOutputSpace();
                    default:
                        return byName ? getFluidInputSpace(name) + getFluidOutputSpace(name)
                            : getFluidCapacity() - getStoredFluid();
                }
            case GAS:
                switch (dir) {
                    case INPUT:
                        return byName ? getGasInputSpace(name) : getTotalGasInputSpace();
                    case OUTPUT:
                        return byName ? getGasOutputSpace(name) : getTotalGasOutputSpace();
                    default:
                        return byName ? getGasInputSpace(name) + getGasOutputSpace(name)
                            : getGasCapacity() - getTotalStoredGas();
                }
            case ENERGY:
            case MANA:
            case ESSENTIA:
            case VIS:
                return getCapacity(kind) - getAmount(kind, dir, name);
            default:
                return 0L;
        }
    }

    /**
     * Get the current energy stored.
     */
    long getStoredEnergy();

    /**
     * Get the maximum energy capacity.
     */
    long getEnergyCapacity();

    /**
     * Get the energy consumption or production per tick.
     */
    int getEnergyPerTick();

    /**
     * Get the current crafting progress (0.0 to 1.0).
     */
    double getProgressPercent();

    /**
     * Get the raw progress value (usually ticks or work amount).
     */
    default long getProgress() {
        return 0;
    }

    /**
     * Whether the machine is currently running a recipe.
     */
    boolean isRunning();

    /**
     * Whether the machine is waiting for output space.
     */
    boolean isWaitingForOutput();

    /**
     * Get the machine tier.
     */
    int getTier();

    /**
     * Get the total time this machine has been placed in the world (in ticks).
     */
    long getTimePlaced();

    /**
     * Get the total duration the machine has been actively processing recipes (in
     * ticks).
     */
    long getTimeContinuous();

    /**
     * Get the total number of recipes processed by this machine.
     */
    int getRecipeProcessedCount();

    /**
     * Get the number of unique types of recipes processed by this machine.
     */
    int getRecipeProcessedTypesCount();

    /**
     * Get the total fluid stored in the machine (any type).
     */
    long getStoredFluid();

    /**
     * Get the total fluid capacity of the machine.
     */
    long getFluidCapacity();

    /**
     * Get the amount of a specific fluid stored in the machine (both tanks).
     *
     * @param name Fluid name
     */
    long getStoredFluid(String name);

    /**
     * Get the total fluid stored in input tanks (all fluids).
     */
    default long getTotalFluidInput() {
        return 0;
    }

    /**
     * Get the total fluid stored in output tanks (all fluids).
     */
    default long getTotalFluidOutput() {
        return 0;
    }

    /**
     * Get the amount of a specific fluid in input tanks.
     */
    long getFluidInput(String name);

    /**
     * Get the amount of a specific fluid in output tanks.
     */
    default long getFluidOutput(String name) {
        return 0;
    }

    /**
     * Get the remaining space for a specific fluid in input tanks.
     */
    default long getFluidInputSpace(String name) {
        return 0;
    }

    /**
     * Get the remaining space for a specific fluid in output tanks.
     */
    long getFluidOutputSpace(String name);

    /**
     * Get the total remaining space in input tanks (all fluids).
     */
    default long getTotalFluidInputSpace() {
        return 0;
    }

    /**
     * Get the total remaining space in output tanks (all fluids).
     */
    default long getTotalFluidOutputSpace() {
        return 0;
    }

    /**
     * Get the current total mana stored in the machine.
     */
    long getStoredMana();

    /**
     * Get the total mana capacity of the machine.
     */
    long getManaCapacity();

    /**
     * Get the amount of a specific gas stored in the machine (both tanks).
     *
     * @param name Gas name
     */
    long getStoredGas(String name);

    /**
     * Get the total gas stored in the machine (any type, both tanks).
     */
    long getTotalStoredGas();

    /**
     * Get the total gas capacity of the machine.
     */
    long getGasCapacity();

    /**
     * Get the total gas stored in input tanks (all gas types).
     */
    default long getTotalGasInput() {
        return 0;
    }

    /**
     * Get the total gas stored in output tanks (all gas types).
     */
    default long getTotalGasOutput() {
        return 0;
    }

    /**
     * Get the amount of a specific gas in input tanks.
     */
    default long getGasInput(String name) {
        return 0;
    }

    /**
     * Get the amount of a specific gas in output tanks.
     */
    default long getGasOutput(String name) {
        return 0;
    }

    /**
     * Get the remaining space for a specific gas in input tanks.
     */
    default long getGasInputSpace(String name) {
        return 0;
    }

    /**
     * Get the remaining space for a specific gas in output tanks.
     */
    default long getGasOutputSpace(String name) {
        return 0;
    }

    /**
     * Get the total remaining space in gas input tanks.
     */
    default long getTotalGasInputSpace() {
        return 0;
    }

    /**
     * Get the total remaining space in gas output tanks.
     */
    default long getTotalGasOutputSpace() {
        return 0;
    }

    /**
     * Get the amount of a specific essentia aspect stored in the machine.
     *
     * @param aspect Aspect name
     */
    long getStoredEssentia(String aspect);

    /**
     * Get the total essentia stored in the machine (all aspects combined).
     */
    default long getTotalStoredEssentia() {
        return 0;
    }

    /**
     * Get the total essentia capacity of the machine.
     */
    long getEssentiaCapacity();

    /**
     * Get the amount of a specific vis aspect stored in the machine.
     *
     * @param aspect Aspect name
     */
    long getStoredVis(String aspect);

    /**
     * Get the total vis stored in the machine (all aspects combined).
     */
    default long getTotalStoredVis() {
        return 0;
    }

    /**
     * Get the total vis capacity of the machine.
     */
    long getVisCapacity();

    /**
     * Get the current batch size.
     */
    int getBatchSize();

    /**
     * Get the current speed multiplier.
     */
    double getSpeedMultiplier();

    /**
     * Get the current energy multiplier.
     */
    double getEnergyMultiplier();

    /**
     * Get the world tick when the current recipe started.
     */
    default long getRecipeStartTick() {
        return 0;
    }

    /**
     * Get the total count of items on a specific side.
     * 
     * @param direction Port direction (INPUT, OUTPUT, BOTH)
     * @param itemName  Optional item name/ID or OreDict name to filter by.
     *                  If null or empty, returns total count of all items.
     */
    default long getItemCount(IPortType.Direction direction, String itemName) {
        return 0;
    }

    /**
     * Get the remaining space for items on a specific side.
     * 
     * @param direction Port direction (INPUT, OUTPUT, BOTH)
     * @param itemName  Optional item name/ID or OreDict name.
     *                  If specific, counts how many of that item can be added.
     *                  If null, counts total empty slot capacity.
     */
    default long getItemSpace(IPortType.Direction direction, String itemName) {
        return 0;
    }

    /**
     * Get the number of item slots on a specific side.
     * 
     * @param direction Port direction (INPUT, OUTPUT, BOTH)
     * @param emptyOnly If true, only counts empty slots.
     */
    default int getItemSlotCount(IPortType.Direction direction, boolean emptyOnly) {
        return 0;
    }
}
