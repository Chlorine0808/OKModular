package ruiseki.okmodular.common.recipe;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * 実行中の状態が**セーブとロードを跨いで揃っている**ことの検証。
 *
 * ============================================
 * 書いたのに読み戻さない
 * ============================================
 *
 * この mod は「読み戻す側が無い」を既に踏んでいる（ポートのドロップアイテムの NBT）。
 * 書く側だけ足すのは簡単で、**書けた時点で仕事が終わったように見える**のが原因。
 *
 * ここが崩れると「セーブして入り直したら機械の様子が変わる」になる。
 * 特に `cachedOutputs` は**開始時に確定させた払い出し量**なので、
 * 読み戻さなければ `random()` で書いた量がロードのたびに変わる。
 * docs が「セーブ・ロードを跨いでも変わらない」と約束している所そのもの。
 *
 * ============================================
 * 消えるべき状態もある
 * ============================================
 *
 * 全部を永続すればいいわけではない。`currentRecipe` はレシピ本体への参照で、
 * リロードでレシピ定義そのものが入れ替わりうるから**名前から引き直す**のが正しい。
 * `conditionFailure` は毎 tick 計算し直すので、古い値を持ち越すほうが害になる。
 *
 * **判断そのものが無いから付け忘れに気づけない。** だからフィールドを列挙して、
 * 1 つずつどちらかに割り振る。新しいフィールドが増えたら、割り振るまで赤い。
 */
@DisplayName("実行中の状態の永続")
public class ProcessStatePersistenceTest {

    private static final java.nio.file.Path SOURCE = JavaSource.MAIN.resolve("common/recipe/ProcessAgent.java");

    /** NBT に載る状態と、載るときのキー名。 */
    private static final Map<String, String> PERSISTED = new LinkedHashMap<>();

    static {
        PERSISTED.put("progress", "progress");
        PERSISTED.put("maxProgress", "maxProgress");
        PERSISTED.put("energyPerTick", "energyPerTick");
        PERSISTED.put("energyOutputPerTick", "energyOutputPerTick");
        PERSISTED.put("manaPerTick", "manaPerTick");
        PERSISTED.put("manaOutputPerTick", "manaOutputPerTick");
        PERSISTED.put("currentBatchSize", "batchSize");
        PERSISTED.put("running", "running");
        PERSISTED.put("waitingForOutput", "waitingForOutput");
        PERSISTED.put("currentRecipeName", "recipeName");
        PERSISTED.put("workProgress", "workProgress");
        PERSISTED.put("cachedOutputs", "cachedOutputs");
        PERSISTED.put("perTickInputs", "perTickInputs");
        PERSISTED.put("perTickOutputs", "perTickOutputs");
    }

    /** 意図的に持ち越さない状態と、その理由。 */
    private static final Map<String, String> TRANSIENT = new LinkedHashMap<>();

    static {
        TRANSIENT.put("currentRecipe", "recipeName から引き直す。リロードでレシピ定義そのものが入れ替わりうる");
        TRANSIENT.put("context", "機械の TileEntity 自身。持ち主が復元する");
        TRANSIENT.put("conditionBlocked", "毎 tick 計算し直す。古い値を持ち越すと止まったままになる");
        TRANSIENT.put("conditionFailure", "同上。表示用の理由文字列");
        TRANSIENT.put("plannedSpeed", "その tick だけの計画。tick() の頭で毎回捨てる。持ち越すと昔の速度で請求する");
        TRANSIENT.put("plannedFrom", "同上。workProgress から引き直せる");
        TRANSIENT.put("plannedTo", "同上。workProgress と速度から引き直せる");
    }

    @Test
    @DisplayName("書いたキーはすべて読み戻される")
    public void test読み戻さないキーが無い() {
        Set<String> written = keysIn("public void writeToNBT(");
        Set<String> read = keysIn("public void readFromNBT(");

        List<String> lost = new ArrayList<>();
        for (String key : written) {
            if (!read.contains(key)) lost.add(key);
        }

        assertTrue(
            lost.isEmpty(),
            lost + " を書いているのに読み戻していない。セーブして入り直すと消える。" + "cachedOutputs がこれになると、確定させたはずの払い出し量がロードのたびに変わる");
    }

    @Test
    @DisplayName("読むキーはすべて書かれている")
    public void test誰も書かないキーを読んでいない() {
        Set<String> written = keysIn("public void writeToNBT(");
        Set<String> read = keysIn("public void readFromNBT(");

        List<String> phantom = new ArrayList<>();
        for (String key : read) {
            if (!written.contains(key)) phantom.add(key);
        }

        assertTrue(phantom.isEmpty(), phantom + " を読んでいるが誰も書いていない。既定値が入るだけなので、綴り違いでも気づけない");
    }

    @Test
    @DisplayName("実行状態のフィールドがすべて仕分けされている")
    public void test判断されていない状態が無い() {
        List<String> unjudged = new ArrayList<>();

        for (String field : instanceFields()) {
            if (PERSISTED.containsKey(field) || TRANSIENT.containsKey(field)) continue;
            unjudged.add(field);
        }

        assertTrue(
            unjudged.isEmpty(),
            unjudged + " を永続するかどうか決めていない。**決めていないから付け忘れに気づけない**。" + "PERSISTED か TRANSIENT のどちらかに、理由つきで入れること");
    }

    @Test
    @DisplayName("仕分けの一覧が古くなっていない")
    public void test一覧が正しい() {
        Set<String> fields = instanceFields();
        Set<String> written = keysIn("public void writeToNBT(");

        for (Map.Entry<String, String> entry : PERSISTED.entrySet()) {
            assertTrue(fields.contains(entry.getKey()), entry.getKey() + " というフィールドはもう無い。PERSISTED から消すこと");
            assertTrue(written.contains(entry.getValue()), entry.getKey() + " が " + entry.getValue() + " として書かれていない");
        }
        for (String field : TRANSIENT.keySet()) {
            assertTrue(fields.contains(field), field + " というフィールドはもう無い。TRANSIENT から消すこと");
            assertTrue(!PERSISTED.containsKey(field), field + " が両方に載っている");
        }
    }

    @Test
    @DisplayName("走査そのものが空振りしていない")
    public void test走査が効いている() {
        assertTrue(instanceFields().size() >= 15, "実行状態のフィールドが 15 個未満。反射の走査が壊れている: " + instanceFields());
        assertTrue(keysIn("public void writeToNBT(").size() >= 10, "書き出しのキーが 10 個未満。ソースの走査が壊れている");
    }

    // ============================================================

    /** `ProcessAgent` とその親が持つインスタンスフィールド。 */
    private static Set<String> instanceFields() {
        Set<String> found = new TreeSet<>();

        for (Class<?> type = ProcessAgent.class; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (field.isSynthetic()) continue;
                found.add(field.getName());
            }
        }
        return found;
    }

    /** そのメソッドの中で `nbt.setX("key"` / `nbt.getX("key"` として現れるキー。 */
    private static Set<String> keysIn(String signature) {
        String body = JavaSource.methodBody(JavaSource.stripComments(JavaSource.read(SOURCE)), signature);
        Set<String> found = new TreeSet<>();
        Matcher hits = Pattern.compile("nbt\\.(?:set|get|has)[A-Za-z]*\\(\\s*\"([A-Za-z_][A-Za-z0-9_]*)\"")
            .matcher(body);

        while (hits.find()) found.add(hits.group(1));
        return found;
    }
}
