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
 * 計算した量を int に落とすときに、負の量が作れないことの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * **Java の long → int キャストはビットを切り捨てるだけで、上限で止まらない。**
 *
 * <pre>
 * (int) 4000000000L  →  -294967296
 * (int) 4.0e9        →   2147483647   ← double からだと飽和する
 * </pre>
 *
 * 量は long で計算され（`getRequiredAmount(context) * multiplier`）、渡す先はすべて int
 * （`ItemStack.stackSize` / `FluidStack.amount` / `GasStack.amount` / `receiveEnergy` /
 * `addToContainer`）。その narrowing が **13 箇所に散っていて、`EnergyOutput` 以外は
 * 上限で止めていなかった**。21 億を超えた量は「大きな FluidStack」ではなく
 * **負の FluidStack** として届く。
 *
 * さらに 2 方向のキャストが混在していたので、**同じ量が読むときと使うときで別の数**になった。
 * JSON から読むのは double 経由なので 21 億で止まり（`(int) evaluateDouble`）、
 * 使うのは long 経由なので負に回る（`(int) getRequiredAmount`）。
 * 容量検査は前者の数で通し、払い出しは後者の数で動く。
 *
 * ============================================
 * ソースを見ている理由
 * ============================================
 *
 * 値の検査（`Amounts.toInt` の表）だけでは、**呼んでいない箇所が残っていること**を
 * 検出できない。ヘルパーを置いた意味は「1 箇所に寄せた」ことなので、
 * 生のキャストが復活していないことをソースで数える。
 *
 * `(int) Math.min(...)` は対象外。すでに int の値で上から抑えてあるので負にならない
 * （`FluidInput` の `stored.amount`、`ItemInput` の `stack.stackSize` など）。
 *
 * ============================================
 */
@DisplayName("量を int に落とすところ")
public class AmountNarrowingTest {

    private static final Path IO = JavaSource.MAIN.resolve("api/recipe/io");

    /** 量の計算に関わる名前。これらを生でキャストしていたら narrowing が散っている。 */
    private static final Pattern RAW_CAST = Pattern
        .compile("\\(int\\)\\s*\\(?\\s*(get(Required)?Amount|remaining|total|req)\\b");

    @Test
    @DisplayName("負の量は作れない")
    public void test負にならない() {
        assertEquals(0, Amounts.toInt(-1L), "負はそのまま通さない");
        assertEquals(0, Amounts.toInt(Long.MIN_VALUE));
        assertEquals(0, Amounts.toInt(0L));

        // これが直った本体。生のキャストなら -294967296 になる
        assertEquals(Integer.MAX_VALUE, Amounts.toInt(4_000_000_000L), "long → int の切り捨てが残っている");
        assertEquals(Integer.MAX_VALUE, Amounts.toInt(Long.MAX_VALUE));
    }

    @Test
    @DisplayName("収まる量はそのまま")
    public void test素通しする() {
        assertEquals(1, Amounts.toInt(1L));
        assertEquals(64, Amounts.toInt(64L));
        assertEquals(1000, Amounts.toInt(1000L));
        assertEquals(Integer.MAX_VALUE, Amounts.toInt(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE - 1, Amounts.toInt(Integer.MAX_VALUE - 1L));
    }

    @Test
    @DisplayName("キャストしてから掛ける形が残っていない")
    public void test狭めてから掛けていない() {
        // FluidOutput が (int) getRequiredAmount(context) * multiplier だった。
        // キャストは * より強く結合するので、狭めた後に int でもう一度溢れる。
        List<String> found = new ArrayList<>();
        for (Path file : JavaSource.javaFilesUnder(IO)) {
            if (JavaSource.stripComments(JavaSource.read(file))
                .contains("(int) getRequiredAmount")) {
                found.add(JavaSource.label(file));
            }
        }

        assertTrue(found.isEmpty(), found + " が getRequiredAmount を掛ける前に狭めている。括弧を足す前に Amounts.toInt を通すこと");
    }

    @Test
    @DisplayName("量の narrowing が Amounts に寄っている")
    public void test生のキャストが散っていない() {
        List<String> found = new ArrayList<>();

        for (Path file : JavaSource.javaFilesUnder(IO)) {
            if (file.getFileName()
                .toString()
                .equals("Amounts.java")) continue;

            String source = JavaSource.stripComments(JavaSource.read(file));
            Matcher hits = RAW_CAST.matcher(source);
            while (hits.find()) {
                // すでに int の値で上から抑えている形は負にならないので数えない
                String around = source.substring(hits.start(), Math.min(source.length(), hits.start() + 40));
                if (around.contains("Math.min")) continue;
                found.add(JavaSource.label(file) + " の \"" + hits.group() + "\"");
            }
        }

        assertTrue(found.isEmpty(), found + " が量を生でキャストしている。Amounts.toInt を通すこと");
    }

    @Test
    @DisplayName("走査そのものが空振りしていない")
    public void test走査が効いている() {
        List<Path> files = JavaSource.javaFilesUnder(IO);

        assertTrue(files.size() >= 20, "io パッケージが " + files.size() + " ファイルしか見えていない。走査が壊れている");
        assertTrue(
            files.stream()
                .anyMatch(
                    p -> p.getFileName()
                        .toString()
                        .equals("FluidOutput.java")),
            "FluidOutput が走査に入っていない");
        assertTrue(
            RAW_CAST.matcher("(int) remaining")
                .find(),
            "検出パターンが生のキャストに当たらない");
        assertTrue(
            RAW_CAST.matcher("(int) (getRequiredAmount(c) * m)")
                .find(),
            "検出パターンが括弧付きの形に当たらない");
    }
}
