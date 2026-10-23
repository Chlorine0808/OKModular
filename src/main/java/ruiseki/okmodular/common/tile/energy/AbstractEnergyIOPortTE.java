package ruiseki.okmodular.common.tile.energy;

import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.google.common.collect.ImmutableList;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Optional;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergySink;
import ic2.api.energy.tile.IEnergySource;
import lombok.Getter;
import lombok.Setter;
import ruiseki.okcore.enums.RedstoneMode;
import ruiseki.okcore.helper.LangHelpers;
import ruiseki.okmodular.OKModular;
import ruiseki.okmodular.Reference;
import ruiseki.okmodular.api.enums.EnumIO;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.modular.IVisitablePort;
import ruiseki.okmodular.api.recipe.visitor.IRecipeVisitor;
import ruiseki.okmodular.client.gui.OKGuiTextures;
import ruiseki.okmodular.client.gui.widget.CyclicVariantButtonWidget;
import ruiseki.okmodular.client.gui.widget.RedstoneModeWidget;
import ruiseki.okmodular.client.gui.widget.TileWidget;
import ruiseki.okmodular.client.gui.widget.ToggleWidget;
import ruiseki.okmodular.common.network.PacketEnergy;
import ruiseki.okmodular.config.MachineryConfig;
import ruiseki.okmodular.core.energy.EnergyStorage;
import ruiseki.okmodular.core.energy.IOKEnergyIO;
import ruiseki.okmodular.core.persist.nbt.NBTPersist;
import ruiseki.okmodular.core.tileentity.AbstractTE;

/**
 * Energy port: an RF buffer that also speaks IC2 when {@link #useIC2Compat} is on.
 * TODO: add RF-only, EU-only, or both ports
 * TODO: add ???-energy only ports and universal port if needed
 */
@Optional.InterfaceList({ @Optional.Interface(iface = "ic2.api.energy.tile.IEnergySink", modid = "IC2"),
    @Optional.Interface(iface = "ic2.api.energy.tile.IEnergySource", modid = "IC2") })
public abstract class AbstractEnergyIOPortTE extends AbstractTE
    implements IOKEnergyIO, IEnergySink, IEnergySource, IVisitablePort, IGuiHolder<PosGuiData> {

    /** Last known energy stored, used for periodic client synchronization. */
    private int lastSyncPowerStored;

    @NBTPersist
    protected EnergyStorage energyStorage;

    public boolean ic2Registered = false;

    @NBTPersist
    protected final EnumIO[] sides = new EnumIO[6];

    @Getter
    @Setter
    @NBTPersist
    private EnergyMode energyMode = EnergyMode.RF;

    @Getter
    @Setter
    @NBTPersist
    public boolean useIC2Compat = true;

    public AbstractEnergyIOPortTE(int energyCapacity, int energyMaxReceive) {
        energyStorage = new EnergyStorage(energyCapacity, energyMaxReceive) {

            @Override
            public void onEnergyChanged() {
                super.onEnergyChanged();
                markDirty();
            }
        };
        Arrays.fill(sides, EnumIO.NONE);
        // Default IO is NONE, handled by Block.onBlockPlacedBy
    }

    public abstract int getTier();

    public abstract void setTier(int tier);

    public abstract EnumIO getIOLimit();

    @Override
    public IPortType.Type getPortType() {
        return IPortType.Type.ENERGY;
    }

    @Override
    public abstract IPortType.Direction getPortDirection();

    @Override
    public String getLocalizedName() {
        return LangHelpers.localize(getUnlocalizedName() + ".tier_" + getTier() + ".name");
    }

    @Override
    public boolean isActive() {
        return false;
    }

    /** Sends the stored amount to nearby clients when it changes. */
    @Override
    public boolean processTasks(boolean redstoneCheckPassed) {
        boolean powerChanged = (lastSyncPowerStored != getEnergyStored() && shouldDoWorkThisTick(5));
        if (powerChanged) {
            lastSyncPowerStored = getEnergyStored();
            OKModular.instance.getPacketHandler()
                .sendToAllAround(new PacketEnergy(this), this);
        }
        return false;
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return true;
    }

    @Override
    public int getEnergyStored() {
        return energyStorage == null ? 0 : energyStorage.getEnergyStored();
    }

    @Override
    public void setEnergyStored(int storedEnergy) {
        if (energyStorage == null) {
            return;
        }
        energyStorage.setEnergyStored(storedEnergy);
    }

    @Override
    public int getMaxEnergyStored() {
        return energyStorage == null ? 0 : energyStorage.getMaxEnergyStored();
    }

    @Override
    public int getEnergyTransfer() {
        return energyStorage == null ? 0 : energyStorage.getMaxReceive();
    }

    /**
     * Extract energy for machine processing.
     *
     * @param amount requested amount
     * @return amount actually extracted
     */
    public int extractEnergy(int amount) {
        int extracted = Math.min(energyStorage.getEnergyStored(), amount);
        energyStorage.voidEnergy(extracted);
        return extracted;
    }

    /**
     * Internal method to receive energy for machine processing.
     */
    @Override
    public int receiveEnergy(ForgeDirection side, int amount, boolean simulate) {
        if (side == ForgeDirection.UNKNOWN) {
            return internalReceiveEnergy(amount, simulate);
        }
        return energyStorage == null ? 0 : energyStorage.receiveEnergy(amount, simulate);
    }

    private int internalReceiveEnergy(int amount, boolean simulate) {
        int capacity = energyStorage.getMaxEnergyStored();
        int stored = energyStorage.getEnergyStored();
        int receivable = Math.min(amount, capacity - stored);

        if (receivable <= 0) {
            return 0;
        }

        if (!simulate) {
            energyStorage.modifyEnergyStored(receivable);
        }
        return receivable;
    }

    @Override
    public int extractEnergy(ForgeDirection side, int amount, boolean simulate) {
        if (side == ForgeDirection.UNKNOWN) {
            return extractEnergy(amount);
        }
        return energyStorage == null ? 0 : energyStorage.extractEnergy(amount, simulate);
    }

    @Override
    public EnumIO getSideIO(ForgeDirection side) {
        if (side == ForgeDirection.UNKNOWN || side.ordinal() >= 6) {
            return EnumIO.NONE;
        }
        return sides[side.ordinal()];
    }

    @Override
    public void setSideIO(ForgeDirection side, EnumIO state) {
        sides[side.ordinal()] = state;
        forceRenderUpdate();
    }

    @Override
    public void readCommon(NBTTagCompound root) {
        super.readCommon(root);
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public void doUpdate() {
        if (!ic2Registered && useIC2Compat && MachineryConfig.ic2Capability) {
            register();
        } else if (ic2Registered && !useIC2Compat) {
            deregister();
        }
        super.doUpdate();
    }

    @Override
    public void onChunkUnload() {
        if (useIC2Compat) {
            deregister();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        deregister();
    }

    @Override
    public void register() {
        if (!worldObj.isRemote && Loader.isModLoaded("IC2") && MachineryConfig.ic2Capability) {
            registerIC2();
        }
    }

    @Override
    public void deregister() {
        if (!worldObj.isRemote && Loader.isModLoaded("IC2") && ic2Registered) {
            deregisterIC2();
        }
    }

    @Optional.Method(modid = "IC2")
    private void registerIC2() {
        if (ic2Registered) return;
        MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(this));
        ic2Registered = true;
    }

    @Optional.Method(modid = "IC2")
    private void deregisterIC2() {
        MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(this));
        ic2Registered = false;
    }

    @Override
    @Optional.Method(modid = "IC2")
    public double getDemandedEnergy() {
        int missing = getMaxEnergyStored() - getEnergyStored();
        return Math.max(0, (double) missing / MachineryConfig.rftToEU);
    }

    @Override
    @Optional.Method(modid = "IC2")
    public int getSinkTier() {
        return MachineryConfig.ic2SinkTier;
    }

    @Override
    @Optional.Method(modid = "IC2")
    public double injectEnergy(ForgeDirection direction, double amount, double voltage) {
        int rf = (int) (amount * MachineryConfig.rftToEU);
        int accepted = energyStorage.receiveEnergy(rf, false);
        return amount - ((double) accepted / MachineryConfig.rftToEU);
    }

    @Override
    @Optional.Method(modid = "IC2")
    public boolean acceptsEnergyFrom(TileEntity emitter, ForgeDirection direction) {
        return canConnectEnergy(direction);
    }

    @Override
    @Optional.Method(modid = "IC2")
    public double getOfferedEnergy() {
        return (double) getEnergyStored() / MachineryConfig.rftToEU;
    }

    @Override
    @Optional.Method(modid = "IC2")
    public void drawEnergy(double amount) {
        int rf = (int) (amount * MachineryConfig.rftToEU);
        energyStorage.extractEnergy(rf, false);
    }

    @Override
    @Optional.Method(modid = "IC2")
    public int getSourceTier() {
        return MachineryConfig.ic2SourceTier;
    }

    @Override
    @Optional.Method(modid = "IC2")
    public boolean emitsEnergyTo(TileEntity receiver, ForgeDirection direction) {
        return canConnectEnergy(direction);
    }

    @Override
    public boolean onBlockActivated(World world, EntityPlayer player, ForgeDirection side, float hitX, float hitY,
        float hitZ) {
        openGui(player);
        return true;
    }

    public String getEnergyText() {
        if (energyMode == EnergyMode.EU) {
            double eu = (double) getEnergyStored() / MachineryConfig.rftToEU;
            double max = (double) getMaxEnergyStored() / MachineryConfig.rftToEU;
            return String.format("%.1f / %.1f EU", eu, max);
        } else {
            return getEnergyStored() + " / " + getMaxEnergyStored() + " RF";
        }
    }

    public String getEnergyUsedText() {
        if (energyMode == EnergyMode.EU) {
            double eu = (double) energyStorage.getEnergyStored() / MachineryConfig.rftToEU;
            return LangHelpers.localize("gui.machinery.energy_used", String.format("%.1f EU/t", eu));
        } else {
            return LangHelpers.localize("gui.machinery.energy_used", energyStorage.getEnergyStored() + " RF/t");
        }
    }

    public static final UITexture EU_MODE = UITexture.builder()
        .location(Reference.MOD_ID, "gui/machinery/icons")
        .imageSize(256, 256)
        .xy(16, 16, 16, 16)
        .build();
    public static final UITexture RF_MODE = UITexture.builder()
        .location(Reference.MOD_ID, "gui/machinery/icons")
        .imageSize(256, 256)
        .xy(0, 16, 16, 16)
        .build();

    private static final List<CyclicVariantButtonWidget.Variant> ENERGY_VARIANTS = Arrays.asList(
        new CyclicVariantButtonWidget.Variant(IKey.lang("gui.energy_mode.rf"), RF_MODE),
        new CyclicVariantButtonWidget.Variant(IKey.lang("gui.energy_mode.eu"), EU_MODE));

    public static final UITexture IC2_ON = UITexture.builder()
        .location(Reference.MOD_ID, "gui/machinery/icons")
        .imageSize(256, 256)
        .xy(32, 16, 16, 16)
        .build();
    public static final UITexture IC2_OFF = UITexture.builder()
        .location(Reference.MOD_ID, "gui/machinery/icons")
        .imageSize(256, 256)
        .xy(48, 16, 16, 16)
        .build();

    private static final List<CyclicVariantButtonWidget.Variant> IC2_VARIANTS = Arrays.asList(
        new CyclicVariantButtonWidget.Variant(IKey.lang("gui.machinery.ic2_on"), IC2_ON),
        new CyclicVariantButtonWidget.Variant(IKey.lang("gui.machinery.ic2_off"), IC2_OFF));

    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel mainPanel) {
        return new ModularScreen(Reference.MOD_ID, mainPanel);
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = new ModularPanel("energy_port_gui");

        EnumSyncValue<RedstoneMode, ?> redstoneSyncer = new EnumSyncValue<>(
            RedstoneMode.class,
            this::getRedstoneMode,
            this::setRedstoneMode);
        redstoneSyncer.allowC2S();
        syncManager.syncValue("redstoneSyncer", redstoneSyncer);
        IntSyncValue energySyncer = new IntSyncValue(this::getEnergyStored, this::setEnergyStored);
        syncManager.syncValue("energySyncer", energySyncer);
        syncManager.syncValue("maxEnergySyncer", new IntSyncValue(this::getMaxEnergyStored));
        BooleanSyncValue ic2CompatSyncer = new BooleanSyncValue(this::isUseIC2Compat, this::setUseIC2Compat);
        ic2CompatSyncer.allowC2S();
        syncManager.syncValue("ic2CompatSyncer", ic2CompatSyncer);

        EnumSyncValue<EnergyMode, ?> modeSyncer = new EnumSyncValue<>(
            EnergyMode.class,
            this::getEnergyMode,
            this::setEnergyMode);
        modeSyncer.allowC2S();
        syncManager.syncValue("modeSyncer", modeSyncer);

        panel.child(
            new RedstoneModeWidget(redstoneSyncer).pos(-20, 2)
                .size(18)
                .excludeAreaInRecipeViewer());

        panel.child(
            new CyclicVariantButtonWidget(
                ENERGY_VARIANTS,
                modeSyncer.getValue()
                    .ordinal(),
                1,
                16,
                value -> { modeSyncer.setValue(EnergyMode.byIndex(value)); }).size(18)
                    .pos(-20, 22)
                    .excludeAreaInRecipeViewer());

        panel.child(
            new ProgressWidget().texture(OKGuiTextures.ENERGY_BAR, 64)
                .size(16, 64)
                .value(new DoubleSyncValue(() -> (double) this.getEnergyStored() / this.getMaxEnergyStored()))
                .tooltipAutoUpdate(true)
                .direction(ProgressWidget.Direction.UP)
                .tooltipDynamic(tooltip -> {
                    tooltip.addLine(this.getEnergyText());
                    tooltip.addLine(getEnergyUsedText());
                    tooltip.pos(RichTooltip.Pos.NEXT_TO_MOUSE);
                })
                .pos(8, 6));

        panel.child(new TileWidget(this.getLocalizedName()));

        panel.child(
            IKey.lang(data.getPlayer().inventory.getInventoryName())
                .asWidget()
                .pos(8, 72));

        Flow column = Flow.column()
            .coverChildren()
            .pos(28, 6)
            .childPadding(2);

        panel.child(column);

        column.child(
            IKey.dynamic(this::getEnergyText)
                .asWidget()
                .left(0));
        column.child(
            IKey.dynamic(this::getEnergyUsedText)
                .asWidget()
                .left(0));

        column.child(
            Flow.row()
                .coverChildren()
                .left(0)
                .childPadding(2)
                .child(new ToggleWidget(IC2_VARIANTS, ic2CompatSyncer))
                .child(
                    IKey.lang("gui.machinery.use_ic2")
                        .asWidget()));

        syncManager.bindPlayerInventory(data.getPlayer());
        panel.bindPlayerInventory();

        return panel;
    }

    @Override
    public void accept(IRecipeVisitor visitor) {}

    public enum EnergyMode {

        RF,
        EU;

        private static final ImmutableList<EnergyMode> values = ImmutableList.copyOf(values());

        public static EnergyMode byIndex(int index) {
            if (index < 0 || index >= values.size()) return RF;

            return values.get(index);
        }
    }

}
