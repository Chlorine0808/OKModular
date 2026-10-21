package ruiseki.okmodular.structure.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.expression.ExpressionParser;
import ruiseki.okmodular.api.recipe.expression.StubMachineContext;
import ruiseki.okmodular.structure.StructureCellLocator;

/**
 * K1 — `cell_x` / `cell_y` / `cell_z` がセルに届いていることの検証。
 *
 * ============================================
 * K1 が何のためにあるか
 * ============================================
 *
 * B / C で `chance` が式で書けるようになったが、**その値は全セルで同じ数**だった。
 * 2109 セルが独立に同じ重みのコインを投げるので、出るのは均一で、
 * 鉱脈には見えない。濃淡を作るには濃さごとに別の文字を用意して層を手で描き分けるしかなく、
 * 半径 8 なら 4913 文字で殻が 8 枚になる。
 *
 * K1 は式に「そのセルがどこにいるか」を渡して、殻を式に作らせる。
 *
 * ============================================
 * パターンの軸で測る
 * ============================================
 *
 * `cell_*` は**アンカーからそのセルまでのズレを，パターンの軸で測ったもの**．
 * 北向きの横置き機械では世界の軸と一致する．以前は世界の軸で測っていたが，縦置きの機械で
 * 層が行に移ると `cell_y` が水平を向き，形が崩れた（`PatternLocalCellTest` を参照）．
 * 
 * ============================================
 * 一番大事なのは 3 番目のテスト
 * ============================================
 *
 * セルごとに `ConditionContext` を作る配線が新規で、そこで **`recipeContext` を渡し忘れると
 * 機械プロパティが黙って 0 になる**（`_SSOT` の「文脈を間違えて渡す（silent zero）」。
 * `fix-expression` が 7 箇所で全滅させた形）。
 *
 * 例外は出ない。`"chance": "tier * 0.5"` が「tier 0 なので何も置かない」になるだけ。
 * だから `ConditionContext.forCell` は機械の文脈を**引数に取って全部写す**形にしてあり、
 * ここでその結果を値で縛る。
 */
@DisplayName("パターンのセル座標")
public class PatternCellCoordinateTest {

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

    private static CellMapping.Resolved resolvedOf(String value) {
        return mappingOf(value).resolve(StubMachineContext.withMachine());
    }

    // ============================================
    // 変数がセルに届いているか
    // ============================================

    @Test
    @DisplayName("cell_y で硬い形が作れる（乱数を通さない 0 / 1）")
    public void test硬い形が作れる() {
        // 比較は 1.0 か 0.0 を返す。1.0 は抽選を飛ばし、0.0 は必ず外れる。
        // 「アンカーとその下だけ」= cell_y <= 0
        CellMapping.Resolved mapping = resolvedOf("{ 'block': 'minecraft:stone', 'chance': 'cell_y <= 0' }");

        assertEquals("minecraft:stone", mapping.blockFor(0, 64, 0, 0, 0, 0), "アンカーのセルに置いていない");
        assertEquals("minecraft:stone", mapping.blockFor(0, 63, 0, 0, -1, 0), "1 つ下のセルに置いていない");
        assertEquals(null, mapping.blockFor(0, 65, 0, 0, 1, 0), "1 つ上のセルに置いている");
        assertEquals(null, mapping.blockFor(0, 68, 0, 0, 4, 0), "4 つ上のセルに置いている");
    }

    @Test
    @DisplayName("距離で輪郭がぼける（K1 の目的そのもの）")
    public void test距離で濃さが変わる() {
        // 半径 8 の球。中心は必ず置き、外は必ず置かない。
        CellMapping.Resolved mapping = resolvedOf(
            "{ 'block': 'minecraft:stone', 'chance': '1 - sqrt(cell_x^2 + cell_y^2 + cell_z^2) / 8' }");

        assertEquals("minecraft:stone", mapping.blockFor(0, 64, 0, 0, 0, 0), "中心（chance 1.0）に置いていない");
        assertEquals(null, mapping.blockFor(9, 64, 0, 9, 0, 0), "半径 8 の外（chance 負）に置いている");

        // 帯ごとの**密度**が落ちること。個数で比べると外の帯のほうがセル数が多いので、
        // 密度が 1/6 に落ちていても個数はほぼ並ぶ（最初はそれで空振りした）。
        double near = densityInShell(mapping, 1, 2);
        double far = densityInShell(mapping, 6, 7);

        assertTrue(near > 0.6, "中心寄りの密度が " + near + "。chance 0.75〜0.875 のはず");
        assertTrue(far < 0.4, "外寄りの密度が " + far + "。chance 0.125〜0.25 のはず");
        assertTrue(near > far * 1.5, "中心 " + near + " / 外 " + far + "。距離が効いていない");
    }

    /** アンカーから距離 {@code from}〜{@code to} の帯で、置かれたセルの割合。 */
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
    @DisplayName("weights の中でも cell_* が効く")
    public void test重みがセルを読む() {
        // 上へ行くほど鉄、下へ行くほど金。cell_y が届いていなければどちらかに寄り切る。
        CellMapping.Resolved mapping = resolvedOf(
            "{ 'weights': { 'minecraft:iron_ore': 'cell_y > 0', 'minecraft:gold_ore': 'cell_y <= 0' } }");

        assertEquals("minecraft:iron_ore", mapping.blockFor(0, 68, 0, 0, 4, 0), "上のセルが鉄になっていない");
        assertEquals("minecraft:gold_ore", mapping.blockFor(0, 60, 0, 0, -4, 0), "下のセルが金になっていない");
        assertEquals("minecraft:gold_ore", mapping.blockFor(0, 64, 0, 0, 0, 0), "アンカーのセルが金になっていない");
    }

    // ============================================
    // silent zero の回帰テスト — ここが最重要
    // ============================================

    @Test
    @DisplayName("セルごとの経路でも機械が見える")
    public void test機械プロパティが届く() {
        // スタブの機械は tier 3。cell_* を混ぜてセルごとの経路に落としたうえで、
        // 機械の値がまだ読めることを見る。0 になっていたら recipeContext を落としている。
        CellMapping.Resolved withMachine = mappingOf("{ 'block': 'minecraft:stone', 'chance': 'tier * 0.5 + cell_y' }")
            .resolve(StubMachineContext.withMachine());

        // tier 3 → 1.5。cell_y = 0 でも 1.0 を超えるので必ず置く
        assertEquals(
            "minecraft:stone",
            withMachine.blockFor(0, 64, 0, 0, 0, 0),
            "tier が 0 になっている。ConditionContext.forCell が recipeContext を落としている");

        // tier 3 → 1.5。cell_y = -2 で -0.5 になるので必ず外れる
        assertEquals(null, withMachine.blockFor(0, 62, 0, 0, -2, 0), "cell_y が効いていない");

        // 機械が無ければ tier は 0。境界として、こちらは置かないことを見る
        CellMapping.Resolved noMachine = mappingOf("{ 'block': 'minecraft:stone', 'chance': 'tier * 0.5 + cell_y' }")
            .resolve(StubMachineContext.withoutMachine());

        assertEquals(null, noMachine.blockFor(0, 64, 0, 0, 0, 0), "機械が無いのに tier が 0 でない");
    }

    @Test
    @DisplayName("機械しか読まない式は全セルで同じ答え")
    public void test機械だけの式はセルで動かない() {
        // セル文脈が機械の読みを乱していないこと。tier 3 * 0.25 = 0.75 なので
        // セルごとに抽選はされるが、確率そのものは全セルで同じであるべき。
        CellMapping.Resolved mapping = resolvedOf("{ 'block': 'minecraft:stone', 'chance': 'tier * 100' }");

        for (int dy = -5; dy <= 5; dy++) {
            assertEquals(
                "minecraft:stone",
                mapping.blockFor(0, 64 + dy, 0, 0, dy, 0),
                "cell_y=" + dy + " で答えが変わった。確率は 300 なので全セル置くはず");
        }
    }

    // ============================================
    // x / y / z はセルの世界座標
    // ============================================

    @Test
    @DisplayName("セルの文脈が持つ座標はそのセルのもの（機械のではない）")
    public void test世界座標はセルのもの() {
        // ⚠ `y < 40` のような**変数そのもの**はここでは確かめられない。
        // WorldPropertyExpression は `getWorld() == null` で 0 を返し、テストに World を
        // 用意する手段が無い（ConditionBothAnswersTest.IN_GAME_ONLY と同じ境界）。
        //
        // 分けられる契約は 2 つあり、こちらが前者:
        // 1. セルの文脈が「そのセルの世界座標」を持っている ← ここで見る
        // 2. `y` がその座標を読む ← WorldPropertyExpression の既存の振る舞い。実機は TESTING.md
        ConditionContext machine = StubMachineContext.withMachine();
        ConditionContext cell = ConditionContext.forCell(machine, 10, 39, -20, 0, -25, 0);

        assertEquals(10, cell.getX(), "x がコントローラのままになっている");
        assertEquals(39, cell.getY(), "y がコントローラのままになっている");
        assertEquals(-20, cell.getZ(), "z がコントローラのままになっている");

        assertNotEquals(machine.getY(), cell.getY(), "機械の座標と同じ。配置全体が全か無かになる");
    }

    @Test
    @DisplayName("同じ配置の中でセルごとに違う座標が渡る")
    public void testセルごとに座標が動く() {
        ConditionContext machine = StubMachineContext.withMachine();

        Set<Integer> heights = new HashSet<>();
        for (int dy = -3; dy <= 3; dy++) {
            heights.add(
                ConditionContext.forCell(machine, 0, 64 + dy, 0, 0, dy, 0)
                    .getY());
        }

        assertEquals(7, heights.size(), "配置の中で y が動いていない。1 つの文脈を使い回している");
    }

    @Test
    @DisplayName("cell_* と 世界座標は独立に動く")
    public void test2つの座標系が別物() {
        // 同じ cell_y のセルが、別の世界 y に来られること。逆も同じ。
        // 片方をもう片方から導いていたら（例: cell_y を y - 64 で作る）ここが揃ってしまう。
        ConditionContext machine = StubMachineContext.withMachine();

        ConditionContext high = ConditionContext.forCell(machine, 0, 200, 0, 0, 0, 0);
        ConditionContext low = ConditionContext.forCell(machine, 0, 10, 0, 0, 0, 0);

        assertEquals(high.getCellY(), low.getCellY(), "アンカーのセルなのに cell_y が違う");
        assertNotEquals(high.getY(), low.getY(), "世界の y が同じになっている");

        ConditionContext above = ConditionContext.forCell(machine, 0, 64, 0, 0, 5, 0);
        ConditionContext below = ConditionContext.forCell(machine, 0, 64, 0, 0, -5, 0);

        assertEquals(above.getY(), below.getY(), "世界の y が違う");
        assertNotEquals(above.getCellY(), below.getCellY(), "cell_y が同じになっている");
    }

    // ============================================
    // cell_y の向き
    // ============================================

    @Test
    @DisplayName("cell_y は上へ行くほど増える（layers の 1 枚目が上）")
    public void testcellYは上が正() {
        // これは変換の事実。`layers` の 1 枚目が上なので、b が増えると世界の y は下がる。
        // cell_y は世界の y の差なので、b とは逆を向く ── つまりマイクラどおり上が正。
        ExtendedFacing facing = ExtendedFacing.NORTH_NORMAL_NONE;

        int[] atAnchor = StructureCellLocator.toWorld(facing, 0, 64, 0, null, 0, 0, 0);
        int[] oneLayerLater = StructureCellLocator.toWorld(facing, 0, 64, 0, null, 0, 1, 0);

        assertTrue(oneLayerLater[1] < atAnchor[1], "b が増えて世界の y が上がっている。docs の「layers は上から下へ」と食い違う");

        // StructureOutput が渡すのは世界 y の差なので、下の層のセルは cell_y が負になる
        int cellY = oneLayerLater[1] - atAnchor[1];
        assertEquals(-1, cellY, "1 枚下の層のセルの cell_y が -1 でない");
    }

    // ============================================
    // random() はセルごとに散る
    // ============================================

    @Test
    @DisplayName("random() を書いても全か無かにならない")
    public void testrandomがセルごとに散る() {
        // 種にセル座標が混ざっていないと、配置の中の全セルが同じ値を引く。
        CellMapping.Resolved mapping = resolvedOf("{ 'block': 'minecraft:stone', 'chance': 'random()' }");

        int placed = 0;
        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                if (mapping.blockFor(x, 64, z, x, 0, z) != null) placed++;
            }
        }

        assertTrue(placed > 20 && placed < 380, "400 マス中 " + placed + " マス。0 や 400 なら種にセルが混ざっていない");
    }

    @Test
    @DisplayName("同じセルなら何度聞いても同じ")
    public void test決定的である() {
        CellMapping.Resolved mapping = resolvedOf(
            "{ 'block': 'minecraft:stone', 'chance': 'random() * 0.5 + cell_y * 0.01' }");

        for (int x = 0; x < 30; x++) {
            assertEquals(
                mapping.blockFor(x, 64, 7, x, 0, 7),
                mapping.blockFor(x, 64, 7, x, 0, 7),
                "x=" + x + " で答えが変わった");
        }
    }

    // ============================================
    // 境界
    // ============================================

    @Test
    @DisplayName("パターンの外では cell_* は 0")
    public void testパターン外では0() {
        // 既存の全変数と同じ扱い。機械が無ければ tier が 0 なのと同じで、
        // 主体が無いときに 0 を返すのは NoMachineEvaluationTest が仕様として縛っている。
        // 弾く機構は G の C 案と共通なので、そちらで一緒に入れる。
        for (String name : new String[] { "cell_x", "cell_y", "cell_z" }) {
            assertEquals(
                0.0,
                ExpressionParser.parseExpression(name)
                    .evaluateDouble(StubMachineContext.withMachine()),
                name + " がパターンの外で 0 以外を返している");
        }
    }

    @Test
    @DisplayName("cell_* を含む chance は入力に使えない")
    public void test入力には使えない() {
        // 0 か 1 しか返さない式でも一律で止める。「この式は 0 と 1 しか返すか」は
        // 一般に判定できず、近似すると通る式と通らない式の境目が書く人から見えなくなる。
        assertFalse(
            mappingOf("{ 'block': 'minecraft:stone', 'chance': 'cell_y > 0' }").isDeterministic(),
            "乱数を含まない式の chance が決定的だと判定されている");
        assertFalse(mappingOf("{ 'block': 'minecraft:stone', 'chance': 'cell_y * 0.1' }").isDeterministic());
        assertTrue(mappingOf("{ 'block': 'minecraft:stone', 'chance': 1.0 }").isDeterministic(), "定数 1.0 まで巻き込んでいる");
    }

    @Test
    @DisplayName("cell_* は変数として書ける（登録漏れの検出）")
    public void test名前が登録されている() {
        // 名前は「評価する実装」と「レジストリへの登録」の 2 箇所に要る。
        // 片方だけだと Unknown variable か黙って 0 になる（_SSOT の 12 件）。
        Set<String> parsed = new HashSet<>();
        for (String name : new String[] { "cell_x", "cell_y", "cell_z" }) {
            parsed.add(
                ExpressionParser.parseExpression(name)
                    .toString());
        }

        assertEquals(Set.of("cell_x", "cell_y", "cell_z"), parsed, "toString() が書いた名前に戻らない");
    }

    @Test
    @DisplayName("cell_* を含む式が往復する")
    public void test往復する() {
        ConditionContext cell = ConditionContext.forCell(StubMachineContext.withMachine(), 10, 64, -20, 1, -2, 3);

        String source = "cell_x + cell_y * 2 + cell_z * 3";
        double once = ExpressionParser.parseExpression(source)
            .evaluateDouble(cell);
        double twice = ExpressionParser.parseExpression(
            ExpressionParser.parseExpression(source)
                .toString())
            .evaluateDouble(cell);

        assertEquals(1 + -2 * 2 + 3 * 3, once, "セル座標が式に届いていない");
        assertEquals(once, twice, "書き出して読み直すと値が変わる");
    }

    @Test
    @DisplayName("forCell は機械の中身を全部写す")
    public void testforCellが文脈を落とさない() {
        ConditionContext machine = StubMachineContext.withSeed(4649L);
        ConditionContext cell = ConditionContext.forCell(machine, 10, 64, -20, 1, -2, 3);

        assertEquals(machine.getRecipeContext(), cell.getRecipeContext(), "recipeContext を落としている");
        assertEquals(machine.getWorld(), cell.getWorld(), "world を落としている");
        assertEquals(10, cell.getX(), "x がセルの位置になっていない");
        assertEquals(64, cell.getY());
        assertEquals(-20, cell.getZ());
        assertEquals(1, cell.getCellX());
        assertEquals(-2, cell.getCellY());
        assertEquals(3, cell.getCellZ());
        assertTrue(cell.hasCell());
        assertNotEquals(machine.getEvaluationSeed(), cell.getEvaluationSeed(), "種にセルの位置が混ざっていない");

        // forDraw がセルを引き継ぐこと。落とすと 2 回目の抽選から cell_y が 0 になる
        ConditionContext nextDraw = cell.forDraw(1);
        assertTrue(nextDraw.hasCell(), "forDraw がセルを落としている");
        assertEquals(-2, nextDraw.getCellY(), "forDraw がセルを落としている");
    }

    @Test
    @DisplayName("機械が無くても cell_* は読める")
    public void test機械が無くても動く() {
        // ロード時の検査や NEI の描画のように機械が無い経路でも、セルの座標は座標なので答えられる。
        ConditionContext cell = ConditionContext.forCell(null, 10, 64, -20, 1, -2, 3);

        assertEquals(
            -2.0,
            ExpressionParser.parseExpression("cell_y")
                .evaluateDouble(cell));
        assertEquals(
            0.0,
            ExpressionParser.parseExpression("tier")
                .evaluateDouble(cell),
            "機械が無いのに tier が 0 でない");
    }
}
