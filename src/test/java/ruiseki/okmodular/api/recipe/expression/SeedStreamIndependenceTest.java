package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 抽選の系統が**互いに独立**であること、および**確率の両端が確定的**であることの検証。
 *
 * ============================================
 * 系統を使い回すと 2 つの機能が連動する
 * ============================================
 *
 * 系統定数は「同じ種から違う数列を取る」ための番号。番号を使い回すと、
 * **別々のはずの 2 つの抽選が必ず同じ目を出す**。
 *
 * 気づく手段が「なんか揃って出る気がする」しかないのが最悪の点で、
 * 確率的な機能どうしの相関は、目視では偶然と区別できない。
 * 番号は手で振るので、9 個目を足すときに 5 を書いても誰も止めない。
 *
 * 一覧を手で持たず**リフレクションで数える**ので、定数を足した瞬間に対象になる。
 *
 * ============================================
 * 確率の両端は確定的でなければならない
 * ============================================
 *
 * デコレータ 6 種が一度も動いていなかったのに長く気づかれなかったのは、
 * 効果が全部確率的で、**「壊れている」と「運が悪い」が区別できなかった**から。
 *
 * 確率を取る機能には、**必ず起きる入口と必ず起きない入口**が要る。
 * `toUnitInterval` が `[0, 1)` を返すので、`< 1.0` は常に成立し `< 0.0` は決して成立しない。
 * この 2 つが成り立つ限り、レシピ側で `chance: 1.0` を書けば
 * 「効いていない」と「確率が低い」を切り分けられる。
 */
@DisplayName("抽選の系統と確率の両端")
public class SeedStreamIndependenceTest {

    private static final long[] SEEDS = { 0L, 1L, -1L, 42L, 1234567890123L, Long.MIN_VALUE, Long.MAX_VALUE };

    @Test
    @DisplayName("系統定数に重複が無い")
    public void test番号を使い回していない() {
        Map<Long, List<String>> byValue = new LinkedHashMap<>();

        for (Map.Entry<String, Long> stream : streams().entrySet()) {
            byValue.computeIfAbsent(stream.getValue(), value -> new ArrayList<>())
                .add(stream.getKey());
        }

        List<String> collisions = new ArrayList<>();
        for (Map.Entry<Long, List<String>> entry : byValue.entrySet()) {
            if (entry.getValue()
                .size() > 1) collisions.add(entry.getValue() + " が " + entry.getKey());
        }

        assertTrue(collisions.isEmpty(), collisions + " で同じ番号を使っている。2 つの機能が必ず連動し、" + "気づく手段は「なんとなく揃う」しかない");
    }

    @Test
    @DisplayName("どの 2 系統も同じ種で違う値を返す")
    public void test系統どうしが揃わない() {
        List<String> agreeing = new ArrayList<>();
        Map<String, Long> streams = streams();

        for (String a : streams.keySet()) {
            for (String b : streams.keySet()) {
                if (a.compareTo(b) >= 0) continue;

                int same = 0;
                for (long seed : SEEDS) {
                    if (SeedMixer.toUnitInterval(seed, streams.get(a))
                        == SeedMixer.toUnitInterval(seed, streams.get(b))) same++;
                }
                if (same > 0) agreeing.add(a + " と " + b + " が " + same + " 個の種で一致");
            }
        }

        assertTrue(agreeing.isEmpty(), agreeing + "。定数が違っても混ぜ方が弱いと値が揃う");
    }

    @Test
    @DisplayName("確率 1.0 は必ず起き、0.0 は決して起きない")
    public void test確定的な入口がある() {
        // ここが崩れると、レシピ側から「効いていない」を切り分ける手段が無くなる。
        for (long seed : SEEDS) {
            for (long stream : streams().values()) {
                double draw = SeedMixer.toUnitInterval(seed, stream);

                assertTrue(draw < 1.0, "種 " + seed + " 系統 " + stream + " が 1.0 以上を返した。chance 1.0 が外れる");
                assertFalse(draw < 0.0, "種 " + seed + " 系統 " + stream + " が負を返した。chance 0.0 が当たる");
            }
        }
    }

    @Test
    @DisplayName("同じ問いを二度すると同じ答えが返る")
    public void test実行内で動かない() {
        // エンジンは「入るか確かめてから入れる」ので、2 回目が違うと
        // 「置けると答えたのに置かない」が起きる。
        for (long seed : SEEDS) {
            for (long stream : streams().values()) {
                assertEquals(
                    SeedMixer.toUnitInterval(seed, stream),
                    SeedMixer.toUnitInterval(seed, stream),
                    "同じ種と系統で答えが動いた");
            }
        }
    }

    @Test
    @DisplayName("走査そのものが空振りしていない")
    public void test走査が効いている() {
        Map<String, Long> streams = streams();

        assertTrue(streams.size() >= 9, "系統定数が 9 個未満。リフレクションの走査が壊れている: " + streams.keySet());
        assertTrue(streams.containsKey("RANDOM"), "RANDOM すら拾えていない");
        assertTrue(streams.containsKey("PER_POSITION"), "PER_POSITION を拾えていない");
    }

    /** `SeedMixer` の `public static final long` を全部。手で並べるとここが古くなる。 */
    private static Map<String, Long> streams() {
        Map<String, Long> found = new LinkedHashMap<>();
        Set<String> skipped = new TreeSet<>();

        for (Field field : SeedMixer.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers))
                continue;
            if (field.getType() != long.class) continue;

            try {
                found.put(field.getName(), field.getLong(null));
            } catch (IllegalAccessException e) {
                skipped.add(field.getName());
            }
        }

        assertTrue(skipped.isEmpty(), skipped + " が読めなかった。読めないものを黙って除くと、重複していても気づけない");
        return found;
    }
}
