package ruiseki.okmodular.selftest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// nbtEqual compares recorded values as strings and outputMatchesPayout reads "payoutSucceeded"
// with Boolean.parseBoolean, so the flattened view must name ProcessAgent's keys bare and spell
// setBoolean bytes as true/false. Nested keys also keep their dotted path so nothing is lost when
// two compounds share a key name.
class NbtFlatTest {

    private static NBTTagCompound controller() {
        NBTTagCompound agent = new NBTTagCompound();
        agent.setLong("progress", 7L);
        agent.setBoolean("payoutDecided", true);
        agent.setBoolean("payoutSucceeded", false);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("processAgent", agent);
        root.setBoolean("isFormed", true);
        root.setLong("currentRecipeStartTick", 1234L);
        return root;
    }

    @Test
    @DisplayName("入れ子のキーは裸の名前と点区切りの両方で引ける")
    void nestedKeysAreReachableBareAndDotted() {
        Map<String, String> flat = NbtFlat.flatten(controller());
        assertEquals("7", flat.get("progress"));
        assertEquals("7", flat.get("processAgent.progress"));
        assertEquals("1234", flat.get("currentRecipeStartTick"));
    }

    @Test
    @DisplayName("真偽値のバイトは true / false と書く")
    void booleanBytesReadAsBooleans() {
        Map<String, String> flat = NbtFlat.flatten(controller());
        assertEquals("true", flat.get("payoutDecided"));
        assertEquals("false", flat.get("payoutSucceeded"));
        assertEquals("true", flat.get("isFormed"));
    }

    @Test
    @DisplayName("裸の名前がぶつかったら浅いほうが勝つ")
    void shallowerKeyWinsABareNameClash() {
        NBTTagCompound inner = new NBTTagCompound();
        inner.setInteger("x", 2);
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("x", 1);
        root.setTag("inner", inner);
        Map<String, String> flat = NbtFlat.flatten(root);
        assertEquals("1", flat.get("x"));
        assertEquals("2", flat.get("inner.x"));
    }
}
