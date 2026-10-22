package ruiseki.okmodular.common.tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizon.structurelib.alignment.IAlignment;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;

import ruiseki.okcore.enums.RedstoneMode;
import ruiseki.okmodular.Reference;
import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.enums.EnumIO;
import ruiseki.okmodular.api.modular.IMachineController;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.modular.IVisitablePort;
import ruiseki.okmodular.api.modular.PortColor;
import ruiseki.okmodular.api.recipe.context.IRecipeContext;
import ruiseki.okmodular.api.recipe.core.DurationPolicy;
import ruiseki.okmodular.api.recipe.core.IMachineState;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.core.ITieredMachine;
import ruiseki.okmodular.api.recipe.error.ErrorReason;
import ruiseki.okmodular.api.recipe.expression.SeedMixer;
import ruiseki.okmodular.api.recipe.visitor.IRecipeVisitor;
import ruiseki.okmodular.api.structure.core.ConditionPolicy;
import ruiseki.okmodular.api.structure.core.IStructureEntry;
import ruiseki.okmodular.client.gui.handler.ItemStackHandlerBase;
import ruiseki.okmodular.common.block.BlockMachineController;
import ruiseki.okmodular.common.item.ItemMachineBlueprint;
import ruiseki.okmodular.common.recipe.ProcessAgent;
import ruiseki.okmodular.common.tile.agent.MachineStateAgent;
import ruiseki.okmodular.common.tile.agent.RecipeRunner;
import ruiseki.okmodular.core.persist.nbt.NBTPersist;
import ruiseki.okmodular.core.tileentity.AbstractTE;
import ruiseki.okmodular.core.tileentity.StructureOffsets;
import ruiseki.okmodular.structure.StructureManager;
import ruiseki.okmodular.util.Logger;

/**
 * Corresponds to the 'Q' symbol in structure definitions.
 * The controller reads the structure name from the inserted blueprint
 * and validates the surrounding blocks against that structure definition.
 * TODO: Improve holo indicator
 */
public class TEMachineController extends AbstractTE implements IAlignment, IGuiHolder<PosGuiData>, IRecipeContext,
    IVisitablePort, ITieredMachine, IMachineController, MachineStateAgent.Host {

    /** Whether the structure around the controller currently matches its blueprint. */
    @NBTPersist
    protected boolean isFormed = false;
    private transient boolean lastIsFormed = false;

    // ========== Blueprint Inventory ==========
    public static final int BLUEPRINT_SLOT = 0;
    private static final int INVENTORY_SIZE = 1;

    private final ItemStackHandlerBase inventory = new ItemStackHandlerBase(INVENTORY_SIZE) {

        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            if (slot == BLUEPRINT_SLOT) {
                updateStructureFromBlueprint();
            }
            markDirty();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == BLUEPRINT_SLOT) {
                return stack != null && stack.getItem() instanceof ItemMachineBlueprint;
            }
            return false;
        }
    };

    // Port management (delegated to PortManager)
    private final PortManager portManager = new PortManager();

    // Structure management
    private final StructureAgent structureAgent = new StructureAgent(this);

    // Recipe processing
    private final ProcessAgent processAgent = new ProcessAgent(this);
    // Recipe groups - obtained from custom structure or GUI
    private List<String> recipeGroups = new ArrayList<>(Collections.singletonList("default"));

    // Cached port lists to avoid per-tick allocations
    private transient List<IModularPort> cachedInputPorts = null;
    private transient List<IModularPort> cachedOutputPorts = null;

    // GUI management
    private final GuiManager guiManager = new GuiManager(this);

    private ExtendedFacing extendedFacing = ExtendedFacing.DEFAULT;

    // Redstone Control - Inherited from AbstractTE

    // External Port Error Cache (Persistent state for transient proxies)
    private final Set<ChunkCoordinates> reportedErrorPorts = new HashSet<>();

    @NBTPersist
    private long timePlacedCount = 0;
    @NBTPersist
    private long timeContinuousCount = 0;
    /**
     * The tick the running recipe started on, or zero when the machine has never run one.
     * <p>
     * There used to be a {@code currentRecipeSeed} beside this, persisted too, holding the
     * same tick XORed with the world seed and the position. Both were set from the same
     * {@code getTotalWorldTime()} on the same line, and the evaluation seed XORed the world
     * seed and the position back in - so the extra field carried <b>no fact this one does
     * not</b>, and its XOR chain cancelled the world seed out. See
     * {@link SeedMixer#forEvaluation}.
     */
    @NBTPersist
    private long currentRecipeStartTick = 0;

    /**
     * Colour group the running recipe belongs to.
     *
     * Persisted because a machine can be saved mid-recipe, and its output has to go
     * back to the same colour it came from. The colour is stored rather than the port
     * list because ports are resolved on formation, not loaded from NBT.
     */
    @NBTPersist
    private PortColor activeRecipeColor = PortColor.NONE;

    private final MachineStateAgent machineStateAgent = new MachineStateAgent(this, processAgent);

    // Performance modifiers from the structure definition, and the guard that keeps a
    // self-referencing expression from recursing back into the getters below.
    private final MachineModifiers modifiers = new MachineModifiers(this::reportModifierCycle);

    // External Port Configurations
    private final Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> externalPortConfigs = new HashMap<>();

    @Override
    public Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> getExternalPortConfigs() {
        return externalPortConfigs;
    }

    @Override
    public boolean registerExternalPort(int px, int py, int pz, IPortType.Type portType, EntityPlayer player) {
        if (worldObj == null || worldObj.isRemote) return false;

        String structureName = getCustomStructureName();
        if (structureName == null || structureName.isEmpty()) {
            return false;
        }

        IStructureEntry entry = StructureManager.getInstance()
            .getCustomStructure(structureName);
        if (entry == null) {
            return false;
        }

        java.util.Set<Character> exPorts = entry.getExternalPorts();
        if (exPorts == null || exPorts.isEmpty()) return false;

        ChunkCoordinates target = new ChunkCoordinates(px, py, pz);
        boolean isValid = false;
        Character foundSymbol = null;
        for (Character c : exPorts) {
            List<ChunkCoordinates> positions = structureAgent.getSymbolPositionsMap()
                .get(c);
            if (positions != null) {
                if (positions.contains(target)) {
                    isValid = true;
                    foundSymbol = c;
                    break;
                }
            }
        }

        if (!isValid) {
            return false;
        }

        // Check for fixed IO locks
        Map<Character, EnumIO> fixedPorts = entry.getFixedExternalPorts();
        if (fixedPorts.containsKey(foundSymbol)) {
            player.addChatMessage(new ChatComponentTranslation("chat.okmodular.port_io_fixed"));
            return true; // Cancel action but return true to prevent other interactions
        }

        Map<IPortType.Type, EnumIO> typeMap = externalPortConfigs.computeIfAbsent(target, k -> new HashMap<>());
        EnumIO current = typeMap.get(portType);

        if (current == null || current == EnumIO.NONE) {
            typeMap.put(portType, EnumIO.INPUT);
        } else if (current == EnumIO.INPUT) {
            typeMap.put(portType, EnumIO.OUTPUT);
        } else if (current == EnumIO.OUTPUT) {
            typeMap.put(portType, EnumIO.BOTH);
        } else {
            typeMap.remove(portType);
            if (typeMap.isEmpty()) {
                externalPortConfigs.remove(target);
            }
        }

        EnumIO newIo = typeMap.getOrDefault(portType, EnumIO.NONE);
        player
            .addChatMessage(new ChatComponentTranslation("chat.okmodular.port_io_set", portType.name(), newIo.name()));

        setFormed(false);
        clearStructureParts();
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        return true;
    }

    /**
     * Checks if an error should be notified for the given port position.
     * Prevents spam by only allowing one notification per coordinate until reset.
     *
     * @param pos The position of the port
     * @return true if this is the first time an error is reported for this position
     */
    public boolean shouldNotifyError(ChunkCoordinates pos) {
        if (reportedErrorPorts.contains(pos)) return false;
        reportedErrorPorts.add(pos);
        return true;
    }

    /**
     * Reset the error cache for all ports.
     */
    public void resetErrorCache() {
        reportedErrorPorts.clear();
    }

    // Transient flag to trigger tint packet resend on load
    private boolean needsTintResend = false;

    public TEMachineController() {
        super();
    }

    // ========== Structure Definition ==========

    @SuppressWarnings("unchecked")
    public IStructureDefinition<TEMachineController> getStructureDefinition() {
        // StructureAgent returns IStructureDefinition<IMachineController>; safe because
        // TEMachineController is the only IMachineController that ever calls this.
        return (IStructureDefinition<TEMachineController>) (IStructureDefinition<?>) structureAgent
            .getStructureDefinition();
    }

    public int[][] getOffSet() {
        return structureAgent.getOffSet();
    }

    @Override
    public String getStructurePieceName() {
        return structureAgent.getStructurePieceName();
    }

    @Override
    public int getTier() {
        Map<String, Integer> tiers = structureAgent.getComponentTiers();
        if (tiers.isEmpty()) {
            return 1;
        }
        int min = Integer.MAX_VALUE;
        for (int t : tiers.values()) {
            min = Math.min(min, t);
        }
        return min == Integer.MAX_VALUE ? 1 : min;
    }

    @Override
    public void setTier(int tier) {
        // No-op: Controller tier is calculated from structure components, not set
        // directly
    }

    public int getComponentTier(String componentName) {
        return structureAgent.getComponentTier(componentName);
    }

    @Override
    public Map<String, Integer> getComponentTiers() {
        return structureAgent.getComponentTiers();
    }

    public StructureAgent getStructureAgent() {
        return structureAgent;
    }

    public void setComponentTiers(Map<String, Integer> tiers) {
        structureAgent.setComponentTiers(tiers);
    }

    // ========== Structure Parts Tracking ==========

    protected void clearStructureParts() {
        structureAgent.resetStructure();
        invalidatePortCache();
    }

    @Override
    public int[] getSymbolCell(int x, int y, int z) {
        return structureAgent.getSymbolCell(x, y, z);
    }

    @Override
    public int[] getCellPosition(int a, int b, int c) {
        return structureAgent.getCellPosition(a, b, c);
    }

    @Override
    public void trackSymbolPosition(char symbol, int x, int y, int z) {
        structureAgent.trackSymbolPosition(symbol, x, y, z);
    }

    @Override
    public void finalizeSymbolPosition(char symbol, int x, int y, int z) {
        structureAgent.finalizeSymbolPosition(symbol, x, y, z);
    }

    @Override
    public Character getSymbolAt(int x, int y, int z) {
        return structureAgent.getSymbolAt(x, y, z);
    }

    protected boolean addToMachine(Block block, int meta, int x, int y, int z) {
        return structureAgent.addToMachine(block, meta, x, y, z);
    }

    /**
     * Called by StructureLib's ofTileAdder during structure check.
     * Adds a port to the appropriate list based on direction.
     *
     * @param port    The port to add
     * @param isInput True for input list, false for output list
     */
    @Override
    public void addPortFromStructure(IModularPort port, boolean isInput) {
        portManager.addPort(port, isInput);
        // Also add the port's position to structure block positions
        if (port instanceof TileEntity) {
            TileEntity te = (TileEntity) port;
            structureAgent.addStructurePosition(te.xCoord, te.yCoord, te.zCoord);
        }
    }

    /**
     * Called by BlockResolver to track all structure blocks (casings, etc.)
     * for tinting purposes.
     */
    @Override
    public void trackStructureBlock(int x, int y, int z) {
        structureAgent.addStructurePosition(x, y, z);
    }

    // Last process error reason for GUI display
    private final ProcessErrorState processError = new ProcessErrorState();
    private final RecipeRunner recipeRunner = new RecipeRunner(
        new RunnerHost(),
        processAgent,
        processError,
        RecipeRunner.RecipeFinder.LOADER);

    public String getLastProcessErrorDetail() {
        return processError.getDetail();
    }

    protected boolean structureCheck(String piece, int ox, int oy, int oz) {
        return structureAgent.structureCheck(piece, ox, oy, oz);
    }

    /**
     * The performance modifiers the structure definition gives this machine.
     * <p>
     * Each of these used to build its own bare {@link ConditionContext}, which answers zero
     * for every machine property - {@code "speedMultiplier": "tier"} evaluated to zero with
     * no exception and no log. They take {@link #getConditionContext()} instead, and go
     * through {@link MachineModifiers} so that a definition reading the value it defines
     * ({@code "speedMultiplier": "speed_multi * 2"}) stops rather than recursing forever.
     * The context is passed as a supplier and never built when the machine has no structure;
     * {@code speedMultiplier} is read every tick.
     */
    @Override
    public double getSpeedMultiplier() {
        return modifiers.speedMultiplier(getStructureEntry(), this::getConditionContext);
    }

    @Override
    public double getEnergyMultiplier() {
        return modifiers.energyMultiplier(getStructureEntry(), this::getConditionContext);
    }

    @Override
    public int getBatchMin() {
        return modifiers.batchMin(getStructureEntry(), this::getConditionContext);
    }

    @Override
    public int getBatchMax() {
        return modifiers.batchMax(getStructureEntry(), this::getConditionContext);
    }

    private void reportModifierCycle(MachineModifiers.Modifier modifier) {
        Logger.error(
            "Structure '{}' defines a cyclic {}: the expression reads the value it defines. Using the neutral value.",
            getCustomStructureName(),
            modifier.getJsonKey());
    }

    /**
     * Reads every modifier once, so a cyclic definition is reported when the machine forms.
     * <p>
     * A cycle is only noticed while the offending modifier is being evaluated, and the four
     * of them are read at very different times: {@code speedMultiplier} every tick of a
     * running recipe, {@code batchMin} / {@code batchMax} when one starts, and
     * {@code energyMultiplier} only if a recipe happens to mention {@code energy_multi}. A
     * machine that never manages to start a recipe therefore reported nothing at all, and a
     * structure with two broken coefficients reported them minutes apart - which reads as
     * "the report is missing" rather than "the report is waiting".
     * <p>
     * The report stays one-shot per modifier, so forming a structure with two cyclic
     * coefficients still produces exactly two lines, and re-forming produces none.
     */
    private void reportModifierCycles() {
        if (worldObj == null || worldObj.isRemote) return;
        getSpeedMultiplier();
        getEnergyMultiplier();
        getBatchMin();
        getBatchMax();
    }

    @Override
    public IStructureEntry getStructureEntry() {
        if (worldObj == null || !isFormed) return null;
        String name = getCustomStructureName();
        if (name == null) return null;
        return StructureManager.getInstance()
            .getCustomStructure(name);
    }

    @Override
    public DurationPolicy getDurationPolicy() {
        IStructureEntry entry = getStructureEntry();
        return entry != null ? entry.getDurationPolicy() : DurationPolicy.ON_START;
    }

    public String getCustomStructureName() {
        return structureAgent.getCustomStructureName();
    }

    public void onFormed() {
        structureAgent.onFormed();
        updateRecipeGroupFromStructure();
        invalidatePortCache();
        reportModifierCycles();
    }

    /**
     * Main update loop.
     * Blueprint is required for the controller to operate.
     * Uses StructureLib for structure validation when blueprint is set.
     */
    @Override
    public void doUpdate() {
        // Resend tint packet on load/restore
        if (needsTintResend && worldObj != null && !worldObj.isRemote) {
            structureAgent.sendTintPacket();
            needsTintResend = false;
        }

        // Sync customStructureName from blueprint if blueprint is present in GUI
        String blueprintName = getStructureNameFromBlueprint();
        String currentName = structureAgent.getCustomStructureName();

        // Update if blueprint changed (including removal)
        if (!Objects.equals(blueprintName, currentName)) {
            structureAgent.setCustomStructureName(blueprintName);
            setFormed(false);
            clearStructureParts();
            processAgent.abort();
            // A cycle already reported belonged to the previous definition.
            modifiers.reset();
            markDirty();

            if (blueprintName != null && !blueprintName.isEmpty()) {
                updateRecipeGroupFromStructure();
            }
        }

        // Blueprint required - no operation without it
        if (structureAgent.getCustomStructureName() == null || structureAgent.getCustomStructureName()
            .isEmpty()) {
            processError.set(ErrorReason.MISSING_BLUEPRINT);
            return;
        }

        // Redstone, port tasks and render throttling.
        super.doUpdate();

        if (worldObj.isRemote) {
            return;
        }

        if (shouldDoWorkThisTick(5) || !isFormed) {
            // See StructureOffsets for why the tier cannot index this table directly.
            int[] offset = StructureOffsets.forTier(getOffSet(), getTier());
            structureCheck(getStructurePieceName(), offset[0], offset[1], offset[2]);
        }
        // Check Redstone signal for suspension
        if (!isRedstoneActive()) {
            processError.set(ErrorReason.PAUSED);
            return;
        }

        if (!machineConditionsMet()) {
            return;
        }

        // Process recipes when formed
        if (isFormed) {
            if (!worldObj.isRemote) {
                timePlacedCount++;
                if (processAgent.isRunning()) {
                    timeContinuousCount++;
                }
            }
            recipeRunner.process();
        }
    }

    /**
     * Whether the machine's own conditions, declared in its structure definition, hold.
     * <p>
     * Checked in the same place as the redstone signal and for the same reason: this gates
     * the machine as a whole, so it comes before any recipe work and before the running
     * counters move. A machine that is not formed is not gated - there is nothing to gate.
     * <p>
     * <b>A structure that declares no conditions pays nothing.</b> The context is passed as
     * a supplier and never built when the list is empty, which matters on a path that runs
     * every tick.
     * <p>
     * The context comes from {@link #getConditionContext()} rather than a bare one, so a
     * condition can ask about the machine - {@code energy > 1000} and the like. A context
     * without this controller answers zero for every machine property, which would make
     * such a condition quietly always false. It is a fresh instance each tick, so its result
     * cache is never shared with the recipe's own condition checks.
     *
     * @return true when the machine may run
     */
    private boolean machineConditionsMet() {
        IStructureEntry entry = getStructureEntry();
        if (entry == null) return true;

        MachineConditionGate.Verdict verdict = MachineConditionGate
            .evaluate(entry.getConditions(), this::getConditionContext);
        if (verdict.isMet()) return true;

        setProcessError(ErrorReason.CONDITION_NOT_MET, verdict.getFailedDescription());

        // The one place this mod aborts a recipe that may already have eaten its inputs.
        // abort() resets the process without giving anything back, and that is what the
        // policy means - the alternative, PAUSE, is the default precisely because it does
        // not. Elsewhere abort() is only reached once it is known that nothing was consumed
        // (see the isOnlyNonConsumingRecipe checks in RecipeRunner).
        if (entry.getConditionPolicy() == ConditionPolicy.ABORT) {
            processAgent.abort();
        }
        return false;
    }

    @Override
    public boolean onBlockActivated(World world, EntityPlayer player, ForgeDirection side, float hitX, float hitY,
        float hitZ) {
        openGui(player);
        return true;
    }

    public List<IModularPort> getContextualInputPorts() {
        if (cachedInputPorts == null) {
            cachedInputPorts = new ArrayList<>(getInputPorts());
            cachedInputPorts.add(this);
        }
        return cachedInputPorts;
    }

    public List<IModularPort> getContextualOutputPorts() {
        if (cachedOutputPorts == null) {
            cachedOutputPorts = new ArrayList<>(getOutputPorts());
            cachedOutputPorts.add(this);
        }
        return cachedOutputPorts;
    }

    public void invalidatePortCache() {
        this.cachedInputPorts = null;
        this.cachedOutputPorts = null;
        recipeRunner.forgetLookAhead();
    }

    // ========== IModularPort Implementation ==========

    @Override
    public IPortType.Type getPortType() {
        return IPortType.Type.BLOCK;
    }

    @Override
    public IPortType.Direction getPortDirection() {
        return IPortType.Direction.NONE;
    }

    @Override
    public void accept(IRecipeVisitor visitor) {
        // No-op for the controller itself when acting as a port
    }

    @Override
    public EnumIO getSideIO(ForgeDirection side) {
        return side == extendedFacing.getDirection() ? EnumIO.BOTH : EnumIO.NONE;
    }

    @Override
    public void setSideIO(ForgeDirection side, EnumIO state) {
        // No-op
    }

    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel) {
        return new ModularScreen(Reference.MOD_ID, mainPanel);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return guiManager.buildUI(data, syncManager, settings);
    }

    // ========== Blueprint Inventory Methods ==========

    /**
     * Get the blueprint inventory handler.
     */
    public ItemStackHandlerBase getInventory() {
        return inventory;
    }

    /**
     * Get the blueprint ItemStack from the inventory slot.
     */
    @Nullable
    public ItemStack getBlueprintStack() {
        return inventory.getStackInSlot(BLUEPRINT_SLOT);
    }

    /**
     * Get structure name from the blueprint in the slot.
     */
    @Nullable
    private String getStructureNameFromBlueprint() {
        ItemStack blueprint = getBlueprintStack();
        if (blueprint == null || blueprint.stackSize == 0) return null;
        if (!(blueprint.getItem() instanceof ItemMachineBlueprint)) return null;
        return ItemMachineBlueprint.getStructureName(blueprint);
    }

    /**
     * Update custom structure when blueprint changes.
     * Called automatically when inventory contents change.
     * Only processes on server side to ensure proper sync.
     */
    private void updateStructureFromBlueprint() {
        resetErrorCache();
        // Skip if on client side
        if (worldObj != null && worldObj.isRemote) return;

        String newName = getStructureNameFromBlueprint();
        if (!Objects.equals(newName, structureAgent.getCustomStructureName())) {
            structureAgent.setCustomStructureName(newName);
            updateRecipeGroupFromStructure();
            setFormed(false);
            clearStructureParts();
            processAgent.abort();
            processError.set(ErrorReason.NONE);
            markDirty();
            if (worldObj != null) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
    }

    // ========== Getters ==========

    public List<IModularPort> getInputPorts() {
        return portManager.getInputPorts();
    }

    public List<IModularPort> getOutputPorts() {
        return portManager.getOutputPorts();
    }

    public List<IModularPort> getInputPorts(IPortType.Type type) {
        return portManager.getInputPorts(type);
    }

    public List<IModularPort> getOutputPorts(IPortType.Type type) {
        return portManager.getOutputPorts(type);
    }

    public <T extends IModularPort> List<T> getTypedInputPorts(IPortType.Type type, Class<T> portClass) {
        return portManager.getTypedInputPorts(type, portClass);
    }

    public <T extends IModularPort> List<T> getTypedOutputPorts(IPortType.Type type, Class<T> portClass) {
        return portManager.getTypedOutputPorts(type, portClass);
    }

    public <T extends IModularPort> List<T> validPorts(List<T> ports) {
        return portManager.validPorts(ports);
    }

    // ========== IMachineState Integration ==========

    @Override
    public IMachineState getMachineState() {
        return machineStateAgent;
    }

    public long getTimePlaced() {
        return timePlacedCount;
    }

    public long getTimeContinuous() {
        return timeContinuousCount;
    }

    @Override
    public void writeCommon(NBTTagCompound nbt) {
        super.writeCommon(nbt);
        machineStateAgent.writeToNBT(nbt);

        NBTTagList groupList = new NBTTagList();
        for (String group : recipeGroups) {
            groupList.appendTag(new NBTTagString(group));
        }
        nbt.setTag("recipeGroups", groupList);
        structureAgent.writeToNBT(nbt);
        // Save blueprint inventory
        nbt.setTag("inventory", inventory.serializeNBT());
        // Save process agent
        NBTTagCompound agentNbt = new NBTTagCompound();
        processAgent.writeToNBT(agentNbt);
        nbt.setTag("processAgent", agentNbt);
        // Save ExtendedFacing
        nbt.setByte("extendedFacing", (byte) extendedFacing.ordinal());
        // Save External Port Configs. Without this the wrench appears to work and
        // then loses everything on reload: the only reason configured external
        // ports survive today is that applyFixedPortConfigs rebuilds the ones the
        // structure JSON declares, every time the structure forms.
        //
        // Those JSON-derived entries get written too. They are redundant - the next
        // formation re-derives them - but telling them apart would mean tracking
        // where each entry came from, which costs more than the bytes do.
        ExternalPortConfigCodec.write(nbt, externalPortConfigs);
    }

    @Override
    public void readCommon(NBTTagCompound nbt) {
        if (nbt == null) {
            return;
        }
        super.readCommon(nbt);
        machineStateAgent.readFromNBT(nbt);

        recipeGroups = new ArrayList<>();
        if (nbt.hasKey("recipeGroups", 9)) {
            NBTTagList list = nbt.getTagList("recipeGroups", 8);
            for (int i = 0; i < list.tagCount(); i++) {
                recipeGroups.add(list.getStringTagAt(i));
            }
        } else if (nbt.hasKey("recipeGroup")) {
            String group = nbt.getString("recipeGroup");
            if (!group.isEmpty()) recipeGroups.add(group);
        }
        if (recipeGroups.isEmpty()) recipeGroups.add("default");
        // Load blueprint inventory
        if (nbt.hasKey("inventory")) {
            inventory.deserializeNBT(nbt.getCompoundTag("inventory"));
        }

        // Load structure data and positions
        if (structureAgent.readFromNBT(nbt)) {
            needsTintResend = true;
        }

        // Update structure from loaded blueprint
        updateStructureFromBlueprint();
        // Force update recipe groups to apply any JSON changes to existing multiblocks
        updateRecipeGroupFromStructure();
        // Load process agent
        if (nbt.hasKey("processAgent")) {
            processAgent.readFromNBT(nbt.getCompoundTag("processAgent"));
        }
        // Load ExtendedFacing
        ExtendedFacing previousFacing = this.extendedFacing;
        if (nbt.hasKey("extendedFacing")) {
            int ordinal = nbt.getByte("extendedFacing") & 0xFF;
            if (ordinal < ExtendedFacing.VALUES.length) {
                extendedFacing = ExtendedFacing.VALUES[ordinal];
            }
        }

        // Load External Port Configs
        ExternalPortConfigCodec.read(nbt, externalPortConfigs);

        if (worldObj != null && worldObj.isRemote && !Objects.equals(previousFacing, extendedFacing)) {
            worldObj.markBlockRangeForRenderUpdate(xCoord, yCoord, zCoord, xCoord, yCoord, zCoord);
        }
    }

    // ========== Redstone Control ==========

    public boolean isRedstonePowered() {
        return this.redstonePowered;
    }

    public void setRedstonePowered(boolean powered) {
        this.redstonePowered = powered;
    }

    @Override
    public boolean isRedstoneActive() {
        return RedstoneMode.isActive(redstoneMode, redstonePowered);
    }

    // ========== CustomStructure ==========

    /**
     * Set the custom structure name. Also updates recipeGroup from structure.
     */
    public void setCustomStructureName(String name) {
        structureAgent.setCustomStructureName(name);
        updateRecipeGroupFromStructure();

        // Reset structure state
        setFormed(false);
        clearStructureParts();
        processError.set(ErrorReason.NONE);

        // Notify client
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    /**
     * Get the custom structure display name.
     */
    public String getCustomStructureDisplayName() {
        String name = getCustomStructureName();
        if (name != null && !name.isEmpty()) {
            IStructureEntry entry = StructureManager.getInstance()
                .getCustomStructure(name);
            if (entry != null && entry.getDisplayName() != null
                && !entry.getDisplayName()
                    .isEmpty()) {
                return entry.getDisplayName();
            }
        }
        return name;
    }

    /**
     * Get the primary recipe group.
     */
    public String getRecipeGroup() {
        return recipeGroups.isEmpty() ? "default" : recipeGroups.get(0);
    }

    /**
     * Get all recipe groups.
     */
    public List<String> getRecipeGroups() {
        return recipeGroups;
    }

    /**
     * Set the recipe groups manually (single group).
     */
    public void setRecipeGroup(String recipeGroup) {
        this.recipeGroups = new ArrayList<>(Collections.singletonList(recipeGroup));
    }

    /**
     * Set the recipe groups manually.
     */
    public void setRecipeGroups(List<String> recipeGroups) {
        this.recipeGroups = new ArrayList<>(recipeGroups);
    }

    /**
     * Update recipeGroup from custom structure definition.
     */
    private void updateRecipeGroupFromStructure() {
        if (structureAgent.getCustomStructureName() == null || structureAgent.getCustomStructureName()
            .isEmpty()) {
            this.recipeGroups = new ArrayList<>(Collections.singletonList("default"));
            return;
        }
        IStructureEntry entry = StructureManager.getInstance()
            .getCustomStructure(structureAgent.getCustomStructureName());
        if (entry != null && entry.getRecipeGroup() != null
            && !entry.getRecipeGroup()
                .isEmpty()) {
            this.recipeGroups = new ArrayList<>(entry.getRecipeGroup());
        } else {
            this.recipeGroups = new ArrayList<>(Collections.singletonList("default"));
        }
    }

    /**
     * Get the custom structure properties, or null if not using custom structure.
     */
    public IStructureEntry getCustomProperties() {
        return structureAgent.getCustomProperties();
    }

    // ========== IAlignment Implementation ==========
    // TODO: load rotation limits from config.

    @Override
    public ExtendedFacing getExtendedFacing() {
        return extendedFacing;
    }

    @Override
    public void setExtendedFacing(ExtendedFacing facing) {
        if (facing == null) facing = ExtendedFacing.DEFAULT;
        this.extendedFacing = facing;

        // Reset structure state when facing changes
        setFormed(false);
        clearStructureParts();

        markDirty();
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public IAlignmentLimits getAlignmentLimits() {
        // Allow all directions
        return IAlignmentLimits.UNLIMITED;
    }

    // ========== Package Accessors for Managers ==========

    public PortManager getPortManager() {
        return portManager;
    }

    @Override
    public void addPort(IModularPort port, boolean isInput) {
        portManager.addPort(port, isInput);
    }

    public ProcessAgent getProcessAgent() {
        return processAgent;
    }

    public ErrorReason getLastProcessErrorReason() {
        return processError.getReason();
    }

    public void setLastProcessErrorReason(ErrorReason reason) {
        processError.set(reason);
    }

    public void setLastProcessErrorDetail(String detail) {
        processError.setDetail(detail);
    }

    public void setProcessError(ErrorReason reason) {
        processError.set(reason);
    }

    public void setProcessError(ErrorReason reason, String detail) {
        processError.set(reason, detail);
    }

    @Override
    public void updateEntityClient() {
        super.updateEntityClient();
        if (isFormed != lastIsFormed) {
            lastIsFormed = isFormed;
            forceRenderUpdate();
        }
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public boolean processTasks(boolean redstoneChecksPassed) {
        return false;
    }

    public boolean isFormed() {
        return isFormed;
    }

    public void setFormed(boolean formed) {
        this.isFormed = formed;
    }

    // ========== IRecipeContext Implementation ==========

    @Override
    public World getWorld() {
        return worldObj;
    }

    @Override
    public ChunkCoordinates getControllerPos() {
        return new ChunkCoordinates(xCoord, yCoord, zCoord);
    }

    @Override
    public IStructureEntry getCurrentStructure() {
        String structureName = structureAgent.getCustomStructureName();
        if (structureName == null || structureName.isEmpty()) {
            return null;
        }
        return StructureManager.getInstance()
            .getCustomStructure(structureName);
    }

    @Override
    public ForgeDirection getFacing() {
        return extendedFacing.getDirection();
    }

    @Override
    public List<ChunkCoordinates> getSymbolPositions(char symbol) {
        return structureAgent.getSymbolPositions(symbol);
    }

    @Override
    public long getRecipeStartTick() {
        return currentRecipeStartTick;
    }

    @Override
    public int getRedstoneLevel() {
        return redstoneLevel;
    }

    @Override
    public ConditionContext getConditionContext() {
        long seed = SeedMixer.forEvaluation(
            worldObj.getSeed(),
            xCoord,
            yCoord,
            zCoord,
            machineStateAgent.getRecipeProcessedCount(),
            currentRecipeStartTick);
        return new ConditionContext(worldObj, xCoord, yCoord, zCoord, this, seed);
    }

    // ========== ISidedTexture Implementation ==========

    @Override
    public IIcon getTexture(ForgeDirection side, int pass) {
        if (pass == 1) { // Overlay pass
            if (side != extendedFacing.getDirection()) {
                return null;
            }
            Block block = getBlockType();
            if (block instanceof BlockMachineController) {
                BlockMachineController controllerBlock = (BlockMachineController) block;
                // Change overlay based on working state
                boolean isWorking = processAgent != null && processAgent.isRunning();
                return isWorking ? controllerBlock.getOverlayIconActive() : controllerBlock.getOverlayIcon();
            }
        }
        return null; // Base pass handled by ISBRH using standard block render
    }

    @Override
    public void invalidate() {
        super.invalidate();
        structureAgent.resetStructure();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        structureAgent.resetStructure();
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block block) {
        super.onNeighborBlockChange(world, x, y, z, block);
        if (!world.isRemote) {
            boolean powered = world.isBlockIndirectlyGettingPowered(x, y, z);
            if (this.redstonePowered != powered) {
                this.redstonePowered = powered;
                this.redstoneStateDirty = true;
                this.forceClientUpdate = true;
            }

            // Trigger immediate structure check on neighbor change
            if (getCustomStructureName() != null && !getCustomStructureName().isEmpty()) {
                structureAgent.forceStructureCheck();
            }
        }
    }

    /** The controller as {@link RecipeRunner} sees it. */
    private final class RunnerHost implements RecipeRunner.Host {

        @Override
        public ConditionContext newConditionContext() {
            return getConditionContext();
        }

        @Override
        public List<IModularPort> getContextualInputPorts() {
            return TEMachineController.this.getContextualInputPorts();
        }

        @Override
        public List<IModularPort> getContextualOutputPorts() {
            return TEMachineController.this.getContextualOutputPorts();
        }

        @Override
        public List<IModularPort> getOutputPorts() {
            return TEMachineController.this.getOutputPorts();
        }

        @Override
        public List<String> getRecipeGroups() {
            return recipeGroups;
        }

        @Override
        public PortColor getActiveRecipeColor() {
            return activeRecipeColor;
        }

        @Override
        public void onRecipeStarting(PortColor color) {
            activeRecipeColor = color;
            // What makes this run's draws differ from the last run's at this machine. The
            // rest of the evaluation seed - the world, the position, the recipe count - is
            // mixed in by SeedMixer.forEvaluation at the moment of evaluation, so there is
            // nothing to persist here beyond the tick itself.
            currentRecipeStartTick = worldObj.getTotalWorldTime();
        }

        @Override
        public void recordRecipeCompletion(IModularRecipe recipe) {
            machineStateAgent.recordRecipeCompletion(recipe);
        }

        @Override
        public void onWorkingChanged() {
            if (worldObj != null && !worldObj.isRemote) {
                worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            }
        }
    }
}
