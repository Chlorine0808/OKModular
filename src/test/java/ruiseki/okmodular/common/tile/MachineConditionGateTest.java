package ruiseki.okmodular.common.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ICondition;

/**
 * 機械そのものの稼働条件の判定。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * B8（機械の動作条件）の判定部分。**`TEMachineController` はユニットテストで組めない**
 * （`MockWorld` がコンストラクタで NPE を出し、既存の TE テストは `@Disabled`）ので、
 * 判定を World 不要の純粋クラスに切り出してここでテストする。
 *
 * B6 + A1 で `ExternalPortConfigCodec` / `PortColorGrouping` / `ColoredRecipeSearch` に
 * 対してやったのと同じ方針。**実機でしか通らないのは配線だけ**にする。
 *
 * ============================================
 * 何を守るのか — 「条件を書いていない機械のコスト」
 * ============================================
 *
 * これは**毎 tick 走る**。だから「条件が無いときに何もしない」ことを
 * 性能の話ではなく**契約として**縛る。具体的には
 * **`ConditionContext` を 1 個も作らない**（`HashMap` を 1 個も確保しない）。
 *
 * A1 の `PortColorGrouping.select` に「無色ならリストをコピーしない」fast path を
 * 入れたのと同じ理由。**使っていない機能のためにコストを払わせない。**
 *
 * `Supplier` で受けるのはそのため。呼ばれたかどうかがテストから見える。
 *
 * ============================================
 * 評価の順序も契約
 * ============================================
 *
 * - 最初に落ちた条件で**短絡**する（後続は評価しない）
 * - `ConditionContext` は**1 回だけ**作って条件間で使い回す（同一 tick の同一機械なので同じ文脈）
 * - `getDescription()` は**呼ばれるまで呼ばない**。`StatCollector` を引くので毎 tick やる意味がない
 *
 * ============================================
 */
@DisplayName("機械の稼働条件の判定")
public class MachineConditionGateTest {

    // ========== スタブ ==========

    /** 決まった答えを返す条件。評価されたかどうかを覚える。 */
    private static final class StubCondition implements ICondition {

        private final boolean answer;
        private final String description;
        private int evaluations = 0;

        StubCondition(boolean answer, String description) {
            this.answer = answer;
            this.description = description;
        }

        @Override
        public boolean isMet(ConditionContext context) {
            evaluations++;
            return answer;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public void write(JsonObject json) {}
    }

    /** `getDescription()` を呼ばれたら落ちる条件。遅延評価の確認用。 */
    private static final class DescriptionExplodes implements ICondition {

        @Override
        public boolean isMet(ConditionContext context) {
            return true;
        }

        @Override
        public String getDescription() {
            throw new AssertionError("getDescription() は必要になるまで呼んではいけない");
        }

        @Override
        public void write(JsonObject json) {}
    }

    /** 呼ばれた回数を数える文脈の供給元。**World には触らない。** */
    private static final class CountingSupplier implements Supplier<ConditionContext> {

        private int calls = 0;

        @Override
        public ConditionContext get() {
            calls++;
            // ConditionContext は World を保持するだけで参照しないので null で足りる。
            return new ConditionContext(null, 0, 0, 0);
        }
    }

    /** 呼ばれたら落ちる供給元。**作られないこと**の確認用。 */
    private static Supplier<ConditionContext> mustNotBeCalled() {
        return () -> { throw new AssertionError("条件が無いのに ConditionContext を作った"); };
    }

    // ========== 条件が無いとき ==========

    @Test
    @DisplayName("条件が無ければ通り、ConditionContext を作らない")
    public void test条件が無ければ何もしない() {
        assertTrue(
            MachineConditionGate.evaluate(Collections.emptyList(), mustNotBeCalled())
                .isMet());
    }

    @Test
    @DisplayName("null のリストも条件が無いのと同じ")
    public void testnullのリスト() {
        // 構造 JSON に conditions を書いていない場合。
        assertTrue(
            MachineConditionGate.evaluate(null, mustNotBeCalled())
                .isMet());
    }

    @Test
    @DisplayName("通ったとき落ちた条件は無い")
    public void test通ったときの中身() {
        MachineConditionGate.Verdict verdict = MachineConditionGate
            .evaluate(Collections.emptyList(), mustNotBeCalled());

        assertNull(verdict.getFailedCondition());
        assertNull(verdict.getFailedDescription());
    }

    // ========== 満たしているとき ==========

    @Test
    @DisplayName("すべて満たせば通る")
    public void testすべて満たせば通る() {
        List<ICondition> conditions = Arrays.asList(new StubCondition(true, "a"), new StubCondition(true, "b"));

        assertTrue(
            MachineConditionGate.evaluate(conditions, new CountingSupplier())
                .isMet());
    }

    @Test
    @DisplayName("ConditionContext は 1 回だけ作って使い回す")
    public void test文脈を使い回す() {
        // 同じ tick の同じ機械を見ているので、条件ごとに作り直す意味がない。
        List<ICondition> conditions = Arrays
            .asList(new StubCondition(true, "a"), new StubCondition(true, "b"), new StubCondition(true, "c"));
        CountingSupplier supplier = new CountingSupplier();

        MachineConditionGate.evaluate(conditions, supplier);

        assertEquals(1, supplier.calls, "条件の数だけ ConditionContext を作っている");
    }

    @Test
    @DisplayName("すべての条件が評価される")
    public void test全部評価される() {
        StubCondition first = new StubCondition(true, "a");
        StubCondition second = new StubCondition(true, "b");

        MachineConditionGate.evaluate(Arrays.asList(first, second), new CountingSupplier());

        assertEquals(1, first.evaluations);
        assertEquals(1, second.evaluations);
    }

    // ========== 満たしていないとき ==========

    @Test
    @DisplayName("1 つでも満たさなければ止まり、落ちた条件を返す")
    public void test落ちた条件を返す() {
        StubCondition failing = new StubCondition(false, "雨が必要");
        MachineConditionGate.Verdict verdict = MachineConditionGate
            .evaluate(Collections.singletonList(failing), new CountingSupplier());

        assertFalse(verdict.isMet());
        assertSame(failing, verdict.getFailedCondition(), "落ちた条件そのものを返すこと");
        assertEquals("雨が必要", verdict.getFailedDescription());
    }

    @Test
    @DisplayName("最初に落ちた条件で短絡する")
    public void test短絡する() {
        StubCondition failing = new StubCondition(false, "落ちる");
        StubCondition after = new StubCondition(true, "評価されないはず");

        MachineConditionGate.Verdict verdict = MachineConditionGate
            .evaluate(Arrays.asList(failing, after), new CountingSupplier());

        assertSame(failing, verdict.getFailedCondition());
        assertEquals(0, after.evaluations, "落ちた後の条件を評価している");
    }

    @Test
    @DisplayName("報告するのは最初に落ちた条件")
    public void test最初に落ちたものを報告する() {
        StubCondition first = new StubCondition(false, "1 つ目");
        StubCondition second = new StubCondition(false, "2 つ目");

        assertEquals(
            "1 つ目",
            MachineConditionGate.evaluate(Arrays.asList(first, second), new CountingSupplier())
                .getFailedDescription());
    }

    // ========== 遅延評価 ==========

    @Test
    @DisplayName("getDescription は聞かれるまで呼ばれない")
    public void test説明は遅延して読む() {
        // 毎 tick 走るので、通っている間に StatCollector を引く理由がない。
        MachineConditionGate.Verdict verdict = MachineConditionGate
            .evaluate(Collections.singletonList(new DescriptionExplodes()), new CountingSupplier());

        assertTrue(verdict.isMet()); // ここまでで getDescription() が呼ばれていたら落ちている
    }

    // ========== null 要素 ==========

    @Test
    @DisplayName("null の条件は飛ばす")
    public void testnullの条件を飛ばす() {
        // パーサは読めなかった条件に null を返し、その時点で警告を出す（`ConditionParserRegistry`）。
        // 読み取り側が濾すのが本筋だが、毎 tick 走る場所で NPE を出すより飛ばす。
        List<ICondition> conditions = new ArrayList<>();
        conditions.add(null);
        conditions.add(new StubCondition(true, "a"));

        assertTrue(
            MachineConditionGate.evaluate(conditions, new CountingSupplier())
                .isMet());
    }

    @Test
    @DisplayName("null だけなら ConditionContext も作らない")
    public void testnullだけのとき() {
        List<ICondition> conditions = new ArrayList<>();
        conditions.add(null);

        assertTrue(
            MachineConditionGate.evaluate(conditions, mustNotBeCalled())
                .isMet());
    }
}
