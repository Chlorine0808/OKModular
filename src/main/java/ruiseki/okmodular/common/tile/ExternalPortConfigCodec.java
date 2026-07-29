package ruiseki.okmodular.common.tile;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChunkCoordinates;

import ruiseki.okmodular.api.enums.EnumIO;
import ruiseki.okmodular.api.modular.IPortType;

/**
 * Reads and writes a controller's external port configurations to NBT.
 *
 * <h2>Why this is its own class</h2>
 *
 * A {@code TEMachineController} cannot be constructed in a unit test - the mock
 * world available to tests fails in its constructor, which is why the one tile
 * entity test in this source tree is disabled. {@link NBTTagCompound} needs no
 * world, so keeping the serialization here is what makes it testable at all.
 *
 * <h2>Three formats live on disk</h2>
 *
 * <pre>
 * {"types": [{"type": "FLUID", "io": "OUTPUT"}]}   current: written by name()
 * {"types": [{"type": 1b,      "io": 2b}]}         older:   enum ordinals as bytes
 * {"io": 1b}                                       oldest:  no type list, Type.ITEM implied
 * </pre>
 *
 * Writing ordinals is what made {@code IPortType.Type} unable to accept a new
 * constant anywhere but the end: the ordinal <em>was</em> the stored value, so
 * inserting one made every save after it decode to the wrong type. Names do not
 * have that problem, which is the point of the current format.
 *
 * <p>
 * The ordinal tables below are therefore <strong>frozen literals, not
 * {@code values()}</strong>. Reading old saves through {@code values()} would put
 * the constraint straight back: insert a constant and the table shifts with it,
 * so bytes already on disk would start decoding to the neighbouring type. These
 * arrays record what the ordinals meant when they were written, and a new
 * constant does not belong in them.
 *
 * <h2>Bad input is dropped, never thrown</h2>
 *
 * The generic {@code @NBTPersist} enum handler decodes with {@code Enum.valueOf},
 * which throws on a name it does not know. Throwing while a tile entity loads
 * costs the chunk, so an entry that cannot be decoded is skipped instead.
 */
public final class ExternalPortConfigCodec {

    public static final String KEY = "externalPortConfigs";

    private static final String KEY_TYPES = "types";
    private static final String KEY_TYPE = "type";
    private static final String KEY_IO = "io";

    // NBT tag type ids, as NBTTagCompound.hasKey takes them. 99 matches any numeric tag.
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_ANY_NUMBER = 99;

    /**
     * What a stored {@code type} byte meant. Frozen - see the class comment.
     * Append nothing here; new constants are only ever written by name.
     */
    // spotless:off
    private static final IPortType.Type[] LEGACY_TYPES = {
        IPortType.Type.ITEM,        // 0
        IPortType.Type.FLUID,       // 1
        IPortType.Type.ENERGY,      // 2
        IPortType.Type.MANA,        // 3
        IPortType.Type.GAS,         // 4
        IPortType.Type.ESSENTIA,    // 5
        IPortType.Type.VIS,         // 6
        IPortType.Type.BLOCK,       // 7
        IPortType.Type.NONE,        // 8
    };

    /** What a stored {@code io} byte meant. Frozen for the same reason. */
    private static final EnumIO[] LEGACY_IO = {
        EnumIO.NONE,                // 0
        EnumIO.INPUT,               // 1
        EnumIO.OUTPUT,              // 2
        EnumIO.BOTH,                // 3
    };
    // spotless:on

    private ExternalPortConfigCodec() {}

    /**
     * Replaces the contents of {@code into} with what {@code nbt} holds.
     *
     * The map is filled in place because the controller owns it as a final field.
     * Anything undecodable is left out rather than reported - a save that predates
     * a format is the normal case here, not an error.
     */
    public static void read(NBTTagCompound nbt, Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> into) {
        into.clear();
        if (nbt == null || !nbt.hasKey(KEY, TAG_LIST)) return;

        NBTTagList portList = nbt.getTagList(KEY, TAG_COMPOUND);
        for (int i = 0; i < portList.tagCount(); i++) {
            NBTTagCompound portTag = portList.getCompoundTagAt(i);
            Map<IPortType.Type, EnumIO> typeMap = readTypes(portTag);
            if (typeMap.isEmpty()) continue;

            into.put(
                new ChunkCoordinates(portTag.getInteger("x"), portTag.getInteger("y"), portTag.getInteger("z")),
                typeMap);
        }
    }

    private static Map<IPortType.Type, EnumIO> readTypes(NBTTagCompound portTag) {
        Map<IPortType.Type, EnumIO> typeMap = new HashMap<>();

        if (portTag.hasKey(KEY_TYPES, TAG_LIST)) {
            NBTTagList typeList = portTag.getTagList(KEY_TYPES, TAG_COMPOUND);
            for (int i = 0; i < typeList.tagCount(); i++) {
                NBTTagCompound typeTag = typeList.getCompoundTagAt(i);
                IPortType.Type type = decode(typeTag, KEY_TYPE, IPortType.Type.values(), LEGACY_TYPES);
                EnumIO io = decode(typeTag, KEY_IO, EnumIO.values(), LEGACY_IO);
                if (type != null && io != null) {
                    typeMap.put(type, io);
                }
            }
            return typeMap;
        }

        // No type list: the oldest format, which only knew about item ports.
        if (portTag.hasKey(KEY_IO)) {
            EnumIO io = decode(portTag, KEY_IO, EnumIO.values(), LEGACY_IO);
            if (io != null) {
                typeMap.put(IPortType.Type.ITEM, io);
            }
        }
        return typeMap;
    }

    /**
     * Decodes one enum, accepting either format.
     *
     * @param byName          every constant that exists now, matched against a stored name
     * @param byLegacyOrdinal what the ordinals meant when bytes were written; an ordinal
     *                        past the end of this belongs to a format this build predates
     */
    private static <E extends Enum<E>> E decode(NBTTagCompound tag, String key, E[] byName, E[] byLegacyOrdinal) {
        if (tag.hasKey(key, TAG_STRING)) {
            String name = tag.getString(key);
            for (E candidate : byName) {
                if (candidate.name()
                    .equals(name)) {
                    return candidate;
                }
            }
            return null;
        }

        if (tag.hasKey(key, TAG_ANY_NUMBER)) {
            int ordinal = tag.getByte(key) & 0xFF;
            if (ordinal < byLegacyOrdinal.length) {
                return byLegacyOrdinal[ordinal];
            }
        }
        return null;
    }
}
