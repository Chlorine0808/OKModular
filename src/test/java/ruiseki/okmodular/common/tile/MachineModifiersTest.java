package ruiseki.okmodular.common.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.common.tile.MachineModifiers.Modifier;

/**
 * 構造体が与える性能係数（`speedMultiplier` / `energyMultiplier` / `batchMin` / `batchMax`）の評価。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * 元は `TEMachineController` の 4 メソッドに直接書かれていて、そこで
 * **素の `ConditionContext` を作って渡していた**。`MachinePropertyExpression` は
 * 機械の繋がっていない文脈に対して**無条件に 0 を返す**ので、
 * `"speedMultiplier": "tier"` のような式が**例外もログも無く 0 になっていた**。
 *
 * `TEMachineController` はユニットテストで組めない（`MockWorld` がコンストラクタで NPE）ので、
 * `MachineConditionGate` と同じ手で **World 不要の純粋クラスに切り出して**ここで縛る。
 * TE 側に残るのは `getConditionContext()` を渡す 1 行だけになる。
 *
 * ============================================
 * 自己参照は止めなければならない
 * ============================================
 *
 * `speed_multi` は**機械プロパティとして登録済み**で、その実体は
 * `MachineStateAgent` → `TEMachineController.getSpeedMultiplier()` へ委譲される。
 * つまり `"speedMultiplier": "speed_multi * 2"` は
 *
 * <pre>
 * getSpeedMultiplier() → 式の評価 → speed_multi → getSpeedMultiplier() → ...
 * </pre>
 *
 * という循環になり、**文脈を正しく渡した瞬間に `StackOverflowError` でサーバが落ちる**。
 * 今それが起きていないのは、上記のバグ（機械プロパティが 0 になる）が
 * たまたま再帰を断ち切っていたからにすぎない。
 *
 * だから**バグの修正と再入ガードは 1 つの変更**で、ここで一緒に縛る。
 * 循環したら中立値（倍率 1.0 / バッチ 1）を返し、**報告は係数ごとに 1 回だけ**行う
 * （毎 tick 走る場所なので、ログを流し続けるわけにはいかない）。
 *
 * ============================================
 * 係数を書いていない機械のコスト
 * ============================================
 *
 * `speedMultiplier` は**毎 tick 呼ばれる**（`ProcessAgent.executeTick`）。
 * 構造体が無いとき（未形成・ブループリント未挿入）に
 * **`ConditionContext` を 1 個も作らない**ことを性能ではなく契約として縛る。
 * `MachineConditionGate` が `Supplier` で受けているのと同じ理由。
 *
 * ============================================
 */
@DisplayName("構造体の性能係数の評価")
public class MachineModifiersTest {

    // 中立値とも互いとも重ならない値。どのメソッドが呼ばれたかが返り値で分かる。
    private static final double SPEED = 2.5;
    private static final double ENERGY = 3.5;
    private static final int BATCH_MIN = 4;
    private static final int BATCH_MAX = 9;

    // ========== スタブ ==========

    /** 渡された文脈と呼ばれた回数を覚える構造体。評価中に任意の処理を挟める。 */
    private static final class RecordingEntry extends StubStructureEntry {

        private ConditionContext lastContext;
        private int speedCalls = 0;
        private int energyCalls = 0;
        private int batchMinCalls = 0;
        private int batchMaxCalls = 0;

        /** 評価の**最中**に走らせるもの。再入と例外の再現に使う。 */
        private Runnable duringEvaluation = () -> {};

        RecordingEntry() {
            super("recording", Collections.emptyMap());
        }

        @Override
        public double evaluateSpeedMultiplier(ConditionContext context) {
            speedCalls++;
            lastContext = context;
            duringEvaluation.run();
            return SPEED;
        }

        @Override
        public double evaluateEnergyMultiplier(ConditionContext context) {
            energyCalls++;
            lastContext = context;
            duringEvaluation.run();
            return ENERGY;
        }

        @Override
        public int evaluateBatchMin(ConditionContext context) {
            batchMinCalls++;
            lastContext = context;
            duringEvaluation.run();
            return BATCH_MIN;
        }

        @Override
        public int evaluateBatchMax(ConditionContext context) {
            batchMaxCalls++;
            lastContext = context;
            duringEvaluation.run();
            return BATCH_MAX;
        }
    }

    /** 常に同じ文脈を返し、聞かれた回数を数える供給元。**World には触らない。** */
    private static final class CountingSupplier implements Supplier<ConditionContext> {

        private final ConditionContext context = new ConditionContext(null, 0, 0, 0);
        private int calls = 0;

        @Override
        public ConditionContext get() {
            calls++;
            return context;
        }
    }

    /** 呼ばれたら落ちる供給元。**作られないこと**の確認用。 */
    private static Supplier<ConditionContext> mustNotBeCalled() {
        return () -> { throw new AssertionError("構造体が無いのに ConditionContext を作った"); };
    }

    /** 報告された循環を順に覚える。 */
    private static final class CycleLog extends ArrayList<Modifier> {

        private static final long serialVersionUID = 1L;
    }

    // ========== 構造体が無いとき ==========

    @Test
    @DisplayName("構造体が無ければ中立値を返し、ConditionContext を作らない")
    public void test構造体が無ければ何もしない() {
        MachineModifiers modifiers = new MachineModifiers(null);

        assertEquals(1.0, modifiers.speedMultiplier(null, mustNotBeCalled()));
        assertEquals(1.0, modifiers.energyMultiplier(null, mustNotBeCalled()));
        assertEquals(1, modifiers.batchMin(null, mustNotBeCalled()));
        assertEquals(1, modifiers.batchMax(null, mustNotBeCalled()));
    }

    // ========== 通常の評価 ==========

    @Test
    @DisplayName("それぞれ対応する evaluate に委譲する")
    public void test委譲先() {
        RecordingEntry entry = new RecordingEntry();
        MachineModifiers modifiers = new MachineModifiers(null);

        assertEquals(SPEED, modifiers.speedMultiplier(entry, new CountingSupplier()));
        assertEquals(ENERGY, modifiers.energyMultiplier(entry, new CountingSupplier()));
        assertEquals(BATCH_MIN, modifiers.batchMin(entry, new CountingSupplier()));
        assertEquals(BATCH_MAX, modifiers.batchMax(entry, new CountingSupplier()));

        assertEquals(1, entry.speedCalls);
        assertEquals(1, entry.energyCalls);
        assertEquals(1, entry.batchMinCalls);
        assertEquals(1, entry.batchMaxCalls);
    }

    @Test
    @DisplayName("供給元が返した文脈をそのまま渡す")
    public void test文脈をそのまま渡す() {
        // ここが本題。TE 側が渡すのは機械を知っている getConditionContext() の結果で、
        // 途中で作り直したり包み直したりしてはいけない。
        RecordingEntry entry = new RecordingEntry();
        CountingSupplier supplier = new CountingSupplier();

        new MachineModifiers(null).speedMultiplier(entry, supplier);

        assertSame(supplier.context, entry.lastContext, "文脈を作り直している");
    }

    @Test
    @DisplayName("文脈は 1 回の評価につき 1 回だけ要求する")
    public void test文脈は1回だけ作る() {
        CountingSupplier supplier = new CountingSupplier();

        new MachineModifiers(null).speedMultiplier(new RecordingEntry(), supplier);

        assertEquals(1, supplier.calls);
    }

    // ========== 自己参照 ==========

    @Test
    @DisplayName("自分自身を読む式は中立値で止まる")
    public void test自己参照は中立値で止まる() {
        // `"speedMultiplier": "speed_multi * 2"` の再現。
        RecordingEntry entry = new RecordingEntry();
        MachineModifiers modifiers = new MachineModifiers(null);
        double[] reentrant = new double[1];

        entry.duringEvaluation = () -> reentrant[0] = modifiers.speedMultiplier(entry, new CountingSupplier());

        assertEquals(SPEED, modifiers.speedMultiplier(entry, new CountingSupplier()));
        assertEquals(1.0, reentrant[0], "再入した側が中立値を返していない");
        assertEquals(1, entry.speedCalls, "再入した側でも式を評価している");
    }

    @Test
    @DisplayName("バッチも自己参照で止まる")
    public void testバッチの自己参照() {
        RecordingEntry entry = new RecordingEntry();
        MachineModifiers modifiers = new MachineModifiers(null);
        int[] reentrant = new int[1];

        entry.duringEvaluation = () -> reentrant[0] = modifiers.batchMax(entry, new CountingSupplier());

        assertEquals(BATCH_MAX, modifiers.batchMax(entry, new CountingSupplier()));
        assertEquals(1, reentrant[0]);
    }

    @Test
    @DisplayName("評価が終われば次の呼び出しは通る")
    public void test評価後に状態が戻る() {
        RecordingEntry entry = new RecordingEntry();
        MachineModifiers modifiers = new MachineModifiers(null);

        modifiers.speedMultiplier(entry, new CountingSupplier());

        assertEquals(SPEED, modifiers.speedMultiplier(entry, new CountingSupplier()), "1 回目で塞がったままになっている");
    }

    @Test
    @DisplayName("評価が例外を投げても状態が戻る")
    public void test例外でも状態が戻る() {
        // 式の評価は throw しうる（未登録の名前・ゼロ除算の類）。
        // ここで塞がったままになると、その機械は二度と係数を評価できなくなる。
        RecordingEntry entry = new RecordingEntry();
        MachineModifiers modifiers = new MachineModifiers(null);

        entry.duringEvaluation = () -> { throw new IllegalStateException("式が壊れている"); };
        assertThrows(IllegalStateException.class, () -> modifiers.speedMultiplier(entry, new CountingSupplier()));

        entry.duringEvaluation = () -> {};
        assertEquals(SPEED, modifiers.speedMultiplier(entry, new CountingSupplier()));
    }

    // ========== 係数どうしの参照 ==========

    @Test
    @DisplayName("別の係数は塞がない")
    public void test別の係数は塞がない() {
        // `"speedMultiplier": "energy_multi"` は循環ではないので通ること。
        // ガードが 1 個のフラグだとここが落ちる。
        RecordingEntry entry = new RecordingEntry();
        MachineModifiers modifiers = new MachineModifiers(null);
        double[] nested = new double[1];

        entry.duringEvaluation = () -> {
            entry.duringEvaluation = () -> {};
            nested[0] = modifiers.energyMultiplier(entry, new CountingSupplier());
        };

        assertEquals(SPEED, modifiers.speedMultiplier(entry, new CountingSupplier()));
        assertEquals(ENERGY, nested[0], "別の係数まで塞いでいる");
    }

    // ========== 報告 ==========

    @Test
    @DisplayName("循環したら報告する")
    public void test循環を報告する() {
        RecordingEntry entry = new RecordingEntry();
        CycleLog log = new CycleLog();
        MachineModifiers modifiers = new MachineModifiers(log::add);

        entry.duringEvaluation = () -> modifiers.speedMultiplier(entry, new CountingSupplier());
        modifiers.speedMultiplier(entry, new CountingSupplier());

        assertEquals(Collections.singletonList(Modifier.SPEED_MULTIPLIER), log);
    }

    @Test
    @DisplayName("報告は係数ごとに 1 回だけ")
    public void test報告は1回だけ() {
        // 毎 tick 走るので、循環したままログを流し続けてはいけない。
        RecordingEntry entry = new RecordingEntry();
        CycleLog log = new CycleLog();
        MachineModifiers modifiers = new MachineModifiers(log::add);

        entry.duringEvaluation = () -> modifiers.speedMultiplier(entry, new CountingSupplier());
        for (int i = 0; i < 20; i++) {
            modifiers.speedMultiplier(entry, new CountingSupplier());
        }

        assertEquals(1, log.size(), "循環するたびに報告している");
    }

    @Test
    @DisplayName("係数が違えば別々に報告される")
    public void test係数ごとに報告する() {
        RecordingEntry entry = new RecordingEntry();
        CycleLog log = new CycleLog();
        MachineModifiers modifiers = new MachineModifiers(log::add);

        entry.duringEvaluation = () -> modifiers.speedMultiplier(entry, new CountingSupplier());
        modifiers.speedMultiplier(entry, new CountingSupplier());

        entry.duringEvaluation = () -> modifiers.batchMin(entry, new CountingSupplier());
        modifiers.batchMin(entry, new CountingSupplier());

        assertTrue(log.contains(Modifier.SPEED_MULTIPLIER));
        assertTrue(log.contains(Modifier.BATCH_MIN));
    }

    @Test
    @DisplayName("構造体が変われば報告し直す")
    public void testリセットで報告し直す() {
        // ブループリントを差し替えたら別の構造体なので、その循環は改めて報告する価値がある。
        RecordingEntry entry = new RecordingEntry();
        CycleLog log = new CycleLog();
        MachineModifiers modifiers = new MachineModifiers(log::add);

        entry.duringEvaluation = () -> modifiers.speedMultiplier(entry, new CountingSupplier());
        modifiers.speedMultiplier(entry, new CountingSupplier());
        modifiers.reset();
        modifiers.speedMultiplier(entry, new CountingSupplier());

        assertEquals(2, log.size());
    }

    @Test
    @DisplayName("報告先が無くても循環は止まる")
    public void test報告先が無くても止まる() {
        RecordingEntry entry = new RecordingEntry();
        MachineModifiers modifiers = new MachineModifiers(null);

        entry.duringEvaluation = () -> modifiers.speedMultiplier(entry, new CountingSupplier());

        assertEquals(SPEED, modifiers.speedMultiplier(entry, new CountingSupplier()));
    }
}
