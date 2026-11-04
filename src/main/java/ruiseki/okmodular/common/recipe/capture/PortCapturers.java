package ruiseki.okmodular.common.recipe.capture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.io.FluidInput;
import ruiseki.okmodular.api.recipe.io.FluidOutput;
import ruiseki.okmodular.api.recipe.io.ItemInput;
import ruiseki.okmodular.api.recipe.io.ItemOutput;

/** Capturers by port type. Types without one are skipped when capturing. */
public final class PortCapturers {

    private static final Map<IPortType.Type, PortCapturer> CAPTURERS = new LinkedHashMap<>();

    static {
        register(IPortType.Type.ITEM, PortCapturers::captureItems);
        register(IPortType.Type.FLUID, PortCapturers::captureFluids);
    }

    private PortCapturers() {}

    public static void register(IPortType.Type type, PortCapturer capturer) {
        CAPTURERS.put(type, capturer);
    }

    public static Map<IPortType.Type, PortCapturer> all() {
        return Collections.unmodifiableMap(CAPTURERS);
    }

    private static List<JsonObject> captureItems(IModularPort port, boolean output) {
        List<JsonObject> entries = new ArrayList<>();
        if (!(port instanceof IInventory inventory)) return entries;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack == null || stack.stackSize <= 0) continue;
            JsonObject json = new JsonObject();
            if (output) new ItemOutput(stack.copy()).write(json);
            else new ItemInput(stack.copy()).write(json);
            entries.add(json);
        }
        return entries;
    }

    private static List<JsonObject> captureFluids(IModularPort port, boolean output) {
        List<JsonObject> entries = new ArrayList<>();
        if (!(port instanceof IFluidHandler handler)) return entries;
        FluidTankInfo[] tanks = handler.getTankInfo(ForgeDirection.UNKNOWN);
        if (tanks == null) return entries;
        for (FluidTankInfo tank : tanks) {
            FluidStack fluid = tank.fluid;
            if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0) continue;
            JsonObject json = new JsonObject();
            if (output) new FluidOutput(
                fluid.getFluid()
                    .getName(),
                fluid.amount).write(json);
            else new FluidInput(fluid.copy()).write(json);
            entries.add(json);
        }
        return entries;
    }
}
