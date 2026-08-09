package ruiseki.okmodular.common.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * 速度倍率がついた機械で、**毎 tick の資源が何回引かれるか**の検証。
 *
 * ============================================
 * 「要実測」を実測しないで済ませる
 * ============================================
 *
 * 項目 M は長らく「実機で測らないと分からない」ままだった。
 * だが取りこぼしが起きるかどうかは、`ProcessAgent` の 2 行だけで決まる。
 *
 * <pre>
 * workProgress += speedMultiplier;   // 実数で貯める
 * progress = (long) workProgress;    // 整数に落とす
 * ...
 * PerTickSchedule.isDue(progress, interval)   // progress % interval == 0
 * </pre>
 *
 * 倍率が整数でないと **progress は値を飛ぶ**。1.5 倍なら 0, 1, 3, 4, 6, 7, 9 と進み、
 * 2 や 5 や 8 には一度もならない。`progress % interval == 0` は
 * 「その値ちょうど」でしか成立しないので、**飛ばされた倍数は永久に来ない**。
 *
 * 機械は仕事だけ進めて、代金を払わない。
 *
 * ============================================
 * ここで測るのは模型
 * ============================================
 *
 * `ProcessAgent.executeTick` は `World` も TileEntity も要るのでゲームの外では回せない。
 * だから**進み方だけを写した模型**を回す。写しである以上ずれうるので、
 * 本物の 2 行がその形のままであることを別のテストで縛る。
 * 模型が古くなったら赤くなるので、緑の模型が嘘をつくことは無い。
 */
@DisplayName("速度倍率と毎 tick 資源の取りこぼし")
public class PerTickScheduleTest {

    @Test
    @DisplayName("等速なら間隔どおりに引かれる")
    public void test倍率1倍() {
        // 100 tick で progress は 0..99。2 の倍数はちょうど 50 個。
        assertEquals(50, firings(100, 1.0, 2), "等速で取りこぼしが出ている");
        assertEquals(34, firings(100, 1.0, 3), "0, 3, 6, ... 99 で 34 回");
    }

    @Test
    @DisplayName("1.5 倍は仕事に見合う回数より少なく引く")
    public void test倍率1_5倍で足りない() {
        // 100 tick で仕事は 150 進む。間隔 2 なら本来 75 回ぶんの代金がかかる。
        int actual = firings(100, 1.5, 2);

        assertEquals(50, actual, "1.5 倍・間隔 2 の実際の回数");
        assertTrue(actual < 75, "仕事量に見合う回数 (75) より少ないはずが、そうなっていない");
    }

    @Test
    @DisplayName("倍率が整数なら取りこぼしは起きない")
    public void test倍率2倍() {
        // progress は 0, 2, 4, 6 ... と進むので 2 の倍数だけを踏む。間隔 2 は毎 tick 成立。
        assertEquals(100, firings(100, 2.0, 2), "2 倍・間隔 2 は毎 tick 引かれるはず");
        // 間隔 3 は 0, 6, 12 ... でしか成立しない。3 は progress が飛ばす。
        assertEquals(34, firings(100, 2.0, 3), "2 倍・間隔 3 は 6 の倍数でしか来ない");
    }

    @Test
    @DisplayName("飛ばされる progress の値がある")
    public void test飛ばされる値() {
        List<Long> seen = progressValues(8, 1.5);

        assertEquals(List.of(0L, 1L, 3L, 4L, 6L, 7L, 9L, 10L), seen, "1.5 倍の progress の並びが変わった");
        assertTrue(!seen.contains(2L), "2 は飛ばされるはず");
        assertTrue(!seen.contains(5L), "5 は飛ばされるはず");
    }

    @Test
    @DisplayName("間隔 0 以下は毎 tick 判定に出てこない")
    public void test間隔なし() {
        assertTrue(!PerTickSchedule.isDue(0, 0), "間隔 0 で成立している。0 除算ではなく「毎 tick 資源ではない」の意味");
        assertTrue(!PerTickSchedule.isDue(10, -1), "負の間隔で成立している");
    }

    @Test
    @DisplayName("模型が本物と同じ進み方をしている")
    public void test模型が本物と揃っている() {
        // ここが緑でないと、上の 5 つは「かつての ProcessAgent」を測っているだけになる。
        String body = JavaSource.methodBody(
            JavaSource.stripComments(JavaSource.read(JavaSource.MAIN.resolve("common/recipe/ProcessAgent.java"))),
            "public void executeTick(");
        String packed = body.replaceAll("\\s+", "");

        assertTrue(packed.contains("workProgress+=speedMultiplier;"), "進み方が変わった。模型 (firings) を合わせ直すこと");
        assertTrue(packed.contains("progress=(long)workProgress;"), "整数への落とし方が変わった。模型を合わせ直すこと");
        assertTrue(packed.contains("PerTickSchedule.isDue(progress,"), "毎 tick 判定が isDue を通らなくなった。模型が別物を測っている");
    }

    // ============================================================

    /** `ProcessAgent` の進み方を写した模型。判定そのものは本物を呼ぶ。 */
    private static int firings(int ticks, double speedMultiplier, int interval) {
        int fired = 0;
        double workProgress = 0;
        long progress = 0;

        for (int tick = 0; tick < ticks; tick++) {
            // 判定は進める前の progress で行われる（tick() も executeTick() も同じ値を見る）。
            if (PerTickSchedule.isDue(progress, interval)) fired++;
            workProgress += speedMultiplier;
            progress = (long) workProgress;
        }
        return fired;
    }

    private static List<Long> progressValues(int ticks, double speedMultiplier) {
        List<Long> seen = new ArrayList<>();
        double workProgress = 0;
        long progress = 0;

        for (int tick = 0; tick < ticks; tick++) {
            seen.add(progress);
            workProgress += speedMultiplier;
            progress = (long) workProgress;
        }
        return seen;
    }
}
