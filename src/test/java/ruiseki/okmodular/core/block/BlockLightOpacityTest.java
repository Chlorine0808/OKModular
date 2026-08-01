package ruiseki.okmodular.core.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * この mod のブロックが「光を遮る」と申告していることの検証。
 *
 * ============================================
 * 何が起きていたか
 * ============================================
 *
 * `Block(Material)` はコンストラクタの中でこう書いている:
 *
 * <pre>
 * this.opaque = this.isOpaqueCube();
 * this.lightOpacity = this.isOpaqueCube() ? 255 : 0;
 * </pre>
 *
 * `BlockOK.isOpaqueCube()` は `isOpaque` を返す。そして `isOpaque = true` は
 * **フィールド初期化子**なので、**`super()` が返るまで走らない**。
 * つまり上の 2 行が走る時点では `false` で、
 * **この mod の全ブロックが `lightOpacity 0` で固定されていた。**
 * `isOpaqueCube()` だけが後から `true` を返すので、2 つが食い違ったまま残る。
 *
 * ============================================
 * 何に効いていたか — 式だけの話ではない
 * ============================================
 *
 * chunk の heightMap は `Chunk.func_150808_b` → `Block.getLightOpacity()` で作られる。
 * 0 を返すブロックは**heightMap から見えない**。だから:
 *
 * - 機械の壁を天空光が素通りしていた（中が明るい）
 * - `World.canBlockSeeTheSky` が機械の中で true
 * - 雨雪の着地 / `isRaining(x,y,z)` / 湧き判定が機械を無視していた
 * - `can_see_sky()` が機械を透過していた
 *
 * ============================================
 * なぜ `isOpaque` から導かないのか
 * ============================================
 *
 * **`isOpaque` は描画の話。** コントローラとポートは半透明のオーバーレイパスを出すために
 * `isOpaque = false` にしているが、**プレイヤーから見れば中身の詰まったブロック**。
 * 描画の都合が世界の明るさを決めてはいけないので、`BlockOK` は無条件に
 * 「光を遮る」側で申告し、本当に透けるブロックだけが自分の
 * コンストラクタで `setLightOpacity(0)` を呼ぶ（`super()` の後なので勝つ）。
 *
 * ============================================
 * なぜソース走査なのか
 * ============================================
 *
 * `Block` を組むには MC の静的初期化が要るのでユニットテストからは触れない。
 * 代わりに **①言語規則そのもの**（下の素の Java での再現）と
 * **②申告している場所**を縛る。
 *
 * ============================================
 */
@DisplayName("ブロックの光の透過")
public class BlockLightOpacityTest {

    private static final Path BLOCK_OK = Paths.get("src/main/java/ruiseki/okmodular/core/block/BlockOK.java");

    private static final Path VISION = Paths
        .get("src/main/java/ruiseki/okmodular/api/recipe/expression/VisionFunctionExpression.java");

    // ========== 言語規則の再現 ==========

    /** `Block(Material)` と同じことをする。仮想呼び出しでフィールドを読む。 */
    private abstract static class SuperclassReadingAVirtual {

        final int frozen;

        SuperclassReadingAVirtual() {
            frozen = solid() ? 255 : 0;
        }

        abstract boolean solid();
    }

    /** `BlockOK` と同じことをする。フィールド初期化子で true を入れる。 */
    private static final class SubclassInitialisingAField extends SuperclassReadingAVirtual {

        private boolean isSolid = true;

        @Override
        boolean solid() {
            return isSolid;
        }
    }

    @Test
    @DisplayName("フィールド初期化子は super() の後に走る — これが原因")
    public void testフィールド初期化子は後に走る() {
        SubclassInitialisingAField block = new SubclassInitialisingAField();

        assertEquals(0, block.frozen, "super() の中では初期化子がまだ走っていないので false と読まれる");
        assertTrue(block.solid(), "初期化子が走った後は true。ここが食い違いになる");
    }

    // ========== 申告している場所 ==========

    @Test
    @DisplayName("BlockOK が光の遮蔽を自分で申告している")
    public void testBlockOKが申告している() {
        String source = read(BLOCK_OK);

        assertTrue(
            source.contains("setLightOpacity(SOLID)"),
            "BlockOK が setLightOpacity を呼んでいない。" + "Block のコンストラクタ任せだと全ブロックが 0 になる");
        assertTrue(source.contains("SOLID = 255"), "遮蔽値が 255 でない");
    }

    @Test
    @DisplayName("光の遮蔽を isOpaque から導いていない")
    public void testisOpaqueから導いていない() {
        String source = read(BLOCK_OK);

        // isOpaque は描画の都合（コントローラとポートが false にする）。
        // ここから導くと、オーバーレイを出すブロックが光を通してしまう。
        assertFalse(source.contains("setLightOpacity(isOpaque"), "isOpaque から導いている。描画の都合が世界の明るさを決めてしまう");
    }

    @Test
    @DisplayName("視界判定は不透過キューブかどうかではなく光の遮蔽で見る")
    public void test視界判定が光の遮蔽で見る() {
        String source = read(VISION);

        // isOpaqueCube() で見ると、ハーフブロックや階段、そしてこの mod の
        // コントローラ・ポートのように「描画だけ特殊な中身の詰まったブロック」を透過してしまう。
        assertFalse(source.contains("!block.isOpaqueCube() ||"), "isOpaqueCube() を透過条件にしている。ポートとコントローラが素通りする");
        assertTrue(source.contains("return block.getLightOpacity() == 0;"), "透過条件が光の遮蔽になっていない");
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file.toAbsolutePath(), e);
        }
    }
}
