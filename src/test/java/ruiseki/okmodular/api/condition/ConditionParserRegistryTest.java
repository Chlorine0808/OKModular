package ruiseki.okmodular.api.condition;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    // tile_nbt の廃止と、その代替
    // ========================================

    @Test
    @DisplayName("【廃止】tile_nbt は解決されない")
    public void testTileNbtは廃止された() {
        // 独自の比較パーサを持ち、階層パス・別ブロック・!= のいずれも書けなかった。
        // expression + nbt() が上位互換なので削除した
        assertNull(parse("{ \"tile_nbt\": \"energy >= 1000\" }"));
        assertNull(parse("{ \"key\": \"energy\", \"op\": \"greater_or_equal\", \"value\": 1000 }"));
    }

    @Test
    @DisplayName("【代替】expression + nbt() で書ける")
    public void testNbt関数で代替できる() {
        assertNotNull(parse("{ \"expression\": \"nbt('energy') >= 1000\" }"), "式条件として成立するべき");
    }

    @Test
    @DisplayName("【代替】has_nbt() でキーの存在を条件にできる")
    public void testHasNbtで存在確認できる() {
        // tile_nbt は「キーが無ければ false」だったが、nbt() は 0 を返すので
        // `<= 100` のような比較では逆の結果になる。has_nbt() がその差を埋める
        assertNotNull(parse("{ \"expression\": \"has_nbt('heat') && nbt('heat') <= 100\" }"));
    }

    @Test
    @DisplayName("【代替】nbt() は階層パスと別ブロックも書ける（tile_nbt では不可能だった）")
    public void testNbtは表現力が高い() {
        assertNotNull(parse("{ \"expression\": \"nbt('customData.heat') >= 50\" }"), "階層パス");
        assertNotNull(parse("{ \"expression\": \"nbt('S', 'stored') >= 50\" }"), "別ブロック");
        assertNotNull(parse("{ \"expression\": \"nbt('mode') != 3\" }"), "!= 比較");
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
