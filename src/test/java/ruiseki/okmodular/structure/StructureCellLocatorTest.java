package ruiseki.okmodular.structure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

/**
 * 世界座標から「パターンのどのセルか」を求める計算の検証。
 *
 * ============================================
 * なぜこれが要るか
 * ============================================
 *
 * 構造の走査は `IStructureElement.check(t, world, x, y, z)` を**世界座標だけ**で呼ぶ。
 * StructureLib 側はローカル座標（ABC）を持っていて `IStructureWalker.visit` には
 * 渡しているのだが、こちらが使っている `IStructureDefinition.check` の経路は
 * walker を内部で作るので受け取れない。
 *
 * よって**こちらで復元する**。構造 IO は「どのセルか」を記録する設計なので、
 * ここが 1 つずれると記録が全部ずれる。
 *
 * ============================================
 * 計算の根拠
 * ============================================
 *
 * `StructureUtility.iterateV2` は次の順で世界座標を作っている:
 *
 * <pre>
 * basePositionA = -ox            // 3123-3125 行で符号反転される
 * xyz = getWorldOffset(abc) + コントローラ世界座標
 * </pre>
 *
 * よって逆は `abc = getOffsetABC(世界座標 - コントローラ世界座標)`。
 * さらに走査が報告するセル番号は `abc - basePositionA` = `abc + ox` なので、
 * **コントローラオフセットを足し戻す**。
 *
 * <pre>
 * セル = getOffsetABC(世界座標 - コントローラ座標) + コントローラオフセット
 * </pre>
 *
 * **符号反転を見落とすとここでずれる。** それがこのテストの主目的。
 *
 * ============================================
 * なぜ「走査と同じ式で世界座標を作ってから引く」のか
 * ============================================
 *
 * 期待値を手で書くと、**間違った式を 2 回書いて一致させてしまう**。
 * そこでセルから世界座標を `getWorldOffset` で作り（走査がやっているのと同じ手順）、
 * それを `locate` に渡して元のセルに戻ることを見る。
 * **期待値が実装と独立**になるので、両方が同じ間違いをしていても落ちる。
 */
@DisplayName("パターンセルの復元")
public class StructureCellLocatorTest {

    /** コントローラが置かれている世界座標。原点から離してある — 0 だと引き算の誤りが消える。 */
    private static final int CX = 100;
    private static final int CY = 64;
    private static final int CZ = -50;

    /** 検証するセル。軸ごとに違う値にして、軸の入れ替わりを検出できるようにしてある。 */
    private static final List<int[]> CELLS = Arrays.asList(
        new int[] { 0, 0, 0 },
        new int[] { 1, 0, 0 },
        new int[] { 0, 1, 0 },
        new int[] { 0, 0, 1 },
        new int[] { 2, 1, 3 },
        new int[] { -2, 1, -3 });

    /** コントローラがパターンのどこに居るか。0 と非 0 の両方を見る。 */
    private static final List<int[]> CONTROLLER_OFFSETS = Arrays
        .asList(new int[] { 0, 0, 0 }, new int[] { 0, 0, 1 }, new int[] { 2, -1, 3 });

    /**
     * 走査と同じ手順でセルから世界座標を作る。
     * `iterateV2` の `getWorldOffset(abc, xyz)` + base と同じ形。
     */
    private static int[] worldPositionOf(ExtendedFacing facing, int[] cell, int[] controllerOffset) {
        int[] abc = { cell[0] - controllerOffset[0], cell[1] - controllerOffset[1], cell[2] - controllerOffset[2] };
        int[] xyz = new int[3];
        facing.getWorldOffset(abc, xyz);
        return new int[] { xyz[0] + CX, xyz[1] + CY, xyz[2] + CZ };
    }

    @Test
    @DisplayName("走査が置いた世界座標から元のセルに戻る")
    public void test世界座標からセルに戻る() {
        for (ExtendedFacing facing : ExtendedFacing.VALUES) {
            for (int[] controllerOffset : CONTROLLER_OFFSETS) {
                for (int[] cell : CELLS) {
                    int[] world = worldPositionOf(facing, cell, controllerOffset);

                    int[] located = StructureCellLocator
                        .locate(facing, CX, CY, CZ, controllerOffset, world[0], world[1], world[2]);

                    assertArrayEquals(
                        cell,
                        located,
                        () -> "セル " + Arrays.toString(cell)
                            + " が "
                            + facing.name()
                            + " / オフセット "
                            + Arrays.toString(controllerOffset)
                            + " で復元できない（得られた値 "
                            + Arrays.toString(located)
                            + "）");
                }
            }
        }
    }

    @Test
    @DisplayName("コントローラ自身はコントローラオフセットのセルになる")
    public void testコントローラ自身のセル() {
        for (ExtendedFacing facing : ExtendedFacing.VALUES) {
            for (int[] controllerOffset : CONTROLLER_OFFSETS) {
                int[] located = StructureCellLocator.locate(facing, CX, CY, CZ, controllerOffset, CX, CY, CZ);

                assertArrayEquals(
                    controllerOffset,
                    located,
                    () -> "コントローラ位置が " + facing.name()
                        + " でオフセット "
                        + Arrays.toString(controllerOffset)
                        + " にならない。符号反転の扱いを間違えている");
            }
        }
    }

    @Test
    @DisplayName("セルから世界座標を出せる（走査と同じ答えになる）")
    public void testセルから世界座標を出せる() {
        for (ExtendedFacing facing : ExtendedFacing.VALUES) {
            for (int[] controllerOffset : CONTROLLER_OFFSETS) {
                for (int[] cell : CELLS) {
                    int[] expected = worldPositionOf(facing, cell, controllerOffset);

                    int[] actual = StructureCellLocator
                        .toWorld(facing, CX, CY, CZ, controllerOffset, cell[0], cell[1], cell[2]);

                    assertArrayEquals(
                        expected,
                        actual,
                        () -> "セル " + Arrays.toString(cell)
                            + " の世界座標が "
                            + facing.name()
                            + " で走査と食い違う。構造 IO はここで求めた座標にブロックを置く");
                }
            }
        }
    }

    /**
     * 構造 IO は**構造の外**にもリージョンを置けるようにしたいので、
     * 形成判定が触っていないセルでも往復すること自体を見ておく。
     * 形成判定を通っていないセルは `locate` の入力に現れないため、
     * 往復で確かめる以外に縛る手段が無い。
     */
    @Test
    @DisplayName("セル → 世界 → セル で戻る")
    public void testセルから往復して戻る() {
        int[] farCell = { 9, -4, 7 };

        for (ExtendedFacing facing : ExtendedFacing.VALUES) {
            for (int[] controllerOffset : CONTROLLER_OFFSETS) {
                int[] world = StructureCellLocator
                    .toWorld(facing, CX, CY, CZ, controllerOffset, farCell[0], farCell[1], farCell[2]);
                int[] back = StructureCellLocator
                    .locate(facing, CX, CY, CZ, controllerOffset, world[0], world[1], world[2]);

                assertArrayEquals(farCell, back, () -> "構造の外のセルが " + facing.name() + " で往復しない。リージョンを構造の外に置けなくなる");
            }
        }
    }

    @Test
    @DisplayName("オフセットが null なら原点扱い")
    public void testオフセットが無いとき() {
        int[] located = StructureCellLocator.locate(ExtendedFacing.DEFAULT, CX, CY, CZ, null, CX, CY, CZ);

        assertArrayEquals(new int[] { 0, 0, 0 }, located, "オフセット未指定でコントローラが原点にならない");
    }

    @Test
    @DisplayName("渡した配列を書き換えない")
    public void test引数を壊さない() {
        int[] controllerOffset = { 2, -1, 3 };
        int[] copy = controllerOffset.clone();

        StructureCellLocator.locate(ExtendedFacing.DEFAULT, CX, CY, CZ, controllerOffset, CX + 1, CY, CZ);

        assertArrayEquals(copy, controllerOffset, "コントローラオフセットの配列が書き換えられている。呼び出し元は使い回している");
    }
}
