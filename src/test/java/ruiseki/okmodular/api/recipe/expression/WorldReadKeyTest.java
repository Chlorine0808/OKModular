package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The keys world-reading functions file their results under.
 *
 * A machine's cache is shared by every expression it evaluates, so two calls that would read
 * different things must never share a key: count_blocks(1) answering for count_blocks(8) is a
 * wrong value, not a stale one. A World cannot be built in a test, so the keys are pinned
 * here and the wiring is checked in the source, as VisionVariableDelegationTest does.
 */
@DisplayName("ワールドを読む関数のキャッシュキー")
public class WorldReadKeyTest {

    @Test
    @DisplayName("count_blocks: 同じ引数なら同じキー")
    public void testCountBlocks同じ引数() {
        assertEquals(
            CountBlocksFunctionExpression.cacheKey(4, Arrays.asList("minecraft:stone")),
            CountBlocksFunctionExpression.cacheKey(4, Arrays.asList("minecraft:stone")));
    }

    @Test
    @DisplayName("count_blocks: 距離が違えば別のキー")
    public void testCountBlocks距離() {
        assertNotEquals(
            CountBlocksFunctionExpression.cacheKey(1, null),
            CountBlocksFunctionExpression.cacheKey(8, null));
    }

    @Test
    @DisplayName("count_blocks: フィルタが違えば別のキー")
    public void testCountBlocksフィルタ() {
        assertNotEquals(
            CountBlocksFunctionExpression.cacheKey(4, null),
            CountBlocksFunctionExpression.cacheKey(4, Arrays.asList("minecraft:stone")));
        // No filter counts every non-air block; an empty one counts none.
        assertNotEquals(
            CountBlocksFunctionExpression.cacheKey(4, null),
            CountBlocksFunctionExpression.cacheKey(4, Collections.emptyList()));
        // Joined naively, ["a,b"] and ["a", "b"] would collide.
        assertNotEquals(
            CountBlocksFunctionExpression.cacheKey(4, Arrays.asList("a,b")),
            CountBlocksFunctionExpression.cacheKey(4, Arrays.asList("a", "b")));
    }

    @Test
    @DisplayName("count_blocks は機械のキャッシュを通して読む")
    public void testCountBlocksの配線() {
        assertTrue(
            read("CountBlocksFunctionExpression.java").contains("WorldReadCache.read("),
            "count_blocks がキャッシュを通っていない");
    }

    static String read(String file) {
        Path path = Paths.get("src/main/java/ruiseki/okmodular/api/recipe/expression", file);
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + path.toAbsolutePath(), e);
        }
    }
}
