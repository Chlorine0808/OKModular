package ruiseki.okmodular;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizon.gtnhlib.itemrendering.TexturedItemRenderer;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ruiseki.okcore.init.ModBase;
import ruiseki.okcore.proxy.ClientProxyComponent;
import ruiseki.okmodular.client.handler.FluidFogHandler;
import ruiseki.okmodular.client.render.ItemPortRenderer;
import ruiseki.okmodular.client.render.PortOverlayISBRH;
import ruiseki.okmodular.client.render.StructureWandRenderer;
import ruiseki.okmodular.client.render.WrenchOverlayRenderer;
import ruiseki.okmodular.common.block.AbstractPortBlock;
import ruiseki.okmodular.common.block.BlockMachineCasing;
import ruiseki.okmodular.common.block.BlockMachineController;
import ruiseki.okmodular.common.init.MachineryItems;
import ruiseki.okmodular.common.item.ItemFluidCanister;
import ruiseki.okmodular.util.Logger;

/**
 * Client-side module for Machinery.
 * Handles renderers and other client-only features.
 * Port overlays are rendered via ISBRH for optimal performance.
 */
@SideOnly(Side.CLIENT)
public class MachineryClient extends ClientProxyComponent {

    public MachineryClient() {
        super(new MachineryCommon());
    }

    @Override
    public ModBase getMod() {
        return OKModular.instance;
    }

    @Override
    public void registerRenderers() {
        super.registerRenderers();
        AbstractPortBlock.portRendererId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(PortOverlayISBRH.INSTANCE);
        Logger.info("MachineryClient: Registered PortOverlayISBRH with ID " + AbstractPortBlock.portRendererId);
        TexturedItemRenderer.register((ItemFluidCanister) MachineryItems.FLUID_CANISTER.getItem());

        for (Object obj : Block.blockRegistry) {
            Block block = (Block) obj;
            if (block instanceof AbstractPortBlock<?> || block instanceof BlockMachineController
                || block instanceof BlockMachineCasing) {
                Item item = Item.getItemFromBlock(block);
                if (item != null) {
                    MinecraftForgeClient.registerItemRenderer(item, new ItemPortRenderer());
                }
            }
        }
    }

    @Override
    public void registerEventHooks() {
        super.registerEventHooks();
        MinecraftForge.EVENT_BUS.register(new FluidFogHandler());
        MinecraftForge.EVENT_BUS.register(new StructureWandRenderer());
        MinecraftForge.EVENT_BUS.register(new WrenchOverlayRenderer());
    }
}
