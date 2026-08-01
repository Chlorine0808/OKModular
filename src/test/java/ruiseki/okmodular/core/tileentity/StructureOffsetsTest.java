package ruiseki.okmodular.core.tileentity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 構造判定を始める位置（コントローラからのオフセット）の選び方。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `AbstractMBModifierTE.doUpdate` が **`getOffSet()[getTier() - 1]`** と書いていた。
 * `int[][]` は「Tier ごとに 1 行」という古い固定マルチブロックの形だが、
 * **`StructureAgent.getOffSet()` は常に 1 行しか返さない**（構造 JSON のオフセットが 1 個か、既定の `{0,0,0}`）。
 *
 * 一方 `TEMachineController.getTier()` は**構成ブロックから計算した機械の Tier**で、添字ではない。
 * 構造 JSON に `tierMap` を書いて Tier 2 以上の機械を組んだ瞬間、
 * **形成された次の tick に `ArrayIndexOutOfBoundsException` でサーバが落ちていた**
 * （実測: `sample_07_tier_boost` を Tier 3 のケーシングで組んで再現）。
 *
 * `tierMap` を使う構造体がこれまで 1 つも無かったので `getTier()` が常に 1 を返し、
 * **添字が偶然 0 に収まっていた**だけだった。
 *
 * ============================================
 * なぜ「常に 0 行目」ではなく丸めるのか
 * ============================================
 *
 * `int[][]` を返す契約は残っているので、**本当に Tier ごとの表を持つ実装が来たら**そちらを尊重したい。
 * 丸めておけば、表が 1 行しか無い今の唯一の実装（`StructureAgent`）では必ず 0 行目になり、
 * **`StructureAgent.forceStructureCheck` が前から `offsets[0]` を使っていたのと一致する**。
 * 同じ構造体に対して「定期チェックは Tier 行・強制チェックは 0 行目」という食い違いも同時に消える。
 *
 * ============================================
 */
@DisplayName("構造判定の開始オフセット")
public class StructureOffsetsTest {

    private static final int[] ORIGIN = { 0, 0, 0 };

    @Test
    @DisplayName("Tier 1 は 0 行目")
    public void testTier1は0行目() {
        int[][] table = { { 1, 2, 3 }, { 4, 5, 6 } };

        assertArrayEquals(new int[] { 1, 2, 3 }, StructureOffsets.forTier(table, 1));
    }

    @Test
    @DisplayName("Tier ごとの表があればその行を使う")
    public void testTierごとの行() {
        int[][] table = { { 1, 2, 3 }, { 4, 5, 6 }, { 7, 8, 9 } };

        assertArrayEquals(new int[] { 4, 5, 6 }, StructureOffsets.forTier(table, 2));
        assertArrayEquals(new int[] { 7, 8, 9 }, StructureOffsets.forTier(table, 3));
    }

    @Test
    @DisplayName("【回帰防止】行が足りなければ最後の行に丸める")
    public void test行が足りないとき丸める() {
        // これが落ちていたケース。構造 JSON のオフセットは 1 個しか無いのに
        // Tier 3 の機械が組まれて getOffSet()[2] を引いていた。
        int[][] oneRow = { { 1, 2, 3 } };

        assertArrayEquals(new int[] { 1, 2, 3 }, StructureOffsets.forTier(oneRow, 3), "Tier で配列の外に出ている");
        assertArrayEquals(new int[] { 1, 2, 3 }, StructureOffsets.forTier(oneRow, 16), "Tier は 16 段まであり得る");
    }

    @Test
    @DisplayName("Tier が 0 以下でも 0 行目に丸める")
    public void testTierが0以下() {
        // componentTiers に 0 が混ざると getTier() が 0 を返しうる。添字は -1 になる。
        int[][] table = { { 1, 2, 3 }, { 4, 5, 6 } };

        assertArrayEquals(new int[] { 1, 2, 3 }, StructureOffsets.forTier(table, 0));
        assertArrayEquals(new int[] { 1, 2, 3 }, StructureOffsets.forTier(table, -5));
    }

    // ========== 表そのものが無いとき ==========

    @Test
    @DisplayName("表が null なら原点")
    public void test表がnull() {
        assertArrayEquals(ORIGIN, StructureOffsets.forTier(null, 1));
    }

    @Test
    @DisplayName("表が空なら原点")
    public void test表が空() {
        assertArrayEquals(ORIGIN, StructureOffsets.forTier(new int[0][], 1));
    }

    @Test
    @DisplayName("行が null なら原点")
    public void test行がnull() {
        assertArrayEquals(ORIGIN, StructureOffsets.forTier(new int[][] { null }, 1));
    }

    @Test
    @DisplayName("行が 3 要素に満たなければ原点")
    public void test行が短い() {
        // 呼び出し側は [0] [1] [2] を読むので、短い行を返すと今度はそこで落ちる。
        assertArrayEquals(ORIGIN, StructureOffsets.forTier(new int[][] { { 1, 2 } }, 1));
    }

    @Test
    @DisplayName("返した配列を書き換えても表は壊れない")
    public void test返り値は独立している() {
        int[][] table = { { 1, 2, 3 } };

        int[] first = StructureOffsets.forTier(table, 1);
        first[0] = 99;

        assertArrayEquals(new int[] { 1, 2, 3 }, StructureOffsets.forTier(table, 1), "表そのものを渡してしまっている");
    }
}
