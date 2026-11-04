package ruiseki.okmodular.api.recipe.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * A batch copy scales the amount in long and saturates at Integer.MAX_VALUE instead of wrapping
 * negative. The types below build without a Minecraft registry; the source scan covers the rest.
 */
@DisplayName("copy(multiplier) のバッチ倍は int で溢れない")
public class CopyMultiplierOverflowTest {

    private static final Path IO = JavaSource.MAIN.resolve("api/recipe/io");
    private static final int BIG = 2_000_000_000;

    /** An int-by-int product or compound assignment with the multiplier, starting at a whole operand. */
    private static final Pattern INT_PRODUCT = Pattern
        .compile("(?<![\\w.])(?<!\\(long\\) )[\\w.]+\\s*\\*=?\\s*multiplier\\b");

    @Test
    @DisplayName("値で確かめられる型は上限で止まる")
    public void testSaturates() {
        assertEquals(
            Integer.MAX_VALUE,
            new BlockInput('A', "minecraft:stone", null, BIG, true, false).copy(2)
                .getRequiredAmount());
        assertEquals(
            Integer.MAX_VALUE,
            new BlockOutput('A', "minecraft:stone", null, BIG, false, null).copy(2)
                .getRequiredAmount());
        assertEquals(
            Integer.MAX_VALUE,
            new StructureInput('A', "p", BIG, true, false).copy(2)
                .getRequiredAmount());
        assertEquals(
            Integer.MAX_VALUE,
            new StructureOutput('A', "p", BIG, false).copy(2)
                .getRequiredAmount());
        assertEquals(
            Integer.MAX_VALUE,
            new EnergyInput(BIG).copy(2)
                .getRequiredAmount());
        assertEquals(
            Integer.MAX_VALUE,
            new EnergyOutput(BIG).copy(2)
                .getRequiredAmount());
        assertEquals(
            Integer.MAX_VALUE,
            new ManaInput(BIG).copy(2)
                .getRequiredAmount());
        assertEquals(
            Integer.MAX_VALUE,
            new ManaOutput(BIG).copy(2)
                .getRequiredAmount());
    }

    @Test
    @DisplayName("どの copy(multiplier) も int のまま掛けていない")
    public void testNoIntProduct() {
        List<String> found = new ArrayList<>();
        int scanned = 0;
        for (Path file : JavaSource.javaFilesUnder(IO)) {
            String source = JavaSource.stripComments(JavaSource.read(file));
            if (!source.contains("copy(int multiplier) {")) continue;
            scanned++;
            Matcher hits = INT_PRODUCT.matcher(JavaSource.methodBody(source, "copy(int multiplier) {"));
            while (hits.find()) found.add(JavaSource.label(file) + " の \"" + hits.group() + "\"");
        }
        assertTrue(scanned >= 16, "copy(multiplier) が " + scanned + " 個しか見えていない。走査が壊れている");
        assertTrue(found.isEmpty(), found + " が int のまま掛けている。(long) にして Amounts.toInt を通すこと");
    }

    @Test
    @DisplayName("検出パターンが空振りしていない")
    public void testPatternHits() {
        assertTrue(
            INT_PRODUCT.matcher("amount * multiplier")
                .find());
        assertTrue(
            INT_PRODUCT.matcher("copy.stackSize *= multiplier")
                .find());
        assertTrue(
            !INT_PRODUCT.matcher("Amounts.toInt((long) amount * multiplier)")
                .find());
        assertTrue(
            !INT_PRODUCT.matcher("Amounts.toInt((long) result.required.stackSize * multiplier)")
                .find());
        // The expression is scaled in double, so it is not an int product
        assertTrue(
            !INT_PRODUCT.matcher("ArithmeticExpression.scaled(this.amountExpr, multiplier)")
                .find());
    }
}
