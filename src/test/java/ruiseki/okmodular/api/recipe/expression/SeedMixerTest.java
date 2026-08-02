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
    @DisplayName("実機と同じ形の種でも 1/3 ずつに割れる")
    public void test実機の種で一様になる() {
        // 機械の種は座標・ワールドシード・レシピ回数・開始 tick の XOR で作られる。
        // 「ばらつくが一様に見えない」という報告に対して、種の作り方ごと写して数える。
        long x = 389L, z = 179L, worldSeed = -8140596933576549875L;
        int[] counts = new int[3];

        for (int recipe = 0; recipe < 3000; recipe++) {
            long startTick = 1000L + recipe * 400L; // duration 400 のレシピを連続で回した形
            long recipeSeed = startTick ^ worldSeed ^ ((x << 32) | (z & 0xFFFFFFFFL));
            long seed = x ^ (z << 32) ^ worldSeed ^ ((long) recipe << 16) ^ recipeSeed;

            counts[(int) Math.floor(SeedMixer.toUnitInterval(seed, SeedMixer.RANDOM) * 3)]++;
        }

        for (int i = 0; i < 3; i++) {
            assertTrue(
                counts[i] > 850 && counts[i] < 1150,
                "1+floor(random()*3) の " + (i + 1) + " が " + counts[i] + " 回");
        }
    }

    @Test
    @DisplayName("1.0 は返さない")
    public void test上端を含まない() {
        // "1 + floor(random() * 3)" が 4 を出さないことの根拠。53 bit を 2^-53 倍するので
        // 最大でも (2^53 - 1) / 2^53 にしかならない。
        for (long seed = -2000; seed < 2000; seed++) {
            assertTrue(SeedMixer.toUnitInterval(seed, SeedMixer.RANDOM) < 1.0, "seed=" + seed + " が 1.0 を返した");
            assertTrue(SeedMixer.toUnitInterval(seed, SeedMixer.CHANCE) < 1.0, "seed=" + seed + " が 1.0 を返した");
        }
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

    // ============================================
    // 位置由来の種（forPosition）
    // ============================================
    //
    // 1 レシピの実行中、評価の種は固定される（`currentRecipeSeed` は開始時に 1 回だけ決まり、
    // NBT で永続化される）。**それが欲しい性質**で、セーブ・ロードを跨いでも判定が揺れない。
    //
    // しかしそのせいで、**位置ごとに引きたいものが全部同じ値になる**。
    // `PerPositionProbabilityDecorator` は座標を回して 1 個ずつ確率判定するが、
    // 種が固定なら 50 マス全部が同じ数を引く ＝「全部置く」か「全部置かない」の二択に潰れる。
    // 構造体 IO の重みテーブルも同じ穴を踏む。
    //
    // 位置を種に混ぜる口をここに置く。ここで縛るのは
    // **「隣どうしが違う値になる」**ことと **「同じ位置なら何度でも同じ」**ことの両立。

    @Test
    @DisplayName("同じ位置・同じ種なら同じ値")
    public void test位置由来_決定的である() {
        // checkCapacity で引いた値と apply で引いた値が一致しないと、
        // 「置けると答えたのに置かない」が起きる。
        assertEquals(SeedMixer.forPosition(12345L, 4, -7, 92), SeedMixer.forPosition(12345L, 4, -7, 92));
    }

    @Test
    @DisplayName("隣り合う位置が違う値になる")
    public void test位置由来_隣が違う() {
        long base = SeedMixer.forPosition(12345L, 0, 0, 0);

        assertNotEquals(base, SeedMixer.forPosition(12345L, 1, 0, 0));
        assertNotEquals(base, SeedMixer.forPosition(12345L, 0, 1, 0));
        assertNotEquals(base, SeedMixer.forPosition(12345L, 0, 0, 1));
    }

    @Test
    @DisplayName("軸を取り違えない")
    public void test位置由来_軸ごとに違う() {
        // 座標を足して混ぜるだけの実装（x + y + z）はここで落ちる。
        // 構造体のセルは (a, b, c) が小さい整数なので、和が衝突する組が大量にある。
        assertNotEquals(SeedMixer.forPosition(1L, 1, 0, 0), SeedMixer.forPosition(1L, 0, 1, 0));
        assertNotEquals(SeedMixer.forPosition(1L, 0, 1, 0), SeedMixer.forPosition(1L, 0, 0, 1));
        assertNotEquals(SeedMixer.forPosition(1L, 1, 2, 3), SeedMixer.forPosition(1L, 3, 2, 1));
        assertNotEquals(SeedMixer.forPosition(1L, 2, 0, 0), SeedMixer.forPosition(1L, 1, 1, 0));
    }

    @Test
    @DisplayName("原点でも素の種をそのまま返さない")
    public void test位置由来_原点() {
        // forDraw は index 0 を素通しする（バッチ 1 を従来どおりにするため）。
        // forPosition は素通ししてはいけない。アンカーセルの (0,0,0) が
        // 「位置を混ぜない draw」と同じ値になると、そのセルだけ他の判定と連動する。
        assertNotEquals(12345L, SeedMixer.forPosition(12345L, 0, 0, 0));
    }

    @Test
    @DisplayName("種が違えば同じ位置でも違う")
    public void test位置由来_種が違えば違う() {
        // 実行ごとに隕石の形が変わるのはここが担う。
        assertNotEquals(SeedMixer.forPosition(1L, 3, 3, 3), SeedMixer.forPosition(2L, 3, 3, 3));
    }

    @Test
    @DisplayName("負の座標でも散る")
    public void test位置由来_負の座標() {
        // 構造体のセルはアンカー相対なので **半分が負**。A/C は [-2, 2]、B は下向きに負。
        // 座標を非負前提で詰める実装（シフトして OR するなど）はここで潰れる。
        Set<Long> seen = new HashSet<>();
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= -1; b++) {
                for (int c = -2; c <= 2; c++) {
                    seen.add(SeedMixer.forPosition(777L, a, b, c));
                }
            }
        }

        assertEquals(50, seen.size(), "5x5x2 の 50 セルで重複が出た");
    }

    @Test
    @DisplayName("50 セルの確率判定が全部同じ答えに潰れない")
    public void test位置由来_セルごとの確率判定が潰れない() {
        // PerPositionProbabilityDecorator が壊れていた形そのもの。
        // 種を固定したまま位置を混ぜずに引くと、この数は 0 か 50 にしかならない。
        int hits = 0;
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= -1; b++) {
                for (int c = -2; c <= 2; c++) {
                    if (SeedMixer.toUnitInterval(SeedMixer.forPosition(20260802L, a, b, c), SeedMixer.CHANCE) < 0.5) {
                        hits++;
                    }
                }
            }
        }

        assertTrue(hits > 10 && hits < 40, "50 セル中 " + hits + " 個が当たった（0 や 50 に潰れている）");
    }

    @Test
    @DisplayName("位置を振ると区間に散る")
    public void test位置由来_一様に散る() {
        // 落ちるべきときに確実に落ちる粗い下限。期待値 200 に対して 100 で縛る。
        int[] buckets = new int[10];
        for (int x = 0; x < 20; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 20; z++) {
                    double value = SeedMixer.toUnitInterval(SeedMixer.forPosition(0L, x, y, z), SeedMixer.RANDOM);
                    buckets[(int) (value * 10)]++;
                }
            }
        }

        for (int i = 0; i < buckets.length; i++) {
            assertTrue(buckets[i] >= 100, "位置由来の区間 " + i + " が " + buckets[i] + " 個");
        }
    }
}
