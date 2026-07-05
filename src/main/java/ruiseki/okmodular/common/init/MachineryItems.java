package ruiseki.okmodular.common.init;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.registry.GameRegistry;
import lombok.Getter;
import ruiseki.omoshiroikamo.core.common.util.Logger;
import ruiseki.okmodular.common.item.ItemMachineBlueprint;
import ruiseki.okmodular.common.item.ItemMaterialPart;

public enum MachineryItems {

    // spotless: off
    MACHINE_BLUEPRINT(new ItemMachineBlueprint()),
    INGOT(new ItemMaterialPart("ingot")),
    PLATE(new ItemMaterialPart("plate")),
    // GEAR(new ItemMaterialPart("gear")),
    DUST(new ItemMaterialPart("dust")),;
    // spotless: on

    public static final MachineryItems[] VALUES = values();

    public static void preInit() {
        for (MachineryItems item : VALUES) {
            try {
                GameRegistry.registerItem(item.getItem(), item.getName());
                Logger.info("Successfully initialized " + item.name());
            } catch (Exception e) {
                Logger.error("Failed to initialize item: +" + item.name());
            }
        }
    }

    @Getter
    private final Item item;

    MachineryItems(Item item) {
        this.item = item;
    }

    public String getName() {
        return getItem().getUnlocalizedName()
            .replace("item.", "");
    }

    public ItemStack newItemStack() {
        return newItemStack(1);
    }

    public ItemStack newItemStack(int count) {
        return newItemStack(count, 0);
    }

    public ItemStack newItemStack(int count, int meta) {
        return new ItemStack(this.getItem(), count, meta);
    }
}
