package ruiseki.okmodular.api.recipe.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ruiseki.okmodular.api.recipe.parser.InputNBTRegistry;
import ruiseki.okmodular.api.recipe.parser.InputParserRegistry;
import ruiseki.okmodular.structure.pattern.StructurePatternLoader;

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

    // ============================================
    // 何が置かれるか決まらないパターンは入力に使えない
    // ============================================
    //
    // `chance` や重み表を持つパターンは、**置くたびに違う形になる**のが目的です。
    // それを照合に使うと「何と一致すればいいのか」が定義できません。
    //
    // 実行時に静かに一致しないだけだと、書いた人には「レシピが動かない」としか見えないので、
    // レシピを読む時点で断ります。パターンはレシピより先に読まれるので、ここで判定できます
    // （`MachineryModule.postInit` にその順序の理由が書いてあります）。

    @TempDir
    File patternDir;

    @AfterEach
    public void tearDown() {
        // ローダは singleton。ここで読んだパターンを残すと他のテストから見えてしまう。
        StructurePatternLoader.getInstance()
            .clear();
    }

    private void loadPattern(String json) {
        try {
            Files.write(
                new File(patternDir, "p.json").toPath(),
                json.replace('\'', '"')
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        StructurePatternLoader.getInstance()
            .loadFrom(patternDir);
    }

    private static StructureInput parseNaming(String patternName) {
        return (StructureInput) StructureInput.fromJson(
            new JsonParser()
                .parse(("{ 'type': 'structure', 'pattern': '" + patternName + "', 'symbol': 'S' }").replace('\'', '"'))
                .getAsJsonObject());
    }

    @Test
    @DisplayName("確率で欠けるパターンを入力に使えない")
    public void test非決定なパターンは入力に使えない() {
        loadPattern(
            "{ 'name': 'speckled', 'mappings': { 'S': { 'block': 'minecraft:stone', 'chance': 0.5 } },"
                + " 'layers': [ ['S'] ] }");

        IllegalArgumentException refused = assertThrows(
            IllegalArgumentException.class,
            () -> parseNaming("speckled"),
            "置くたびに形が変わるパターンを照合に使えてしまっている");

        assertTrue(
            refused.getMessage()
                .contains("speckled"),
            "どのパターンが問題なのか message に無い: " + refused.getMessage());
    }

    @Test
    @DisplayName("重み表を持つパターンも入力に使えない")
    public void test重み表のパターンは入力に使えない() {
        loadPattern(
            "{ 'name': 'veined', 'mappings': { 'S': { 'weights': { 'minecraft:iron_ore': 1,"
                + " 'minecraft:gold_ore': 1 } } }, 'layers': [ ['S'] ] }");

        assertThrows(IllegalArgumentException.class, () -> parseNaming("veined"), "中身が振れるパターンを照合に使えてしまっている");
    }

    @Test
    @DisplayName("形の決まったパターンは今までどおり使える")
    public void test決定的なパターンは通る() {
        loadPattern("{ 'name': 'solid', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] }");

        assertEquals("solid", parseNaming("solid").getPatternName());
    }

    @Test
    @DisplayName("読めていないパターン名はここでは断らない")
    public void test未知のパターンは断らない() {
        // パターンの読み込みに失敗した場合、その報告は errors.txt の担当。
        // ここで落とすと、原因（パターン側のエラー）ではなく結果（レシピが読めない）だけが出る。
        assertEquals("never_loaded", parseNaming("never_loaded").getPatternName());
    }
}
