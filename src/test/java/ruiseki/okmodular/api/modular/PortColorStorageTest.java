package ruiseki.okmodular.api.modular;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ruiseki.okmodular.core.persist.nbt.NBTPersist;

/**
 * ポートが色を持てることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * 色は**ポート TE 側**に置く。コントローラ側の位置キー Map に相乗りする案は成立しない。
 * ポート TE は自分のコントローラを知らない（`getController()` は
 * `AbstractExternalProxy` にしかない）ので、**塗られた瞬間に書き込む先を引けない**。
 *
 * 置き場は `AbstractTE` のフィールド 1 本。ポート族は 7 つあるが、
 * `assignedIndex` と同じくフィールドは 1 箇所で足りる。
 * ここが崩れると「ある種類のポートだけ色が効かない」という形で現れ、例外は出ない。
 *
 * ============================================
 * TileEntity を組めないので反射で確かめている
 * ============================================
 *
 * `AbstractTE` の実体化は `OKEventFactory.gatherCapabilities` を通るので
 * ユニットテストでは組めない。そこで:
 *
 * - **永続化されること**は 2 つに分けて示す。フレームワークが `@NBTPersist` の enum を
 * 正しく扱うことは `EnumPersistenceTest` が実証し、**その注釈が実際に付いていること**を
 * ここで確かめる
 * - **どのポート族も色を持つこと**は、`getPortColor` の宣言クラスが
 * `IModularPort`（既定値を返すだけ）**ではない**ことで確かめる
 *
 * ============================================
 */
@DisplayName("ポートの色の格納")
public class PortColorStorageTest {

    private static final String ABSTRACT_TE = "ruiseki.okmodular.core.tileentity.AbstractTE";

    /**
     * 色を持つべきポート族。手書きのリテラルであること自体が仕様 —
     * 実行時に走査すると、**新しい族を足して配線を忘れたときに一覧からも消える**ので
     * 検出できない。
     */
    // spotless:off
    private static final String[] PORT_FAMILIES = {
        "ruiseki.okmodular.common.tile.item.AbstractItemIOPortTE",
        "ruiseki.okmodular.common.tile.fluid.AbstractFluidPortTE",
        "ruiseki.okmodular.common.tile.energy.AbstractEnergyIOPortTE",
        "ruiseki.okmodular.common.tile.gas.AbstractGasPortTE",
        "ruiseki.okmodular.common.tile.essentia.AbstractEssentiaPortTE",
        "ruiseki.okmodular.common.tile.vis.AbstractVisPortTE",
        "ruiseki.okmodular.common.tile.mana.AbstractManaPortTE",
    };
    // spotless:on

    private static Stream<String> ポート族() {
        return Stream.of(PORT_FAMILIES);
    }

    // ========== インタフェースの既定値 ==========

    /** 色を持たない実装。`IModularPort` の既定値だけを使う。 */
    private static final class ColorlessPort implements IModularPort {

        @Override
        public int getTier() {
            return 0;
        }

        @Override
        public void setTier(int tier) {}

        @Override
        public Type getPortType() {
            return Type.NONE;
        }

        @Override
        public Direction getPortDirection() {
            return Direction.NONE;
        }
    }

    @Test
    @DisplayName("色を実装しないポートは NONE を返す")
    public void test既定値はNone() {
        assertSame(PortColor.NONE, new ColorlessPort().getPortColor(), "色を持たない実装は「塗っていない」と答えるべき。null は返さない");
    }

    @Test
    @DisplayName("色を実装しないポートへの設定は無視される")
    public void test既定のSetterは無視する() {
        ColorlessPort port = new ColorlessPort();
        port.setPortColor(PortColor.RED);

        assertSame(PortColor.NONE, port.getPortColor(), "格納先が無い実装は設定を捨てる。例外にはしない");
    }

    // ========== 格納先 ==========

    @Test
    @DisplayName("AbstractTE が PortColor 型のフィールドを持つ")
    public void test色のフィールドがある() throws Exception {
        Field field = findPortColorField(load(ABSTRACT_TE));

        assertSame(PortColor.class, field.getType());
    }

    @Test
    @DisplayName("色のフィールドが @NBTPersist を持つ")
    public void test色が永続化される() throws Exception {
        Field field = findPortColorField(load(ABSTRACT_TE));

        assertTrue(
            field.isAnnotationPresent(NBTPersist.class),
            "@NBTPersist が無いと色はセーブにもクライアントにも届かない。" + "description packet が NBT 全体を送る仕組みに乗っているのがこの注釈");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ポート族")
    @DisplayName("どのポート族も色の格納先を持つ")
    public void test全ポート族が色を持つ(String className) throws Exception {
        Method getter = load(className).getMethod("getPortColor");

        assertNotEquals(
            IModularPort.class,
            getter.getDeclaringClass(),
            () -> className + " は getPortColor がインタフェースの既定値のままなので、塗っても捨てられる");
    }

    @Test
    @DisplayName("ポート族の一覧が 7 件のまま")
    public void test一覧の件数() {
        assertEquals(7, PORT_FAMILIES.length, "ポート族が増えたなら、この一覧に足すこと。足さないと色の配線漏れを検出できない");
    }

    // ========== 補助 ==========

    /** static 初期化を走らせずにクラスを取る。ポート TE の static 初期化は重い。 */
    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, false, PortColorStorageTest.class.getClassLoader());
    }

    private static Field findPortColorField(Class<?> clazz) throws NoSuchFieldException {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getType() == PortColor.class) return field;
        }
        throw new NoSuchFieldException(clazz.getName() + " に PortColor 型のフィールドが無い");
    }
}
