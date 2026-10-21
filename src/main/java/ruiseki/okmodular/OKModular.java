package ruiseki.okmodular;

import net.minecraft.command.ICommandSender;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.Level;

import com.gtnewhorizon.gtnhlib.client.model.loading.ModelRegistry;
import com.gtnewhorizon.gtnhlib.config.ConfigException;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLConstructionEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import ruiseki.okcore.enums.Mods;
import ruiseki.okcore.helper.MinecraftHelpers;
import ruiseki.okcore.init.ModBase;
import ruiseki.okcore.proxy.ICommonProxy;
import ruiseki.okmodular.client.nei.NEIConfig;
import ruiseki.okmodular.common.command.CommandModularHand;
import ruiseki.okmodular.common.command.CommandModularList;
import ruiseki.okmodular.common.command.CommandModularReload;
import ruiseki.okmodular.common.command.CommandModularWand;
import ruiseki.okmodular.config.MachineryConfig;
import ruiseki.okmodular.config.WorldGenConfig;
import ruiseki.okmodular.integration.waila.BlockProvider;

/**
 * OK Modular: modular multiblock machinery, split out of Omoshiroi Kamo.
 * Depends on OKCore as its core library (api/core packages).
 */
@Mod(
    modid = Reference.MOD_ID,
    name = Reference.MOD_NAME,
    version = Reference.VERSION,
    dependencies = Reference.DEPENDENCIES,
    guiFactory = Reference.GUI_FACTORY)
public class OKModular extends ModBase {

    static {
        try {
            MachineryConfig.registerConfig();
            WorldGenConfig.registerConfig();
        } catch (ConfigException e) {
            throw new RuntimeException(e);
        }
    }

    @SidedProxy(serverSide = Reference.PROXY_COMMON, clientSide = Reference.PROXY_CLIENT)
    public static ICommonProxy proxy;

    @Instance(Reference.MOD_ID)
    public static OKModular instance;

    public OKModular() {
        super(Reference.MOD_ID, Reference.MOD_NAME);
        putGenericReference(REFKEY_MOD_VERSION, Reference.VERSION);
    }

    @EventHandler
    public void onConstruction(FMLConstructionEvent event) {
        registerModule(new MachineryModule());
    }

    // TODO: restore the "okm" alias once okcore's CommandMod exposes aliasing.

    /**
     * Attaches the machinery subcommands to the root, giving /okmodular &lt;reload|list|wand&gt;.
     *
     * They used to arrive through MachineryModule.constructModuleCommand, which nests one literal
     * per module and produced /okmodular modular &lt;...&gt; - a level that only repeated the mod's
     * own name once the machinery became its own mod.
     */
    @Override
    protected LiteralArgumentBuilder<ICommandSender> constructBaseCommand(MinecraftServer server) {
        return super.constructBaseCommand(server).then(new CommandModularReload(this).make())
            .then(new CommandModularList(this).make())
            .then(new CommandModularWand(this).make())
            .then(new CommandModularHand(this).make());
    }

    @Override
    @EventHandler
    public final void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        if (MinecraftHelpers.isClientSide()) {
            ModelRegistry.registerModid(Reference.MOD_ID);
            if (Mods.NotEnoughItems.isLoaded()) {
                NEIConfig config = new NEIConfig();
                MinecraftForge.EVENT_BUS.register(config);
                config.loadConfig();
            }
        }
    }

    @Override
    @EventHandler
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // Without this, every getWailaInfo this mod writes is unreachable: the split brought
        // the interfaces across but left the provider that hands them to WAILA behind, and
        // OKCore's own provider registers for a same-named interface in a different package.
        if (Mods.Waila.isLoaded()) {
            BlockProvider.init();
        }
    }

    @Override
    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
        // Register machinery structures with StructureLib.
        MachineryModule.postInitStructures();
        if (MinecraftHelpers.isClientSide() && Mods.NotEnoughItems.isLoaded()) {
            NEIConfig.registerStructurePreviews();
        }
    }

    @Override
    @EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        super.onServerStarting(event);
    }

    @Override
    @EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        super.onServerStarted(event);
    }

    @Override
    @EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        super.onServerStopping(event);
    }

    @Override
    @EventHandler
    public void onServerStopped(FMLServerStoppedEvent event) {
        super.onServerStopped(event);
    }

    @Override
    public CreativeTabs constructDefaultCreativeTab() {
        return null;
    }

    @Override
    @EventHandler
    public ICommonProxy getProxy() {
        return proxy;
    }

    /**
     * Log a new info message for this mod.
     *
     * @param message The message to show.
     */
    public static void okLog(String message) {
        OKModular.instance.log(Level.INFO, message);
    }

    /**
     * Log a new message of the given level for this mod.
     *
     * @param level   The level in which the message must be shown.
     * @param message The message to show.
     */
    public static void okLog(Level level, String message) {
        OKModular.instance.log(level, message);
    }
}
