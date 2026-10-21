package ruiseki.okmodular.common.integration.structurelib;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

/**
 * 構造ローカル座標（ABC）と世界座標の往復が厳密であることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * 構造 IO は「この世界座標はパターンのどのセルか」を記録する設計に立っている。
 * その復元は `ExtendedFacing.getOffsetABC` に頼っており、**これが走査側の変換の
 * 厳密な逆でなければ、記録したセル番号が静かにずれる**。
 *
 * 走査側（`StructureUtility.iterateV2`）はローカル → 世界に `getWorldOffset` を使う。
 * こちらは世界 → ローカルに `getOffsetABC` を使う。中身は `IntegerAxisSwap` の
 * `inverseTranslate` と `translate` で、**互いの転置**。行列は各軸が ±1 で入れ替わる
 * だけの符号付き置換行列（直交行列）なので転置 = 逆行列になり、しかも整数演算なので
 * 誤差も出ない。**理屈の上では厳密に一致する。**
 *
 * ============================================
 * 理屈があるのに、なぜテストするのか
 * ============================================
 *
 * **StructureLib 自身が往復を検証していない。** 向こうの `ExtendedFacingTest` にあるのは
 * `getWorldDirection` の 2 本だけで、座標変換の往復は誰も見ていない。
 *
 * つまりこれは**他 mod の内部実装に対する仮定**であり、向こうの更新で軸の取り方が
 * 変わっても、こちらには例外もログも出ない。**構造 IO が全部ずれてから実機で気づく**形になる。
 * 依存の前提はこちら側で縛る。
 *
 * ============================================
 * このテストが落ちたら
 * ============================================
 *
 * StructureLib の更新で座標変換の意味が変わったということ。**構造 IO の記録した
 * セル番号は全部信用できない**ので、変換の対応を取り直すまで先に進まないこと。
 */
@DisplayName("ExtendedFacing の座標往復")
public class ExtendedFacingRoundTripTest {

    /**
     * 往復に使うオフセット。
     *
     * **軸ごとに違う値**にしてある — (1,1,1) のような対称な点は軸が入れ替わっても
     * 同じ値になるので、**入れ替えの誤りを検出できない**。符号も混ぜてあるのは
     * 反転（`Flip`）の取り違えを見るため。
     */
    private static final List<int[]> OFFSETS = Arrays.asList(
        new int[] { 0, 0, 0 },
        new int[] { 1, 0, 0 },
        new int[] { 0, 1, 0 },
        new int[] { 0, 0, 1 },
        new int[] { 1, 2, 3 },
        new int[] { -1, 2, -3 },
        new int[] { 5, -7, 11 });

    @Test
    @DisplayName("ローカル → 世界 → ローカル で元の値に戻る")
    public void testローカルから往復して戻る() {
        for (ExtendedFacing facing : ExtendedFacing.VALUES) {
            for (int[] abc : OFFSETS) {
                int[] xyz = new int[3];
                int[] back = new int[3];

                facing.getWorldOffset(abc, xyz);
                facing.getOffsetABC(xyz, back);

                assertArrayEquals(
                    abc,
                    back,
                    () -> "ABC " + Arrays.toString(abc)
                        + " が "
                        + facing.name()
                        + " で往復しない（戻り値 "
                        + Arrays.toString(back)
                        + "）。構造 IO の記録するセル番号がずれる");
            }
        }
    }

    @Test
    @DisplayName("世界 → ローカル → 世界 で元の値に戻る")
    public void test世界から往復して戻る() {
        for (ExtendedFacing facing : ExtendedFacing.VALUES) {
            for (int[] xyz : OFFSETS) {
                int[] abc = new int[3];
                int[] back = new int[3];

                facing.getOffsetABC(xyz, abc);
                facing.getWorldOffset(abc, back);

                assertArrayEquals(xyz, back, () -> "XYZ " + Arrays.toString(xyz) + " が " + facing.name() + " で往復しない");
            }
        }
    }

    /**
     * 走査が実在することの自己検査。
     *
     * 変換が全方向で恒等写像だったら往復テストは**何も見ていないのに緑になる**。
     * 「回転がある」ことを別に確かめておく。
     */
    @Test
    @DisplayName("恒等写像ではない向きが存在する（テストが空回りしていないこと）")
    public void test回転が実際に効いている() {
        int[] probe = { 1, 2, 3 };
        boolean sawRotation = false;

        for (ExtendedFacing facing : ExtendedFacing.VALUES) {
            int[] xyz = new int[3];
            facing.getWorldOffset(probe, xyz);
            if (!Arrays.equals(probe, xyz)) {
                sawRotation = true;
                break;
            }
        }

        assertTrue(sawRotation, "どの向きでも座標が変わらない。ExtendedFacing が読めていないか、走査が空になっている");
    }

    @Test
    @DisplayName("向きの一覧が空でない")
    public void test向きが列挙できている() {
        assertTrue(
            ExtendedFacing.VALUES.length >= 24,
            "ExtendedFacing.VALUES が " + ExtendedFacing.VALUES.length + " 件しかない。列挙の取り方が変わった可能性がある");
    }
}
