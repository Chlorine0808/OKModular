package ruiseki.okmodular.common.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.recipe.error.ErrorReason;

/**
 * GUI に出す「止まっている理由」と、その `%s` に入る詳細の組。
 *
 * 元は `TEMachineController` の 2 フィールドで、レシピ進行と `GuiManager` の sync value が
 * 別々に読み書きしていた。レシピ進行をコントローラから切り出すには、まずこの組を
 * 1 つの値として渡せる形にする必要がある。
 *
 * 切り出し前は、理由だけの直接代入 `lastProcessErrorReason = X` が詳細を残していた。
 * `%s` を持つ理由でそこを通るのは redstone の `PAUSED` だけで、GUI は redstone を
 * 先に判定するので表に出なかった。残す理由が無いので、理由だけの更新は常に詳細を消す。
 */
@DisplayName("処理エラーの状態")
public class ProcessErrorStateTest {

    @Test
    @DisplayName("初期状態は NONE で詳細なし")
    public void test初期状態() {
        ProcessErrorState state = new ProcessErrorState();

        assertEquals(ErrorReason.NONE, state.getReason());
        assertNull(state.getDetail());
    }

    @Test
    @DisplayName("理由と詳細を一緒に書ける")
    public void test理由と詳細() {
        ProcessErrorState state = new ProcessErrorState();

        state.set(ErrorReason.CONDITION_NOT_MET, "tier >= 2");

        assertEquals(ErrorReason.CONDITION_NOT_MET, state.getReason());
        assertEquals("tier >= 2", state.getDetail());
    }

    @Test
    @DisplayName("理由だけを書くと詳細は消える")
    public void test理由だけなら詳細を消す() {
        ProcessErrorState state = new ProcessErrorState();
        state.set(ErrorReason.CONDITION_NOT_MET, "tier >= 2");

        state.set(ErrorReason.OUTPUT_FULL);

        assertEquals(ErrorReason.OUTPUT_FULL, state.getReason());
        assertNull(state.getDetail());
    }

    @Test
    @DisplayName("詳細だけを書ける（GUI の同期が理由の後に届く）")
    public void test詳細だけ() {
        ProcessErrorState state = new ProcessErrorState();
        state.set(ErrorReason.PAUSED);

        state.setDetail("redstone");

        assertEquals(ErrorReason.PAUSED, state.getReason());
        assertEquals("redstone", state.getDetail());
    }
}
