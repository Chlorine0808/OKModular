package ruiseki.okmodular.client.nei;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.event.NEIRegisterHandlerInfosEvent;
import codechicken.nei.recipe.HandlerInfo;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import ruiseki.okcore.addon.nei.IRecipeHandlerBase;
import ruiseki.okcore.api.structure.core.IStructureEntry;
import ruiseki.okcore.enums.Mods;
import ruiseki.okcore.structure.CustomStructureRegistry;
import ruiseki.okcore.structure.StructureManager;
import ruiseki.okcore.util.Logger;
import ruiseki.okmodular.MachineryModule;
import ruiseki.okmodular.Reference;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.common.init.MachineryBlocks;
import ruiseki.okmodular.common.init.MachineryItems;
import ruiseki.okmodular.common.item.ItemMachineBlueprint;
import ruiseki.okmodular.common.recipe.RecipeLoader;

/**
 * NEI configuration for OK Modular.
 * Split out of OmoshiroiKamo's NEIConfig (machinery sections).
 */
public class NEIConfig implements IConfigureNEI {

    /**
     * Register handler info for Modular Machine NEI tab.
     * This controls the appearance of the recipe tab in NEI.
     */
    @SubscribeEvent
    public void registerHandlerInfo(NEIRegisterHandlerInfosEvent event) {
        if (!Mods.BlockRenderer6343.isLoaded()) return;

        // Register icon for the generic preview handler
        event.registerHandlerInfo(
            new HandlerInfo.Builder(ModularMachineNEIHandler.class.getName(), Reference.MOD_NAME, Reference.MOD_ID)
                .setDisplayStack(getStructureLibTrigger())
                .setHeight(168)
                .setWidth(192)
                .setShiftY(6)
                .build());

        // Register icons for EACH structure (because they use separate IDs in
        // getOverlayIdentifier)
        for (String structureName : CustomStructureRegistry.getRegisteredNames()) {
            String handlerID = "modular_structure_" + structureName;
            event.registerHandlerInfo(
                new HandlerInfo.Builder(handlerID, Reference.MOD_NAME, Reference.MOD_ID)
                    .setDisplayStack(getStructureLibTrigger())
                    .setHeight(168)
                    .setWidth(192)
                    .setShiftY(6)
                    .build());
        }

        // Register dynamic Modular Machine recipe groups
        for (String group : MachineryModule.getCachedGroupNames()) {
            String handlerID = "modular_" + group;
            event.registerHandlerInfo(
                new HandlerInfo.Builder(handlerID, Reference.MOD_NAME, Reference.MOD_ID)
                    .setDisplayStack(new ItemStack(MachineryBlocks.MACHINE_CONTROLLER.getBlock()))
                    .setHeight(100)
                    .setWidth(166)
                    .build());
        }
    }

    @Override
    public void loadConfig() {
        Logger.info("Loading NEIConfig: {}", getName());
        // Structure preview handlers are registered later (after CustomStructureRegistry.registerAll())
        // via registerStructurePreviews() called from OKModular.postInit()

        // Register Modular Machine Recipes (JSON)
        registerModularMachineryRecipes();
    }

    /**
     * Register ModularMachineNEIHandler instances for each known structure.
     * Must be called AFTER CustomStructureRegistry.registerAll() (i.e., after StructureCompat.postInit()
     * in OmoshiroiKamo's postInit).
     *
     * TODO: Fix catalyst blueprints appear briefly in left tab then disappear.
     * TODO: Enable 'P' button in structure preview (Name is currently null)
     */
    public static void registerStructurePreviews() {
        if (!Mods.BlockRenderer6343.isLoaded()) return;
        if (!FMLCommonHandler.instance()
            .getEffectiveSide()
            .isClient()) return;

        for (String structureName : CustomStructureRegistry.getRegisteredNames()) {
            ModularMachineNEIHandler handler = new ModularMachineNEIHandler(structureName);
            API.registerUsageHandler(handler);

            String recipeID = handler.getHandlerId();
            ItemStack blueprint = ItemMachineBlueprint
                .createBlueprint(MachineryItems.MACHINE_BLUEPRINT.getItem(), structureName);
            ItemStack controller = new ItemStack(MachineryBlocks.MACHINE_CONTROLLER.getBlock());

            API.addRecipeCatalyst(blueprint, recipeID);
            API.addRecipeCatalyst(controller, recipeID);
        }

        Logger.info(
            "NEIConfig: registered {} structure preview handler(s)",
            CustomStructureRegistry.getRegisteredNames()
                .size());
    }

    private static Set<String> registeredModularGroups = new HashSet<>();

    private void registerModularMachineryRecipes() {
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isServer()) return;
        List<String> groups = new ArrayList<>(MachineryModule.getCachedGroupNames());

        List<IModularRecipe> allRecipes = RecipeLoader.getInstance()
            .getAllRecipes();
        for (IModularRecipe recipe : allRecipes) {
            String group = recipe.getRecipeGroup();
            if (!groups.contains(group)) {
                groups.add(group);
            }
        }

        for (String group : groups) {
            if (registeredModularGroups.contains(group)) continue;
            registeredModularGroups.add(group);

            ModularRecipeNEIHandler handler = new ModularRecipeNEIHandler(group);
            registerHandler(handler);

            ItemStack catalyst = new ItemStack(MachineryBlocks.MACHINE_CONTROLLER.getBlock());
            API.addRecipeCatalyst(catalyst, handler.getRecipeID());
            for (String structureName : CustomStructureRegistry.getRegisteredNames()) {
                IStructureEntry entry = StructureManager.getInstance()
                    .getCustomStructure(structureName);
                if (entry != null && entry.getRecipeGroup() != null
                    && entry.getRecipeGroup()
                        .contains(group)) {
                    ItemStack blueprint = ItemMachineBlueprint
                        .createBlueprint(MachineryItems.MACHINE_BLUEPRINT.getItem(), structureName);
                    API.addRecipeCatalyst(blueprint, handler.getRecipeID());
                }
            }
        }
    }

    public static void reloadModularMachineryRecipes() {
        if (FMLCommonHandler.instance()
            .getEffectiveSide()
            .isServer()) return;
        if (!Mods.BlockRenderer6343.isLoaded()) return;

        List<String> groups = new ArrayList<>(MachineryModule.getCachedGroupNames());
        List<IModularRecipe> allRecipes = RecipeLoader.getInstance()
            .getAllRecipes();

        for (IModularRecipe recipe : allRecipes) {
            String group = recipe.getRecipeGroup();
            if (!groups.contains(group)) {
                groups.add(group);
            }
        }

        for (String group : groups) {
            if (registeredModularGroups.contains(group)) continue;
            registeredModularGroups.add(group);

            ModularRecipeNEIHandler handler = new ModularRecipeNEIHandler(group);
            registerHandler(handler);

            ItemStack catalyst = new ItemStack(MachineryBlocks.MACHINE_CONTROLLER.getBlock());
            API.addRecipeCatalyst(catalyst, handler.getRecipeID());

            try {
                Class<?> guiRecipeClass = Class.forName("codechicken.nei.recipe.GuiRecipe");
                Method method = guiRecipeClass.getMethod("registerHandlerInfo", HandlerInfo.class);
                method.invoke(
                    null,
                    new HandlerInfo.Builder(handler.getRecipeID(), Reference.MOD_NAME, Reference.MOD_ID)
                        .setDisplayStack(catalyst)
                        .setHeight(100)
                        .setWidth(166)
                        .build());
            } catch (Throwable t) {
                Logger.error("Failed to register handler info for group " + group);
                Logger.info("Maybe incompatible NEI version is used");
            }

            for (String structureName : CustomStructureRegistry.getRegisteredNames()) {
                IStructureEntry entry = StructureManager.getInstance()
                    .getCustomStructure(structureName);
                if (entry != null && entry.getRecipeGroup() != null
                    && entry.getRecipeGroup()
                        .contains(group)) {
                    ItemStack blueprint = ItemMachineBlueprint
                        .createBlueprint(MachineryItems.MACHINE_BLUEPRINT.getItem(), structureName);
                    API.addRecipeCatalyst(blueprint, handler.getRecipeID());
                }
            }
        }
    }

    protected static void registerHandler(IRecipeHandlerBase handler) {
        handler.prepare();
        API.registerRecipeHandler(handler);
        API.registerUsageHandler(handler);
    }

    private static ItemStack getStructureLibTrigger() {
        Item trigger = GameRegistry.findItem("structurelib", "item.structurelib.constructableTrigger");
        if (trigger != null) {
            return new ItemStack(trigger);
        }
        return new ItemStack(MachineryBlocks.MACHINE_CONTROLLER.getBlock());
    }

    @Override
    public String getName() {
        return Reference.MOD_NAME;
    }

    @Override
    public String getVersion() {
        return Reference.VERSION;
    }
}
