package ruiseki.okmodular.api.structure.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ICondition;

/**
 * 構造エントリが持つ「機械そのものの稼働条件」と、条件が崩れたときの方針。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * B8 の入れ物側。レシピは以前から `conditions` を持っていたが、**機械自体**の条件は
 * 置き場が無かった。構造 JSON のトップレベル `conditions` と `conditionPolicy` がその置き場で、
 * ここはそれを構造エントリまで運べることを見る。
 *
 * ============================================
 * 既定値が契約
 * ============================================
 *
 * **`conditions` は空リスト、`conditionPolicy` は `PAUSE`。**
 * どちらも「書いていない既存の構造 JSON が 1 つも壊れない」ことを意味する。
 * B8 の完了条件 5（`conditions` が無い構造の挙動が改修前と同一）の入り口。
 *
 * **空リストは null ではない。** `MachineConditionGate` は null も空も同じに扱うが、
 * 呼ぶ側にそれを意識させたくない。毎 tick 回す相手なので、null チェックを
 * 各所に散らすより空リストを返すほうが安い。
 *
 * ============================================
 * なぜ `PAUSE` を既定にしたか
 * ============================================
 *
 * レッドストーン停止に前例があり、**消費済みの材料が失われない**（ユーザー決定 = D3）。
 * `abort` は「条件が崩れたら生産が台無しになる」緊張感を作りたい機械のための選択肢。
 *
 * ============================================
 */
@DisplayName("構造エントリの稼働条件")
public class StructureEntryConditionsTest {

    /** 判定内容には興味が無いので、識別できる名前だけ持つ。 */
    private static final class NamedCondition implements ICondition {

        private final String name;

        NamedCondition(String name) {
            this.name = name;
        }

        @Override
        public boolean isMet(ConditionContext context) {
            return true;
        }

        @Override
        public String getDescription() {
            return name;
        }

        @Override
        public void write(JsonObject json) {}
    }

    private static StructureEntryBuilder entry() {
        return new StructureEntryBuilder().setName("test");
    }

    // ========== 既定値 ==========

    @Test
    @DisplayName("条件を書かなければ空リスト（null ではない）")
    public void test既定は空リスト() {
        List<ICondition> conditions = entry().build()
            .getConditions();

        assertNotNull(conditions, "null を返すと呼ぶ側に null チェックが散る");
        assertTrue(conditions.isEmpty());
    }

    @Test
    @DisplayName("方針を書かなければ PAUSE")
    public void test既定はPAUSE() {
        // 既存の構造 JSON は 1 つも conditionPolicy を書いていない。
        assertSame(
            ConditionPolicy.PAUSE,
            entry().build()
                .getConditionPolicy());
    }

    // ========== 運べること ==========

    @Test
    @DisplayName("条件が順番どおりに運ばれる")
    public void test条件が運ばれる() {
        // 順番は意味を持つ。`MachineConditionGate` は最初に落ちた条件を報告する。
        ICondition first = new NamedCondition("1 つ目");
        ICondition second = new NamedCondition("2 つ目");

        List<ICondition> conditions = entry().addCondition(first)
            .addCondition(second)
            .build()
            .getConditions();

        assertEquals(2, conditions.size());
        assertSame(first, conditions.get(0));
        assertSame(second, conditions.get(1));
    }

    @Test
    @DisplayName("方針が運ばれる")
    public void test方針が運ばれる() {
        assertSame(
            ConditionPolicy.ABORT,
            entry().setConditionPolicy(ConditionPolicy.ABORT)
                .build()
                .getConditionPolicy());
    }

    @Test
    @DisplayName("返るリストは書き換えられない")
    public void testリストは不変() {
        // 毎 tick そのまま回す相手なので、外から触れる余地を残さない。
        // 他のコレクション（layers / mappings / requirements）と同じ扱い。
        List<ICondition> conditions = entry().addCondition(new NamedCondition("a"))
            .build()
            .getConditions();

        assertThrows(UnsupportedOperationException.class, () -> conditions.add(new NamedCondition("b")));
    }

    // ========== 方針の読み取り ==========

    @Test
    @DisplayName("方針の名前は表記ゆれを吸収する")
    public void test方針の表記ゆれ() {
        // `durationPolicy` と同じ寛容さ。JSON を書く人に大文字小文字を覚えさせない。
        for (String written : Arrays.asList("pause", "PAUSE", "Pause", " pause ")) {
            assertSame(ConditionPolicy.PAUSE, ConditionPolicy.fromString(written, ConditionPolicy.ABORT), written);
        }
        for (String written : Arrays.asList("abort", "ABORT", "Abort")) {
            assertSame(ConditionPolicy.ABORT, ConditionPolicy.fromString(written, ConditionPolicy.PAUSE), written);
        }
    }

    @Test
    @DisplayName("読めない名前と null は既定値になる")
    public void test読めない名前() {
        // 綴りを間違えた JSON で機械が止まるより、既定で動くほうがまし。
        // 読み取り側（S7）が警告を出す。
        assertSame(ConditionPolicy.PAUSE, ConditionPolicy.fromString("halt", ConditionPolicy.PAUSE));
        assertSame(ConditionPolicy.PAUSE, ConditionPolicy.fromString(null, ConditionPolicy.PAUSE));
        assertSame(ConditionPolicy.ABORT, ConditionPolicy.fromString("", ConditionPolicy.ABORT));
    }
}
