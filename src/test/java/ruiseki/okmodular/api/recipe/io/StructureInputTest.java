package ruiseki.okmodular.api.recipe.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ruiseki.okmodular.api.recipe.parser.InputNBTRegistry;
import ruiseki.okmodular.api.recipe.parser.InputParserRegistry;

/**
 * 構造 IO の入力側の検証。
 *
 * ============================================
 * ここで見られないもの
 * ============================================
 *
 * 実際の照合はワールドのブロックを読むので、ゲーム外では動かせない
 * （`BlockInput` にもその範囲のテストは無い）。座標の計算そのものは
 * `StructureCellLocatorTest` と `StructurePatternTest` が値で縛っている。
 *
 * ============================================
 * なぜ登録名と type の一致を見るのか
 * ============================================
 *
 * `BlockOutput` は JSON に `"type": "block"` と書き出すのに、
 * `OutputParserRegistry` への登録名は `symbol` になっている。**今それが通っているのは
 * 登録名をキーとして探すフォールバックに `"symbol"` が偶然引っかかるから**で、
 * 書いた type で引けているわけではない。
 *
 * 同じ形を増やさないために、**書き出した type でレジストリから戻ってくること**を
 * ここで縛る。往復しない型名は「docs どおりに書いたのに読まれない」として届く。
 */
@DisplayName("構造 IO 入力")
public class StructureInputTest {

    private static final String JSON = ("{ 'type': 'structure', 'pattern': 'altar_core', 'symbol': 'S',"
        + " 'amount': 2, 'consume': true, 'index': 3, 'pertick': 5 }").replace('\'', '"');

    private static StructureInput parse() {
        return (StructureInput) StructureInput.fromJson(
            new JsonParser().parse(JSON)
                .getAsJsonObject());
    }

    @Test
    @DisplayName("JSON から参照先と対象シンボルを読む")
    public void testJSONを読む() {
        StructureInput input = parse();

        assertEquals("altar_core", input.getPatternName(), "パターン名が読まれていない");
        assertEquals('S', input.getSymbol(), "アンカーになるシンボルが読まれていない");
        assertEquals(2, input.getAmount());
        assertTrue(input.isConsume());
        assertEquals(3, input.getIndex());
        assertEquals(5, input.getInterval());
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
            StructureInput.class,
            InputParserRegistry.parse(written),
            "書き出した type でレジストリが引けない。登録名と綴りが揃っていない");
    }

    @Test
    @DisplayName("NBT で往復する")
    public void testNBT往復() {
        NBTTagCompound nbt = new NBTTagCompound();
        parse().writeToNBT(nbt);

        assertEquals("structure", nbt.getString("id"), "NBT の id が登録名と揃っていない。稼働中に保存すると復元できない");

        StructureInput restored = assertInstanceOf(StructureInput.class, InputNBTRegistry.read(nbt));
        assertEquals("altar_core", restored.getPatternName());
        assertEquals('S', restored.getSymbol());
        assertEquals(2, restored.getAmount());
        assertTrue(restored.isConsume());
        assertEquals(3, restored.getIndex());
        assertEquals(5, restored.getInterval());
    }

    /**
     * `BlockOutput.copy` は amount 式・interval・index を落としていた前科がある
     * （バッチや毎 tick の指定が黙って素の 1 回に戻っていた）。同じ穴を開けない。
     */
    @Test
    @DisplayName("copy が指定を落とさない")
    public void testコピーで失われない() {
        StructureInput copy = (StructureInput) parse().copy(3);

        assertEquals("altar_core", copy.getPatternName());
        assertEquals('S', copy.getSymbol());
        assertEquals(6, copy.getAmount(), "バッチ倍率が amount に効いていない");
        assertTrue(copy.isConsume());
        assertEquals(3, copy.getIndex(), "index が落ちている");
        assertEquals(5, copy.getInterval(), "interval が落ちている");
    }
}
