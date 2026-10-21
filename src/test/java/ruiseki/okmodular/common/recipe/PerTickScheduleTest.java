package ruiseki.okmodular.common.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * 速度倍率がついた機械が、**毎 tick の資源をいくつ引くか**の検証。
 *
 * ============================================
 * 「要実測」を実測しないで済ませる
 * ============================================
 *
 * 項目 M は長らく「実機で測らないと分からない」ままだった。
 * だが取りこぼしが起きるかどうかは、`ProcessAgent` の 2 行だけで決まる。
 *
 * <pre>
 * workProgress += speedMultiplier; // 実数で貯める
 * progress = (long) workProgress; // 整数に落とす
 * </pre>
 *
 * かつての判定は `progress % interval == 0`、つまり
 * 「**その値ちょうど**を踏んだか」だった。progress は倍率が 1 でないと素直に動かない:
 *
 * <pre>
 * 1.5 倍 → 0, 1, 3, 4, 6, 7   値を飛ばす  → 踏まれない倍数は永久に来ない（払わない）
 * 0.5 倍 → 0, 0, 1, 1, 2, 2   値が居座る  → 同じ倍数を何度も踏む（二重に払う）
 * </pre>
 *
 * **1 倍より速い機械は不足しかせず、遅い機械は過剰にしか払わない。**
 * 1 tick に 1 より多く進めば飛ばせるが戻れず、1 より少なく進めば居座れるが飛べないから。
 * 正しかったのはちょうど 1 倍だけだった。
 *
 * ============================================
 * 直した形 — 「またいだ数」を数える
 * ============================================
 *
 * `dueCount` は区間 `[前, 後)` に入る倍数の個数を返す。これが `process` / `apply` に渡す
 * 倍率になるので、**1 tick に 2 回ぶん払うことがある**。
 *
 * 効くのは総量の不変条件:
 *
 * <pre>
 * レシピ 1 回の抽出回数 = ceil(duration / interval)   ← 速度によらず一定
 * </pre>
 *
 * これが無いと「このレシピは 10 万 EU」を NEI に書けない。答えが機械の速度で変わるので。
 * **倍率 1.0 の機械は 1 回も変わりません**（区間を半開にしてあるのはそのため。
 * 閉じ方を逆にすると duration 100・間隔 3 が 34 回から 33 回に落ちる）。
 * つまり動くのは倍率が 1 でない機械だけ。
 *
 * ============================================
 * ここで測るのは模型
 * ============================================
 *
 * `ProcessAgent.executeTick` は `World` も TileEntity も要るのでゲームの外では回せない。
 * だから**進み方だけを写した模型**を回す。写しである以上ずれうるので、
 * 本物がその形のままであることを別のテストで縛る。
 * 模型が古くなったら赤くなるので、緑の模型が嘘をつくことは無い。
 */
@DisplayName("速度倍率と毎 tick 資源の回数")
public class PerTickScheduleTest {

    @Test
    @DisplayName("レシピ 1 回の総回数が速度で変わらない")
    public void test総量が速度に依らない() {
        // ここが M の全部。duration 100・間隔 1 なら、どの速度でも 100 回。
        // 間隔が duration を割り切らないときは切り上げ（100/3 → 34、100/7 → 15）。
        for (double speed : new double[] { 0.25, 0.5, 1.0, 1.5, 2.0, 3.0, 7.5 }) {
            assertEquals(100, totalDraws(100, speed, 1), speed + " 倍・間隔 1 の総回数");
            assertEquals(50, totalDraws(100, speed, 2), speed + " 倍・間隔 2 の総回数");
            assertEquals(34, totalDraws(100, speed, 3), speed + " 倍・間隔 3 の総回数");
            assertEquals(20, totalDraws(100, speed, 5), speed + " 倍・間隔 5 の総回数");
            assertEquals(15, totalDraws(100, speed, 7), speed + " 倍・間隔 7 の総回数");
        }
    }

    @Test
    @DisplayName("1 倍の機械は 1 回も変わらない")
    public void test等速は据え置き() {
        // 移行の安全性はここに乗っている。1 倍の機械だけは前後でぴったり同じ回数でなければ、
        // 「既存レシピ全部の消費量が動く」が本当になってしまう。
        // 区間を [前, 後) と半開にしてあるのはこのため。(前, 後] だと間隔が duration を
        // 割り切らないとき、最後の半端な区間ぶんが丸ごと無料になる。
        for (int interval : new int[] { 1, 2, 3, 5, 7, 13, 100, 101 }) {
            assertEquals(
                oldTotalDraws(100, 1.0, interval),
                totalDraws(100, 1.0, interval),
                "等速・間隔 " + interval + " で回数が変わった");
        }
    }

    @Test
    @DisplayName("速い機械は 1 tick に 2 回ぶん払うことがある")
    public void test速い機械の並び() {
        // 平均は必ず 倍率 ÷ 間隔。間隔 1 で 1.5 倍なら 1.5/tick なので 1, 2 が交互。
        assertEquals(List.of(1, 2, 1, 2, 1, 2), drawsPerTick(6, 1.5, 1), "1.5 倍・間隔 1");

        // 間隔 2 なら 0.75/tick。4 tick に 3 回。
        assertEquals(List.of(1, 1, 0, 1, 1, 1, 0, 1), drawsPerTick(8, 1.5, 2), "1.5 倍・間隔 2");

        // 3 倍・間隔 2 も平均 1.5 なので 2 と 1 が交互になる。
        assertEquals(List.of(2, 1, 2, 1), drawsPerTick(4, 3.0, 2), "3.0 倍・間隔 2");
    }

    @Test
    @DisplayName("止まっている機械は払わない")
    public void test倍率0は引かない() {
        // `"speedMultiplier": "0"` は書ける。旧実装では progress 0 に居座り、
        // **0 はあらゆる間隔の倍数**なので毎 tick 引かれ続けた。進まないのに払う機械。
        assertEquals(List.of(0, 0, 0, 0), drawsPerTick(4, 0.0, 1), "進まないのに引いている");
        assertEquals(4, oldFirings(4, 0.0, 1), "旧実装は毎 tick 引いていた（この行が記録）");
    }

    @Test
    @DisplayName("遅い機械は引かない tick がある")
    public void test遅い機械の並び() {
        // 0.5 倍・間隔 1 は 2 tick に 1 回。かつては毎 tick 引いていた（2 倍払い）。
        assertEquals(List.of(0, 1, 0, 1, 0, 1), drawsPerTick(6, 0.5, 1), "0.5 倍・間隔 1");
        assertEquals(200, oldTotalDraws(100, 0.5, 1), "旧: 0.5 倍は仕事 100 に対して 200 回払っていた");
        assertEquals(100, totalDraws(100, 0.5, 1), "新: 仕事どおりの 100 回");
    }

    @Test
    @DisplayName("旧実装が取りこぼしていた値を再現する")
    public void test旧実装の取りこぼし() {
        // 記録として残す。直したことの意味は、直す前の数字が無いと読めない。
        assertEquals(50, oldFirings(100, 1.5, 2), "1.5 倍・間隔 2 は 100 tick で 50 回だった（本来 75）");
        assertEquals(34, oldFirings(100, 2.0, 3), "2.0 倍・間隔 3 は 6 の倍数でしか来なかった（本来 67）");
        assertEquals(50, oldFirings(100, 1.0, 2), "等速だけは正しかった");
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
    @DisplayName("間隔 0 以下は毎 tick 資源ではない")
    public void test間隔なし() {
        assertEquals(0, PerTickSchedule.dueCount(0, 10, 0), "間隔 0 で引かれている。0 除算ではなく「毎 tick 資源ではない」の意味");
        assertEquals(0, PerTickSchedule.dueCount(0, 10, -1), "負の間隔で引かれている");
    }

    @Test
    @DisplayName("進まない tick は引かない")
    public void test進まなければ引かない() {
        assertEquals(0, PerTickSchedule.dueCount(5, 5, 1), "進んでいないのに引いている");
        assertEquals(0, PerTickSchedule.dueCount(5, 4, 1), "戻っているのに引いている");
    }

    @Test
    @DisplayName("模型が本物と同じ進み方をしている")
    public void test模型が本物と揃っている() {
        // ここが緑でないと、上の 8 つは「かつての ProcessAgent」を測っているだけになる。
        String source = JavaSource
            .stripComments(JavaSource.read(JavaSource.MAIN.resolve("common/recipe/ProcessAgent.java")));
        String executeTick = JavaSource.methodBody(source, "public void executeTick(")
            .replaceAll("\\s+", "");
        String planTick = JavaSource.methodBody(source, "private void planTick(")
            .replaceAll("\\s+", "");

        assertTrue(executeTick.contains("workProgress+=speedMultiplier;"), "進み方が変わった。模型を合わせ直すこと");
        assertTrue(executeTick.contains("progress=(long)workProgress;"), "整数への落とし方が変わった。模型を合わせ直すこと");
        assertTrue(planTick.contains("plannedFrom=(long)workProgress;"), "区間の始まりが変わった。模型を合わせ直すこと");
        assertTrue(planTick.contains("plannedTo=(long)reached;"), "区間の終わりが変わった。模型を合わせ直すこと");
        assertTrue(planTick.contains("reached>maxProgress"), "完成ぶんの打ち切りが消えた。総量が duration を超える");
    }

    @Test
    @DisplayName("確認と消費が同じ数を使っている")
    public void test確認と消費が食い違わない() {
        // 確認が 1 を見て消費が 2 取る、が最悪の壊れ方。確認できていない資源を引くので、
        // ポートが空になるか、負の残量になる。**綴りを 1 つにするのがその防ぎ方。**
        String source = JavaSource
            .stripComments(JavaSource.read(JavaSource.MAIN.resolve("common/recipe/ProcessAgent.java")));
        String packed = source.replaceAll("\\s+", "");

        assertEquals(
            0,
            countOf(packed, "progress%"),
            "生の progress %% interval が復活している。ProcessAgent は dueThisTick だけを通ること");
        assertEquals(5, countOf(packed, "dueThisTick("), "確認 2 箇所・消費 2 箇所・定義 1 箇所で 5。数が変わった");
        assertEquals(
            1,
            countOf(packed, "PerTickSchedule.dueCount("),
            "dueCount を呼ぶ場所が 1 つでなくなった。倍率の解決が 2 回に増えると確認と消費がずれる");
    }

    // ============================================================

    /** `ProcessAgent` の進み方を写した模型。数え方そのものは本物を呼ぶ。 */
    private static List<Integer> drawsPerTick(int ticks, double speedMultiplier, int interval) {
        List<Integer> drawn = new ArrayList<>();
        double workProgress = 0;

        for (int tick = 0; tick < ticks; tick++) {
            long from = (long) workProgress;
            workProgress += speedMultiplier;
            drawn.add(PerTickSchedule.dueCount(from, (long) workProgress, interval));
        }
        return drawn;
    }

    /** レシピ 1 回を最後まで回したときの総回数。打ち切りも含めて本物と同じ形。 */
    private static int totalDraws(int duration, double speedMultiplier, int interval) {
        int drawn = 0;
        double workProgress = 0;

        while (workProgress < duration) {
            long from = (long) workProgress;
            double reached = Math.min(workProgress + speedMultiplier, duration);
            drawn += PerTickSchedule.dueCount(from, (long) reached, interval);
            workProgress += speedMultiplier;
        }
        return drawn;
    }

    /** 同じレシピを旧実装で回したときの総回数。 */
    private static int oldTotalDraws(int duration, double speedMultiplier, int interval) {
        int fired = 0;
        double workProgress = 0;

        while (workProgress < duration) {
            if (interval > 0 && (long) workProgress % interval == 0) fired++;
            workProgress += speedMultiplier;
        }
        return fired;
    }

    /** 旧実装を tick 数で切ったときの回数。 */
    private static int oldFirings(int ticks, double speedMultiplier, int interval) {
        int fired = 0;
        double workProgress = 0;

        for (int tick = 0; tick < ticks; tick++) {
            if (interval > 0 && (long) workProgress % interval == 0) fired++;
            workProgress += speedMultiplier;
        }
        return fired;
    }

    private static List<Long> progressValues(int ticks, double speedMultiplier) {
        List<Long> seen = new ArrayList<>();
        double workProgress = 0;

        for (int tick = 0; tick < ticks; tick++) {
            seen.add((long) workProgress);
            workProgress += speedMultiplier;
        }
        return seen;
    }

    private static int countOf(String haystack, String needle) {
        int found = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) found++;
        return found;
    }
}
