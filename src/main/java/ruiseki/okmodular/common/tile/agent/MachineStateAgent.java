package ruiseki.okmodular.common.tile.agent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.ToLongFunction;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;
import net.minecraftforge.oredict.OreDictionary;

import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.core.IMachineState;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.common.recipe.ProcessAgent;
import ruiseki.okmodular.common.tile.TEMachineController;
import ruiseki.okmodular.common.tile.essentia.AbstractEssentiaPortTE;
import ruiseki.okmodular.common.tile.vis.AbstractVisPortTE;
import ruiseki.okmodular.core.energy.IOKEnergySource;
import ruiseki.okmodular.core.gas.GasTankInfo;
import ruiseki.okmodular.core.gas.IGasHandler;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import vazkii.botania.api.mana.IManaPool;
import vazkii.botania.api.mana.spark.ISparkAttachable;

/**
 * Agent responsible for tracking and reporting the machine's state and
 * statistics.
 * Delegates dynamic state (energy, progress) to the controller, but owns
 * statistical data.
 */
public class MachineStateAgent implements IMachineState {

    private final TEMachineController controller;
    private final ProcessAgent processAgent;

    private int recipeProcessedCount = 0;
    private final Set<String> processedRecipeTypes = new HashSet<>();

    public MachineStateAgent(TEMachineController controller, ProcessAgent processAgent) {
        this.controller = controller;
        this.processAgent = processAgent;
    }

    // --- IMachineState Implementation ---

    /**
     * The energy the machine can draw on, summed over its energy input ports.
     * <p>
     * These two used to read the controller's own buffer, which nothing ever fills - a
     * multiblock keeps its energy in the ports, and {@code EnergyInput} draws from them. So
     * the {@code energy} expression answered zero on a machine with a full energy hatch, and
     * {@code "energy &gt; 1000"} could never hold. Every other storable kind already summed
     * its ports; energy was the one that did not.
     */
    @Override
    public long getStoredEnergy() {
        return sumPortProperty(IPortType.Type.ENERGY, IOKEnergySource.class, IOKEnergySource::getEnergyStored);
    }

    @Override
    public long getEnergyCapacity() {
        return sumPortProperty(IPortType.Type.ENERGY, IOKEnergySource.class, IOKEnergySource::getMaxEnergyStored);
    }

    @Override
    public int getEnergyPerTick() {
        return processAgent.getEnergyPerTick();
    }

    @Override
    public double getProgressPercent() {
        return processAgent.getProgressPercent();
    }

    @Override
    public long getProgress() {
        return processAgent.getProgress();
    }

    @Override
    public boolean isRunning() {
        return processAgent.isRunning();
    }

    @Override
    public boolean isWaitingForOutput() {
        return processAgent.isWaitingForOutput();
    }

    @Override
    public int getTier() {
        return controller.getTier();
    }

    @Override
    public long getTimePlaced() {
        return controller.getTimePlaced();
    }

    @Override
    public long getTimeContinuous() {
        return controller.getTimeContinuous();
    }

    @Override
    public int getRecipeProcessedCount() {
        return recipeProcessedCount;
    }

    @Override
    public int getRecipeProcessedTypesCount() {
        return processedRecipeTypes.size();
    }

    // --- Statistics Recording ---

    /**
     * Records the completion of a recipe to update statistics.
     */
    public void recordRecipeCompletion(IModularRecipe recipe) {
        if (recipe == null) return;
        recipeProcessedCount++;
        if (recipe.getRecipeGroup() != null) {
            processedRecipeTypes.add(recipe.getRecipeGroup());
        }
    }

    // --- NBT Persistence ---

    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("recipeProcessedCount", recipeProcessedCount);
        if (!processedRecipeTypes.isEmpty()) {
            NBTTagList typeList = new NBTTagList();
            for (String type : processedRecipeTypes) {
                typeList.appendTag(new NBTTagString(type));
            }
            nbt.setTag("processedRecipeTypes", typeList);
        }
    }

    public void readFromNBT(NBTTagCompound nbt) {
        recipeProcessedCount = nbt.getInteger("recipeProcessedCount");
        processedRecipeTypes.clear();
        if (nbt.hasKey("processedRecipeTypes", 9)) {
            NBTTagList typeList = nbt.getTagList("processedRecipeTypes", 8);
            for (int i = 0; i < typeList.tagCount(); i++) {
                processedRecipeTypes.add(typeList.getStringTagAt(i));
            }
        }
    }

    // --- Generic Helper Methods ---

    /**
     * Generic helper to sum a property from all ports of a specific type.
     *
     * @param portType  The port type to query
     * @param portClass The expected port interface class
     * @param extractor Function to extract the value from each port
     * @param <T>       The port type
     * @return The sum of all extracted values
     */
    private <T> long sumPortProperty(IPortType.Type portType, Class<T> portClass, ToLongFunction<T> extractor) {
        return controller.getPortManager()
            .getInputPorts(portType)
            .stream()
            .filter(portClass::isInstance)
            .map(portClass::cast)
            .mapToLong(extractor)
            .sum();
    }

    /**
     * Generic helper for named resources (gas, fluid, essentia, vis).
     *
     * @param portType  The port type to query
     * @param portClass The expected port interface class
     * @param name      The resource name
     * @param extractor Function to extract amount for the specific name
     * @param <T>       The port type
     * @return The sum of amounts for the named resource
     */
    private <T> long sumNamedResource(IPortType.Type portType, Class<T> portClass, String name,
        BiFunction<T, String, Long> extractor) {
        if (name == null || name.isEmpty()) return 0;
        return controller.getPortManager()
            .getInputPorts(portType)
            .stream()
            .filter(portClass::isInstance)
            .map(portClass::cast)
            .mapToLong(port -> extractor.apply(port, name))
            .sum();
    }

    /**
     * The ports of a kind on one side. {@link #sumPortProperty} and
     * {@link #sumNamedResource} only ever look at the input side, which is why the
     * output-side figures below cannot use them.
     */
    private List<IModularPort> ports(IPortType.Type type, boolean output) {
        return output ? controller.getPortManager()
            .getOutputPorts(type)
            : controller.getPortManager()
                .getInputPorts(type);
    }

    /**
     * Fluid held on one side, optionally of one kind.
     * <p>
     * Every fluid figure funnels through this and {@link #fluidSpace}, so the direction and
     * the name are arguments rather than four near-identical loops. They were four loops, and
     * the ones for the output side and for input space had simply never been written -
     * {@link IMachineState} carried {@code default}s returning 0 in their place, which meant
     * a recipe asking for output-tank contents got a confident zero.
     *
     * @param name a fluid name, or null for every fluid
     */
    private long fluidAmount(boolean output, String name) {
        long total = 0;
        for (IModularPort port : ports(IPortType.Type.FLUID, output)) {
            if (!(port instanceof IFluidHandler fluidPort)) continue;
            FluidTankInfo[] tanks = fluidPort.getTankInfo(ForgeDirection.UNKNOWN);
            if (tanks == null) continue;
            for (FluidTankInfo tank : tanks) {
                if (tank.fluid == null) continue;
                if (name != null && !tank.fluid.getFluid()
                    .getName()
                    .equals(name)) continue;
                total += tank.fluid.amount;
            }
        }
        return total;
    }

    /**
     * Room left on one side, optionally for one fluid.
     * <p>
     * Asked about a named fluid, an empty tank counts: it can take that fluid. A tank holding
     * something else does not, however much room it has.
     *
     * @param name a fluid name, or null for room regardless of what would go in it
     */
    private long fluidSpace(boolean output, String name) {
        long total = 0;
        for (IModularPort port : ports(IPortType.Type.FLUID, output)) {
            if (!(port instanceof IFluidHandler fluidPort)) continue;
            FluidTankInfo[] tanks = fluidPort.getTankInfo(ForgeDirection.UNKNOWN);
            if (tanks == null) continue;
            for (FluidTankInfo tank : tanks) {
                if (name != null && tank.fluid != null
                    && !tank.fluid.getFluid()
                        .getName()
                        .equals(name))
                    continue;
                total += Math.max(0, tank.capacity - (tank.fluid != null ? tank.fluid.amount : 0));
            }
        }
        return total;
    }

    /** Gas held on one side, optionally of one kind. Mirrors {@link #fluidAmount}. */
    private long gasAmount(boolean output, String name) {
        long total = 0;
        for (IModularPort port : ports(IPortType.Type.GAS, output)) {
            if (!(port instanceof IGasHandler gasPort)) continue;
            GasTankInfo[] tanks = gasPort.getTankInfo(ForgeDirection.UNKNOWN);
            if (tanks == null) continue;
            for (GasTankInfo tank : tanks) {
                if (tank.gas == null) continue;
                if (name != null && !tank.gas.getGas()
                    .getName()
                    .equals(name)) continue;
                total += tank.gas.amount;
            }
        }
        return total;
    }

    /** Room left for gas on one side, optionally for one kind. Mirrors {@link #fluidSpace}. */
    private long gasSpace(boolean output, String name) {
        long total = 0;
        for (IModularPort port : ports(IPortType.Type.GAS, output)) {
            if (!(port instanceof IGasHandler gasPort)) continue;
            GasTankInfo[] tanks = gasPort.getTankInfo(ForgeDirection.UNKNOWN);
            if (tanks == null) continue;
            for (GasTankInfo tank : tanks) {
                if (name != null && tank.gas != null
                    && !tank.gas.getGas()
                        .getName()
                        .equals(name))
                    continue;
                total += Math.max(0, tank.capacity - (tank.gas != null ? tank.gas.amount : 0));
            }
        }
        return total;
    }

    /** Everything an aspect container on one kind of port holds, across all aspects. */
    private long totalAspects(IPortType.Type type) {
        return sumPortProperty(type, IAspectContainer.class, container -> {
            AspectList held = container.getAspects();
            if (held == null) return 0L;
            long sum = 0;
            for (Aspect aspect : held.getAspects()) {
                if (aspect != null) sum += held.getAmount(aspect);
            }
            return sum;
        });
    }

    @Override
    public long getStoredFluid() {
        return fluidAmount(false, null);
    }

    @Override
    public long getFluidCapacity() {
        long total = 0;
        for (IModularPort port : controller.getPortManager()
            .getInputPorts(IPortType.Type.FLUID)) {
            if (!(port instanceof IFluidHandler fluidPort)) continue;
            FluidTankInfo[] tanks = fluidPort.getTankInfo(ForgeDirection.UNKNOWN);
            if (tanks == null) continue;
            for (FluidTankInfo tank : tanks) {
                total += tank.capacity;
            }
        }
        return total;
    }

    @Override
    public long getStoredFluid(String name) {
        return named(name) ? fluidAmount(false, name) : 0;
    }

    @Override
    public long getFluidInput(String name) {
        return getStoredFluid(name);
    }

    @Override
    public long getTotalFluidInput() {
        return fluidAmount(false, null);
    }

    @Override
    public long getFluidOutput(String name) {
        return named(name) ? fluidAmount(true, name) : 0;
    }

    @Override
    public long getTotalFluidOutput() {
        return fluidAmount(true, null);
    }

    @Override
    public long getFluidInputSpace(String name) {
        return named(name) ? fluidSpace(false, name) : 0;
    }

    @Override
    public long getTotalFluidInputSpace() {
        return fluidSpace(false, null);
    }

    @Override
    public long getFluidOutputSpace(String name) {
        return named(name) ? fluidSpace(true, name) : 0;
    }

    @Override
    public long getTotalFluidOutputSpace() {
        return fluidSpace(true, null);
    }

    /** Whether the caller named a resource at all. Empty means "the kind as a whole". */
    private static boolean named(String name) {
        return name != null && !name.isEmpty();
    }

    @Override
    public long getStoredMana() {
        return sumPortProperty(IPortType.Type.MANA, IManaPool.class, p -> (long) p.getCurrentMana());
    }

    @Override
    public long getManaCapacity() {
        return controller.getPortManager()
            .getInputPorts(IPortType.Type.MANA)
            .stream()
            .filter(p -> p instanceof IManaPool)
            .mapToLong(p -> {
                long current = (long) ((IManaPool) p).getCurrentMana();
                if (p instanceof ISparkAttachable) {
                    return current + (long) ((ISparkAttachable) p).getAvailableSpaceForMana();
                }
                return current;
            })
            .sum();
    }

    /**
     * These two used to read the tanks through {@code drawGas(UNKNOWN, MAX_VALUE, false)}
     * while {@link #getGasCapacity()} read {@code getTankInfo}, so
     * {@code getSpace(GAS, BOTH, null)} subtracted a figure obtained one way from a figure
     * obtained another. The ports return a single tank either way, so nothing moves today -
     * but a port with two tanks would have made the two disagree, and only one of them would
     * have been right. One way of asking now.
     */
    @Override
    public long getStoredGas(String name) {
        return named(name) ? gasAmount(false, name) : 0;
    }

    @Override
    public long getTotalStoredGas() {
        return gasAmount(false, null);
    }

    @Override
    public long getGasInput(String name) {
        return named(name) ? gasAmount(false, name) : 0;
    }

    @Override
    public long getTotalGasInput() {
        return gasAmount(false, null);
    }

    @Override
    public long getGasOutput(String name) {
        return named(name) ? gasAmount(true, name) : 0;
    }

    @Override
    public long getTotalGasOutput() {
        return gasAmount(true, null);
    }

    @Override
    public long getGasInputSpace(String name) {
        return named(name) ? gasSpace(false, name) : 0;
    }

    @Override
    public long getTotalGasInputSpace() {
        return gasSpace(false, null);
    }

    @Override
    public long getGasOutputSpace(String name) {
        return named(name) ? gasSpace(true, name) : 0;
    }

    @Override
    public long getTotalGasOutputSpace() {
        return gasSpace(true, null);
    }

    @Override
    public long getGasCapacity() {
        long total = 0;
        for (IModularPort port : controller.getPortManager()
            .getInputPorts(IPortType.Type.GAS)) {
            if (!(port instanceof IGasHandler gasPort)) continue;
            GasTankInfo[] tankInfo = gasPort.getTankInfo(ForgeDirection.UNKNOWN);
            if (tankInfo != null) {
                for (GasTankInfo info : tankInfo) {
                    total += info.capacity;
                }
            }
        }
        return total;
    }

    @Override
    public long getStoredEssentia(String aspectName) {
        Aspect aspect = Aspect.getAspect(aspectName);
        if (aspect == null) return 0;
        return sumPortProperty(
            IPortType.Type.ESSENTIA,
            IAspectContainer.class,
            p -> (long) p.containerContains(aspect));
    }

    @Override
    public long getTotalStoredEssentia() {
        return totalAspects(IPortType.Type.ESSENTIA);
    }

    @Override
    public long getEssentiaCapacity() {
        return sumPortProperty(
            IPortType.Type.ESSENTIA,
            AbstractEssentiaPortTE.class,
            p -> (long) p.getMaxCapacityPerAspect());
    }

    @Override
    public long getStoredVis(String aspectName) {
        Aspect aspect = Aspect.getAspect(aspectName);
        if (aspect == null) return 0;
        return sumPortProperty(IPortType.Type.VIS, IAspectContainer.class, p -> (long) p.containerContains(aspect));
    }

    @Override
    public long getTotalStoredVis() {
        return totalAspects(IPortType.Type.VIS);
    }

    @Override
    public long getVisCapacity() {
        return sumPortProperty(IPortType.Type.VIS, AbstractVisPortTE.class, p -> (long) p.getMaxVisPerAspect());
    }

    @Override
    public int getBatchSize() {
        return processAgent.getBatchSize();
    }

    @Override
    public double getSpeedMultiplier() {
        return controller.getSpeedMultiplier();
    }

    @Override
    public double getEnergyMultiplier() {
        return controller.getEnergyMultiplier();
    }

    @Override
    public long getRecipeStartTick() {
        return controller.getRecipeStartTick();
    }

    @Override
    public long getItemCount(IPortType.Direction direction, String itemName) {
        long total = 0;
        for (IModularPort port : getPorts(direction)) {
            if (!(port instanceof IInventory inv)) continue;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack stack = inv.getStackInSlot(i);
                if (stack != null && matches(stack, itemName)) {
                    total += stack.stackSize;
                }
            }
        }
        return total;
    }

    @Override
    public long getItemSpace(IPortType.Direction direction, String itemName) {
        long total = 0;
        ItemStack filterStack = null;
        if (itemName != null && !itemName.isEmpty() && !itemName.startsWith("ore:")) {
            Item item = (Item) Item.itemRegistry.getObject(itemName);
            if (item != null) filterStack = new ItemStack(item);
        }

        for (IModularPort port : getPorts(direction)) {
            if (!(port instanceof IInventory inv)) continue;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack stack = inv.getStackInSlot(i);
                if (stack == null) {
                    // Empty slot: if specific item, use its max stack size, else use 64
                    total += (filterStack != null) ? filterStack.getMaxStackSize() : 64;
                } else if (filterStack != null && stack.isItemEqual(filterStack)
                    && ItemStack.areItemStackTagsEqual(stack, filterStack)) {
                        // Same item: add remaining space in stack
                        total += Math.max(0, stack.getMaxStackSize() - stack.stackSize);
                    }
            }
        }
        return total;
    }

    @Override
    public int getItemSlotCount(IPortType.Direction direction, boolean emptyOnly) {
        int total = 0;
        for (IModularPort port : getPorts(direction)) {
            if (!(port instanceof IInventory inv)) continue;
            if (emptyOnly) {
                for (int i = 0; i < inv.getSizeInventory(); i++) {
                    if (inv.getStackInSlot(i) == null) total++;
                }
            } else {
                total += inv.getSizeInventory();
            }
        }
        return total;
    }

    private List<IModularPort> getPorts(IPortType.Direction direction) {
        if (direction == IPortType.Direction.INPUT) {
            return controller.getPortManager()
                .getInputPorts(IPortType.Type.ITEM);
        } else if (direction == IPortType.Direction.OUTPUT) {
            return controller.getPortManager()
                .getOutputPorts(IPortType.Type.ITEM);
        } else {
            List<IModularPort> all = new ArrayList<>();
            all.addAll(
                controller.getPortManager()
                    .getInputPorts(IPortType.Type.ITEM));
            all.addAll(
                controller.getPortManager()
                    .getOutputPorts(IPortType.Type.ITEM));
            return all;
        }
    }

    private boolean matches(ItemStack stack, String filter) {
        if (filter == null || filter.isEmpty()) return true;

        if (filter.startsWith("ore:")) {
            String oreName = filter.substring(4);
            int oreId = OreDictionary.getOreID(oreName);
            if (oreId < 0) return false;
            int[] ids = OreDictionary.getOreIDs(stack);
            for (int id : ids) if (id == oreId) return true;
            return false;
        } else {
            String name = Item.itemRegistry.getNameForObject(stack.getItem());
            return filter.equals(name);
        }
    }
}
