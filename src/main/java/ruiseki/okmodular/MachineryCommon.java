package ruiseki.okmodular;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.common.MinecraftForge;

import ruiseki.okcore.init.ModBase;
import ruiseki.okcore.network.PacketBase;
import ruiseki.okcore.network.PacketHandler;
import ruiseki.okcore.proxy.CommonProxyComponent;
import ruiseki.okmodular.common.handler.FluidPhysicsHandler;
import ruiseki.okmodular.common.network.PacketReloadNEI;
import ruiseki.okmodular.common.network.PacketStructureTint;
import ruiseki.okmodular.common.network.PacketToggleSide;

/**
 * Modular Machinery Backport module entry point.
 * Provides a flexible multiblock machine system with JSON-based structure
 * definitions.
 */
public class MachineryCommon extends CommonProxyComponent {

    /**
     * Every packet class this mod puts on its own channel.
     * <p>
     * <b>Sending a packet that is not in this list does nothing.</b> FML resolves the
     * wire discriminator from the class, and an unregistered class has none, so the
     * send fails inside the channel pipeline rather than at the call site. Nothing
     * links sending to registering at compile time, which is why this list is a named
     * constant: {@code PacketRegistrationCoverageTest} checks it against the packet
     * classes that actually exist.
     * <p>
     * Position in the list decides the discriminator, and nothing persists
     * discriminators, so reordering is harmless. Append at the end anyway - it keeps
     * the existing numbers still and makes a diff say only what was added.
     */
    public static final List<Class<? extends PacketBase>> PACKETS = Collections.unmodifiableList(
        Arrays.asList(
            PacketStructureTint.class,
            PacketReloadNEI.class,
            // The wrench's per-side IO toggle. It was missing, so ItemWrench sent this
            // to a channel that had no discriminator for it and the toggle never
            // reached the server. The split brought the sender over from the parent
            // mod's CoreCommon without bringing the registration.
            PacketToggleSide.class));

    @Override
    public ModBase getMod() {
        return OKModular.instance;
    }

    @Override
    public void registerPacketHandlers(PacketHandler packetHandler) {
        super.registerPacketHandlers(packetHandler);
        for (Class<? extends PacketBase> packet : PACKETS) {
            packetHandler.register(packet);
        }
    }

    @Override
    public void registerEventHooks() {
        super.registerEventHooks();
        MinecraftForge.EVENT_BUS.register(new FluidPhysicsHandler());
    }
}
