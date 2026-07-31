package ruiseki.okmodular.integration.waila;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import cpw.mods.fml.common.registry.GameData;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.SpecialChars;
import ruiseki.okcore.helper.LangHelpers;
import ruiseki.okmodular.api.enums.EnumIO;
import ruiseki.okmodular.core.energy.IOKEnergyTile;
import ruiseki.okmodular.core.gas.GasTankInfo;
import ruiseki.okmodular.core.gas.IGasHandler;
import ruiseki.okmodular.core.tileentity.ICraftingTile;
import ruiseki.okmodular.core.tileentity.IProgressTile;
import ruiseki.okmodular.core.tileentity.ISidedIO;
import vazkii.botania.api.mana.IManaBlock;

public class WailaUtils {

    public static String getProgress(IProgressTile handler) {
        float progress = handler.getProgress();
        return LangHelpers.localize("gui.progress", Math.max(0, progress * 100));
    }

    public static String getCraftingState(ICraftingTile handler) {
        return LangHelpers.localize(
            "gui.craftingState." + handler.getCraftingState()
                .getName());
    }

    public static String getEnergyTransfer(IOKEnergyTile handler) {
        return LangHelpers.localize("gui.energy_transfer", handler.getEnergyTransfer());
    }

    public static List<String> getFluidTooltip(IFluidHandler handler) {
        if (handler == null) return new ArrayList<>();

        List<String> list = new ArrayList<>();
        FluidTankInfo[] tanks = handler.getTankInfo(ForgeDirection.UNKNOWN);

        if (tanks == null) return list;

        for (FluidTankInfo tank : tanks) {
            if (tank == null) continue;

            boolean empty = tank.fluid == null;

            list.add(
                SpecialChars.getRenderString(
                    "waila.fluid",
                    empty ? "EMPTYFLUID"
                        : tank.fluid.getFluid()
                            .getName(),
                    empty ? "EMPTYFLUID" : tank.fluid.getLocalizedName(),
                    String.valueOf(empty ? 0 : tank.fluid.amount),
                    String.valueOf(tank.capacity)));
        }
        return list;
    }

    public static List<String> getGasTooltip(IGasHandler handler) {
        if (handler == null) return new ArrayList<>();

        List<String> list = new ArrayList<>();
        GasTankInfo[] tanks = handler.getTankInfo(ForgeDirection.UNKNOWN);

        if (tanks == null) return list;

        for (GasTankInfo tank : tanks) {
            if (tank == null) continue;

            boolean empty = tank.gas == null;

            list.add(
                SpecialChars.getRenderString(
                    "waila.fluid",
                    empty ? "EMPTYFLUID"
                        : tank.gas.getGas()
                            .getName(),
                    empty ? "EMPTYFLUID"
                        : tank.gas.getGas()
                            .getLocalizedName(),
                    String.valueOf(empty ? 0 : tank.gas.amount),
                    String.valueOf(tank.capacity)));
        }
        return list;
    }

    public static String getManaToolTip(IManaBlock handler) {
        return LangHelpers.localize("gui.mana_info", handler.getCurrentMana());
    }

    public static String getInventoryTooltip(IInventory inv) {
        String renderStr = "";
        if (inv == null) return null;

        int index = 1;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);

            if (stack == null || stack.getItem() == null) {
                continue;
            }
            String name = GameData.getItemRegistry()
                .getNameForObject(stack.getItem());
            renderStr += SpecialChars.getRenderString(
                "waila.stack",
                String.valueOf(index),
                name,
                String.valueOf(stack.stackSize),
                String.valueOf(stack.getItemDamage()));

        }

        return renderStr;
    }

    /**
     * What the side the cursor is over is set to.
     *
     * <p>
     * Carries a label. The bare word - "Input", or worse "None" - sat in the WAILA box
     * among the port's other lines with nothing saying what it described, and the
     * direction it describes is not even the face being pointed at: the wrench maps the
     * nine sections of a face onto the six sides, so the edge of the top face reports a
     * horizontal neighbour.
     */
    public static String getSideIOTooltip(ISidedIO handler, ForgeDirection direction) {
        if (handler == null) return null;
        EnumIO io = handler.getSideIO(direction);
        return LangHelpers.localize("tooltip.okmodular.side_io", LangHelpers.localize(io.getName()));
    }

    public static Vec3 getLocalHit(IWailaDataAccessor accessor) {
        if (accessor == null) return null;

        MovingObjectPosition mop = accessor.getPosition();
        if (mop == null || mop.hitVec == null) return null;

        return Vec3.createVectorHelper(
            mop.hitVec.xCoord - mop.blockX,
            mop.hitVec.yCoord - mop.blockY,
            mop.hitVec.zCoord - mop.blockZ);
    }

}
