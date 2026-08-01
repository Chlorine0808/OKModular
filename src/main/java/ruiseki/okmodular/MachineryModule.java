package ruiseki.okmodular;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.gtnewhorizon.structurelib.alignment.constructable.IMultiblockInfoContainer;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import ruiseki.okcore.helper.MinecraftHelpers;
import ruiseki.okcore.init.ModModuleBase;
import ruiseki.okcore.json.JsonErrorCollector;
import ruiseki.okcore.proxy.ICommonProxy;
import ruiseki.okmodular.api.condition.Conditions;
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
import ruiseki.okmodular.core.capabilities.energy.CapabilityEnergy;
import ruiseki.okmodular.core.capabilities.fluid.CapabilityFluidHandler;
import ruiseki.okmodular.core.capabilities.item.CapabilityItemHandler;
import ruiseki.okmodular.core.capabilities.light.CapabilityLight;
import ruiseki.okmodular.core.capabilities.redstone.CapabilityRedstone;
import ruiseki.okmodular.core.event.MemoryEventHandler;
import ruiseki.okmodular.structure.BlockResolver;
import ruiseki.okmodular.structure.CustomStructureRegistry;
import ruiseki.okmodular.structure.StructureManager;
import ruiseki.okmodular.structure.pattern.StructurePatternLoader;

public class MachineryModule extends ModModuleBase {

    private static File configDir;
    private static List<String> cachedGroupNames = new ArrayList<>();

    /** Module identifier. Not a command literal any more - see {@link #constructModuleCommand}. */
    public static final String NAME = "modular";

    public MachineryModule() {
        super(OKModular.instance, NAME);
    }

    /**
     * This mod's config root, already resolved (e.g. {@code config/okmodular}) --
     * <b>not</b> Minecraft's {@code config/}. Append leaf names directly; do not prepend
     * {@link Reference#CONFIG_DIR} again.
     */
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

    /**
     * No module subtree: this mod is the machinery mod, so a `modular` level under /okmodular would
     * only repeat the mod's own name. {@link OKModular#constructBaseCommand} attaches the
     * subcommands to the root instead.
     */
    @Override
    protected LiteralArgumentBuilder<ICommandSender> constructModuleCommand(MinecraftServer server) {
        return null;
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        // Resolve this mod's config root exactly once. Everything downstream appends leaf names to it.
        configDir = new File(event.getModConfigurationDirectory(), Reference.CONFIG_DIR);

        // Condition parsers, before anything reads JSON. Without this the registry is empty
        // and every condition in every recipe and structure is dropped with "Unknown or
        // non-inferable condition type" - which is exactly what was happening. Nothing
        // called this, so weather, biome, time and expression conditions had never once
        // taken effect.
        //
        // It has to be an explicit call. RecipeParserRegistry gets away with a static
        // initialiser because parsing references that class, but nothing references
        // Conditions, so its class would never load and its static block would never run.
        Conditions.registerDefaults();

        // Structure engine wiring (formerly done by the parent mod's CoreModule).
        // The config root is this mod's ID, matching RecipeLoader and TierConfigLoader.
        StructureManager.setReloadCallback(CustomStructureRegistry::registerAll);
        StructureManager.getInstance()
            .initialize(configDir);

        // Capabilities the machinery ports/proxies depend on. Idempotent, so
        // harmless if the parent mod already registered its own copies.
        CapabilityItemHandler.register();
        CapabilityEnergy.register();
        CapabilityFluidHandler.register();
        CapabilityRedstone.register();
        CapabilityLight.register();

        ModFluidGases.preInit();
        MachineryIntegration.preInit();
        MachineryBlocks.preInit();
        BlockResolver.registerHintBlock(MachineryBlocks.CASING_PLAIN.getBlock());
        CustomStructureRegistry.registerControllerBlock(MachineryBlocks.MACHINE_CONTROLLER.getBlock());
        // Fallback for the 'F' symbol when a structure declares no mapping for it. The parent mod's
        // MultiBlockModule used to supply this; the plain casing is this mod's equivalent.
        CustomStructureRegistry.registerDefaultStructureBlock(MachineryBlocks.CASING_PLAIN.getBlock());
        MachineryItems.preInit();
        MachineryOreDict.init();

        for (EnumFluidMaterial mat : EnumFluidMaterial.values()) {
            ItemFluidCanister.registerFluidColor(mat.getName(), mat.getColor());
        }
        MemoryEventHandler.registerOnWorldUnload(world -> StructureTintCache.clearDimension(world));
        MemoryEventHandler.registerOnClientDisconnect(StructureTintCache::clearAll);
        // Subscribe the handler itself, otherwise the callbacks above can never fire.
        // The parent mod does this from its own @Mod class; this mod carries its own copy.
        MemoryEventHandler.INSTANCE.register();

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
        // Before the recipes: a `structure` input names a pattern, and resolving that name
        // against an empty registry would report every recipe as pointing at nothing.
        StructurePatternLoader.getInstance()
            .loadAll(configDir);
        RecipeLoader.getInstance()
            .loadAll(configDir);
    }

    /**
     * Register machinery structures with StructureLib, then register the
     * {@link IMultiblockInfoContainer} for the controller so StructureLib can
     * render machine previews in NEI. When running standalone this is the only
     * caller of {@link CustomStructureRegistry#registerAll()}; when the parent
     * mod is present it maintains its own separate engine (registerAll is
     * idempotent per registry).
     */
    public static void postInitStructures() {
        CustomStructureRegistry.registerAll();
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
            StructurePatternLoader.getInstance()
                .loadAll(configDir);
            sender.addChatMessage(
                new ChatComponentText(EnumChatFormatting.GREEN + "  [Modular] Structure IO patterns reloaded"));
        } catch (Exception e) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED + "  [Modular] Structure IO patterns failed: " + e.getMessage()));
            hasErrors = true;
        }

        // After the patterns, for the reason postInit loads them in that order.
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
