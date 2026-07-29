package ruiseki.okmodular.common.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;

import ruiseki.okcore.network.CodecField;
import ruiseki.okcore.network.ExtendedBuffer;
import ruiseki.okcore.network.PacketCodec;
import ruiseki.okmodular.common.tile.StructureTintCache;
import ruiseki.okmodular.util.Logger;

/**
 * Packet to synchronize structure tint colors from server to client.
 * Sent when a structure is formed (to set color) or unformed (to clear color).
 * <p>
 * <b>{@link #decode} checks the buffer before handing it to the framework.</b> This is
 * the only packet in the mod with a {@code @CodecField} list, which makes it the only
 * one that can be handed bytes it cannot read and turn that into a problem:
 * <ul>
 * <li>The framework's list codec reads a 4-byte length first. On a buffer too short for
 * it, the read throws, and the reflective field loop catches the exception and prints
 * the stack trace. <b>Nothing fails and nothing recovers</b> - the packet is silently
 * half-decoded and the log grows. One session produced 698 copies of the same trace
 * and grew the log from 1.2MB to 5.8MB.
 * <li>On a buffer that is long enough but holds something else, the length read returns
 * whatever those four bytes happen to be, and the codec passes it straight to
 * {@code Lists.newArrayListWithExpectedSize}. A garbage length is then an allocation
 * of that size.
 * </ul>
 * Both are ruled out by looking at the buffer first. A length that cannot describe this
 * packet means the bytes belong to something else, so they are dropped.
 * <p>
 * <b>This does not fix why foreign bytes arrive here.</b> That needs the channel's
 * discriminator table, which only exists in a running game. What it does is stop the
 * symptom without guessing, and it keeps a legitimate packet working - the wire format
 * is untouched, and {@code encode} still goes through the framework.
 */
public class PacketStructureTint extends PacketCodec {

    /**
     * Bytes the three scalars take, and so the offset of the list's length.
     * <p>
     * The framework sorts {@code @CodecField}s <b>by name</b>, not by declaration
     * order: {@code clear}(1) + {@code color}(4) + {@code dimensionId}(4).
     */
    private static final int SCALAR_BYTES = 9;

    /** Smallest a real one can be: the scalars plus a list length of zero. */
    private static final int MIN_WIRE_BYTES = SCALAR_BYTES + 4;

    /** Logged once per session. The point is to notice it, not to count it. */
    private static boolean warnedAboutForeignBytes = false;

    @CodecField
    private int dimensionId;

    @CodecField
    private int color;

    @CodecField
    private boolean clear;

    @CodecField
    private List<ChunkCoordinates> positions;

    public PacketStructureTint() {
        this.positions = new ArrayList<>();
    }

    /**
     * Create a packet to set tint color for positions.
     */
    public PacketStructureTint(int dimensionId, int color, List<ChunkCoordinates> positions) {
        this.dimensionId = dimensionId;
        this.color = color;
        this.clear = false;
        this.positions = new ArrayList<>(positions);
    }

    /**
     * Create a packet to clear tint color for positions.
     */
    public static PacketStructureTint createClear(int dimensionId, List<ChunkCoordinates> positions) {
        PacketStructureTint packet = new PacketStructureTint();
        packet.dimensionId = dimensionId;
        packet.color = 0;
        packet.clear = true;
        packet.positions = new ArrayList<>(positions);
        return packet;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    /** Whether a buffer of this size could hold one of these at all. */
    static boolean isDecodable(int readableBytes) {
        return readableBytes >= MIN_WIRE_BYTES;
    }

    /**
     * Whether that list length could have been written by {@link #encode} into a buffer
     * of this size.
     * <p>
     * The bound is deliberately loose - after the length come a class name and, per
     * element, an index and a value, so an element cannot cost less than one byte. It
     * only has to be tight enough that a length read out of foreign bytes cannot become
     * a large allocation.
     */
    static boolean isPlausibleListLength(int listLength, int readableBytes) {
        if (listLength < 0) return false;
        if (listLength == 0) return true;
        return listLength <= readableBytes - MIN_WIRE_BYTES;
    }

    @Override
    public void decode(ExtendedBuffer input) {
        int available = input.readableBytes();
        if (!isDecodable(available)) {
            drop(available, "too short to be one of these");
            return;
        }

        // Absolute get: looks at the length without moving the reader, so a buffer that
        // passes is handed to the framework exactly as it arrived.
        //
        // The order of the two checks matters. An absolute get is bounds-checked against
        // the buffer's capacity, not its writer index, so on a short buffer this would
        // quietly read past what was written instead of failing. isDecodable having
        // passed is what puts bytes 0..12 inside the written region.
        int listLength = input.getInt(input.readerIndex() + SCALAR_BYTES);
        if (!isPlausibleListLength(listLength, available)) {
            drop(available, "list length " + listLength + " cannot fit");
            return;
        }

        super.decode(input);
    }

    /**
     * Leaves the fields alone. Writing a half-read value would be worse than dropping:
     * the client would render a tint it was never sent, and keep it until the next
     * legitimate packet.
     */
    private static void drop(int readableBytes, String why) {
        if (warnedAboutForeignBytes) return;
        warnedAboutForeignBytes = true;
        Logger.warn(
            "Dropped {} bytes handed to PacketStructureTint ({}). Something else is arriving on this"
                + " channel under this packet's discriminator; further occurrences are not logged.",
            readableBytes,
            why);
    }

    @Override
    public void actionClient(World world, EntityPlayer player) {
        if (world == null || world.provider.dimensionId != dimensionId) {
            return;
        }

        if (clear) {
            // Clear colors and trigger re-render
            StructureTintCache.clearAll(world, positions);
        } else {
            // Set colors
            for (ChunkCoordinates pos : positions) {
                StructureTintCache.put(world, pos.posX, pos.posY, pos.posZ, color);
            }
        }

        // Trigger block re-renders
        for (ChunkCoordinates pos : positions) {
            world.markBlockForUpdate(pos.posX, pos.posY, pos.posZ);
        }
    }

    @Override
    public void actionServer(World world, EntityPlayerMP player) {

    }
}
