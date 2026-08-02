package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.recipe.expression.ConstantExpression;
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

    private static WeightedRandomDecorator weighted(int... weights) {
        List<WeightedRandomDecorator.WeightedOutputEntry> pool = new ArrayList<>();
        for (int weight : weights) {
            pool.add(new WeightedRandomDecorator.WeightedOutputEntry(null, weight));
        }
        return new WeightedRandomDecorator(null, pool, 1);
    }

    /** 重み表のどの枠が当たったか。等値比較できないので添字で答え合わせする。 */
    private static int pickedIndex(WeightedRandomDecorator decorator, ConditionContext context, int draw) {
        return decorator.getPool()
            .indexOf(decorator.pick(context, draw));
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
    }

}
