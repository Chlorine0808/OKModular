package ruiseki.okmodular.api.recipe.error;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 待機中の GUI に出す状態の選び方の検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `GuiManager` は待機中に出す理由を**手書きのホワイトリスト**で選んでいた。
 * 18 定数のうち列挙されていたのは 5 つだけで、残りは "Idle" に落ちていた。
 * `PAUSED` / `NO_MANA` / `BLOCK_MISSING` / `MISSING_BLUEPRINT` / `BLOCK_OUTPUT_FULL` /
 * `WAITING_OUTPUT` は**設定され同期もされているのに画面に出なかった**。
 *
 * SSOT が繰り返し記録している「書かれているのに読む側が無い」の一種で、
 * これは**表示側が濾している**形。
 *
 * ============================================
 * なぜ述語にしたか
 * ============================================
 *
 * ホワイトリストのままだと、**新しい定数を足したとき何も起きない**（黙って "Idle"）。
 * 述語にして既定を「出す」にすると、**足した定数は自動的に出る**ので、
 * 配線を忘れても症状が「出ない」ではなく「訳が無い」になり、
 * S1 の lang 網羅テストが即座に落ちる。B9 の `isStorable()` と同じ設計。
 *
 * つまりこの 2 つのテストは組で効く。
 * **述語 = 出すかどうか / lang テスト = 出すなら訳があるか。**
 *
 * ============================================
 * 何を凍結するか
 * ============================================
 *
 * 「出さない」側だけをリテラルで書く。**出す側は列挙して数えない** —
 * 定数が増えたら自動的に出る、が仕様なので、件数を固定すると仕様と喧嘩する。
 *
 * ============================================
 */
@DisplayName("待機中に出す状態の選び方")
public class ErrorReasonDisplayTest {

    /** 待機中に出してはいけないもの。ここに無い定数はすべて出す。 */
    private static final Set<ErrorReason> HIDDEN = EnumSet.of(ErrorReason.NONE, ErrorReason.RUNNING);

    /**
     * 改修前に `GuiManager` のホワイトリストに並んでいた 5 つ。
     * <p>
     * 述語に置き換えて**この 5 つが出なくなっていない**ことを確かめるための凍結。
     */
    private static final ErrorReason[] PREVIOUSLY_SHOWN = { ErrorReason.NO_MATCHING_RECIPE, ErrorReason.NO_ENERGY,
        ErrorReason.INPUT_MISSING, ErrorReason.OUTPUT_CAPACITY_INSUFFICIENT, ErrorReason.OUTPUT_FULL };

    /**
     * ホワイトリストから漏れていたもの。**この修正の本体。**
     * <p>
     * どれも `TEMachineController` が実際に設定していて、`GuiManager` が同期もしていた。
     * 出ていなかっただけ。
     */
    private static final ErrorReason[] PREVIOUSLY_SWALLOWED = { ErrorReason.PAUSED, ErrorReason.NO_MANA,
        ErrorReason.BLOCK_MISSING, ErrorReason.MISSING_BLUEPRINT, ErrorReason.BLOCK_OUTPUT_FULL,
        ErrorReason.WAITING_OUTPUT, ErrorReason.NO_INPUT, ErrorReason.NO_RECIPES, ErrorReason.NO_INPUT_PORTS,
        ErrorReason.NO_OUTPUT_PORTS, ErrorReason.IDLE };

    /**
     * ホワイトリストが無くなった後に足された定数。
     * <p>
     * **この一覧が空でないこと自体が、述語に変えた効果の証拠。** 足しただけで表示され、
     * `GuiManager` に 1 行も書かなくてよかったもの。
     */
    private static final ErrorReason[] ADDED_AFTER_THE_WHITELIST = { ErrorReason.CONDITION_NOT_MET };

    @Test
    @DisplayName("ホワイトリスト撤去後に足した定数も、何もせずに表示される")
    public void test後から足した定数も出る() {
        for (ErrorReason reason : ADDED_AFTER_THE_WHITELIST) {
            assertTrue(reason.showsWhenIdle(), reason + " が出ない。既定は「出す」のはず");
        }
    }

    @Test
    @DisplayName("出さないのは NONE と RUNNING だけ")
    public void test出さないものが二つだけ() {
        for (ErrorReason reason : ErrorReason.values()) {
            if (HIDDEN.contains(reason)) {
                assertFalse(reason.showsWhenIdle(), reason + " は待機中に出してはいけない");
            } else {
                assertTrue(reason.showsWhenIdle(), reason + " が待機中に出ない。既定は「出す」のはず");
            }
        }
    }

    @Test
    @DisplayName("NONE を出さない理由 — エラーが無い状態だから")
    public void testNONEは出さない() {
        // 出すと「エラー無し」を表す空文字が状態欄に出る。
        assertFalse(ErrorReason.NONE.showsWhenIdle());
    }

    @Test
    @DisplayName("RUNNING を出さない理由 — 待機中に「処理中」は矛盾するから")
    public void testRUNNINGは出さない() {
        // 待機中の表示を組み立てる経路でしか使わないので、
        // ここに RUNNING が来るのは前の状態が残っているとき。出すと嘘になる。
        assertFalse(ErrorReason.RUNNING.showsWhenIdle());
    }

    @Test
    @DisplayName("改修前に出ていた 5 つは今も出る")
    public void test退行していない() {
        for (ErrorReason reason : PREVIOUSLY_SHOWN) {
            assertTrue(reason.showsWhenIdle(), reason + " が出なくなっている。改修前は出ていた");
        }
    }

    @Test
    @DisplayName("ホワイトリストから漏れていたものが出るようになった")
    public void test漏れていたものが出る() {
        for (ErrorReason reason : PREVIOUSLY_SWALLOWED) {
            assertTrue(reason.showsWhenIdle(), reason + " がまだ出ない。これがこの修正の本体");
        }
    }

    @Test
    @DisplayName("定数が可変な状態を持たない")
    public void test定数が状態を持たない() {
        // **enum 定数はプロセス全体で 1 個しかない。** そこに書き換え可能なフィールドを持つと、
        // ある機械の状態が別の機械の表示に混ざる。
        //
        // 実際にその形の罠があった。`withDetail(String)` が `this.detail = detail` を
        // 定数に対してやっていた。呼び出し元が 0 件だったので実害は出ていなかったが、
        // 詳細を付けたい場所（`GuiManager`）の真横にあり、いつ使われてもおかしくなかった。
        // コントローラ側の `lastProcessErrorDetail` が正しい置き場所。
        for (Field field : ErrorReason.class.getDeclaredFields()) {
            if (field.isSynthetic()) continue;
            if (Modifier.isStatic(field.getModifiers())) continue; // 定数自身と $VALUES
            assertTrue(
                Modifier.isFinal(field.getModifiers()),
                "ErrorReason." + field.getName() + " が final でない。enum 定数は共有されるので、" + "機械ごとの値はコントローラ側に置くこと");
        }
    }

    @Test
    @DisplayName("凍結した 2 つの一覧が実在の定数を全て覆っている")
    public void test一覧が網羅している() {
        // 定数を足したとき、この一覧のどちらにも入らないものが出る。
        // それ自体は正しい（既定で出る）が、**意図して分類したか**を確認させたい。
        for (ErrorReason reason : ErrorReason.values()) {
            boolean classified = HIDDEN.contains(reason) || Arrays.asList(PREVIOUSLY_SHOWN)
                .contains(reason)
                || Arrays.asList(PREVIOUSLY_SWALLOWED)
                    .contains(reason)
                || Arrays.asList(ADDED_AFTER_THE_WHITELIST)
                    .contains(reason);
            assertTrue(
                classified,
                reason + " がどの一覧にも無い。既定では出るので動作は正しいが、" + "出してよいか判断して HIDDEN か ADDED_AFTER_THE_WHITELIST に分類すること");
        }
    }
}
