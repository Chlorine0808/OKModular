package ruiseki.okmodular.selftest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;

/** Flattens NBT to strings for nbtEqual: dotted paths plus bare leaf names, the shallower key first. */
final class NbtFlat {

    private NbtFlat() {}

    static Map<String, String> flatten(NBTTagCompound root) {
        Map<String, String> out = new LinkedHashMap<>();
        Deque<Object[]> queue = new ArrayDeque<>();
        queue.add(new Object[] { "", root });
        // Breadth first, so a shallower key claims the bare name.
        while (!queue.isEmpty()) {
            Object[] next = queue.poll();
            String prefix = (String) next[0];
            NBTTagCompound c = (NBTTagCompound) next[1];
            for (String key : c.func_150296_c()) {
                NBTBase tag = c.getTag(key);
                String path = prefix + key;
                if (tag instanceof NBTTagCompound) {
                    queue.add(new Object[] { path + ".", tag });
                    continue;
                }
                String v = value(tag);
                out.put(path, v);
                if (!out.containsKey(key)) out.put(key, v);
            }
        }
        return out;
    }

    private static String value(NBTBase tag) {
        if (tag instanceof NBTTagByte) {
            // setBoolean writes a byte; spell 0 and 1 the way Boolean.parseBoolean reads them.
            byte b = ((NBTTagByte) tag).func_150290_f();
            return b == 0 ? "false" : b == 1 ? "true" : Byte.toString(b);
        }
        if (tag instanceof NBTTagString) return ((NBTTagString) tag).func_150285_a_();
        if (tag instanceof NBTBase.NBTPrimitive) {
            NBTBase.NBTPrimitive p = (NBTBase.NBTPrimitive) tag;
            double d = p.func_150286_g();
            return d == Math.rint(d) && !Double.isInfinite(d) ? Long.toString(p.func_150291_c()) : Double.toString(d);
        }
        return tag.toString();
    }
}
