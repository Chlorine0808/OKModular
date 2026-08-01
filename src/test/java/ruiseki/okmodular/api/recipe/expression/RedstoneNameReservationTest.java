package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * `redstone` という式名を空けたことを縛る（release_freeze F-2）。
 *
 * ============================================
 * なぜ改名したのか
 * ============================================
 *
 * `IPortType.Type` に定数を足すと、**同じ綴りの式名が自動生成される**（B9 の設計）。
 * レッドストーン IO をロードマップに持っている以上 `Type.REDSTONE` はいずれ入り、
 * そのとき `redstone` という名前が 2 つの意味で登録される。
 *
 * **衝突は例外を出さない。** 登録順で片方が勝ち、負けたほうは黙って別の値を返す。
 * レシピ JSON 側からは「なぜか値が違う」としてしか見えない。
 *
 * よって世界プロパティの側を `redstone_signal` に退避し、`redstone` を空けた。
 * **alias は残していない** — 残すと衝突がそのまま残るので、改名した意味が消える。
 *
 * `redstone` は docs 公開済みなので、これは公開契約を破る変更。SSOT D-7 の
 * 「後方互換を切るのはこのリリース 1 回だけ」の枠内で、**タグ前だから無料**という理由で今やっている。
 *
 * ============================================
 * なぜ値ではなくソースを見るのか
 * ============================================
 *
 * `WorldPropertyExpression` は `world == null` で `ZERO` を返して打ち切る。
 * 評価まで届かせるには `World` が要り、`MockWorld` はコンストラクタで NPE を出す
 * （`VisionVariableDelegationTest` / `MachineConditionGateTest` に同じ事情がある）。
 *
 * よって「値が正しいか」ではなく **「答えを出している場所が改名されているか」** を見る。
 * 登録名だけ直して評価側の `case` を直し忘れると、`redstone_signal` は
 * **パースは通るのに既定の 0 を返し続ける** — その形の欠落がこの mod で繰り返し出ているので、
 * 両側が揃っていることを静的に確かめる。
 *
 * ============================================
 * このテストが落ちたら
 * ============================================
 *
 * - `redstone` を再登録した → F-2 の決定に反する
 * - `Type.REDSTONE` を足した → **このテストを消して、
 * 「`redstone` はポート由来の値である」ことを見るテストに置き換える**
 */
@DisplayName("redstone という式名の予約")
public class RedstoneNameReservationTest {

    private static final Path REGISTRY = Paths
        .get("src/main/java/ruiseki/okmodular/api/recipe/expression/ExpressionRegistry.java");

    private static final Path WORLD_PROPERTY = Paths
        .get("src/main/java/ruiseki/okmodular/api/recipe/expression/WorldPropertyExpression.java");

    @Test
    @DisplayName("redstone_signal が変数として書ける")
    public void test新しい名前が書ける() {
        assertNotNull(ExpressionParser.parseExpression("redstone_signal"), "redstone_signal が変数として登録されていない");
    }

    @Test
    @DisplayName("redstone は未登録のまま — Type.REDSTONE のために空けてある")
    public void test古い名前が空いている() {
        assertThrows(
            Exception.class,
            () -> ExpressionParser.parseExpression("redstone"),
            "redstone が変数として登録されている。Type.REDSTONE を足した日に" + "同名の式が自動生成されて衝突し、登録順で片方が黙って別の値を返す。"
                + "世界プロパティ側は redstone_signal を使うこと");
    }

    @Test
    @DisplayName("登録も評価も新しい名前に揃っている")
    public void test登録と評価が揃っている() {
        assertTrue(
            read(REGISTRY).contains("registerWorldProperty(\"redstone_signal\")"),
            "ExpressionRegistry が redstone_signal を登録していない");
        assertFalse(
            read(REGISTRY).contains("registerWorldProperty(\"redstone\")"),
            "ExpressionRegistry が redstone をまだ登録している。alias として残すのも不可 —" + "衝突が残るので改名した意味が消える");

        assertTrue(
            read(WORLD_PROPERTY).contains("case \"redstone_signal\":"),
            "評価側の case が改名されていない。登録だけ直すと redstone_signal は" + "パースは通るのに既定の 0 を返し続ける");
        assertFalse(read(WORLD_PROPERTY).contains("case \"redstone\":"), "評価側に redstone の case が残っている");
    }

    @Test
    @DisplayName("信号強度の出どころは IRecipeContext のまま")
    public void test信号強度の出どころ() {
        assertTrue(
            read(WORLD_PROPERTY).contains("getRedstoneLevel()"),
            "改名の際に値の出どころごと落としている。redstone_signal は" + "コントローラが受けている信号強度でなければならない");
    }

    private static String read(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + file.toAbsolutePath(), e);
        }
    }
}
