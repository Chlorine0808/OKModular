package ruiseki.okmodular.api.recipe.io;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 構造 JSON / レシピ JSON に書くブロック指定の照合規則の検証。
 *
 * ============================================
 * なぜ切り出したか
 * ============================================
 *
 * この照合は `BlockInput` と `BlockOutput` に**それぞれ private で写経されていた**。
 * しかも既に食い違っていて、`BlockInput` 側にだけ null ガードがある。
 * 構造 IO が 3 つ目の写しを作れば、次に規則を直す人は 1 箇所だけ直して直った気になる
 * （`can_see_sky` が 2 実装を持っていたのと同じ形）。
 *
 * 純粋な文字列処理なので、ゲーム外で値のまま縛れる。
 */
@DisplayName("ブロック ID の照合")
public class BlockIdMatcherTest {

    private static final String STONE = "minecraft:stone:3";

    @Test
    @DisplayName("* はすべてに一致する")
    public void testワイルドカード() {
        assertTrue(BlockIdMatcher.matches(STONE, "*"));
    }

    @Test
    @DisplayName("mod:name はメタを問わない")
    public void test二要素はメタ無視() {
        assertTrue(BlockIdMatcher.matches(STONE, "minecraft:stone"), "メタ違いを弾いてしまっている");
        assertFalse(BlockIdMatcher.matches(STONE, "minecraft:dirt"));
        assertFalse(BlockIdMatcher.matches(STONE, "othermod:stone"));
    }

    @Test
    @DisplayName("mod:name:meta はメタまで見る")
    public void test三要素はメタも見る() {
        assertTrue(BlockIdMatcher.matches(STONE, "minecraft:stone:3"));
        assertFalse(BlockIdMatcher.matches(STONE, "minecraft:stone:1"));
    }

    @Test
    @DisplayName("mod:name:* はメタを問わない")
    public void testメタのワイルドカード() {
        assertTrue(BlockIdMatcher.matches(STONE, "minecraft:stone:*"));
        assertFalse(BlockIdMatcher.matches(STONE, "minecraft:dirt:*"));
    }

    /**
     * `BlockInput` 側にだけあったガード。**写経の 2 つが既に食い違っていた**証拠なので、
     * 統合するときに厳しいほう（落ちないほう）へ揃える。
     */
    @Test
    @DisplayName("null は一致しない（落ちない）")
    public void testnullは不一致() {
        assertFalse(BlockIdMatcher.matches(STONE, null));
        assertFalse(BlockIdMatcher.matches(null, "minecraft:stone"));
    }

    @Test
    @DisplayName("メタの無い ID はメタ指定に一致しない")
    public void test要素数が足りないとき() {
        assertFalse(
            BlockIdMatcher.matches("minecraft:stone", "minecraft:stone:0"),
            "メタを持たない ID にメタ 0 を当てて通すと、`meta: 0` の指定が形骸化する");
    }
}
