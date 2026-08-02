package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.util.ChunkCoordinates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.expression.ConstantExpression;
import ruiseki.okmodular.api.recipe.expression.StubMachineContext;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;

/**
 * デコレータの抽選が機械の種から出ていることの検証。
 *
 * ============================================
 * 何が壊れていたか
 * ============================================
 *
 * 5 つのデコレータが `private final Random rand = new Random();` を**フィールドで**持っていた。
 *
 * レシピは JSON から 1 回だけ組まれ、**そのレシピを動かす全マシンで共有される**。
 * デコレータもレシピの一部なので、この `rand` も共有されていた。つまり:
 *
 * - 引く値が **他のマシンが何回引いたか**に依存する（マシン間の結合）
 * - JVM を再起動すると系列が変わる（再現性ゼロ）
 * - 同じ実行を 2 回評価すると違う答えが出る
 *
 * 最後の 1 つが一番効く。この mod は
 * **「置けるか確かめてから置く」「セーブ・ロードを跨いで続きを実行する」**をやるので、
 * 同じ実行内で答えが揺れると「置けると答えたのに置かない」が普通に起きる。
 *
 * ============================================
 * 何に置き換えたか
 * ============================================
 *
 * 機械の評価シードはレシピ開始時に 1 回だけ決まり、NBT で永続化される。
 * そこから `SeedMixer` で引けば、
 *
 * - 同じ実行の中では何度聞いても同じ（セーブ・ロードを跨いでも同じ）
 * - 次の実行では違う（ボーナスが毎回同じでは困る）
 *
 * の両方が出る。デコレータごとに**別の系統**を割り当てているのは、
 * 1 つのレシピが複数のデコレータを持てるから。同じ系統だと全部が揃って当たり外れする。
 *
 * ============================================
 * 残っている穴（意図的）
 * ============================================
 *
 * **同じ種類のデコレータを 1 レシピに 2 つ**書くと、その 2 つは同じ系統・同じ種なので
 * 必ず同じ答えを出す。系統はデコレータの種類ごとであってインスタンスごとではない。
 * ここでは直していない（`StructureIO_todos.md` に項目として積んである）。
 */
@DisplayName("デコレータの抽選")
public class DecoratorDrawTest {

    private static ConditionContext seeded(long seed) {
        // World も IRecipeContext も要らない。定数式しか評価しないので、
        // 種だけ持った文脈で抽選そのものを値で確かめられる。
        return new ConditionContext(null, 0, 0, 0, null, seed);
    }

    private static BonusOutputDecorator bonus(double chance) {
        return new BonusOutputDecorator(null, new ConstantExpression(chance), new ArrayList<IRecipeOutput>(), null);
    }

    private static BonusBlockOutputDecorator bonusBlock(double chance) {
        return new BonusBlockOutputDecorator(null, new ConstantExpression(chance), new ArrayList<>());
    }

    /** スタブの機械が申告するバッチ数。ここで数字を書き写すと片方だけ変わる。 */
    private static final int MACHINE_BATCH = StubMachineContext.BATCH_SIZE;

    private static ConditionContext withMachine() {
        return StubMachineContext.withMachine();
    }

    private static ConditionContext withoutMachine() {
        return StubMachineContext.withoutMachine();
    }

    private static List<WeightedRandomDecorator.WeightedOutputEntry> poolOf(int... weights) {
        List<WeightedRandomDecorator.WeightedOutputEntry> pool = new ArrayList<>();
        for (int weight : weights) {
            pool.add(new WeightedRandomDecorator.WeightedOutputEntry(null, weight));
        }
        return pool;
    }

    private static WeightedRandomDecorator weighted(int... weights) {
        return new WeightedRandomDecorator(null, poolOf(weights), 1);
    }

    /** 重み表のどの枠が当たったか。等値比較できないので添字で答え合わせする。 */
    private static int pickedIndex(WeightedRandomDecorator decorator, ConditionContext context, int draw) {
        return decorator.getPool()
            .indexOf(decorator.pick(context, draw));
    }

    private static PerPositionProbabilityDecorator perPosition(double chance) {
        return new PerPositionProbabilityDecorator(null, new ConstantExpression(chance), 'L', null);
    }

    private static RandomBlockOutputDecorator randomBlocks() {
        return new RandomBlockOutputDecorator(null, new ConstantExpression(1), new ArrayList<>());
    }

    /**
     * 5x5x2 の 50 マス。サンプルの SpatialCrafter の床と同じ形にしてある。
     *
     * 座標を負に振ってあるのは、構造体のセルがアンカー相対で**半分が負**だから。
     * 非負前提で座標を詰める実装はここで潰れる。
     */
    private static List<ChunkCoordinates> cells() {
        List<ChunkCoordinates> cells = new ArrayList<>();
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= -1; b++) {
                for (int c = -2; c <= 2; c++) {
                    cells.add(new ChunkCoordinates(a, b, c));
                }
            }
        }
        return cells;
    }

    // ============================================
    // 同じ実行の中では揺れない
    // ============================================

    @Test
    @DisplayName("同じ種なら何度聞いても同じ答え")
    public void test同じ種なら同じ答え() {
        BonusOutputDecorator decorator = bonus(0.5);
        ConditionContext context = seeded(12345L);

        boolean first = decorator.rolls(context);
        for (int i = 0; i < 20; i++) {
            assertEquals(first, decorator.rolls(context), i + " 回目で答えが変わった");
        }
    }

    @Test
    @DisplayName("他のインスタンスが引いても答えが変わらない")
    public void test他のインスタンスに引きずられない() {
        // 共有 Random だったときはここが壊れていた。同じレシピを動かす別のマシンが
        // 引くたびに系列が進み、自分の答えが変わった。
        BonusOutputDecorator mine = bonus(0.5);
        BonusOutputDecorator other = bonus(0.5);
        ConditionContext context = seeded(999L);

        boolean before = mine.rolls(context);
        for (int i = 0; i < 50; i++) {
            other.rolls(seeded(i));
        }

        assertEquals(before, mine.rolls(context), "他のマシンが引いたら自分の答えが変わった");
    }

    @Test
    @DisplayName("同じ種の別インスタンスは同じ答え")
    public void test同じ種の別インスタンス() {
        // 状態ではなく種から出ていることの裏。セーブ・ロードでデコレータが作り直されても、
        // 種が同じなら実行の続きは同じ答えになる。
        assertEquals(bonus(0.5).rolls(seeded(4242L)), bonus(0.5).rolls(seeded(4242L)));
    }

    // ============================================
    // 実行ごとには変わる
    // ============================================

    @Test
    @DisplayName("種を振れば当たりも外れも出る")
    public void test種を振れば割れる() {
        BonusOutputDecorator decorator = bonus(0.5);
        int hits = 0;
        for (long seed = 0; seed < 200; seed++) {
            if (decorator.rolls(seeded(seed))) hits++;
        }

        assertTrue(hits > 60 && hits < 140, "200 回中 " + hits + " 回しか当たっていない（0.5 のはず）");
    }

    @Test
    @DisplayName("確率 0 は当たらず、確率 1 は外れない")
    public void test確率の両端() {
        // toUnitInterval は [0, 1) なので、1.0 は必ず当たり 0.0 は必ず外れになる。
        // 「99% と書いたのに 100 回に 1 回外れない」より、両端が言い切れることのほうが大事。
        for (long seed = 0; seed < 500; seed++) {
            assertFalse(bonus(0.0).rolls(seeded(seed)), "確率 0 が当たった: seed=" + seed);
            assertTrue(bonus(1.0).rolls(seeded(seed)), "確率 1 が外れた: seed=" + seed);
        }
    }

    // ============================================
    // デコレータどうしが連動しない
    // ============================================

    @Test
    @DisplayName("bonus と bonus_block が揃って当たり外れしない")
    public void testデコレータ間で連動しない() {
        // 1 つのレシピに両方書ける。同じ系統から引いていると
        // 「片方が当たったらもう片方も必ず当たる」になり、確率を 2 つ書いた意味が消える。
        int agreed = 0;
        for (long seed = 0; seed < 200; seed++) {
            if (bonus(0.5).rolls(seeded(seed)) == bonusBlock(0.5).rolls(seeded(seed))) agreed++;
        }

        assertTrue(agreed > 60 && agreed < 140, "200 回中 " + agreed + " 回一致した（同じ系統から引いている）");
    }

    // ============================================
    // 重み表
    // ============================================

    @Test
    @DisplayName("重み表も同じ種なら同じ枠を引く")
    public void test重み表が決定的である() {
        WeightedRandomDecorator decorator = weighted(70, 30);
        ConditionContext context = seeded(555L);

        int first = pickedIndex(decorator, context, 0);
        for (int i = 0; i < 20; i++) {
            assertEquals(first, pickedIndex(decorator, context, 0), i + " 回目で選ばれた枠が変わった");
        }
    }

    @Test
    @DisplayName("rolls が 2 以上でも同じ枠ばかり引かない")
    public void test複数ロールが潰れない() {
        // 共有 Random を消して初めて見えた穴。種は実行中ずっと固定なので、
        // 引く番号を混ぜないと rolls: 3 が「同じ枠を 3 回」になる。
        WeightedRandomDecorator decorator = weighted(50, 50);
        ConditionContext context = seeded(31337L);

        boolean differed = false;
        for (int draw = 1; draw < 10; draw++) {
            if (pickedIndex(decorator, context, draw) != pickedIndex(decorator, context, 0)) differed = true;
        }

        assertTrue(differed, "10 回引いて全部同じ枠だった");
    }

    @Test
    @DisplayName("重みの比どおりに割れる")
    public void test重みが効いている() {
        WeightedRandomDecorator decorator = weighted(90, 10);
        int[] counts = new int[2];
        for (long seed = 0; seed < 1000; seed++) {
            counts[pickedIndex(decorator, seeded(seed), 0)]++;
        }

        assertTrue(counts[0] > 820 && counts[0] < 970, "90:10 のはずが " + counts[0] + ":" + counts[1]);
    }

    @Test
    @DisplayName("重み 0 の枠は引かれない")
    public void test重み0は引かれない() {
        WeightedRandomDecorator decorator = weighted(0, 5, 0);
        for (long seed = 0; seed < 200; seed++) {
            assertEquals(1, pickedIndex(decorator, seeded(seed), 0), "重み 0 の枠が選ばれた: seed=" + seed);
        }
    }

    @Test
    @DisplayName("全部重み 0 なら何も選ばない")
    public void test重みが全部0() {
        // 合計 0 で割ると落ちる。書き間違えたレシピでクラッシュさせない。
        assertNull(weighted(0, 0).pick(seeded(1L), 0));
        assertNull(new WeightedRandomDecorator(null, Collections.emptyList(), 1).pick(seeded(1L), 0));
    }

    @Test
    @DisplayName("種を持たない文脈でも落ちない")
    public void test文脈が無くても落ちない() {
        // ポート列にコントローラが居ないと文脈は null になる。
        // 旧コードはそれでも抽選していたので、例外にはしない。
        bonus(0.5).rolls(null);
        bonusBlock(0.5).rolls(null);
        weighted(1, 1).pick(null, 0);
        perPosition(0.5).rollsAt(null, new ChunkCoordinates(0, 0, 0));
        randomBlocks().select(cells(), 2, null);
    }

    // ============================================
    // 位置ごとの確率判定
    // ============================================

    @Test
    @DisplayName("位置ごとの判定が全部同じ答えに潰れない")
    public void test位置ごとに割れる() {
        // 種は実行中ずっと固定なので、位置を混ぜないと 50 マスが同じ数を引く。
        // 「全部置く」か「全部置かない」の二択になり、確率を書いた意味が消える。
        PerPositionProbabilityDecorator decorator = perPosition(0.5);
        ConditionContext context = seeded(20260802L);

        int hits = 0;
        for (ChunkCoordinates pos : cells()) {
            if (decorator.rollsAt(context, pos)) hits++;
        }

        assertTrue(hits > 10 && hits < 40, "50 マス中 " + hits + " マスが当たった（0 や 50 に潰れている）");
    }

    @Test
    @DisplayName("同じマスは何度聞いても同じ答え")
    public void test位置ごとの判定が決定的である() {
        PerPositionProbabilityDecorator decorator = perPosition(0.5);
        ConditionContext context = seeded(77L);
        ChunkCoordinates pos = new ChunkCoordinates(13, -4, 208);

        boolean first = decorator.rollsAt(context, pos);
        for (int i = 0; i < 20; i++) {
            assertEquals(first, decorator.rollsAt(context, pos), i + " 回目で答えが変わった");
        }
    }

    @Test
    @DisplayName("位置ごとの判定でも確率 0 と 1 は言い切れる")
    public void test位置ごとの確率の両端() {
        for (ChunkCoordinates pos : cells()) {
            assertFalse(perPosition(0.0).rollsAt(seeded(5L), pos), "確率 0 が当たった: " + pos);
            assertTrue(perPosition(1.0).rollsAt(seeded(5L), pos), "確率 1 が外れた: " + pos);
        }
    }

    @Test
    @DisplayName("実行が変われば当たるマスも変わる")
    public void test位置ごとの判定が実行ごとに変わる() {
        // 隕石が毎回同じ形では困る。
        PerPositionProbabilityDecorator decorator = perPosition(0.5);

        int differed = 0;
        for (ChunkCoordinates pos : cells()) {
            if (decorator.rollsAt(seeded(1L), pos) != decorator.rollsAt(seeded(2L), pos)) differed++;
        }

        assertTrue(differed > 5, "種を変えても " + differed + " マスしか変わらなかった");
    }

    // ============================================
    // N マスだけ選ぶ
    // ============================================

    @Test
    @DisplayName("同じ種なら同じマスが選ばれる")
    public void test選択が決定的である() {
        RandomBlockOutputDecorator decorator = randomBlocks();
        ConditionContext context = seeded(4649L);

        List<ChunkCoordinates> first = decorator.select(cells(), 7, context);
        for (int i = 0; i < 10; i++) {
            assertEquals(first, decorator.select(cells(), 7, context), i + " 回目で選ばれたマスが変わった");
        }
    }

    @Test
    @DisplayName("数を増やしても前に選んだマスは外れない")
    public void test選択が積み上がる() {
        // シャッフルではなく「くじ番号の小さい順」なので、数を増やすと前の選択を含む。
        // 稼働中に少しずつ置いていく形（徐々に組み上がる構造体）がこれで書ける。
        RandomBlockOutputDecorator decorator = randomBlocks();
        ConditionContext context = seeded(31415L);

        List<ChunkCoordinates> few = decorator.select(cells(), 5, context);
        List<ChunkCoordinates> many = decorator.select(cells(), 12, context);

        assertEquals(5, few.size());
        assertEquals(12, many.size());
        assertTrue(many.containsAll(few), "数を増やしたら前に選んだマスが外れた");
    }

    @Test
    @DisplayName("実行が変われば選ばれるマスも変わる")
    public void test選択が実行ごとに変わる() {
        RandomBlockOutputDecorator decorator = randomBlocks();

        assertNotEquals(decorator.select(cells(), 8, seeded(1L)), decorator.select(cells(), 8, seeded(2L)));
    }

    @Test
    @DisplayName("マス数より多く要求されても落ちない")
    public void test選択の端() {
        RandomBlockOutputDecorator decorator = randomBlocks();

        assertEquals(
            50,
            decorator.select(cells(), 999, seeded(1L))
                .size());
        assertTrue(
            decorator.select(cells(), 0, seeded(1L))
                .isEmpty());
        assertTrue(
            decorator.select(cells(), -1, seeded(1L))
                .isEmpty());
        assertTrue(
            decorator.select(Collections.emptyList(), 3, seeded(1L))
                .isEmpty());
        assertTrue(
            decorator.select(null, 3, seeded(1L))
                .isEmpty());
    }

    @Test
    @DisplayName("選ばれるマスが特定の場所に偏らない")
    public void test選択が偏らない() {
        // くじ番号順に並べる実装なので、番号が座標に引きずられていると
        // 「いつも同じ端から埋まる」になる。種を振って各マスの当選回数を見る。
        RandomBlockOutputDecorator decorator = randomBlocks();
        List<ChunkCoordinates> cells = cells();
        int[] wins = new int[cells.size()];

        for (long seed = 0; seed < 500; seed++) {
            for (ChunkCoordinates pos : decorator.select(cells, 10, seeded(seed))) {
                wins[cells.indexOf(pos)]++;
            }
        }

        // 50 マスから 10 個を 500 回。期待値は 1 マスあたり 100。
        for (int i = 0; i < wins.length; i++) {
            assertTrue(wins[i] > 40 && wins[i] < 180, cells.get(i) + " が 500 回中 " + wins[i] + " 回選ばれた（偏っている）");
        }
    }

    // ============================================
    // バッチ回数分引く
    // ============================================
    //
    // バッチ n は「レシピを n 回動かしたのを 1 回にまとめたもの」。
    // レシピ本体の出力は既にそう払う（`resolveAmount` が n 回引く）のに、
    // デコレータはバッチに関係なく **1 回しか引いていなかった**。
    //
    // つまりバッチを解禁した瞬間、ボーナスの期待値が **1/n に落ちる**。
    // 出力は n 倍になるのにボーナスは据え置き、という気づきにくい下方修正になっていた。

    @Test
    @DisplayName("バッチ n ならボーナスを n 回引く")
    public void testバッチ回数分引く() {
        // 確率 1 なら回数がそのまま出るので、引いた回数を直接数えられる。
        BonusOutputDecorator always = bonus(1.0);

        assertEquals(1, always.timesFiring(seeded(1L), 1));
        assertEquals(4, always.timesFiring(seeded(1L), 4));
        assertEquals(16, always.timesFiring(seeded(1L), 16));
    }

    @Test
    @DisplayName("外れる確率でもバッチ内で当たり外れが混ざる")
    public void testバッチ内で割れる() {
        // n 回とも同じ答えなら「1 回引いて n 倍した」のと変わらない。
        // 抽選が引くたびに動いていることを、0 でも n でもない結果が出ることで見る。
        BonusOutputDecorator decorator = bonus(0.5);

        boolean mixed = false;
        for (long seed = 0; seed < 50; seed++) {
            int fired = decorator.timesFiring(seeded(seed), 8);
            if (fired > 0 && fired < 8) mixed = true;
        }

        assertTrue(mixed, "バッチ 8 で毎回 0 か 8 しか出なかった（1 回引いて使い回している）");
    }

    @Test
    @DisplayName("バッチ 1 は従来と同じ答え")
    public void testバッチ1は据え置き() {
        // forDraw(0) は文脈をそのまま返すので、バッチを入れても
        // 既存のレシピの当たり外れは 1 ビットも変わらない。
        BonusOutputDecorator decorator = bonus(0.5);
        for (long seed = 0; seed < 100; seed++) {
            assertEquals(decorator.rolls(seeded(seed)) ? 1 : 0, decorator.timesFiring(seeded(seed), 1));
        }
    }

    @Test
    @DisplayName("確率 0 はバッチを積んでも当たらない")
    public void testバッチと確率0() {
        assertEquals(0, bonus(0.0).timesFiring(seeded(1L), 16));
    }

    @Test
    @DisplayName("bonus_block もバッチ回数分引く")
    public void testブロック側もバッチ回数分引く() {
        assertEquals(4, bonusBlock(1.0).timesFiring(seeded(1L), 4));
        assertEquals(0, bonusBlock(0.0).timesFiring(seeded(1L), 4));
    }

    @Test
    @DisplayName("重み表は rolls × バッチ回数だけ引く")
    public void test重み表もバッチ回数分引く() {
        // rolls は「1 回のレシピにつき何個選ぶか」。バッチ n なら n 倍。
        WeightedRandomDecorator decorator = new WeightedRandomDecorator(null, poolOf(1, 1), 3);

        assertEquals(3, decorator.totalRolls(withoutMachine()));
        assertEquals(3 * MACHINE_BATCH, decorator.totalRolls(withMachine()));
    }

    // ============================================
    // 機械からバッチ数を読めているか
    // ============================================

    @Test
    @DisplayName("機械が繋がっていればバッチ数を読む")
    public void test機械からバッチ数を読む() {
        // ここが繋がっていないと、上のバッチ処理は書かれたのに誰も使わない形になる。
        assertEquals(MACHINE_BATCH, bonus(1.0).timesFiring(withMachine()));
        assertEquals(0, bonus(0.0).timesFiring(withMachine()));
    }

    @Test
    @DisplayName("機械が繋がっていなければ 1 回")
    public void test機械が無ければ1回() {
        // NEI のレシピ表示や検証経路は機械を持たない。バッチを 0 と読んで
        // 「一度も引かない」になると、ボーナスが黙って消える。
        assertEquals(1, bonus(1.0).timesFiring(withoutMachine()));
        assertEquals(1, bonus(1.0).timesFiring(null));
    }

    // ============================================
    // 生の Random が戻ってこないこと
    // ============================================

    @Test
    @DisplayName("デコレータに種無しの Random が残っていない")
    public void test種無しのRandomが残っていない() {
        Path decorators = Paths.get("src/main/java/ruiseki/okmodular/api/recipe/decorator");
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(decorators)) {
            files.filter(
                p -> p.toString()
                    .endsWith(".java"))
                .forEach(p -> {
                    if (read(p).contains("new Random()")) {
                        offenders.add(
                            p.getFileName()
                                .toString());
                    }
                });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertTrue(
            offenders.isEmpty(),
            offenders + " が種無しの Random を持っている。レシピは全マシンで共有されるので、" + "フィールドに持つと引いた値が他のマシンに依存する");
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + path, e);
        }
    }
}
