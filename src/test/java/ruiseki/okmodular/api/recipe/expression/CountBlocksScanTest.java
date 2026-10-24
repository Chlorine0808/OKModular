package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `count_blocks` の走査範囲。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * 式の中で計算量に上限が無かったのは `count_blocks(d)` だけだった。毎回 (2d+1)³ 個の
 * ブロックを読み、d に上限が無く、読み込まれていないチャンクにも触れてロードさせていた。
 * 待機中の機械はレシピ検索を繰り返すので、条件に書くとこれが毎回走る。
 *
 * `World` はテストで組めないので、走査を問い合わせ口（`Probe`）越しの静的メソッドに
 * 切り出し、ここで範囲だけを固定する。
 */
@DisplayName("count_blocks の走査範囲")
public class CountBlocksScanTest {

    /** Every loaded position counts; records what was asked. */
    private static final class Recorder implements CountBlocksFunctionExpression.Probe {

        final Set<Long> unloadedChunks = new HashSet<>();
        int maxReach;
        int reads;

        @Override
        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            return !unloadedChunks.contains(key(chunkX, chunkZ));
        }

        @Override
        public boolean counts(int x, int y, int z) {
            reads++;
            maxReach = Math.max(maxReach, Math.max(Math.abs(x), Math.abs(z)));
            return true;
        }

        static long key(int chunkX, int chunkZ) {
            return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        }
    }

    @Test
    @DisplayName("中心を除いた立方体を数える")
    public void test立方体() {
        Recorder probe = new Recorder();

        long count = CountBlocksFunctionExpression.scan(0, 64, 0, 1, probe);

        assertEquals(26, count);
    }

    @Test
    @DisplayName("距離は上限で打ち切る")
    public void test距離の上限() {
        Recorder probe = new Recorder();

        CountBlocksFunctionExpression.scan(0, 64, 0, 1000, probe);

        assertEquals(CountBlocksFunctionExpression.MAX_DISTANCE, probe.maxReach);
    }

    @Test
    @DisplayName("読み込まれていないチャンクには触れない")
    public void test未読み込みのチャンク() {
        Recorder probe = new Recorder();
        probe.unloadedChunks.add(Recorder.key(-1, 0));

        long count = CountBlocksFunctionExpression.scan(0, 64, 0, 1, probe);

        // x = -1 lies in chunk -1; with z in -1..1, z = -1 is chunk -1 too, so only x = -1, z >= 0 drops.
        assertEquals(26 - 6, count);
    }

    @Test
    @DisplayName("ワールドの高さの外は読まない")
    public void test高さの外() {
        Recorder probe = new Recorder();

        long count = CountBlocksFunctionExpression.scan(0, 0, 0, 1, probe);

        assertEquals(17, count);
        assertEquals(17, probe.reads, "y = -1 の 9 マスを読んでいる");
    }
}
