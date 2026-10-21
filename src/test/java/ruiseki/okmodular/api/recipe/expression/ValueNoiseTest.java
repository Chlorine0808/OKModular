package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * K2 — 値ノイズ。**隣り合う座標が近い値を返すこと**の検証。
 *
 * ============================================
 * これが足すのは「なめらかさ」だけ
 * ============================================
 *
 * 「座標に対して決まった、でたらめに見える値」は**既に手元にあります** ——
 * `random()` は `toUnitInterval(種, RANDOM)` で、パターンの中では種にセルの座標が
 * 混ざっているので、座標ごとに独立な値になります。
 *
 * それを `chance` に渡すと**塩胡椒**（均一な砂嵐）になります。塊にするのに足りないのは
 * **分布の形ではなく空間的な相関**で、K2 が入れるのはそれだけです。
 *
 * <pre>
 * random()  x: 0  1  2  3  4  5  6  7    隣が無関係
 *           v: .9 .1 .7 .2 .8 .0 .5 .9
 *
 * noise(4)  x: 0  1  2  3  4  5  6  7    隣は近い。4 ブロックで山谷が入れ替わる
 *           v: .9 .8 .6 .4 .2 .2 .3 .5
 * </pre>
 *
 * ============================================
 * 契約 2 つでは足りない
 * ============================================
 *
 * 設計メモには「同じ座標なら同じ値」「近い座標は近い値」の 2 つが挙がっていました。
 * **常に 0.5 を返す関数がその 2 つを満たします。** だから「離れた座標は相関しない」を
 * 3 つ目に足してあります。定数関数はそこで落ちます。
 *
 * ============================================
 * 種は配置全体で同じでなければならない
 * ============================================
 *
 * ⚠ 格子点のハッシュに**セルごとに違う種**を渡すと、格子ごとに別の種でハッシュすることに
 * なり、**なめらかさが消えてホワイトノイズに戻ります**。
 *
 * つまり `noise()` はセルの文脈の種（`forPosition` 済み）を使えません。
 * 間違えると契約 2 がここで落ちるので、静かには壊れません。
 */
@DisplayName("値ノイズ")
public class ValueNoiseTest {

    private static final long SEED = 4649L;

    private static double at(int x, int y, int z, double scale) {
        return ValueNoise.at(SEED, x, y, z, scale);
    }

    // ============================================
    // 1. 再現性
    // ============================================

    @Test
    @DisplayName("同じ座標・同じ種・同じ scale なら同じ値")
    public void test再現する() {
        for (int x = -20; x <= 20; x++) {
            assertEquals(at(x, 64, 7, 8), at(x, 64, 7, 8), "x=" + x + " で答えが変わった");
        }
        assertEquals(at(0, 0, 0, 8), at(0, 0, 0, 8));
        assertEquals(at(-1000, 5, 99999, 16), at(-1000, 5, 99999, 16), "大きい座標でも再現する");
    }

    @Test
    @DisplayName("種が違えば別の場になる")
    public void test種で変わる() {
        int differences = 0;
        for (int x = 0; x < 40; x++) {
            if (ValueNoise.at(1L, x, 64, 0, 8) != ValueNoise.at(2L, x, 64, 0, 8)) differences++;
        }
        assertTrue(differences > 35, "40 点のうち " + differences + " 点しか違わない。種が効いていない");
    }

    // ============================================
    // 2. 近い座標は近い値 — これが K2 の本体
    // ============================================

    @Test
    @DisplayName("隣り合う座標の差が小さい")
    public void test隣は近い() {
        double worst = 0;
        for (int x = -50; x < 50; x++) {
            worst = Math.max(worst, Math.abs(at(x, 64, 0, 16) - at(x + 1, 64, 0, 16)));
        }

        // scale 16 なら 1 ブロック進んでも山谷の 1/16 しか動かない。
        // 相関が無ければ隣の差は 1 に近い値まで出る。
        assertTrue(worst < 0.2, "隣り合う座標の最大差が " + worst + "。相関が無い（ホワイトノイズのまま）");
    }

    @Test
    @DisplayName("scale を上げるほど滑らかになる")
    public void testscaleで滑らかさが変わる() {
        double coarse = averageNeighbourStep(4);
        double smooth = averageNeighbourStep(32);

        assertTrue(smooth < coarse, "scale 32 の隣接差 " + smooth + " が scale 4 の " + coarse + " より小さくない");
        assertTrue(smooth * 2 < coarse, "scale を 8 倍にしたのに滑らかさがほとんど変わっていない");
    }

    /** x 方向に 1 ブロック進んだときの値の変化の平均。 */
    private static double averageNeighbourStep(double scale) {
        double total = 0;
        int samples = 0;
        for (int x = -100; x < 100; x++) {
            total += Math.abs(at(x, 64, 0, scale) - at(x + 1, 64, 0, scale));
            samples++;
        }
        return total / samples;
    }

    // ============================================
    // 3. 離れた座標は相関しない — 定数関数を落とす
    // ============================================

    @Test
    @DisplayName("離れた座標では値が散る")
    public void test離れた座標は相関しない() {
        double min = 1;
        double max = 0;
        for (int x = 0; x < 200; x++) {
            // scale の何倍も離した点を拾う
            double value = at(x * 37, 64, x * 53, 8);
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        assertTrue(max - min > 0.5, "200 点の値が " + min + "〜" + max + " に収まっている。場が動いていない（定数関数）");
    }

    @Test
    @DisplayName("3 軸すべてが効いている")
    public void test3軸とも効く() {
        double origin = at(0, 0, 0, 8);

        assertTrue(at(40, 0, 0, 8) != origin, "x を動かしても値が変わらない");
        assertTrue(at(0, 40, 0, 8) != origin, "y を動かしても値が変わらない");
        assertTrue(at(0, 0, 40, 8) != origin, "z を動かしても値が変わらない");
    }

    // ============================================
    // 4. 範囲
    // ============================================

    @Test
    @DisplayName("値は [0, 1) に入る")
    public void test範囲に収まる() {
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z += 7) {
                double value = at(x, 64, z, 8);
                assertTrue(value >= 0 && value < 1, "(" + x + "," + z + ") の値が範囲外: " + value);
            }
        }
    }

    @Test
    @DisplayName("⚠ 分布は一様ではない（0.5 付近に寄る）")
    public void test分布は一様でない() {
        // これを知らずに `noise(8) < 0.2` と書くと、20% どころか数 % しか通らない。
        // docs に明記してあることを、値でも残しておく。
        int middle = 0;
        int total = 0;
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z++) {
                double value = at(x, 64, z, 8);
                if (value > 0.35 && value < 0.65) middle++;
                total++;
            }
        }

        double ratio = (double) middle / total;
        assertTrue(ratio > 0.4, "中央 30% の帯に " + ratio + " しか入っていない。一様分布に近い（補間が効いていない？）");
    }

    // ============================================
    // 5. scale の下限
    // ============================================

    @Test
    @DisplayName("scale 1 はホワイトノイズに退化する")
    public void testscale1は相関しない() {
        // 格子の間隔が 1 ブロックなら全ブロックが格子点で、補間が起きない。
        // これは打ち切りではなく本当の極限なので、下限として筋が通る。
        double step = averageNeighbourStep(1);

        assertTrue(step > 0.25, "scale 1 の隣接差が " + step + "。ホワイトノイズなら 1/3 前後になるはず");
        assertTrue(step > averageNeighbourStep(4) * 1.5, "scale 1 が scale 4 より相関している");
    }

    @Test
    @DisplayName("scale が 1 未満・0・負でも 1 として扱う")
    public void test小さいscaleは丸める() {
        // 式が実行時に持っていった場合の逃げ場。リテラルはロード時に断るので、
        // ここに来るのは `noise(tier - 5)` のような形だけ。
        double atOne = at(3, 64, 5, 1);

        assertEquals(atOne, at(3, 64, 5, 0.5), "0.5 が 1 に丸められていない");
        assertEquals(atOne, at(3, 64, 5, 0), "0 が 1 に丸められていない");
        assertEquals(atOne, at(3, 64, 5, -8), "負が 1 に丸められていない");
    }

    // ============================================
    // 6. ホワイトノイズとの対比
    // ============================================

    @Test
    @DisplayName("同じ座標を random() の系統で引くと隣接差が桁違いに大きい")
    public void testホワイトノイズとの差() {
        // 「相関がある」の対比。同じ座標・同じ種で、既存の per-position 抽選を並べる。
        double noiseStep = averageNeighbourStep(16);

        double whiteTotal = 0;
        int samples = 0;
        for (int x = -100; x < 100; x++) {
            double here = SeedMixer.toUnitInterval(SeedMixer.forPosition(SEED, x, 64, 0), SeedMixer.RANDOM);
            double next = SeedMixer.toUnitInterval(SeedMixer.forPosition(SEED, x + 1, 64, 0), SeedMixer.RANDOM);
            whiteTotal += Math.abs(here - next);
            samples++;
        }
        double whiteStep = whiteTotal / samples;

        assertTrue(
            whiteStep > noiseStep * 5,
            "ホワイトノイズの隣接差 " + whiteStep + " が noise(16) の " + noiseStep + " の 5 倍に届かない");
    }

    @Test
    @DisplayName("格子点をずらす系統が他と衝突していない")
    public void test系統が独立している() {
        // NOISE を既存の系統と同じ番号にすると、noise が chance と同じ値を返す。
        for (int x = 0; x < 20; x++) {
            long position = SeedMixer.forPosition(SEED, x, 64, 0);

            double noise = SeedMixer.toUnitInterval(position, SeedMixer.NOISE);
            assertTrue(
                noise != SeedMixer.toUnitInterval(position, SeedMixer.RANDOM),
                "x=" + x + " で NOISE と RANDOM が同じ値");
            assertTrue(
                noise != SeedMixer.toUnitInterval(position, SeedMixer.PATTERN_CELL_CHANCE),
                "x=" + x + " で NOISE と PATTERN_CELL_CHANCE が同じ値");
            assertTrue(
                noise != SeedMixer.toUnitInterval(position, SeedMixer.PATTERN_CELL_WEIGHT),
                "x=" + x + " で NOISE と PATTERN_CELL_WEIGHT が同じ値");
        }
    }
}
