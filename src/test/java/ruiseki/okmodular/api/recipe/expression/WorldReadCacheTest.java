package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;

/**
 * How long a world read is reused.
 *
 * A working machine evaluates its conditions every tick, and count_blocks(16) reads about
 * 36 000 blocks each time. The cache trades up to 20 ticks of staleness for that cost, so
 * the bound itself is what is pinned here: one tick longer and the docs would be wrong.
 */
@DisplayName("ワールド読み取りのキャッシュ")
public class WorldReadCacheTest {

    /** Answers 1, 2, 3, ... and counts how often it was asked. */
    private static final class Reader implements Supplier<EvaluationValue> {

        final AtomicInteger reads = new AtomicInteger();

        @Override
        public EvaluationValue get() {
            return new EvaluationValue((double) reads.incrementAndGet());
        }
    }

    @Test
    @DisplayName("20 tick 未満なら読み直さない")
    public void test保持期間内() {
        WorldReadCache cache = new WorldReadCache();
        Reader reader = new Reader();

        cache.get("k", 100, reader);
        EvaluationValue last = cache.get("k", 100 + WorldReadCache.MAX_AGE - 1, reader);

        assertEquals(1, reader.reads.get());
        assertEquals(1.0, last.asDouble());
    }

    @Test
    @DisplayName("20 tick 経ったら読み直す")
    public void test保持期間切れ() {
        WorldReadCache cache = new WorldReadCache();
        Reader reader = new Reader();

        cache.get("k", 100, reader);
        EvaluationValue fresh = cache.get("k", 100 + WorldReadCache.MAX_AGE, reader);

        assertEquals(2, reader.reads.get());
        assertEquals(2.0, fresh.asDouble());
    }

    @Test
    @DisplayName("上限は 20 tick")
    public void test上限() {
        assertEquals(20, WorldReadCache.MAX_AGE);
    }

    @Test
    @DisplayName("キーが違えば別に読む")
    public void testキーごと() {
        WorldReadCache cache = new WorldReadCache();
        Reader reader = new Reader();

        cache.get("count_blocks|1", 100, reader);
        cache.get("count_blocks|2", 100, reader);

        assertEquals(2, reader.reads.get());
    }

    @Test
    @DisplayName("clear の後は読み直す")
    public void test破棄() {
        WorldReadCache cache = new WorldReadCache();
        Reader reader = new Reader();

        cache.get("k", 100, reader);
        cache.clear();
        cache.get("k", 101, reader);

        assertEquals(2, reader.reads.get());
    }

    @Test
    @DisplayName("時刻が戻ったら読み直す")
    public void test時刻の巻き戻し() {
        // The total world time should not go backwards, but if it does, an entry stamped in
        // the future would otherwise be served for longer than the bound.
        WorldReadCache cache = new WorldReadCache();
        Reader reader = new Reader();

        cache.get("k", 100, reader);
        cache.get("k", 50, reader);

        assertEquals(2, reader.reads.get());
    }

    @Test
    @DisplayName("期限切れのエントリは溜まらない")
    public void test期限切れの掃除() {
        // A key built from an argument that changes every tick, such as count_blocks(tick % 1000),
        // is never asked for again. Without a sweep the map grows until the structure re-forms.
        WorldReadCache cache = new WorldReadCache();
        Reader reader = new Reader();

        for (int tick = 0; tick < 1000; tick++) {
            cache.get("k" + tick, tick, reader);
        }

        assertTrue(cache.size() <= 2 * WorldReadCache.MAX_AGE, "entries: " + cache.size());
    }

    @Test
    @DisplayName("機械のコンテキストは機械のキャッシュを使う")
    public void test機械のコンテキスト() {
        ConditionContext context = StubMachineContext.withMachine();
        Reader reader = new Reader();

        WorldReadCache.read(context, 100, "k", reader);
        WorldReadCache.read(context, 101, "k", reader);

        assertEquals(1, reader.reads.get());
    }

    @Test
    @DisplayName("機械の無いコンテキストは毎回読む")
    public void test機械なし() {
        ConditionContext context = StubMachineContext.withoutMachine();
        Reader reader = new Reader();

        WorldReadCache.read(context, 100, "k", reader);
        WorldReadCache.read(context, 100, "k", reader);

        assertEquals(2, reader.reads.get());
    }

    @Test
    @DisplayName("パターンのセルは毎回読む")
    public void testセル() {
        // A pattern is written once per placement, so caching per cell would only hold
        // entries nobody asks for again.
        ConditionContext cell = ConditionContext.forCell(StubMachineContext.withMachine(), 5, 64, 5, 1, 0, 1);
        Reader reader = new Reader();

        WorldReadCache.read(cell, 100, "k", reader);
        WorldReadCache.read(cell, 100, "k", reader);

        assertEquals(2, reader.reads.get());
    }
}
