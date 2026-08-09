package ruiseki.okmodular.api.condition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * 条件が**両方の答えを返せる**ことの検証。
 *
 * ============================================
 * 代償の無いチェックはチェックではない
 * ============================================
 *
 * 常に true を返す条件は、書いた人からは「条件が通っている」に見える。
 * 実際には**何も判定していない**が、判定していないことを示す出力がどこにも無い。
 *
 * 「動いている」と「条件が効いている」の区別がつかないのは、
 * デコレータが 6 種死んでいたときと同じ構図。あれは確率が低いのと区別できず、
 * こちらは条件が満たされているのと区別できない。
 *
 * だから each 条件について **true と false の両方を実際に出させる**。
 * 片方しか出ないなら、その条件は定数と同じ。
 *
 * ============================================
 * ゲームの外で踏めないもの
 * ============================================
 *
 * `block` は `GameRegistry` からブロックを引く。`biome` / `dimension` / `weather` は
 * 本物の `World` の状態を読む。どれもゲームの外では組めない（D-2）。
 *
 * 諦めるのではなく**名指しで残す**。ここに並んでいるものが実機チェックの対象で、
 * 並んでいないものを実機で確かめているなら、それはテストの穴の証拠。
 */
@DisplayName("条件が両方の答えを返す")
public class ConditionBothAnswersTest {

    /**
     * ゲームの外では両方の答えを出させられない条件。**理由つきで**残す。
     *
     * <pre>
     * block / block_below  GameRegistry からブロックを引く
     * dimension            World の provider を読む
     * biome / pattern      World の biome を読む
     * weather              World の天候を読む
     * offset               別座標の World 状態を読む
     * </pre>
     */
    private static final List<String> IN_GAME_ONLY = Arrays
        .asList("block", "block_below", "dimension", "biome", "pattern", "weather", "offset");

    private static final String TRUE = "{\"expression\":\"1 > 0\"}";
    private static final String FALSE = "{\"expression\":\"1 < 0\"}";

    /** ゲーム無しで両方の答えを出せる条件と、その最小 JSON（true になる形 / false になる形）。 */
    private static final Map<String, String[]> PURE = new LinkedHashMap<>();

    static {
        PURE.put("expression", new String[] { "{\"expression\":\"1 > 0\"}", "{\"expression\":\"1 < 0\"}" });
        PURE.put(
            "comparison",
            new String[] { "{\"left\":\"2\",\"operator\":\">\",\"right\":\"1\"}",
                "{\"left\":\"1\",\"operator\":\">\",\"right\":\"2\"}" });
        PURE.put(
            "and",
            new String[] { "{\"and\":[" + TRUE + "," + TRUE + "]}", "{\"and\":[" + TRUE + "," + FALSE + "]}" });
        PURE.put(
            "or",
            new String[] { "{\"or\":[" + FALSE + "," + TRUE + "]}", "{\"or\":[" + FALSE + "," + FALSE + "]}" });
        PURE.put("not", new String[] { "{\"not\":" + FALSE + "}", "{\"not\":" + TRUE + "}" });
        PURE.put(
            "nand",
            new String[] { "{\"nand\":[" + TRUE + "," + FALSE + "]}", "{\"nand\":[" + TRUE + "," + TRUE + "]}" });
        PURE.put(
            "nor",
            new String[] { "{\"nor\":[" + FALSE + "," + FALSE + "]}", "{\"nor\":[" + TRUE + "," + FALSE + "]}" });
        PURE.put(
            "xor",
            new String[] { "{\"xor\":[" + TRUE + "," + FALSE + "]}", "{\"xor\":[" + TRUE + "," + TRUE + "]}" });
    }

    @BeforeAll
    static void 登録しておく() {
        Conditions.registerDefaults();
    }

    @Test
    @DisplayName("純粋な条件は true と false の両方を返す")
    public void test片方しか返さない条件が無い() {
        List<String> constant = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : PURE.entrySet()) {
            ICondition yes = parse(entry.getValue()[0]);
            ICondition no = parse(entry.getValue()[1]);
            assertNotNull(yes, entry.getKey() + " の true 側が解決できない");
            assertNotNull(no, entry.getKey() + " の false 側が解決できない");

            ConditionContext context = new ConditionContext(null, 0, 0, 0);
            if (!yes.isMet(context) || no.isMet(context)) constant.add(entry.getKey());
        }

        assertTrue(constant.isEmpty(), constant + " が片方の答えしか返さない。条件の形をした定数で、書いた側からは通っているようにしか見えない");
    }

    @Test
    @DisplayName("論理演算子の真理値表が正しい")
    public void test真理値表() {
        ConditionContext context = new ConditionContext(null, 0, 0, 0);

        assertTrue(parse("{\"and\":[" + TRUE + "," + TRUE + "]}").isMet(context), "and(T,T)");
        assertFalse(parse("{\"and\":[" + TRUE + "," + FALSE + "]}").isMet(context), "and(T,F)");
        assertTrue(parse("{\"or\":[" + FALSE + "," + TRUE + "]}").isMet(context), "or(F,T)");
        assertFalse(parse("{\"or\":[" + FALSE + "," + FALSE + "]}").isMet(context), "or(F,F)");

        // nand / nor / xor は and / or の否定として書かれることが多く、綴りを取り違えても
        // 片方の入力では気づけない。両方の入力で見る。
        assertTrue(parse("{\"nand\":[" + TRUE + "," + FALSE + "]}").isMet(context), "nand(T,F)");
        assertFalse(parse("{\"nand\":[" + TRUE + "," + TRUE + "]}").isMet(context), "nand(T,T)");
        assertTrue(parse("{\"nor\":[" + FALSE + "," + FALSE + "]}").isMet(context), "nor(F,F)");
        assertFalse(parse("{\"nor\":[" + TRUE + "," + FALSE + "]}").isMet(context), "nor(T,F)");
        assertTrue(parse("{\"xor\":[" + TRUE + "," + FALSE + "]}").isMet(context), "xor(T,F)");
        assertFalse(parse("{\"xor\":[" + TRUE + "," + TRUE + "]}").isMet(context), "xor(T,T)");
        assertFalse(parse("{\"xor\":[" + FALSE + "," + FALSE + "]}").isMet(context), "xor(F,F)");
    }

    @Test
    @DisplayName("登録された条件がすべてどちらかの一覧に載っている")
    public void test取りこぼした条件が無い() {
        List<String> unaccounted = new ArrayList<>();

        for (String type : registeredTypes()) {
            if (PURE.containsKey(type) || IN_GAME_ONLY.contains(type)) continue;
            unaccounted.add(type);
        }

        assertTrue(
            unaccounted.isEmpty(),
            unaccounted + " が登録されているのに、両方の答えを見るテストにも実機のみの一覧にも載っていない。" + "どちらか決めること。決めないまま増やすと、常に true の条件が紛れても分からない");
    }

    @Test
    @DisplayName("実機のみの一覧が古くなっていない")
    public void test実機のみの一覧が正しい() {
        Set<String> registered = registeredTypes();

        for (String type : IN_GAME_ONLY) {
            assertTrue(registered.contains(type), type + " はもう登録されていない。IN_GAME_ONLY から消すこと");
            assertFalse(PURE.containsKey(type), type + " は両方に載っている。どちらかにすること");
        }
        assertTrue(registered.size() >= 10, "登録された条件が 10 個未満。走査が壊れている: " + registered);
    }

    private static ICondition parse(String json) {
        JsonObject object = new JsonParser().parse(json)
            .getAsJsonObject();
        return ConditionParserRegistry.parse(object);
    }

    private static Set<String> registeredTypes() {
        Set<String> found = new TreeSet<>();
        String source = JavaSource
            .stripComments(JavaSource.read(JavaSource.MAIN.resolve("api/condition/Conditions.java")));
        Matcher hits = Pattern.compile("register\\(\\s*\"([a-z_0-9]+)\"")
            .matcher(source);

        while (hits.find()) found.add(hits.group(1));
        return found;
    }
}
