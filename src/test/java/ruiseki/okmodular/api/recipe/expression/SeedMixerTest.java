package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 評価の種から乱数を作る部分。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `random()` と `chance()` は **`new Random(seed).nextDouble()` を使い捨て**していた。
 * Java の `Random` は線形合同法で、**最初の 1 個は種のハッシュとして極端に弱い**。
 * 種が 1 違うだけの値はほぼ同じ数を返す（実測）:
 *
 * <pre>
 * seed +0  → 0.997969      seed +200  → 0.028785
 * seed +1  → 0.997521      seed +400  → 0.942070
 * seed +2  → 0.997432      seed +600  → 0.967153
 * seed +3  → 0.997700      seed +800  → 0.972170
 * </pre>
 *
 * 機械の種は座標とワールドシードとレシピ回数から作るので、**同じ機械では種が近い値のまま動く**。
 * その結果 `"1 + floor(random() * 3)"` と書いた出力が
 * **「2 しか出ない機械」と「3 しか出ない機械」**になった（実機で観測）。
 * 上の 10 サンプルでも「2」に当たる区間が 1 度も出ていない。
 *
 * 種そのものは悪くない（機械ごと・レシピごとに違う）。**使い方が悪い。**
 * SplitMix64 の finalizer を噛ませてから 53 bit を取り出す形にした。
 *
 * ============================================
 * 何を縛るのか
 * ============================================
 *
 * 「一様である」ことは統計の話なので、**落ちるべきときに確実に落ちる粗い下限**で縛る。
 * 旧実装は隣接する種で 1 つの区間に固まるので、区間ごとの下限を置けば必ず落ちる。
 * 乱数そのものの質を測るテストではない。
 *
 * ============================================
 */
@DisplayName("評価の種から乱数を作る")
public class SeedMixerTest {

    @Test
    @DisplayName("同じ種と系統なら同じ値")
    public void test決定的である() {
        // レシピの再現性の土台。同じ判定を 2 回評価したら同じ答えでなければならない。
        assertEquals(
            SeedMixer.toUnitInterval(12345L, SeedMixer.RANDOM),
            SeedMixer.toUnitInterval(12345L, SeedMixer.RANDOM));
    }

    @Test
    @DisplayName("0 以上 1 未満に収まる")
    public void test範囲() {
        for (long seed = -500; seed < 500; seed++) {
            double value = SeedMixer.toUnitInterval(seed, SeedMixer.RANDOM);
            assertTrue(value >= 0.0 && value < 1.0, "範囲外: seed=" + seed + " value=" + value);
        }
    }

    @Test
    @DisplayName("【回帰防止】隣接する種が同じ区間に固まらない")
    public void test隣接する種が散る() {
        // 旧実装はここで落ちる。10 区間に 1000 個を配ると期待値は各 100。
        // 下限 50 は「偏っている」ではなく「固まっている」だけを捕まえる粗さ。
        int[] buckets = new int[10];
        for (long seed = 0; seed < 1000; seed++) {
            buckets[(int) (SeedMixer.toUnitInterval(seed, SeedMixer.RANDOM) * 10)]++;
        }

        for (int i = 0; i < buckets.length; i++) {
            assertTrue(buckets[i] >= 50, "区間 " + i + " に " + buckets[i] + " 個しか来ていない。種が散っていない");
        }
    }

    @Test
    @DisplayName("【回帰防止】1 + floor(random() * 3) が 3 通りとも出る")
    public void test3通りとも出る() {
        // 実機で観測した症状そのもの。同じ機械で連続して引いても値が変わること。
        Set<Integer> seen = new HashSet<>();
        for (long seed = 900000L; seed < 900100L; seed++) {
            seen.add(1 + (int) Math.floor(SeedMixer.toUnitInterval(seed, SeedMixer.RANDOM) * 3));
        }

        assertEquals(Set.of(1, 2, 3), seen, "同じ機械が同じ値ばかり返している");
    }

    @Test
    @DisplayName("レシピ 1 本分（200 tick）離れた種でも散る")
    public void testレシピ間隔でも散る() {
        // 機械の種はレシピごとに totalWorldTime の分だけ進む。旧実装はこの間隔でも
        // 値が塊で動いていた（0.278 / 0.283 / 0.216 / 0.244 / 0.252 …）。
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            seen.add(1 + (int) Math.floor(SeedMixer.toUnitInterval(1234567890123L + i * 200L, SeedMixer.RANDOM) * 3));
        }

        assertEquals(Set.of(1, 2, 3), seen);
    }

    @Test
    @DisplayName("系統が違えば同じ種でも違う値")
    public void test系統が独立している() {
        // random() と chance() は同じ評価の中で両方書ける。同じ種から作るので、
        // 系統を分けないと「chance が当たったときだけ random が大きい」といった相関が出る。
        for (long seed = 0; seed < 200; seed++) {
            assertNotEquals(
                SeedMixer.toUnitInterval(seed, SeedMixer.RANDOM),
                SeedMixer.toUnitInterval(seed, SeedMixer.CHANCE),
                "seed=" + seed + " で系統が一致している");
        }
    }

    @Test
    @DisplayName("系統どうしも固まらない")
    public void test系統どうしが散る() {
        int[] buckets = new int[10];
        for (long seed = 0; seed < 1000; seed++) {
            buckets[(int) (SeedMixer.toUnitInterval(seed, SeedMixer.CHANCE) * 10)]++;
        }

        for (int i = 0; i < buckets.length; i++) {
            assertTrue(buckets[i] >= 50, "chance 系統の区間 " + i + " が " + buckets[i] + " 個");
        }
    }

    @Test
    @DisplayName("種 0 でも特別扱いしない")
    public void test種が0のとき() {
        // 文脈が種を持たない経路（NEI・フォールバック）は 0 を渡す。
        // 旧実装ではここが new Random(0) の 0.7309 に固定されていた。
        double value = SeedMixer.toUnitInterval(0L, SeedMixer.RANDOM);

        assertTrue(value >= 0.0 && value < 1.0);
        assertNotEquals(value, SeedMixer.toUnitInterval(1L, SeedMixer.RANDOM));
    }
}
