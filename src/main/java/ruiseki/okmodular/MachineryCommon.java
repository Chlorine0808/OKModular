package ruiseki.okmodular;

import net.minecraftforge.common.MinecraftForge;

import ruiseki.okmodular.common.handler.FluidPhysicsHandler;
import ruiseki.okmodular.common.network.PacketStructureTint;
import ruiseki.omoshiroikamo.core.init.ModBase;
import ruiseki.omoshiroikamo.core.network.PacketHandler;
import ruiseki.omoshiroikamo.core.network.packet.PacketReloadNEI;
import ruiseki.omoshiroikamo.core.proxy.CommonProxyComponent;

/**
 * Modular Machinery Backport module entry point.
 * Provides a flexible multiblock machine system with JSON-based structure
 * definitions.
 */
public class MachineryCommon extends CommonProxyComponent {

    @Override
    public ModBase getMod() {
        return OKModular.instance;
    }

    @Override
    public void registerPacketHandlers(PacketHandler packetHandler) {
        super.registerPacketHandlers(packetHandler);
        packetHandler.register(PacketStructureTint.class);
        // Core packet, additionally registered on this mod's channel so that
        // MachineryModule.reload() can broadcast it through OKModular's handler.
        packetHandler.register(PacketReloadNEI.class);
    }

    @Override
    public void registerEventHooks() {
        super.registerEventHooks();
        MinecraftForge.EVENT_BUS.register(new FluidPhysicsHandler());
    }
}
