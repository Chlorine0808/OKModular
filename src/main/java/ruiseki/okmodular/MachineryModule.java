package ruiseki.okmodular;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import ruiseki.okcore.event.MemoryEventHandler;
import ruiseki.okcore.helper.MinecraftHelpers;
import ruiseki.okcore.init.ModModuleBase;
import ruiseki.okcore.json.JsonErrorCollector;
import ruiseki.okcore.proxy.ICommonProxy;
import ruiseki.okcore.structure.BlockResolver;
import ruiseki.okcore.structure.CustomStructureRegistry;
import ruiseki.okcore.structure.StructureManager;
import ruiseki.okmodular.common.command.CommandModular;
import ruiseki.okmodular.common.fluid.EnumFluidMaterial;
import ruiseki.okmodular.common.fluid.ModFluidGases;
import ruiseki.okmodular.common.init.MachineryBlocks;
import ruiseki.okmodular.common.init.MachineryItems;
import ruiseki.okmodular.common.init.MachineryOreDict;
import ruiseki.okmodular.common.integration.MachineryIntegration;
import ruiseki.okmodular.common.integration.structurelib.MachineControllerInfoContainer;
import ruiseki.okmodular.common.item.ItemFluidCanister;
import ruiseki.okmodular.common.network.PacketReloadNEI;
import ruiseki.okmodular.common.recipe.RecipeLoader;
import ruiseki.okmodular.common.tier.TierConfigLoader;
import ruiseki.okmodular.common.tile.StructureTintCache;
import ruiseki.okmodular.common.tile.TEMachineController;

public class MachineryModule extends ModModuleBase {

    private static File configDir;
    private static List<String> cachedGroupNames = new ArrayList<>();

    public MachineryModule() {
        super(OKModular.instance);
    }

    public static File getConfigDir() {
        return configDir;
    }

    /**
     * Get cached recipe group names, scanned during preInit.
     * Available before RecipeLoader.loadAll() completes.
     */
    public static List<String> getCachedGroupNames() {
        return cachedGroupNames;
    }

    @Override
    protected ICommonProxy createProxy() {
        try {
            if (MinecraftHelpers.isClientSide()) {
                return new MachineryClient();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return new MachineryCommon();
    }

    @Override
    public boolean isEnable() {
        return true;
    }

    @Override
    protected void registerSubCommand(Map<String, ICommand> subcommand) {
        super.registerSubCommand(subcommand);
        subcommand.put(CommandModular.NAME, new CommandModular(this.getMod()));
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        configDir = event.getModConfigurationDirectory();
        ModFluidGases.preInit();
        MachineryIntegration.preInit();
        MachineryBlocks.preInit();
        BlockResolver.registerHintBlock(MachineryBlocks.CASING_PLAIN.getBlock());
        CustomStructureRegistry.registerControllerBlock(MachineryBlocks.MACHINE_CONTROLLER.getBlock());
        MachineryItems.preInit();
        MachineryOreDict.init();

        for (EnumFluidMaterial mat : EnumFluidMaterial.values()) {
            ItemFluidCanister.registerFluidColor(mat.getName(), mat.getColor());
        }
        MemoryEventHandler.registerOnWorldUnload(world -> StructureTintCache.clearDimension(world));
        MemoryEventHandler.registerOnClientDisconnect(StructureTintCache::clearAll);

        // Pre-scan recipe group names so NEI can register handlers
        // before RecipeLoader.loadAll() runs in postInit
        cachedGroupNames = RecipeLoader.scanGroupNames(configDir);
    }

    @Override
    public void init(FMLInitializationEvent event) {

    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        TierConfigLoader.INSTANCE.load(configDir);
        RecipeLoader.getInstance()
            .loadAll(configDir);
    }

    /**
     * Register StructureLib's IMultiblockInfoContainer after CustomStructureRegistry.registerAll()
     * so that structures are available when StructureLib queries them for NEI display.
     * Called from OKModular.postInit() after StructureCompat.postInit().
     */
    public static void postInitStructures() {
        IMultiblockInfoContainer.registerTileClass(TEMachineController.class, new MachineControllerInfoContainer());
    }

    @Override
    public void reload(ICommandSender sender) throws Exception {
        boolean hasErrors = false;

        try {
            StructureManager.getInstance()
                .reload();
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "  [Modular] Structures reloaded"));
        } catch (Exception e) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "  [Modular] Structures failed: " + e.getMessage()));
            hasErrors = true;
        }

        try {
            TierConfigLoader.INSTANCE.reload();
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "  [Modular] Tier config reloaded"));
        } catch (Exception e) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "  [Modular] Tier config failed: " + e.getMessage()));
            hasErrors = true;
        }

        try {
            RecipeLoader.getInstance()
                .reload(configDir);
            sender.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN + "  [Modular] Recipes reloaded"));
            OKModular.instance.getPacketHandler()
                .sendToAll(new PacketReloadNEI());
        } catch (Exception e) {
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.RED + "  [Modular] Recipes failed: " + e.getMessage()));
            hasErrors = true;
        }

        if (hasErrors || JsonErrorCollector.getInstance()
            .hasErrors()) {
            JsonErrorCollector.getInstance()
                .writeToFile();
            JsonErrorCollector.getInstance()
                .reportToChat(sender);
        }
    }
}
