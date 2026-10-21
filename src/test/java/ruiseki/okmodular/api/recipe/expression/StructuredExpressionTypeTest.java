package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * 構造化形式の式が、**登録された型すべてで最小 JSON から組める**ことの検証。
 *
 * ============================================
 * パースが通ることでは足りない
 * ============================================
 *
 * 式は 2 通りの書き方がある。文字列（`"tier * 2"`）と、
 * `{"type": "arithmetic", ...}` の構造化形式。後者は
 * `ExpressionsParser` が型名でパーサを引く形なので、**登録し忘れが起こせる**。
 *
 * 登録されていない型名を書くと、パーサは知らない型として扱う。
 * これはデコレータが 6 種まとめて死んだときと同じ構図で、
 * **型が合っているのに何も起きない**。
 *
 * ここでは各型を最小 JSON から組み、**組めたことと、評価が例外を出さないこと**まで見る。
 * 値そのものは型ごとに事情が違うので、値の検証は既存の個別テストに任せる。
 *
 * ============================================
 * 一覧を手で持たない
 * ============================================
 *
 * 期待値は `ExpressionsParser` のソースから**その場で数える**。
 * 手で書いた一覧だと、型を足した人がここを更新しなければ、
 * 「新しい型だけテストが無い」状態が黙って作れてしまう。
 * それはまさにこのテストが防ごうとしている形なので、名簿の側を生かしておく。
 */
@DisplayName("構造化形式の式の型")
public class StructuredExpressionTypeTest {

    /** 型ごとの最小 JSON。ここに無い型が登録されたら、下のテストが落ちる。 */
    private static final Map<String, String> MINIMAL = new LinkedHashMap<>();

    static {
        MINIMAL.put("constant", "{\"type\":\"constant\",\"value\":3}");
        MINIMAL.put("string_literal", "{\"type\":\"string_literal\",\"value\":\"minecraft:stone\"}");
        MINIMAL.put("world_property", "{\"type\":\"world_property\",\"property\":\"tick\"}");
        MINIMAL.put("machine_property", "{\"type\":\"machine_property\",\"property\":\"tier\"}");
        MINIMAL.put("component_tier", "{\"type\":\"component_tier\",\"component\":\"casing\"}");
        MINIMAL.put("nbt", "{\"type\":\"nbt\",\"key\":\"charge\",\"default\":7}");
        MINIMAL.put(
            "nbt_assignment",
            "{\"type\":\"nbt_assignment\",\"key\":\"charge\",\"value\":{\"type\":\"constant\",\"value\":1}}");
        MINIMAL.put(
            "arithmetic",
            "{\"type\":\"arithmetic\",\"operation\":\"+\"," + "\"left\":{\"type\":\"constant\",\"value\":2},"
                + "\"right\":{\"type\":\"constant\",\"value\":3}}");
        MINIMAL.put(
            "map_range",
            "{\"type\":\"map_range\",\"input\":{\"type\":\"constant\",\"value\":5},"
                + "\"minIn\":0,\"maxIn\":10,\"minOut\":0,\"maxOut\":100}");
    }

    @Test
    @DisplayName("登録された型はすべて最小 JSON から組める")
    public void test全部の型が組める() {
        List<String> untested = new ArrayList<>();

        for (String type : registeredTypes()) {
            if (!MINIMAL.containsKey(type)) {
                untested.add(type);
                continue;
            }
            assertNotNull(parse(MINIMAL.get(type)), type + " が null になった");
        }

        assertTrue(
            untested.isEmpty(),
            untested + " が登録されているのに最小 JSON が無い。" + "型を足したときここも足すこと。足さないと、その型だけ一度も組まれないまま出荷される");
    }

    @Test
    @DisplayName("最小 JSON の一覧に幽霊が居ない")
    public void test消えた型が残っていない() {
        Set<String> registered = registeredTypes();

        for (String type : MINIMAL.keySet()) {
            assertTrue(registered.contains(type), type + " はもう登録されていない。MINIMAL から消すこと");
        }
    }

    @Test
    @DisplayName("組んだ式は機械が無くても評価できる")
    public void test機械が無くても落ちない() {
        // NEI はレシピを機械の無い文脈で描く。そこで例外が出ると
        // 「レシピ一覧が開かない」になるので、値ではなく**落ちないこと**を見る。
        for (Map.Entry<String, String> entry : MINIMAL.entrySet()) {
            IExpression expression = parse(entry.getValue());
            assertNotNull(expression, entry.getKey());
            expression.evaluateDouble(null);
        }
    }

    @Test
    @DisplayName("入れ子の式が実際に効いている")
    public void test入れ子が畳まれる() {
        // arithmetic と map_range は子の式を ExpressionsParser に投げ返す。
        // 投げ返しが壊れると子が定数 0 になり、答えは出るが意味が消える。
        assertEquals(5.0, parse(MINIMAL.get("arithmetic")).evaluateDouble(null), 1e-9, "2 + 3 になっていない");
        assertEquals(50.0, parse(MINIMAL.get("map_range")).evaluateDouble(null), 1e-9, "0-10 の 5 が 0-100 の 50 にならない");
    }

    private static IExpression parse(String json) {
        JsonObject object = new JsonParser().parse(json)
            .getAsJsonObject();
        return ExpressionsParser.parse(object);
    }

    /** `ExpressionsParser` が `register("x", ...)` している型名。 */
    private static Set<String> registeredTypes() {
        Set<String> found = new TreeSet<>();
        String source = JavaSource
            .stripComments(JavaSource.read(JavaSource.MAIN.resolve("api/recipe/expression/ExpressionsParser.java")));
        Matcher hits = Pattern.compile("register\\(\\s*\"([a-z_0-9]+)\"")
            .matcher(source);

        while (hits.find()) found.add(hits.group(1));
        assertTrue(found.size() >= 9, "登録された型が 9 個未満。走査が壊れている: " + found);
        return found;
    }
}
