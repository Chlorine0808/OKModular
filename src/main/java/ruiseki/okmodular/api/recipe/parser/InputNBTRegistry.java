package ruiseki.okmodular.api.recipe.parser;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import ruiseki.okmodular.api.recipe.io.BlockInput;
import ruiseki.okmodular.api.recipe.io.EnergyInput;
import ruiseki.okmodular.api.recipe.io.EssentiaInput;
import ruiseki.okmodular.api.recipe.io.FluidInput;
import ruiseki.okmodular.api.recipe.io.GasInput;
import ruiseki.okmodular.api.recipe.io.IRecipeInput;
import ruiseki.okmodular.api.recipe.io.ItemInput;
import ruiseki.okmodular.api.recipe.io.ManaInput;
import ruiseki.okmodular.api.recipe.io.VisInput;
import ruiseki.okmodular.util.Logger;

/**
 * Registry to reconstruct IRecipeInput from NBT.
 */
public class InputNBTRegistry {

    private static final Map<String, Supplier<IRecipeInput>> REGISTRY = new HashMap<>();

    static {
        register("item", () -> new ItemInput((ItemStack) null));
        register("fluid", () -> new FluidInput(null));
        register("energy", () -> new EnergyInput(0, false));
        register("mana", () -> new ManaInput(0, false));
        register("gas", () -> new GasInput(null, 0));
        register("essentia", () -> new EssentiaInput("", 0));
        register("vis", () -> new VisInput("", 0));
        register("block", () -> new BlockInput());
    }

    public static void register(String id, Supplier<IRecipeInput> supplier) {
        REGISTRY.put(id, supplier);
    }

    public static IRecipeInput read(NBTTagCompound nbt) {
        String id = nbt.getString("id");
        Supplier<IRecipeInput> supplier = REGISTRY.get(id);
        if (supplier != null) {
            IRecipeInput input = supplier.get();
            input.readFromNBT(nbt);
            return input;
        }
        Logger.warn("Unknown input type in NBT: {}", id);
        return null;
    }
}
