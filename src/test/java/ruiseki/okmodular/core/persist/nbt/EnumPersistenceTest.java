package ruiseki.okmodular.core.persist.nbt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * `@NBTPersist` が enum フィールドをどう扱うかの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * enum フィールドは既に 4 つ永続化されている（`redstoneMode` / `forward` / `up` /
 * これから足すポートの色）。ハンドラは `name()` で書くので**要素の並べ替えに強い**が、
 * **読みが `Enum.valueOf` だった**ので未知の名前で `IllegalArgumentException` を投げていた。
 *
 * TileEntity の読み込み中に例外が飛ぶと、**チャンクごと失われる**。
 * 名前が合わないのは異常ではなく、ビルドを跨いだセーブでは普通に起きる:
 *
 * - 定数を改名した（このリポジトリは禁止しているが、依存 mod は保証しない）
 * - 新しい定数を持つビルドで保存し、古いビルドで開いた
 * - セーブを手で編集した
 *
 * **読めない値はフィールドを触らない** = 初期値のまま、という扱いにする。
 * 設定 1 つが既定に戻るのは、チャンクが消えるより軽い。
 *
 * 「初期値のまま」であることが重要で、`getDefaultValue()`（= 最初の定数）に
 * 倒すのでは駄目。ポートの色は宣言順が羊毛メタと一致していて最初の定数が `WHITE` なので、
 * 塗っていないポートが白くなってしまう。
 *
 * ============================================
 */
@DisplayName("enum フィールドの永続化")
public class EnumPersistenceTest {

    private enum Mood {
        CALM,
        BUSY,
        ANGRY
    }

    /** `@NBTPersist` を持つだけの最小の器。TileEntity は要らない。 */
    private static final class Holder implements INBTProvider {

        @NBTPersist
        Mood mood = Mood.BUSY;

        @NBTPersist
        Mood absent = null;

        private final INBTProvider provider = new NBTProviderComponent(this);

        @Override
        public void writeGeneratedFieldsToNBT(NBTTagCompound tag) {
            provider.writeGeneratedFieldsToNBT(tag);
        }

        @Override
        public void readGeneratedFieldsFromNBT(NBTTagCompound tag) {
            provider.readGeneratedFieldsFromNBT(tag);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Mood.class)
    @DisplayName("enum は名前で往復する")
    public void test名前で往復する(Mood value) {
        Holder source = new Holder();
        source.mood = value;

        NBTTagCompound tag = new NBTTagCompound();
        source.writeGeneratedFieldsToNBT(tag);

        assertEquals(value.name(), tag.getString("mood"), "ordinal ではなく名前で書かれるべき");

        Holder target = new Holder();
        target.readGeneratedFieldsFromNBT(tag);

        assertSame(value, target.mood);
    }

    @Test
    @DisplayName("null のフィールドは書かれない")
    public void testNullは書かれない() {
        NBTTagCompound tag = new NBTTagCompound();
        new Holder().writeGeneratedFieldsToNBT(tag);

        assertFalse(tag.hasKey("absent"), "null を書こうとすると name() で NPE になる。書かないのが正しい");
    }

    @Test
    @DisplayName("キーが無ければ初期値が残る")
    public void testキーが無ければ初期値() {
        Holder target = new Holder();
        target.mood = Mood.ANGRY;

        target.readGeneratedFieldsFromNBT(new NBTTagCompound());

        assertSame(Mood.ANGRY, target.mood, "キーが無いのは「そのフィールドを持たない古いセーブ」。触ってはいけない");
        assertNull(target.absent);
    }

    /**
     * 読めない名前でフィールドを触らないこと。
     *
     * 改修前は `Enum.valueOf` がそのまま投げていた。TileEntity の読み込み中の例外は
     * チャンクを落とすので、**設定 1 つを既定に戻す方に倒す**。
     */
    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = { "FURIOUS", "calm", "", " CALM", "CALM " })
    @DisplayName("読めない名前は初期値のまま（throw しない）")
    public void test読めない名前は初期値のまま(String stored) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("mood", stored);

        Holder target = new Holder();
        target.mood = Mood.ANGRY;

        assertDoesNotThrow(() -> target.readGeneratedFieldsFromNBT(tag), () -> "'" + stored + "' で例外が飛ぶとチャンクごと失われる");
        assertSame(Mood.ANGRY, target.mood, () -> "'" + stored + "' は無視され、フィールドは元の値のままであるべき");
    }

    /**
     * 型が違うキーでも落ちないこと。
     *
     * 1.7.10 の `getString` は**型が合わなくても空文字列を返さない**。タグの
     * 文字列表現をそのまま返すので、int の 2 が入っていれば `"2"` が返る。
     * どの定数にも一致しないので上と同じ経路に落ちる、という形で守られている。
     */
    @Test
    @DisplayName("数値が書かれていても初期値のまま")
    public void test型違いでも初期値のまま() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("mood", 2);

        Holder target = new Holder();
        target.mood = Mood.CALM;

        assertDoesNotThrow(() -> target.readGeneratedFieldsFromNBT(tag));
        assertSame(Mood.CALM, target.mood, "ordinal のように見える数値を拾ってはいけない。名前が唯一の表現");
    }
}
