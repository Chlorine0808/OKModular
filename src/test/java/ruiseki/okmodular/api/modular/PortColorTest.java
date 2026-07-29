package ruiseki.okmodular.api.modular;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import ruiseki.okcore.enums.EnumDye;

/**
 * ポートの色の検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * 色の番号づけが**外の 3 つの規約と一致していないと静かに壊れる**。
 * 色がずれても例外は出ず、「塗ったのに別の色群に入る」という形で現れるだけなので、
 * 番号の対応はテストで固定するしかない。
 *
 * <pre>
 * バニラの羊毛メタデータ 0=白 … 15=黒
 * AE2 の AEColor.ordinal() 0=White … 15=Black, 16=Transparent
 * Forge の Block.recolourBlock の colour 引数 上と同じ番号で渡ってくる
 * </pre>
 *
 * この 3 つは同じ並びなので、**`PortColor` の宣言順をそれに合わせる**。
 * AE2 の Color Applicator は `IColorableTile` が無ければ
 * `blk.recolourBlock(w, x, y, z, side, newColor.ordinal())` に落ちる
 * （`ToolColorApplicator:364`）ので、この一致がスプレー互換の全体。
 *
 * ============================================
 * OKCore の EnumDye は逆順である
 * ============================================
 *
 * `ruiseki.okcore.enums.EnumDye` は**染料のダメージ値順**（BLACK=0 … WHITE=15）。
 * 上の 3 つとちょうど逆で、`15 - ordinal` の関係になる。
 * OKCore は改変しない（SSOT D-2）ので、変換はこちら側が持つ。
 *
 * **ここを間違えると色が上下反転する。** 白を塗ったら黒群に入る、という壊れ方をする。
 *
 * ============================================
 * NONE は AEColor.Transparent と同じ番号にしてある
 * ============================================
 *
 * 「塗っていない」を 16 番に置くと AE2 の Transparent と一致するので、
 * 色消し操作もそのまま通る。副産物として `ordinal()` が 17 個すべてで
 * AEColor と揃う。
 *
 * ============================================
 */
@DisplayName("ポートの色")
public class PortColorTest {

    /**
     * 宣言順の凍結。
     *
     * 手書きのリテラルであること自体が仕様。`values()` から組み立てると両辺が
     * 一緒に動くので、並べ替えを検出できない。**そして並びが番号そのもの**なので、
     * 並べ替えは既存セーブとスプレー互換の両方を壊す。
     */
    @Test
    @DisplayName("宣言順が羊毛メタ・AEColor と一致している")
    public void test宣言順が凍結されている() {
        List<String> frozen = Arrays.asList(
            "WHITE",
            "ORANGE",
            "MAGENTA",
            "LIGHT_BLUE",
            "YELLOW",
            "LIME",
            "PINK",
            "GRAY",
            "LIGHT_GRAY",
            "CYAN",
            "PURPLE",
            "BLUE",
            "BROWN",
            "GREEN",
            "RED",
            "BLACK",
            "NONE");

        List<String> actual = new ArrayList<>();
        for (PortColor color : PortColor.values()) {
            actual.add(color.name());
        }

        assertEquals(
            frozen,
            actual,
            "宣言順が変わった。この並びはバニラの羊毛メタ・AEColor.ordinal()・recolourBlock の引数と"
                + "同じ番号でなければならず、かつ名前がセーブに書かれる。並べ替えも改名も破壊的変更。");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PortColor.class)
    @DisplayName("色番号は ordinal と一致する")
    public void test色番号はOrdinalと一致する(PortColor color) {
        assertEquals(color.ordinal(), color.toColorIndex(), () -> color + " の色番号が ordinal とずれている");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PortColor.class)
    @DisplayName("色番号から往復できる")
    public void test色番号から往復できる(PortColor color) {
        assertSame(color, PortColor.fromColorIndex(color.toColorIndex()));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({ "0, WHITE", "1, ORANGE", "2, MAGENTA", "3, LIGHT_BLUE", "4, YELLOW", "5, LIME", "6, PINK", "7, GRAY",
        "8, LIGHT_GRAY", "9, CYAN", "10, PURPLE", "11, BLUE", "12, BROWN", "13, GREEN", "14, RED", "15, BLACK",
        "16, NONE" })
    @DisplayName("羊毛メタ / AEColor の番号が正しい色になる")
    public void test色番号の対応(int index, String expected) {
        assertSame(
            PortColor.valueOf(expected),
            PortColor.fromColorIndex(index),
            () -> "番号 " + index + " は " + expected + " でなければならない");
    }

    /**
     * AE2 の Transparent が「塗っていない」になること。
     *
     * Color Applicator を右クリックで塗り、シフトで色を消すのが AE2 の操作。
     * 消す側が届かないと、塗ったら二度と戻せないポートになる。
     */
    @Test
    @DisplayName("AEColor.Transparent(16) は NONE になる")
    public void testTransparentはNoneになる() {
        assertSame(PortColor.NONE, PortColor.fromColorIndex(16));
    }

    @ParameterizedTest(name = "index={0}")
    @ValueSource(ints = { -1, -100, 17, 100, Integer.MAX_VALUE, Integer.MIN_VALUE })
    @DisplayName("範囲外の色番号は NONE になる")
    public void test範囲外の色番号はNone(int index) {
        assertSame(PortColor.NONE, PortColor.fromColorIndex(index), () -> index + " は NONE に倒れるべき");
    }

    @Test
    @DisplayName("NONE 以外はすべて色つき")
    public void testIsColored() {
        for (PortColor color : PortColor.values()) {
            if (color == PortColor.NONE) {
                assertFalse(color.isColored(), "NONE は色つきではない");
            } else {
                assertTrue(color.isColored(), () -> color + " は色つきであるべき");
            }
        }
    }

    // ========== EnumDye との対応（逆順） ==========

    /**
     * EnumDye は染料ダメージ値順なので、こちらとは足して 15 になる。
     *
     * この不変式が崩れると色が上下反転する。白を塗ったら黒として扱われる。
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PortColor.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("EnumDye の ordinal と足して 15 になる")
    public void testEnumDyeとは逆順(PortColor color) {
        EnumDye dye = color.toDye();

        assertEquals(
            15,
            color.ordinal() + dye.ordinal(),
            () -> color + " (" + color.ordinal() + ") と " + dye + " (" + dye.ordinal() + ") の対応がずれている");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PortColor.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("EnumDye と往復できる")
    public void testEnumDyeと往復できる(PortColor color) {
        assertSame(color, PortColor.fromDye(color.toDye()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(EnumDye.class)
    @DisplayName("すべての EnumDye に対応する色がある")
    public void test全EnumDyeに対応がある(EnumDye dye) {
        PortColor color = PortColor.fromDye(dye);

        assertTrue(color.isColored(), () -> dye + " に対応する色が無い");
        assertSame(dye, color.toDye());
    }

    @Test
    @DisplayName("NONE に対応する染料は無い")
    public void testNoneに染料は無い() {
        assertNull(PortColor.NONE.toDye(), "塗っていない状態に対応する染料は存在しない");
        assertSame(PortColor.NONE, PortColor.fromDye(null));
    }

    // ========== 描画用の色 ==========

    /**
     * RGB の凍結。
     *
     * バニラの `ItemDye` の色表と同じ値。ここは打ち間違えても何も落ちず、
     * 「微妙に色が違う」だけになるので、リテラルで固定する。
     */
    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({ "WHITE, F0F0F0", "ORANGE, EB8844", "MAGENTA, C354CD", "LIGHT_BLUE, 6689D3", "YELLOW, DECF2A",
        "LIME, 41CD34", "PINK, D88198", "GRAY, 434343", "LIGHT_GRAY, ABABAB", "CYAN, 287697", "PURPLE, 7B2FBE",
        "BLUE, 253192", "BROWN, 51301A", "GREEN, 3B511A", "RED, B3312C", "BLACK, 1E1B1B" })
    @DisplayName("RGB がバニラの染料色と一致する")
    public void testRgbの凍結(String name, String hex) {
        assertEquals(
            Integer.parseInt(hex, 16),
            PortColor.valueOf(name)
                .getRgb(),
            () -> name + " の RGB が 0x" + hex + " ではない");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PortColor.class)
    @DisplayName("RGB にアルファは含まれない")
    public void testRgbにアルファが無い(PortColor color) {
        assertEquals(
            0,
            color.getRgb() & 0xFF000000,
            () -> color + " の RGB に上位バイトが立っている。colorMultiplier は 0xRRGGBB を期待する");
    }

    @Test
    @DisplayName("NONE には色が無いので白を返す")
    public void testNoneのRgb() {
        assertEquals(0xFFFFFF, PortColor.NONE.getRgb(), "塗っていないポートは色を掛けない = 白");
    }

    /**
     * 塗った色が構造の色より優先されること。
     *
     * 塗る目的は**群が一目で見分けられること**なので、機械の配色が上に残ると意味がない。
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PortColor.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("塗った色は構造の色に勝つ")
    public void testTintOrは塗った色を優先する(PortColor color) {
        assertEquals(color.getRgb(), color.tintOr(0x123456), () -> color + " が構造の色に負けている");
    }

    @Test
    @DisplayName("塗っていなければ渡された色をそのまま返す")
    public void testTintOrは無色なら素通し() {
        assertEquals(0x123456, PortColor.NONE.tintOr(0x123456), "塗っていないポートの見た目は変わらないべき");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PortColor.class)
    @DisplayName("lang キーが名前から導かれる")
    public void testLangキー(PortColor color) {
        assertEquals(
            "gui.port_color." + color.name(),
            color.getUnlocalizedName(),
            () -> color + " の lang キーが規則から外れている");
    }
}
