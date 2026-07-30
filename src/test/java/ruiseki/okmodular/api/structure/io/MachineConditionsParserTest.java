package ruiseki.okmodular.api.structure.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import ruiseki.okmodular.api.condition.Conditions;
import ruiseki.okmodular.api.condition.ICondition;
import ruiseki.okmodular.api.structure.core.ConditionPolicy;

/**
 * 構造 JSON の `conditions` / `conditionPolicy` の読み取り。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * B8 の入口。**`StructureJsonReader` 全体はゲーム外で回せない**（mappings の解決に実ブロックが要る）
 * ので、この 2 キーの読み取りだけを切り出してここで縛る。
 *
 * ============================================
 * 読めなかった条件を「落とす」ことの意味
 * ============================================
 *
 * `ConditionParserRegistry.parse` は組み立てられない条件に **null** を返す。
 * それを機械まで運ぶと、毎 tick 走る場所で null チェックかクラッシュのどちらかになる。
 * だから**読み込み時に報告して落とす**。
 *
 * **代償は正直に書く**: 綴りを間違えた条件は**門を閉じるのではなく緩める**。
 * つまり書いたつもりの条件より少ない条件で機械が動きうる。
 * その代わり警告にファイル名が出る。ここはテストで固定する挙動なので、
 * 「たまたまそう動いている」状態にしない。
 *
 * ============================================
 * 3 つの書き方
 * ============================================
 *
 * 要素 1 つ 1 つの形（明示 `type` / ネスト `{"offset": {...}}` / プロパティからの推論）は
 * `ConditionParserRegistry` の担当。**このクラスが決めるのは「配列か単体か」だけ。**
 * それでも 3 つの形が通ることを確かめる — 経路が繋がっている証拠になる。
 *
 * ============================================
 */
@DisplayName("構造 JSON の稼働条件の読み取り")
public class MachineConditionsParserTest {

    @BeforeAll
    public static void 条件パーサを登録する() {
        Conditions.registerDefaults();
    }

    private static JsonElement json(String text) {
        return new JsonParser().parse(text);
    }

    // ========== 配列か単体か ==========

    @Test
    @DisplayName("配列で複数書ける")
    public void test配列() {
        List<ICondition> conditions = MachineConditionsParser
            .parse(json("[ { \"weather\": \"RAIN\" }, { \"dimension\": 0 } ]"));

        assertEquals(2, conditions.size());
    }

    @Test
    @DisplayName("1 つだけなら配列にしなくてよい")
    public void test単体() {
        // 条件 1 つの機械が大半なので、括弧を強制しない。
        assertEquals(
            1,
            MachineConditionsParser.parse(json("{ \"weather\": \"RAIN\" }"))
                .size());
    }

    @Test
    @DisplayName("ネスト形式も通る")
    public void testネスト形式() {
        // 要素の形は ConditionParserRegistry の担当だが、経路が繋がっていることを確かめる。
        List<ICondition> conditions = MachineConditionsParser
            .parse(json("{ \"offset\": { \"dy\": -1, \"condition\": { \"weather\": \"RAIN\" } } }"));

        assertEquals(1, conditions.size());
    }

    @Test
    @DisplayName("書いていなければ空リスト")
    public void test書いていない() {
        assertTrue(
            MachineConditionsParser.parse(null)
                .isEmpty());
        assertTrue(
            MachineConditionsParser.parse(json("null"))
                .isEmpty());
    }

    // ========== 読めなかったもの ==========

    @Test
    @DisplayName("読めない条件は落として、読めたものは残す")
    public void test読めない条件を落とす() {
        // **門が緩む**方向の挙動。意図した設計なのでテストで固定する。
        List<ICondition> conditions = MachineConditionsParser
            .parse(json("[ { \"weather\": \"RAIN\" }, { \"nonsense\": 1 } ]"));

        assertEquals(1, conditions.size(), "読めなかった 1 件を落として 1 件残るはず");
    }

    @Test
    @DisplayName("オブジェクトでない要素も落とす")
    public void testオブジェクトでない要素() {
        // 配列に文字列や数値を書いてしまった場合。
        assertEquals(
            1,
            MachineConditionsParser.parse(json("[ \"rain\", { \"weather\": \"RAIN\" }, 7 ]"))
                .size());
    }

    @Test
    @DisplayName("全部読めなければ空リストになる")
    public void test全部読めない() {
        assertTrue(
            MachineConditionsParser.parse(json("[ { \"nonsense\": 1 } ]"))
                .isEmpty());
    }

    // ========== 方針 ==========

    @Test
    @DisplayName("方針を読む")
    public void test方針を読む() {
        assertSame(
            ConditionPolicy.ABORT,
            MachineConditionsParser.parsePolicy(json("\"abort\""), ConditionPolicy.PAUSE));
        assertSame(
            ConditionPolicy.PAUSE,
            MachineConditionsParser.parsePolicy(json("\"pause\""), ConditionPolicy.ABORT));
    }

    @Test
    @DisplayName("読めない方針は既定に倒す")
    public void test読めない方針() {
        // `durationPolicy` と同じ。綴り間違いで機械が止まるより既定で動くほうがまし。
        assertSame(ConditionPolicy.PAUSE, MachineConditionsParser.parsePolicy(json("\"halt\""), ConditionPolicy.PAUSE));
        assertSame(ConditionPolicy.PAUSE, MachineConditionsParser.parsePolicy(null, ConditionPolicy.PAUSE));
    }

    @Test
    @DisplayName("文字列でない方針も既定に倒す")
    public void test文字列でない方針() {
        assertSame(ConditionPolicy.PAUSE, MachineConditionsParser.parsePolicy(json("{ }"), ConditionPolicy.PAUSE));
        assertSame(ConditionPolicy.ABORT, MachineConditionsParser.parsePolicy(json("[ ]"), ConditionPolicy.ABORT));
    }
}
