package ruiseki.okmodular.common.command;

import net.minecraft.nbt.NBTTagCompound;

/**
 * How {@code /okmodular hand} writes out the held item, in the spelling a recipe uses.
 */
final class HeldItemDescription {

    private HeldItemDescription() {}

    static String itemLine(String registryName, int meta, int count) {
        return registryName + ":" + meta + " x" + count;
    }

    /** @return the tag as text, or null when there is no tag or it is empty */
    static String nbtLine(NBTTagCompound tag) {
        if (tag == null || tag.hasNoTags()) return null;
        return tag.toString();
    }
}
