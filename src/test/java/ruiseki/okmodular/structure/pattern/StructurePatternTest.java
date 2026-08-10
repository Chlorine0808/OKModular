package ruiseki.okmodular.structure.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.expression.ConstantExpression;
import ruiseki.okmodular.api.recipe.expression.EvaluationValue;
import ruiseki.okmodular.api.recipe.expression.IExpression;
import ruiseki.okmodular.api.recipe.expression.StubMachineContext;
import ruiseki.okmodular.structure.StructureException;

/**
 * 構造 IO パターンの読み取りと、セルがどの座標系に落ちるかの検証。
 *
 * ============================================
 * なぜセルを機械の向きで解決するのか
 * ============================================
 *
 * 構造 JSON の `layers`/`rows` は**そのままの向きでは使われていない**。
 * `StructureShape.process` を通ってはじめて `(col, layer, row)` が `(A, B, C)` になる。
 * パターンが同じ書き方を流用する以上、**同じ変換を通さないと機械に対してだけ回転する**。
 * しかも例外は出ない（`StructureShapeTest` の冒頭に書いた通り）。
 *
 * ここで効いてくるのが「どの向きで process するか」で、**答えは機械側の `defaultFacing`**。
 * パターン側に `defaultFacing` を書かせると**機械のそれと食い違ったときに黙って回る**ので、
 * パターンはファイルに向きを持たず、使う側から受け取る。
 * だから `cellsFor(facing)` であって `getCells()` ではない。
 *
 * ============================================
 * なぜアンカー相対なのか
 * ============================================
 *
 * パターンはレシピが名指しした**シンボル位置に対して**置かれる。その位置のセルは実行時に
 * `TEMachineController.getSymbolCell` から来るので、パターン側が持つべきは
 * 「アンカーから見てどれだけずれているか」だけ。絶対セルを持たせると使う側で毎回引き算になる。
 */
@DisplayName("構造 IO パターン")
public class StructurePatternTest {

    private static StructurePattern parse(String json) {
        return StructurePatternParser.parse(
            new JsonParser().parse(json)
                .getAsJsonObject(),
            "test.json");
    }

    /**
     * セルを {@code "a,b,c"} → ブロック ID の対応に潰す。順序に依存した検証を書かないため。
     *
     * セルが持つのはブロック ID そのものではなく**対応（`CellMapping`）**で、
     * 置くブロックは種と座標から決まります。重み表つきの対応が入ると 1 つに定まらないので、
     * ここは「1 つに定まる対応を、ある 1 箇所に置いたら何になるか」を見ています。
     */
    private static Map<String, String> byCell(List<StructurePattern.Cell> cells) {
        Map<String, String> map = new HashMap<>();
        for (StructurePattern.Cell cell : cells) {
            map.put(
                cell.a + "," + cell.b + "," + cell.c,
                cell.mapping.resolve(StubMachineContext.withoutMachine())
                    .blockFor(0, 0, 0, 0, 0, 0));
        }
        return map;
    }

    /** `mappings` の値 1 つぶんを取り出す。値の書き方そのものを見たいとき用。 */
    private static CellMapping mappingOf(String value) {
        return parse(("{ 'name': 'x', 'mappings': { 'S': " + value + " }, 'layers': [ ['S'] ] }").replace('\'', '"'))
            .cellsFor(null)
            .get(0).mapping;
    }

    /**
     * 対応を「その配置ぶん」確定させる。
     *
     * `chance` も重みも式で書けるので、対応そのものはまだ数を持っていません。
     * 機械に当てて数に落としてから、セルごとに引きます。
     */
    private static CellMapping.Resolved resolvedOf(String value) {
        return resolvedOf(value, StubMachineContext.withoutMachine());
    }

    private static CellMapping.Resolved resolvedOf(String value, ConditionContext context) {
        return mappingOf(value).resolve(context);
    }

    /**
     * 別の実行に当てる。
     *
     * 種は `blockFor` の引数ではなく**文脈が持つ**（K1 でセルごとに文脈を作るようになり、
     * 種はそこから取る形になった）。実行を作り分けたいテストはここを通す。
     */
    private static CellMapping.Resolved resolvedOf(String value, long seed) {
        return resolvedOf(value, StubMachineContext.withSeed(seed));
    }

    /** 20x20 のマスに置いて、実際にブロックが入った数を数える。 */
    private static int placedOf(CellMapping.Resolved mapping) {
        int placed = 0;
        for (int x = 0; x < 20; x++) {
            for (int z = 0; z < 20; z++) {
                if (mapping.blockFor(x, 64, z, 0, 0, 0) != null) placed++;
            }
        }
        return placed;
    }

    // ============================================
    // chance — そのセルに何か置くか
    // ============================================
    //
    // 「置くか」と「何を置くか」は直交した別の軸。ここは前者だけ。
    //
    // 一番の落とし穴は**全セルが同じ答えを出すこと**。評価の種はレシピ実行中は固定なので、
    // 座標を混ぜ忘れると 400 マスが揃って当たるか揃って外れるかの二択に潰れる
    // （`PerPositionProbabilityDecorator` が踏んだ穴と同じ）。

    @Test
    @DisplayName("chance を書いたセルは確率で欠ける")
    public void testchanceでセルが欠ける() {
        int placed = placedOf(resolvedOf("{ 'block': 'minecraft:stone', 'chance': 0.5 }", 4649L));

        assertTrue(placed > 150 && placed < 250, "400 マス中 " + placed + " マス。0 や 400 に潰れているなら座標が混ざっていない");
    }

    @Test
    @DisplayName("chance 1 は必ず置き、0 は決して置かない")
    public void testchanceの両端() {
        assertEquals(400, placedOf(resolvedOf("{ 'block': 'minecraft:stone', 'chance': 1.0 }", 1L)));
        assertEquals(0, placedOf(resolvedOf("{ 'block': 'minecraft:stone', 'chance': 0.0 }", 1L)));
    }

    @Test
    @DisplayName("同じ種・同じ座標なら何度聞いても同じ")
    public void testchanceが決定的である() {
        // 置く前に確かめる経路があるので、ここが揺れると「置けると答えたのに置かない」。
        CellMapping.Resolved mapping = resolvedOf("{ 'block': 'minecraft:stone', 'chance': 0.5 }", 99L);

        for (int x = 0; x < 30; x++) {
            assertEquals(
                mapping.blockFor(x, 64, 7, 0, 0, 0),
                mapping.blockFor(x, 64, 7, 0, 0, 0),
                "x=" + x + " で答えが変わった");
        }
    }

    @Test
    @DisplayName("実行が変われば欠けるマスも変わる")
    public void testchanceが実行ごとに変わる() {
        CellMapping.Resolved oneRun = resolvedOf("{ 'block': 'minecraft:stone', 'chance': 0.5 }", 1L);
        CellMapping.Resolved another = resolvedOf("{ 'block': 'minecraft:stone', 'chance': 0.5 }", 2L);
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();

        for (int x = 0; x < 40; x++) {
            first.append(oneRun.blockFor(x, 64, 0, 0, 0, 0) == null ? '.' : '#');
            second.append(another.blockFor(x, 64, 0, 0, 0, 0) == null ? '.' : '#');
        }

        assertNotEquals(first.toString(), second.toString(), "種を変えても同じ並びになった");
    }

    @Test
    @DisplayName("chance を書かなければ今までどおり必ず置く")
    public void testchance無しは据え置き() {
        assertEquals(400, placedOf(resolvedOf("'minecraft:stone'", 1L)), "文字列で書いた対応が欠けるようになっている");
        assertEquals(400, placedOf(resolvedOf("{ 'block': 'minecraft:stone' }", 1L)), "chance の無いオブジェクトが欠けている");
    }

    @Test
    @DisplayName("何を置くか書いていない対応は読み込まない")
    public void test中身の無い対応は断る() {
        // 置くものを書かずに確率だけ書いても、何も起きないマスにしかならない。
        // 実行時に静かに 0 マスで終わるより、書いた瞬間に落ちるほうがいい。
        StructureException thrown = assertThrows(
            StructureException.class,
            () -> mappingOf("{ 'chance': 0.5 }"),
            "block も weights も無い対応が通っている");

        assertTrue(
            thrown.getMessage() != null && !thrown.getMessage()
                .isEmpty(),
            "何が足りないのか message が言っていない");
    }

    /** 40x40 のマスに置いて、何が何マス入ったかを数える。置かなかったマスは "null"。 */
    private static Map<String, Integer> picksOf(CellMapping.Resolved mapping) {
        Map<String, Integer> counts = new HashMap<>();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                counts.merge(String.valueOf(mapping.blockFor(x, 64, z, 0, 0, 0)), 1, Integer::sum);
            }
        }
        return counts;
    }

    // ============================================
    // weights — 置くとして、何を置くか
    // ============================================
    //
    // `chance` と直交した別の軸。あちらは「そのセルに何か置くか」、こちらは「置くなら何を」。
    // 両方書けば「70% のセルに置き、その内訳が 8:2」になる。
    //
    // 2 つの軸に別々の系統を使っているのはここが理由で、同じ系統だと
    // **置くと決まったセルが必ず同じ内訳を引く**（当たったセルが全部同じブロックになる）。

    @Test
    @DisplayName("重みの比率どおりに散る")
    public void testweightsが比率どおり() {
        Map<String, Integer> picks = picksOf(
            resolvedOf("{ 'weights': { 'minecraft:iron_ore': 3, 'minecraft:gold_ore': 1 } }", 4649L));

        // 1600 マスを 3:1 なら 1200 と 400。落ちるべきときに確実に落ちる粗さで縛る。
        assertTrue(picks.getOrDefault("minecraft:iron_ore", 0) > 1050, "鉄が少なすぎる: " + picks);
        assertTrue(picks.getOrDefault("minecraft:iron_ore", 0) < 1350, "鉄が多すぎる: " + picks);
        assertTrue(picks.getOrDefault("minecraft:gold_ore", 0) > 250, "金が少なすぎる: " + picks);
        assertTrue(picks.getOrDefault("minecraft:gold_ore", 0) < 550, "金が多すぎる: " + picks);
    }

    @Test
    @DisplayName("同じ種・同じ座標なら同じブロックを引く")
    public void testweightsが決定的である() {
        CellMapping.Resolved mapping = resolvedOf(
            "{ 'weights': { 'minecraft:iron_ore': 1, 'minecraft:gold_ore': 1 } }",
            7L);

        for (int x = 0; x < 30; x++) {
            assertEquals(
                mapping.blockFor(x, 64, 3, 0, 0, 0),
                mapping.blockFor(x, 64, 3, 0, 0, 0),
                "x=" + x + " で答えが変わった");
        }
    }

    @Test
    @DisplayName("重み 0 の項目は選ばれない")
    public void testweightsの重み0() {
        Map<String, Integer> picks = picksOf(
            resolvedOf("{ 'weights': { 'minecraft:iron_ore': 1, 'minecraft:gold_ore': 0 } }", 1L));

        assertEquals(1600, picks.getOrDefault("minecraft:iron_ore", 0), "重み 0 の項目が選ばれている: " + picks);
    }

    @Test
    @DisplayName("`_` は空気、空白は触らない")
    public void testweightsの予約キー() {
        // 形を描くときと同じ語彙。「確率で空気にする」と「確率で手を付けない」は別のこと。
        Map<String, Integer> air = picksOf(resolvedOf("{ 'weights': { '_': 1 } }", 1L));
        assertEquals(1600, air.getOrDefault("minecraft:air", 0), "`_` が空気になっていない: " + air);

        Map<String, Integer> untouched = picksOf(resolvedOf("{ 'weights': { ' ': 1 } }", 1L));
        assertEquals(1600, untouched.getOrDefault("null", 0), "空白が「触らない」になっていない: " + untouched);
    }

    @Test
    @DisplayName("chance と weights は組み合わせられる")
    public void testchanceとweightsが直交する() {
        // 50% のセルに置き、置くセルの内訳が 1:1 ＝ 400 / 400 / 800(触らない)。
        Map<String, Integer> picks = picksOf(
            resolvedOf(
                "{ 'chance': 0.5, 'weights': { 'minecraft:iron_ore': 1, 'minecraft:gold_ore': 1 } }",
                20260810L));

        assertTrue(
            picks.getOrDefault("null", 0) > 700 && picks.getOrDefault("null", 0) < 900,
            "欠け方が 50% でない: " + picks);
        assertTrue(picks.getOrDefault("minecraft:iron_ore", 0) > 300, "鉄が少なすぎる: " + picks);
        assertTrue(picks.getOrDefault("minecraft:gold_ore", 0) > 300, "金が少なすぎる: " + picks);
    }

    @Test
    @DisplayName("置くと決まったセルの内訳が一律にならない")
    public void test2つの軸が連動しない() {
        // 系統を分け忘れると、chance に当たったセルが**全部同じブロック**になる。
        // 番号が違うだけでは足りないので、実際に両方が混ざることを見る。
        Map<String, Integer> picks = picksOf(
            resolvedOf("{ 'chance': 0.5, 'weights': { 'minecraft:iron_ore': 1, 'minecraft:gold_ore': 1 } }", 31415L));

        assertTrue(picks.getOrDefault("minecraft:iron_ore", 0) > 0, "置いたセルが片方に寄り切っている: " + picks);
        assertTrue(picks.getOrDefault("minecraft:gold_ore", 0) > 0, "置いたセルが片方に寄り切っている: " + picks);
    }

    // ============================================
    // 書き方の間違いはロードで断る
    // ============================================

    @Test
    @DisplayName("block と weights は同時に書けない")
    public void testblockとweightsは排他() {
        // block は「1 項目だけの weights」の短縮形。両方書くのは同じマスの中身を
        // 2 通り指定したということで、どちらかを黙って選ぶしかなくなる。
        assertThrows(
            StructureException.class,
            () -> mappingOf("{ 'block': 'minecraft:stone', 'weights': { 'minecraft:iron_ore': 1 } }"),
            "block と weights の両方が通っている");
    }

    @Test
    @DisplayName("空の weights は読み込まない")
    public void test空のweightsは断る() {
        assertThrows(StructureException.class, () -> mappingOf("{ 'weights': {} }"), "空の weights が通っている");
    }

    @Test
    @DisplayName("重みが全部 0 の weights は読み込まない")
    public void test重みの合計0は断る() {
        // 引く先が無いので、実行時には静かに何も置かないマスになる。
        assertThrows(
            StructureException.class,
            () -> mappingOf("{ 'weights': { 'minecraft:iron_ore': 0, 'minecraft:gold_ore': 0 } }"),
            "合計 0 の weights が通っている");
    }

    @Test
    @DisplayName("負の重みは読み込まない")
    public void test負の重みは断る() {
        assertThrows(
            StructureException.class,
            () -> mappingOf("{ 'weights': { 'minecraft:iron_ore': -1, 'minecraft:gold_ore': 2 } }"),
            "負の重みが通っている");
    }

    @Test
    @DisplayName("ワイルドカードは weights に書けない")
    public void testワイルドカードは断る() {
        // 照合には使えても**設置はできない**。書けるのに置けないものを残すと、
        // 実行時に静かに何も起きないマスになる。
        assertThrows(
            StructureException.class,
            () -> mappingOf("{ 'weights': { 'minecraft:stone:*': 1 } }"),
            "ワイルドカードが weights に書けてしまっている");
    }

    // ============================================
    // 式で書く
    // ============================================
    //
    // `chance` も重みも、数のかわりに式が書けます。Tier で濃さが変わる鉱脈、
    // 天候で内訳が変わる配置、といったものがここで書けるようになる。
    //
    // **評価の回数は、書いたものが数か式かで変わります。**
    //
    // 数で書いた `chance` や重みは配置につき 1 回で畳まれ、セルごとの文脈は 1 つも作られません。
    // 式は K1（`cell_x` / `cell_y` / `cell_z`）でセルを読めるようになったので、
    // **その値が配置の時点では分かりません** — セルごとに評価します。
    //
    // Tier しか読まない式まで巻き込まれますが、それを見分けるには `IExpression` に
    // 「配置内で不変か」を足して全 composite に転送させる必要があり、
    // `ConstantExpression` と別の「定数」概念が 2 つできます。数千回の軽い評価と引き換えに
    // 概念を 1 つに保つ判断です（K1 の設計、`StructureIO_todos.md`）。
    //
    // 散らばりは座標由来の抽選が担当していて、式の仕事ではありません。

    @Test
    @DisplayName("chance を式で書ける")
    public void testchanceを式で書く() {
        // スタブの機械は tier 3。tier * 0.25 = 0.75。
        int placed = placedOf(
            resolvedOf("{ 'block': 'minecraft:stone', 'chance': 'tier * 0.25' }", StubMachineContext.withSeed(4649L)));

        assertTrue(placed > 250 && placed < 350, "400 マス中 " + placed + " マス。tier 3 なら 75% で 300 前後のはず");
    }

    @Test
    @DisplayName("重みを式で書ける")
    public void testweightsを式で書く() {
        // tier 3 と 1 で 3:1。
        Map<String, Integer> picks = picksOf(
            resolvedOf(
                "{ 'weights': { 'minecraft:iron_ore': 'tier', 'minecraft:gold_ore': 1 } }",
                StubMachineContext.withSeed(4649L)));

        assertTrue(picks.getOrDefault("minecraft:iron_ore", 0) > 1050, "鉄が少なすぎる: " + picks);
        assertTrue(picks.getOrDefault("minecraft:gold_ore", 0) > 250, "金が少なすぎる: " + picks);
        assertTrue(picks.getOrDefault("minecraft:gold_ore", 0) < 550, "金が多すぎる: " + picks);
    }

    @Test
    @DisplayName("数で書いた chance と重みは配置につき 1 回だけ評価される")
    public void test数は配置ごとに1回だけ評価される() {
        // 既存のパターンはほぼ全部この経路。ここが緩むと、球 1 つ置くのに
        // 定数の読み出しを数千回やることになる（しかもセルごとに文脈を 1 つ作る）。
        CountingConstant chance = new CountingConstant(0.5);
        CountingConstant weight = new CountingConstant(1.0);
        CellMapping mapping = new CellMapping(new String[] { "minecraft:stone" }, new IExpression[] { weight }, chance);

        CellMapping.Resolved resolved = mapping.resolve(StubMachineContext.withoutMachine());
        for (int x = 0; x < 100; x++) resolved.blockFor(x, 64, 0, x, 0, 0);

        assertEquals(1, chance.evaluated, "数の chance をセルごとに評価している");
        assertEquals(1, weight.evaluated, "数の重みをセルごとに評価している");
    }

    @Test
    @DisplayName("式で書いた chance と重みはセルごとに評価される")
    public void test式はセルごとに評価される() {
        // K1 で式が `cell_x` を読めるようになったので、配置の時点では値が決まらない。
        // ここを 1 回に畳むと「中心ほど濃い」が全セル同じ濃さになる。
        Counting chance = new Counting(0.5);
        Counting weight = new Counting(1.0);
        CellMapping mapping = new CellMapping(new String[] { "minecraft:stone" }, new IExpression[] { weight }, chance);

        CellMapping.Resolved resolved = mapping.resolve(StubMachineContext.withoutMachine());
        for (int x = 0; x < 100; x++) resolved.blockFor(x, 64, 0, x, 0, 0);

        assertEquals(100, chance.evaluated, "chance が配置につき 1 回に畳まれている");
        // 重みは chance に当たったセルだけ。0.5 なので全部でも 0 でもない
        assertTrue(
            weight.evaluated > 20 && weight.evaluated < 80,
            "重みの評価が " + weight.evaluated + " 回。chance に外れたセルでも評価している（100）か、" + "そもそも評価していない（0）");
    }

    @Test
    @DisplayName("式で書いた chance は決定的ではない扱いになる")
    public void test式のchanceは非決定扱い() {
        // 実際に 1.0 を返すかはロード時には分からない。分からないものを
        // 「たぶん決定的」として入力に通すと、実機で静かに一致しなくなる。
        assertTrue(
            !mappingOf("{ 'block': 'minecraft:stone', 'chance': 'tier' }").isDeterministic(),
            "式の chance が決定的だと判定されている");
        assertTrue(
            mappingOf("{ 'block': 'minecraft:stone', 'chance': 1.0 }").isDeterministic(),
            "定数 1.0 の chance まで非決定扱いになっている");
    }

    /**
     * 評価された回数を数える**数**。
     *
     * `ConstantExpression` を継承しているのが要点で、`CellMapping` が
     * 「畳んでよいか」を `instanceof ConstantExpression` で判定するのでこちらは速い経路に乗る。
     */
    private static final class CountingConstant extends ConstantExpression {

        int evaluated;

        CountingConstant(double value) {
            super(value);
        }

        @Override
        public EvaluationValue evaluate(ConditionContext context) {
            evaluated++;
            return super.evaluate(context);
        }
    }

    /** 評価された回数を数える式。数ではないので遅い経路（セルごと）に乗る。 */
    private static final class Counting implements IExpression {

        int evaluated;
        private final double value;

        Counting(double value) {
            this.value = value;
        }

        @Override
        public EvaluationValue evaluate(ConditionContext context) {
            evaluated++;
            return new EvaluationValue(value);
        }
    }

    @Test
    @DisplayName("同じ対応は何度引いても同じブロックを返す")
    public void test対応が決定的である() {
        // 置く前に「置けるか」を確かめる経路があるので、2 回聞いて答えが変わると
        // 「置けると答えたのに置かない」が起きる。座標や種が変わっても、
        // ブロック ID 1 個だけの対応は動いてはいけない。
        StructurePattern pattern = parse(
            "{ 'name': 'core', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['SQ'] ] }".replace('\'', '"'));
        CellMapping cell = pattern.cellsFor(null)
            .get(0).mapping;

        assertEquals(
            "minecraft:stone",
            cell.resolve(StubMachineContext.withoutMachine())
                .blockFor(0, 0, 0, 0, 0, 0));
        assertEquals(
            "minecraft:stone",
            cell.resolve(StubMachineContext.withSeed(4649L))
                .blockFor(10, 64, -20, 3, -4, 5),
            "種も座標もセルも無視するはず");
    }

    @Test
    @DisplayName("セルはアンカーからの相対で返る")
    public void testセルはアンカー相対() {
        StructurePattern pattern = parse(
            "{ 'name': 'core', 'mappings': { 'S': 'minecraft:stone', 'Q': 'minecraft:obsidian' },"
                + "  'layers': [ ['SQ', 'S_'] ] }".replace('\'', '"'));

        // rotate180 で層 0 の行が逆順になり ["S_", "SQ"]。Q は {col=1, layer=0, row=1}
        Map<String, String> cells = byCell(pattern.cellsFor(null));

        assertEquals("minecraft:obsidian", cells.get("0,0,0"), "アンカー自身が原点に来ていない");
        assertEquals("minecraft:stone", cells.get("-1,0,0"), "アンカーと同じ行の S がずれている");
        assertEquals("minecraft:stone", cells.get("-1,0,-1"), "rotate180 を通っていれば S はアンカーの手前の行にある");
        assertEquals("minecraft:air", cells.get("0,0,-1"), "`_` が air として入っていない");
        assertEquals(4, cells.size(), "セル数が合わない: " + cells);
    }

    @Test
    @DisplayName("機械が縦置きなら層と行が入れ替わる")
    public void test縦置きで軸が変わる() {
        String json = "{ 'name': 'v', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['Q'], ['S'] ] }"
            .replace('\'', '"');

        assertTrue(byCell(parse(json).cellsFor(null)).containsKey("0,1,0"), "横向きの機械では、下の層の S はアンカーの 1 層下（B 方向）に来るはず");
        assertTrue(
            byCell(parse(json).cellsFor("UP")).containsKey("0,0,1"),
            "縦置きの機械では層が行に移るので S は C 方向に来るはず。" + "機械の defaultFacing を無視すると、ここが黙って食い違う");
    }

    @Test
    @DisplayName("空白はパターンに含まれない")
    public void test空白は含まない() {
        StructurePattern pattern = parse(
            "{ 'name': 'gap', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S Q'] ] }".replace('\'', '"'));

        assertEquals(
            1,
            pattern.cellsFor(null)
                .size(),
            "空白がセルとして入っている。空白は構造 JSON でも「検証から外す」意味");
    }

    @Test
    @DisplayName("マッピングの無いアンカーは印だけでセルにならない")
    public void testアンカーは印だけでもよい() {
        StructurePattern pattern = parse(
            "{ 'name': 'marker', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['SQ'] ] }".replace('\'', '"'));

        List<StructurePattern.Cell> cells = pattern.cellsFor(null);
        assertEquals(1, cells.size(), "アンカーにマッピングが無いのにセルが作られている。" + "アンカー位置には普通レシピが名指ししたブロックが既に建っている");
        assertEquals('S', cells.get(0).symbol);
    }

    @Test
    @DisplayName("マッピングの無い記号は読み込みを止める")
    public void test未定義の記号はエラー() {
        StructureException error = assertThrows(
            StructureException.class,
            () -> parse("{ 'name': 'bad', 'mappings': {}, 'layers': [ ['X'] ] }".replace('\'', '"')));

        assertTrue(
            error.getMessage()
                .contains("X"),
            "どの記号が未定義なのかがメッセージに出ていない: " + error.getMessage());
    }

    @Test
    @DisplayName("書いたアンカーが形状に無ければエラー")
    public void test居ないアンカーはエラー() {
        assertThrows(
            StructureException.class,
            () -> parse(
                "{ 'name': 'bad', 'anchor': 'A', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] }"
                    .replace('\'', '"')),
            "書いたアンカーが見つからないのに通ると、原点アンカーとして黙ってずれる");
    }

    @Test
    @DisplayName("アンカー未指定で Q も無ければ原点が基準")
    public void testアンカー省略時は原点() {
        StructurePattern pattern = parse(
            "{ 'name': 'plain', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] }".replace('\'', '"'));

        assertTrue(byCell(pattern.cellsFor(null)).containsKey("0,0,0"), "アンカーを書かない単純なパターンは、最初のセルが基準になるべき");
    }

    @Test
    @DisplayName("name の無いパターンは読み込みを止める")
    public void test名前は必須() {
        assertThrows(
            StructureException.class,
            () -> parse("{ 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] }".replace('\'', '"')),
            "名前が無いパターンはレシピから参照できない。読み込めてしまうと死蔵する");
    }

    @Test
    @DisplayName("layers の無いパターンは読み込みを止める")
    public void test形状は必須() {
        assertThrows(StructureException.class, () -> parse("{ 'name': 'empty', 'mappings': {} }".replace('\'', '"')));
    }

    @Test
    @DisplayName("layers はオブジェクト形式の rows でも書ける")
    public void testオブジェクト形式の層() {
        StructurePattern pattern = parse(
            ("{ 'name': 'obj', 'mappings': { 'S': 'minecraft:stone' }," + "  'layers': [ { 'rows': ['S'] } ] }")
                .replace('\'', '"'));

        assertEquals(
            1,
            pattern.cellsFor(null)
                .size(),
            "構造 JSON が受ける 2 つの層の書き方のうち片方しか読めていない");
    }

    /**
     * 解決結果は向きごとに 1 度だけ作って使い回す。**呼ぶ側に書き換えられると
     * 次の呼び出し以降まで壊れる**ので、渡す前に読み取り専用にしておく。
     */
    @Test
    @DisplayName("返るセル一覧は書き換えられない")
    public void test結果は読み取り専用() {
        StructurePattern pattern = parse(
            "{ 'name': 'cached', 'mappings': { 'S': 'minecraft:stone' }, 'layers': [ ['S'] ] }".replace('\'', '"'));

        List<StructurePattern.Cell> cells = pattern.cellsFor(null);
        assertThrows(UnsupportedOperationException.class, () -> cells.clear(), "使い回す一覧を呼び出し側が空にできてしまう");
    }
}
