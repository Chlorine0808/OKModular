package ruiseki.okmodular.api.recipe.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import ruiseki.okmodular.api.modular.IPortType.Direction;
import ruiseki.okmodular.api.modular.IPortType.Type;
import ruiseki.okmodular.api.recipe.expression.StubMachineContext;

/**
 * すべての資源種が 3 本のアクセサに繋がっていることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `getCapacity` / `getAmount` / `getSpace` はどれも `switch (kind)` で振り分け、
 * **`default: return 0L`** に落ちる。つまり資源種を足して per-kind メソッドまで
 * 書いたのに 3 本の switch に分岐を足し忘れると、
 * **例外もログも出さずに 0 を返す**。
 *
 * これは式名の登録漏れ（energy_per_tick が黙って 0 だった件）と同じ失敗モードで、
 * 手書きの対応表が 3 箇所に増えた分だけ穴の空く場所も増えている。
 * 資源種は今の 7 種で終わりではない（LP / KU・HU / 蜂 が控えている）ので、
 * **足したときに落ちる仕掛け**を先に置く。
 *
 * ============================================
 * なぜ資源種を名指しせず列挙するのか
 * ============================================
 *
 * 資源種を名指しした検証（MachineStateCapacityTest など）は、
 * **新しく足された種を検証対象に含めない**。
 * ここは `Type.values()` を回すので、定数を 1 つ足した瞬間に対象が増える。
 *
 * `isStorable()` は `BLOCK` と `NONE` 以外を true と答えるので、
 * **新しい定数は自動的に「量を持つ種」として扱われ、配線を要求される**。
 * 配線しないままだと 0 が返り、このテストが落ちる。
 *
 * ============================================
 * 不変条件
 * ============================================
 *
 * - 量を持つ種（`isStorable()`）→ 3 本すべてが**非ゼロ**を返す
 * - 量を持たない種（BLOCK / NONE）→ 3 本すべてが **0** を返す
 *
 * 値そのものは見ない（それは MachineState{Capacity,Amount,Space}Test の仕事）。
 * ここが見ているのは「配線されているか」だけ。
 *
 * 方向は INPUT / OUTPUT / BOTH の 3 つだけを回す。
 * `Direction.NONE` は「方向を指定しない」ではなく「方向が無い」を表す値で、
 * 問い合わせに使う向きではない。
 *
 * ============================================
 */
@DisplayName("資源種の配線の網羅")
public class MachineStateKindCoverageTest {

    private static final Direction[] QUERY_DIRECTIONS = { Direction.INPUT, Direction.OUTPUT, Direction.BOTH };

    /** 種類を指定しない問い合わせと、指定する問い合わせの両方を試す。 */
    private static final String[] NAMES = { null, "何らかの資源名" };

    private final IMachineState state = StubMachineContext.machineState();

    @ParameterizedTest(name = "{0}")
    @EnumSource(Type.class)
    @DisplayName("容量が配線されている")
    public void test容量が配線されている(Type kind) {
        long capacity = state.getCapacity(kind);

        if (kind.isStorable()) {
            assertNotEquals(0L, capacity, () -> kind + " の容量が 0。getCapacity の switch に分岐が無く default に落ちている");
        } else {
            assertEquals(0L, capacity, () -> kind + " は量を持たないので 0 であるべき");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Type.class)
    @DisplayName("量が全方向で配線されている")
    public void test量が配線されている(Type kind) {
        for (Direction dir : QUERY_DIRECTIONS) {
            for (String name : NAMES) {
                long amount = state.getAmount(kind, dir, name);

                if (kind.isStorable()) {
                    assertNotEquals(
                        0L,
                        amount,
                        () -> kind + " / "
                            + dir
                            + " / "
                            + (name == null ? "種類指定なし" : "種類指定あり")
                            + " の量が 0。getAmount の switch に分岐が無く default に落ちている");
                } else {
                    assertEquals(0L, amount, () -> kind + " は量を持たないので 0 であるべき");
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Type.class)
    @DisplayName("空き容量が全方向で配線されている")
    public void test空きが配線されている(Type kind) {
        for (Direction dir : QUERY_DIRECTIONS) {
            for (String name : NAMES) {
                long space = state.getSpace(kind, dir, name);

                if (kind.isStorable()) {
                    assertNotEquals(
                        0L,
                        space,
                        () -> kind + " / "
                            + dir
                            + " / "
                            + (name == null ? "種類指定なし" : "種類指定あり")
                            + " の空きが 0。getSpace の switch に分岐が無く default に落ちている");
                } else {
                    assertEquals(0L, space, () -> kind + " は量を持たないので 0 であるべき");
                }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Type.class)
    @DisplayName("方向別の格納を持つ種は、方向で違う答えを返す")
    public void test方向別の格納(Type kind) {
        if (!kind.isStorable()) {
            return;
        }

        long in = state.getAmount(kind, Direction.INPUT, null);
        long out = state.getAmount(kind, Direction.OUTPUT, null);
        long both = state.getAmount(kind, Direction.BOTH, null);

        if (kind.hasDirectionalStorage()) {
            assertNotEquals(in, out, () -> kind + " は入出力が別の格納なのに、方向で同じ値を返している");
        } else {
            assertEquals(both, in, () -> kind + " は単一のプールなので、方向を指定しても合計を返すべき。0 を返してはいけない");
            assertEquals(both, out, () -> kind + " は単一のプールなので、方向を指定しても合計を返すべき");
        }
    }
}
