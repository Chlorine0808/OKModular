package ruiseki.okmodular.api.condition;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * ConditionParserRegistry が受け付ける記述形式の検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * DecoratorParser と同じネスト形式問題を抱えていた。
 * { "offset": { "dx": 0, ... } } のようにキーで型を名指しする形式が
 * どの detector にも当たらず、parse が null を返していた。
 * 呼び出し側（RequirementDecorator）はその null をそのまま抱え込み、
 * 実行時に isConditionMet で NPE になる — レシピ名の付かない形で。
 *
 * nand / nor は detector を持たない register() で登録されていたため、
 * JSON_FORMAT.md が「対応している」と書いているのに "type" を明示しない限り
 * 解決できなかった。
 *
 * ============================================
 */
@DisplayName("Condition パーサ: 記述形式")
public class ConditionParserRegistryTest {

    @BeforeAll
    public static void setUpAll() {
        Conditions.registerDefaults();
    }

    private static ICondition parse(String json) {
        return ConditionParserRegistry.parse(
            new JsonParser().parse(json)
                .getAsJsonObject());
    }

    // ========================================
    // ネスト形式
    // ========================================

    @Test
    @DisplayName("【ネスト】comparison がキー名から解決される")
    public void testネスト形式のComparison() {
        ICondition result = parse("{ \"comparison\": { \"left\": 10, \"operator\": \">\", \"right\": 5 } }");
        assertInstanceOf(ComparisonCondition.class, result);
    }

    @Test
    @DisplayName("【ネスト】offset がキー名から解決される")
    public void testネスト形式のOffset() {
        ICondition result = parse(
            "{ \"offset\": { \"dx\": 0, \"dy\": -1, \"dz\": 0, \"condition\": { \"block\": \"minecraft:grass\" } } }");
        assertInstanceOf(OffsetCondition.class, result);
    }

    @Test
    @DisplayName("【回帰防止】not は値が単一オブジェクトでもネスト宣言と誤認されない")
    public void testNotはネストと誤認されない() {
        // "not" は登録済みの型名で値もオブジェクト。内側 { "dimension": 0 } は
        // not の detector を満たさないので、外側が not として読まれるべき
        ICondition result = parse("{ \"not\": { \"dimension\": 0 } }");
        assertInstanceOf(OpNot.class, result);
    }

    // ========================================
    // 論理演算子
    // ========================================

    @Test
    @DisplayName("【論理】nand がキーから推論される")
    public void testNandが推論される() {
        ICondition result = parse("{ \"nand\": [ { \"dimension\": 0 }, { \"weather\": \"clear\" } ] }");
        assertInstanceOf(OpNand.class, result);
    }

    @Test
    @DisplayName("【論理】nor がキーから推論される")
    public void testNorが推論される() {
        ICondition result = parse("{ \"nor\": [ { \"dimension\": 0 }, { \"weather\": \"rain\" } ] }");
        assertInstanceOf(OpNor.class, result);
    }

    @Test
    @DisplayName("【論理】and / or / xor も従来どおり推論される")
    public void testAndOrXorも推論される() {
        assertInstanceOf(OpAnd.class, parse("{ \"and\": [ { \"dimension\": 0 } ] }"));
        assertInstanceOf(OpOr.class, parse("{ \"or\": [ { \"dimension\": 0 } ] }"));
        assertInstanceOf(OpXor.class, parse("{ \"xor\": [ { \"dimension\": 0 } ] }"));
    }

    // ========================================
    // tile_nbt の 2 形式
    // ========================================

    @Test
    @DisplayName("【tile_nbt】shorthand が key / op / value に分解される")
    public void testTileNbtのShorthand() {
        ICondition result = parse("{ \"tile_nbt\": \"energy >= 1000\" }");
        assertInstanceOf(TileNbtCondition.class, result);

        JsonObject written = new JsonObject();
        result.write(written);
        assertEquals(
            "energy",
            written.get("key")
                .getAsString());
        assertEquals(
            "greater_or_equal",
            written.get("op")
                .getAsString(),
            ">= が > + 余りの = として読まれていないべき");
        assertEquals(
            1000.0,
            written.get("value")
                .getAsDouble());
    }

    @Test
    @DisplayName("【tile_nbt】2 文字演算子が 1 文字として読まれない")
    public void testTileNbtの2文字演算子() {
        JsonObject lessOrEqual = new JsonObject();
        parse("{ \"tile_nbt\": \"heat <= 500\" }").write(lessOrEqual);
        assertEquals(
            "less_or_equal",
            lessOrEqual.get("op")
                .getAsString());

        JsonObject equal = new JsonObject();
        parse("{ \"tile_nbt\": \"stage == 3\" }").write(equal);
        assertEquals(
            "equal",
            equal.get("op")
                .getAsString());
    }

    @Test
    @DisplayName("【tile_nbt】1 文字演算子も読める")
    public void testTileNbtの1文字演算子() {
        JsonObject written = new JsonObject();
        parse("{ \"tile_nbt\": \"energy > 100\" }").write(written);
        assertEquals(
            "greater_than",
            written.get("op")
                .getAsString());
        assertEquals(
            100.0,
            written.get("value")
                .getAsDouble());
    }

    @Test
    @DisplayName("【tile_nbt】spelled-out 形式も従来どおり読める")
    public void testTileNbtのKeyOpValue形式() {
        ICondition result = parse("{ \"key\": \"energy\", \"op\": \"greater_or_equal\", \"value\": 1000 }");
        assertInstanceOf(TileNbtCondition.class, result);
    }

    @Test
    @DisplayName("【tile_nbt】比較対象が数値でなければ弾く")
    public void testTileNbtの不正なshorthand() {
        // parse は例外をログに変えて null を返す既存挙動
        assertNull(parse("{ \"tile_nbt\": \"energy >= たくさん\" }"));
        assertNull(parse("{ \"tile_nbt\": \"演算子がない\" }"));
    }

    // ========================================
    // 従来の推論
    // ========================================

    @Test
    @DisplayName("平坦なプロパティからの推論は従来どおり")
    public void test平坦推論() {
        assertInstanceOf(DimensionCondition.class, parse("{ \"dimension\": 0 }"));
        assertInstanceOf(BiomeCondition.class, parse("{ \"biome\": \"Plains\" }"));
        assertInstanceOf(WeatherCondition.class, parse("{ \"weather\": \"rain\" }"));
    }

    @Test
    @DisplayName("未知の type 名でもプロパティから推論する")
    public void test未知のType名でもプロパティから推論する() {
        assertInstanceOf(DimensionCondition.class, parse("{ \"type\": \"no_such_condition\", \"dimension\": 0 }"));
    }

    @Test
    @DisplayName("解決できない条件は null を返す")
    public void test解決できなければnull() {
        assertNull(parse("{ \"no_such_property\": 1 }"));
    }
}
