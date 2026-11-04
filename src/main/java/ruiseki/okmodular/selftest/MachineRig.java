package ruiseki.okmodular.selftest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;

import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.structure.core.IStructureEntry;
import ruiseki.okmodular.common.init.MachineryBlocks;
import ruiseki.okmodular.common.init.MachineryItems;
import ruiseki.okmodular.common.integration.structurelib.MachineControllerInfoContainer;
import ruiseki.okmodular.common.item.ItemMachineBlueprint;
import ruiseki.okmodular.common.tile.TEMachineController;
import ruiseki.okmodular.common.tile.energy.AbstractEnergyIOPortTE;
import ruiseki.okmodular.common.tile.fluid.AbstractFluidPortTE;
import ruiseki.okmodular.common.tile.item.AbstractItemIOPortTE;
import ruiseki.okmodular.selftest.scenario.InputSpec;
import ruiseki.okmodular.selftest.scenario.MachineSpec;
import ruiseki.okmodular.structure.CustomStructureRegistry;
import ruiseki.okmodular.structure.StructureCellLocator;
import ruiseki.okmodular.structure.StructureManager;

/** Builds a scenario's machine at its site with the controller on the origin, and fills or empties its ports. */
final class MachineRig {

    /** A survival build places what it can per call; a few calls finish any selftest structure. */
    private static final int SURVIVAL_ROUNDS = 16;

    final TestSites.Site site;
    final MachineSpec spec;
    private TEMachineController controller;
    /** The last condition failure seen while sampling; the machine forgets it once a recipe runs. */
    String lastConditionFailure;
    /** Port positions seen while formed, so ports can still be read after a recipe breaks the structure. */
    private final Map<String, List<int[]>> portCells = new HashMap<>();

    private MachineRig(TestSites.Site site, MachineSpec spec, TEMachineController controller) {
        this.site = site;
        this.spec = spec;
        this.controller = controller;
    }

    static MachineRig build(TestSites.Site site, MachineSpec spec) {
        IStructureEntry entry = StructureManager.getInstance()
            .getCustomStructure(spec.structure);
        if (entry == null) throw new IllegalStateException("unknown structure \"" + spec.structure + "\"");

        site.world.setBlock(site.x, site.y, site.z, MachineryBlocks.MACHINE_CONTROLLER.getBlock(), 0, 3);
        TEMachineController te = controllerAt(site);
        ExtendedFacing facing = ExtendedFacing
            .of(ForgeDirection.valueOf(spec.facing), Rotation.valueOf(spec.rotation), Flip.valueOf(spec.flip));
        te.setExtendedFacing(facing);
        ItemStack blueprint = ItemMachineBlueprint
            .createBlueprint(MachineryItems.MACHINE_BLUEPRINT.getItem(), spec.structure);
        te.getInventory()
            .setStackInSlot(TEMachineController.BLUEPRINT_SLOT, blueprint);
        ItemStack trigger = blueprint.copy();
        trigger.stackSize = spec.blueprintCount;
        if (spec.survival) {
            placeCells(site, spec.structure, entry, facing, spec.standing);
            survivalBuild(site, te, trigger, facing, spec.carry);
        } else {
            new MachineControllerInfoContainer().construct(trigger, false, te, facing);
        }

        // The build places the first option of each symbol; cells overrides choose another.
        placeCells(site, spec.structure, entry, facing, spec.cells);
        te.getStructureAgent()
            .forceStructureCheck();
        MachineRig rig = new MachineRig(site, spec, te);
        rig.rememberPorts();
        return rig;
    }

    private static void placeCells(TestSites.Site site, String structure, IStructureEntry entry, ExtendedFacing facing,
        Map<String, String> cells) {
        String[][] shape = authoredShape(entry);
        int[] offset = CustomStructureRegistry.getControllerOffset(structure);
        for (Map.Entry<String, String> cell : cells.entrySet()) {
            BlockId id = BlockId.parse(cell.getValue());
            Block block = block(id.name);
            for (int[] abc : ShapeCells.resolve(shape, entry.getDefaultFacing(), cell.getKey())) {
                int[] p = StructureCellLocator.toWorld(facing, site.x, site.y, site.z, offset, abc[0], abc[1], abc[2]);
                site.world.setBlock(p[0], p[1], p[2], block, id.meta, 3);
            }
        }
    }

    /** Builds as a survival player does: a fake player places blocks taken from a bag of the carried items. */
    private static void survivalBuild(TestSites.Site site, TEMachineController te, ItemStack trigger,
        ExtendedFacing facing, List<InputSpec> carry) {
        List<ItemStack> stacks = new ArrayList<>();
        for (InputSpec in : carry) {
            ItemStack proto = stack(in.id, 1);
            for (int left = in.amount; left > 0; left -= proto.getMaxStackSize()) {
                ItemStack s = proto.copy();
                s.stackSize = Math.min(left, proto.getMaxStackSize());
                stacks.add(s);
            }
        }
        InventoryBasic bag = new InventoryBasic("selftest carry", false, Math.max(1, stacks.size()));
        for (int i = 0; i < stacks.size(); i++) bag.setInventorySlotContents(i, stacks.get(i));
        EntityPlayerMP actor = FakePlayerFactory.getMinecraft((WorldServer) site.world);
        ISurvivalBuildEnvironment env = ISurvivalBuildEnvironment.create(IItemSource.fromInventory(bag), actor);
        MachineControllerInfoContainer info = new MachineControllerInfoContainer();
        for (int round = 0; round < SURVIVAL_ROUNDS; round++) {
            if (info.survivalConstruct(trigger, Integer.MAX_VALUE, env, te, facing) <= 0) break;
        }
    }

    private void rememberPorts() {
        for (boolean input : new boolean[] { true, false }) {
            for (IPortType.Type type : IPortType.Type.values()) {
                List<int[]> cells = new ArrayList<>();
                for (IModularPort p : input ? controller.getInputPorts(type) : controller.getOutputPorts(type)) {
                    if (p instanceof TileEntity t) cells.add(new int[] { t.xCoord, t.yCoord, t.zCoord });
                }
                portCells.put(input + ":" + type, cells);
            }
        }
    }

    TEMachineController controller() {
        return controller;
    }

    /** Picks up the controller TileEntity again; a chunk reload replaces it with a new object. */
    void reattach() {
        controller = controllerAt(site);
    }

    void insert(List<InputSpec> inputs) {
        for (InputSpec in : inputs) {
            switch (in.kind) {
                case ITEM:
                    insertItem(in.id, in.amount);
                    break;
                case FLUID:
                    insertFluid(in.id, in.amount);
                    break;
                case ENERGY:
                    insertEnergy(in.amount);
                    break;
                default:
                    throw new IllegalArgumentException("unknown input kind " + in.kind);
            }
        }
    }

    /** Fills every slot of every item output port with full stacks of the item. */
    void fillOutputs(String item) {
        ItemStack proto = stack(item, 1);
        for (AbstractItemIOPortTE port : ports(false, IPortType.Type.ITEM, AbstractItemIOPortTE.class)) {
            int limit = Math.min(proto.getMaxStackSize(), port.getInventoryStackLimit());
            for (int slot = 0; slot < port.getSizeInventory(); slot++) {
                ItemStack s = proto.copy();
                s.stackSize = limit;
                port.setInventorySlotContents(slot, s);
            }
        }
    }

    /** Puts one stack into the first empty slot of the item output ports. */
    void putOutput(String item, int amount) {
        for (AbstractItemIOPortTE port : ports(false, IPortType.Type.ITEM, AbstractItemIOPortTE.class)) {
            for (int slot = 0; slot < port.getSizeInventory(); slot++) {
                if (port.getStackInSlot(slot) != null) continue;
                port.setInventorySlotContents(slot, stack(item, amount));
                return;
            }
        }
        throw new IllegalStateException(item + " did not fit the item outputs");
    }

    void clearOutputs() {
        for (AbstractItemIOPortTE port : ports(false, IPortType.Type.ITEM, AbstractItemIOPortTE.class)) {
            for (int slot = 0; slot < port.getSizeInventory(); slot++) port.setInventorySlotContents(slot, null);
        }
        for (AbstractFluidPortTE port : ports(false, IPortType.Type.FLUID, AbstractFluidPortTE.class)) {
            port.internalDrain(Integer.MAX_VALUE, true);
        }
    }

    /**
     * The machine's ports of one direction and type. A formed machine lists them itself; once
     * unformed (a block output can overwrite its own structure) the ports seen at build time are
     * read from the world, since their contents stay in the port blocks.
     */
    <T> List<T> ports(boolean input, IPortType.Type type, Class<T> cls) {
        List<T> out = new ArrayList<>();
        List<IModularPort> listed = input ? controller.getInputPorts(type) : controller.getOutputPorts(type);
        if (!listed.isEmpty()) {
            for (IModularPort p : listed) if (cls.isInstance(p)) out.add(cls.cast(p));
            return out;
        }
        for (int[] c : portCells.getOrDefault(input + ":" + type, new ArrayList<>())) {
            TileEntity t = site.world.getTileEntity(c[0], c[1], c[2]);
            if (cls.isInstance(t)) out.add(cls.cast(t));
        }
        return out;
    }

    private void insertItem(String item, int amount) {
        ItemStack proto = stack(item, 1);
        int remaining = amount;
        for (AbstractItemIOPortTE port : ports(true, IPortType.Type.ITEM, AbstractItemIOPortTE.class)) {
            int limit = Math.min(proto.getMaxStackSize(), port.getInventoryStackLimit());
            for (int slot = 0; slot < port.getSizeInventory() && remaining > 0; slot++) {
                ItemStack existing = port.getStackInSlot(slot);
                if (existing == null) {
                    ItemStack s = proto.copy();
                    s.stackSize = Math.min(remaining, limit);
                    port.setInventorySlotContents(slot, s);
                    remaining -= s.stackSize;
                } else if (existing.isItemEqual(proto) && ItemStack.areItemStackTagsEqual(existing, proto)
                    && existing.stackSize < limit) {
                        int n = Math.min(remaining, limit - existing.stackSize);
                        ItemStack s = existing.copy();
                        s.stackSize += n;
                        port.setInventorySlotContents(slot, s);
                        remaining -= n;
                    }
            }
        }
        if (remaining > 0) throw new IllegalStateException(remaining + " of " + item + " did not fit the item inputs");
    }

    private void insertFluid(String fluid, int amount) {
        FluidStack proto = FluidRegistry.getFluidStack(fluid, amount);
        if (proto == null) throw new IllegalArgumentException("unknown fluid \"" + fluid + "\"");
        int remaining = amount;
        for (AbstractFluidPortTE port : ports(true, IPortType.Type.FLUID, AbstractFluidPortTE.class)) {
            if (remaining <= 0) break;
            remaining -= port.fill(ForgeDirection.UNKNOWN, new FluidStack(proto, remaining), true);
        }
        if (remaining > 0)
            throw new IllegalStateException(remaining + " mB of " + fluid + " did not fit the fluid inputs");
    }

    private void insertEnergy(int amount) {
        int remaining = amount;
        for (AbstractEnergyIOPortTE port : ports(true, IPortType.Type.ENERGY, AbstractEnergyIOPortTE.class)) {
            int room = port.getMaxEnergyStored() - port.getEnergyStored();
            int n = Math.min(room, remaining);
            port.setEnergyStored(port.getEnergyStored() + n);
            remaining -= n;
        }
        if (remaining > 0) throw new IllegalStateException(remaining + " RF did not fit the energy inputs");
    }

    private static TEMachineController controllerAt(TestSites.Site site) {
        TileEntity te = site.world.getTileEntity(site.x, site.y, site.z);
        if (!(te instanceof TEMachineController)) {
            throw new IllegalStateException(
                "no machine controller at the site origin (" + site.x
                    + ","
                    + site.y
                    + ","
                    + site.z
                    + " holds "
                    + idOf(site.world.getBlock(site.x, site.y, site.z), 0)
                    + ", tile "
                    + te
                    + ")");
        }
        return (TEMachineController) te;
    }

    private static String[][] authoredShape(IStructureEntry entry) {
        String[][] shape = new String[entry.getLayers()
            .size()][];
        for (int i = 0; i < shape.length; i++) {
            shape[i] = entry.getLayers()
                .get(i)
                .getRows()
                .toArray(new String[0]);
        }
        return shape;
    }

    static Block block(String name) {
        Block b = Block.getBlockFromName(name);
        if (b == null) throw new IllegalArgumentException("unknown block \"" + name + "\"");
        return b;
    }

    static ItemStack stack(String id, int amount) {
        BlockId parsed = BlockId.parse(id);
        Item item = (Item) Item.itemRegistry.getObject(parsed.name);
        if (item == null) throw new IllegalArgumentException("unknown item \"" + parsed.name + "\"");
        return new ItemStack(item, amount, parsed.meta);
    }

    static String idOf(Block block, int meta) {
        return BlockId.format(Block.blockRegistry.getNameForObject(block), meta);
    }

    static String idOf(ItemStack stack) {
        return BlockId.format(Item.itemRegistry.getNameForObject(stack.getItem()), stack.getItemDamage());
    }
}
