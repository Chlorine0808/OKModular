package ruiseki.okmodular.api.recipe.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ruiseki.okmodular.api.recipe.parser.OutputNBTRegistry;
import ruiseki.okmodular.api.recipe.parser.OutputParserRegistry;

/**
 * 構造 IO の出力側の検証。
 *
 * `StructureInputTest` と対になる。**とくに型名の往復**は入力側だけ縛っても意味が無い —
 * `BlockOutput` が「書き出す type と登録名が違う」形をしているのは出力側だからで、
 * 同じ穴が開くとしたらこちらにも開く。
 */
@DisplayName("構造 IO 出力")
public class StructureOutputTest {

    private static final String JSON = ("{ 'type': 'structure', 'pattern': 'altar_core', 'symbol': 'S',"
        + " 'amount': 2, 'index': 3, 'pertick': 5 }").replace('\'', '"');

    private static StructureOutput parse() {
        return (StructureOutput) StructureOutput.fromJson(
            new JsonParser().parse(JSON)
                .getAsJsonObject());
    }

    @Test
    @DisplayName("JSON から参照先と対象シンボルを読む")
    public void testJSONを読む() {
        StructureOutput output = parse();

        assertEquals("altar_core", output.getPatternName());
        assertEquals('S', output.getSymbol());
        assertEquals(2, output.getAmount());
        assertEquals(3, output.getIndex());
        assertEquals(5, output.getInterval());
    }

    @Test
    @DisplayName("書き出した type でレジストリから戻ってくる")
    public void test型名が往復する() {
        JsonObject written = new JsonObject();
        parse().write(written);

        assertEquals(
            "structure",
            written.get("type")
                .getAsString());
        assertInstanceOf(
            StructureOutput.class,
            OutputParserRegistry.parse(written),
            "書き出した type でレジストリが引けない。登録名と綴りが揃っていない");
    }

    @Test
    @DisplayName("NBT で往復する")
    public void testNBT往復() {
        NBTTagCompound nbt = new NBTTagCompound();
        parse().writeToNBT(nbt);

        assertEquals("structure", nbt.getString("id"), "NBT の id が登録名と揃っていない。稼働中に保存すると復元できない");

        StructureOutput restored = assertInstanceOf(StructureOutput.class, OutputNBTRegistry.read(nbt));
        assertEquals("altar_core", restored.getPatternName());
        assertEquals('S', restored.getSymbol());
        assertEquals(2, restored.getAmount());
        assertEquals(3, restored.getIndex());
        assertEquals(5, restored.getInterval());
    }

    @Test
    @DisplayName("copy が指定を落とさない")
    public void testコピーで失われない() {
        StructureOutput copy = (StructureOutput) parse().copy(3);

        assertEquals("altar_core", copy.getPatternName());
        assertEquals('S', copy.getSymbol());
        assertEquals(6, copy.getAmount(), "バッチ倍率が amount に効いていない");
        assertEquals(3, copy.getIndex(), "index が落ちている");
        assertEquals(5, copy.getInterval(), "interval が落ちている");
    }
}
