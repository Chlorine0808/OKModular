package ruiseki.okmodular.structure.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.expression.ExpressionParser;
import ruiseki.okmodular.api.recipe.expression.StubMachineContext;

/**
 * K2 — `noise(scale)` をパターンから使ったときに塊になることの検証。
 *
 * ============================================
 * 一番落ちやすいのは種の取り違え
 * ============================================
 *
 * ⚠ セルの文脈の種（`getEvaluationSeed`）は**セルごとに違います** ——
 * `random()` を散らすために `forPosition` で位置を混ぜてあるからです。
 * その種で格子点をハッシュすると、格子ごとに別の種でハッシュすることになり、
 * **なめらかさが消えてホワイトノイズに戻ります**。
 *
 * だから `noise()` は `getPlacementSeed()`（位置を混ぜる前の、配置全体で同じ種）を使います。
 * 取り違えると下の「隣接セルの差が小さい」がここで落ちます。
 *
 * ============================================
 * `ValueNoiseTest` との分担
 * ============================================
 *
 * 場そのものの性質（再現性・相関・範囲・分布）は `ValueNoiseTest` が純関数として縛っています。
 * こちらが見るのは**配線**だけ ——
 * 式から呼べること、正しい種と正しい座標が渡っていること、パターンの中で塊になること。
 */
@DisplayName("パターンのノイズ")
public class PatternNoiseTest {

    private static CellMapping mappingOf(String value) {
        return StructurePatternParser
            .parse(
                new JsonParser()
                    .parse(
                        ("{ 'name': 'x', 'mappings': { 'S': " + value + " }, 'layers': [ ['S'] ] }").replace('\'', '"'))
                    .getAsJsonObject(),
                "test.json")
            .cellsFor(null)
            .get(0).mapping;
    }

    // ============================================
    // 配線
    // ============================================

    @Test
    @DisplayName("式から noise() を呼べる")
    public void test式から呼べる() {
        double value = ExpressionParser.parseExpression("noise(8)")
            .evaluateDouble(StubMachineContext.withSeed(4649L));

        assertTrue(value >= 0 && value < 1, "値が [0,1) の外: " + value);
    }

    @Test
    @DisplayName("配置全体で同じ種を使う（セルごとの種ではない）")
    public void test配置の種を使う() {
        ConditionContext machine = StubMachineContext.withSeed(4649L);

        // セルの文脈は種が位置ごとに違うが、placementSeed は機械のものと同じであること
        ConditionContext cell = ConditionContext.forCell(machine, 10, 64, -20, 0, 0, 0);

        assertEquals(machine.getEvaluationSeed(), cell.getPlacementSeed(), "配置の種が引き継がれていない");
        assertTrue(cell.getEvaluationSeed() != cell.getPlacementSeed(), "セルの種と配置の種が同じ。位置が混ざっていない");

        // forDraw も配置の種を保つ（バッチの 2 回目でノイズの場が変わってはいけない）
        assertEquals(
            cell.getPlacementSeed(),
            cell.forDraw(1)
                .getPlacementSeed(),
            "forDraw が配置の種を落としている");
    }

    @Test
    @DisplayName("noise() はそのセルの座標で評価される")
    public void testセルの座標で評価される() {
        ConditionContext machine = StubMachineContext.withSeed(4649L);

        double here = ExpressionParser.parseExpression("noise(8)")
            .evaluateDouble(ConditionContext.forCell(machine, 0, 64, 0, 0, 0, 0));
        double faraway = ExpressionParser.parseExpression("noise(8)")
            .evaluateDouble(ConditionContext.forCell(machine, 500, 64, 500, 0, 0, 0));

        assertTrue(here != faraway, "離れた 2 セルで同じ値。座標が渡っていない（機械の座標を見ている？）");
    }

    // ============================================
    // 塊になること — K2 の目的
    // ============================================

    @Test
    @DisplayName("noise を閾値で切ると塊になる")
    public void test閾値で切ると塊になる() {
        // 同じ列を 1 本走って、置いた / 置かないの切り替わり回数を数える。
        // 塊なら少なく、均一に散っていれば多い。
        int blobs = switchesAlongRow("{ 'block': 'minecraft:stone', 'chance': 'noise(8) > 0.5' }");
        int scattered = switchesAlongRow("{ 'block': 'minecraft:stone', 'chance': 'random()' }");

        assertTrue(blobs * 5 < scattered, "閾値の切り替わり " + blobs + " 回 / random の " + scattered + " 回。塊になっていない");
    }

    @Test
    @DisplayName("⚠ noise を確率として渡すだけでは塊にならない")
    public void test確率として渡すと塊にならない() {
        // **これが一番踏みやすい罠。** `chance` の値がなめらかでも、置くかどうかの判定は
        // 依然としてセルごとの独立な抽選なので、noise が 0.5 付近の領域では 50/50 の
        // 散らばりがそのまま出る。塊になるのは場が 0 や 1 に寄っている所だけ。
        //
        // docs にこの形を「塊になる」と書いていて、このテストが誤りを捕まえた。
        int asProbability = switchesAlongRow("{ 'block': 'minecraft:stone', 'chance': 'noise(8)' }");
        int asThreshold = switchesAlongRow("{ 'block': 'minecraft:stone', 'chance': 'noise(8) > 0.5' }");

        assertTrue(
            asProbability > asThreshold * 5,
            "確率として渡した切り替わり " + asProbability + " 回 / 閾値の " + asThreshold + " 回。" + "確率として渡しても塊になっているなら、抽選が働いていない");
    }

    /** x = 0..199 を走って、置く / 置かないが切り替わった回数。 */
    private static int switchesAlongRow(String mapping) {
        CellMapping.Resolved resolved = mappingOf(mapping).resolve(StubMachineContext.withSeed(4649L));

        int switches = 0;
        boolean previous = resolved.blockFor(0, 64, 0, 0, 0, 0) != null;
        for (int x = 1; x < 200; x++) {
            boolean placed = resolved.blockFor(x, 64, 0, x, 0, 0) != null;
            if (placed != previous) switches++;
            previous = placed;
        }
        return switches;
    }

    @Test
    @DisplayName("鉱脈の形は半径の外へ出ない（どの実行でも）")
    public void test鉱脈は半径に収まる() {
        // 距離の項が負になるので、閾値を通ったセルでも半径の外では必ず外れる。
        // ここは場に依らないので少数の種で足りる。
        for (long seed : new long[] { 1L, 2L, 20260811L, -7L }) {
            CellMapping.Resolved vein = mappingOf(VEIN).resolve(StubMachineContext.withSeed(seed));

            assertEquals(null, vein.blockFor(9, 64, 0, 9, 0, 0), "種 " + seed + " で半径 8 の外に置いている");
            assertEquals(null, vein.blockFor(20, 64, 20, 20, 0, 20), "種 " + seed + " で遠くに置いている");
        }
    }

    @Test
    @DisplayName("鉱脈の形は平均すると中心ほど濃い")
    public void test鉱脈は中心ほど濃い() {
        // ⚠ **1 つの種では見えません。** 閾値で切ると距離の項は「塊の内側」にしか効かないので、
        // アンカーが塊の外に落ちた実行では中心が空になります（最初それで落ちた）。
        // 距離が効いていることは実行をまたいだ平均に出ます。
        double near = 0;
        double far = 0;
        int runs = 20;

        for (long seed = 1; seed <= runs; seed++) {
            CellMapping.Resolved vein = mappingOf(VEIN).resolve(StubMachineContext.withSeed(seed));
            near += densityInShell(vein, 0, 2);
            far += densityInShell(vein, 6, 7);
        }
        near /= runs;
        far /= runs;

        assertTrue(near > far * 2, runs + " 実行の平均で 中心 " + near + " / 外 " + far + "。距離が効いていない");
        assertTrue(near > 0.2, "中心付近の平均密度が " + near + "。閾値が厳しすぎて塊が出ていない");
        assertTrue(far > 0, "外側が 0。距離の項が半径の内側で切り落としている");
    }

    /** docs に載せている鉱脈の形。 */
    private static final String VEIN = "{ 'block': 'minecraft:iron_ore', 'chance': "
        + "'noise(6) > 0.45 ? 1 - sqrt(cell_x^2 + cell_y^2 + cell_z^2) / 8 : 0' }";

    private static double densityInShell(CellMapping.Resolved mapping, int from, int to) {
        int placed = 0;
        int total = 0;
        for (int dx = -to; dx <= to; dx++) {
            for (int dz = -to; dz <= to; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < from || distance > to) continue;

                total++;
                if (mapping.blockFor(dx, 64, dz, dx, 0, dz) != null) placed++;
            }
        }
        return total == 0 ? 0 : (double) placed / total;
    }

    @Test
    @DisplayName("同じ実行の中では何度聞いても同じ")
    public void test決定的である() {
        CellMapping.Resolved mapping = mappingOf("{ 'block': 'minecraft:stone', 'chance': 'noise(8)' }")
            .resolve(StubMachineContext.withSeed(7L));

        for (int x = 0; x < 30; x++) {
            assertEquals(
                mapping.blockFor(x, 64, 3, x, 0, 3),
                mapping.blockFor(x, 64, 3, x, 0, 3),
                "x=" + x + " で答えが変わった");
        }
    }

    @Test
    @DisplayName("実行が変われば別の場になる")
    public void test実行ごとに変わる() {
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();

        CellMapping.Resolved one = mappingOf("{ 'block': 'minecraft:stone', 'chance': 'noise(8)' }")
            .resolve(StubMachineContext.withSeed(1L));
        CellMapping.Resolved other = mappingOf("{ 'block': 'minecraft:stone', 'chance': 'noise(8)' }")
            .resolve(StubMachineContext.withSeed(2L));

        for (int x = 0; x < 60; x++) {
            first.append(one.blockFor(x, 64, 0, x, 0, 0) == null ? '.' : '#');
            second.append(other.blockFor(x, 64, 0, x, 0, 0) == null ? '.' : '#');
        }

        assertTrue(
            !first.toString()
                .equals(second.toString()),
            "実行を変えても同じ並びになった");
    }

    // ============================================
    // 書き損じ
    // ============================================

    @Test
    @DisplayName("引数の個数")
    public void test引数の個数() {
        assertThrows(RuntimeException.class, () -> ExpressionParser.parseExpression("noise()"), "引数無しが通る");
        assertThrows(RuntimeException.class, () -> ExpressionParser.parseExpression("noise(8, 2)"), "2 個目が通る");
    }

    @Test
    @DisplayName("リテラルの scale が 1 未満ならロード時に断る")
    public void test小さいリテラルは断る() {
        // 1 ブロックが最も細かい格子で、それは random() そのもの。それ以下に意味は無い。
        assertThrows(RuntimeException.class, () -> ExpressionParser.parseExpression("noise(0)"), "0 が通る");
        assertThrows(RuntimeException.class, () -> ExpressionParser.parseExpression("noise(-8)"), "負が通る");
        assertThrows(RuntimeException.class, () -> ExpressionParser.parseExpression("noise(0.5)"), "0.5 が通る");

        // 式は機械次第なので断れない。実行時に 1 へ丸める（ValueNoiseTest が値で縛っている）
        assertEquals(
            ExpressionParser.parseExpression("noise(1)")
                .evaluateDouble(StubMachineContext.withSeed(3L)),
            ExpressionParser.parseExpression("noise(tier - 3)")
                .evaluateDouble(StubMachineContext.withSeed(3L)),
            "式が 0 に落ちたとき 1 として扱われていない");
    }

    @Test
    @DisplayName("noise を含む式が往復する")
    public void test往復する() {
        ConditionContext context = StubMachineContext.withSeed(4649L);

        String source = "noise(8) * 2";
        double once = ExpressionParser.parseExpression(source)
            .evaluateDouble(context);
        double twice = ExpressionParser.parseExpression(
            ExpressionParser.parseExpression(source)
                .toString())
            .evaluateDouble(context);

        assertEquals(once, twice, "書き出して読み直すと値が変わる");
    }
}
