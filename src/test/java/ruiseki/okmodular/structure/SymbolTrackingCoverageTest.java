package ruiseki.okmodular.structure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * 構造の記号が**位置を記録されている**ことの検証。
 *
 * ============================================
 * 記録されていない記号は、存在しないのと同じ
 * ============================================
 *
 * `wrapTracking` は、その記号がどのマスに現れたかを機械に覚えさせる包み。
 * 覚えた位置は `getSymbolPositions` から引かれ、
 * **デコレータと構造 IO はそれ以外に対象を知る手段が無い**。
 *
 * 包み忘れた記号は、構造としては正しく成立する。ブロックは置けるし、判定も通る。
 * だが `"symbol": "X"` と書いたデコレータは**対象 0 マスで正常終了**する。
 * 例外もログも出ないし、確率的な効果なら「運が悪い」と見分けが付かない
 * ── デコレータ 6 種が死んでいたときとまったく同じ形。
 *
 * ============================================
 * 包んでいない 2 つ
 * ============================================
 *
 * `Q`（コントローラ）と `_`（空気）は包まれていない。
 * どちらも「そこに何があるか」が定義から自明なので、位置を覚える意味が薄い
 * ── という読み方はできる。だが**それは書いてなかった**ので、
 * `"symbol": "_"` と書いた人には「なぜか効かない」としか見えない。
 *
 * 意図なら意図として、ここに名前を残す。
 */
@DisplayName("構造の記号の位置記録")
public class SymbolTrackingCoverageTest {

    private static final Path REGISTRY = JavaSource.MAIN.resolve("structure/CustomStructureRegistry.java");

    /**
     * 位置を記録しない記号と、その理由。**増やすものではない**。
     *
     * <pre>
     * Q  コントローラ。1 つしか無く、機械自身の座標がそれ
     * _  空気。「何も無い」ことに作用するデコレータは今のところ無い
     * </pre>
     *
     * どちらも `getSymbolPositions('Q')` / `('_')` が**必ず空を返す**。
     * デコレータや構造 IO からこの 2 つを指したくなったら、
     * まずここを空にすること。指したまま動かないのが一番悪い。
     */
    private static final List<String> UNTRACKED = Arrays.asList("'Q'", "'_'");

    @Test
    @DisplayName("builder に足す記号は位置を記録している")
    public void test記録されない記号が増えていない() {
        String source = JavaSource.stripComments(JavaSource.read(REGISTRY));
        List<String> untracked = new ArrayList<>();

        Matcher added = Pattern.compile("builder\\.addElement\\(\\s*([^,]+?)\\s*,\\s*([^;]*?)\\)\\s*;", Pattern.DOTALL)
            .matcher(source);

        while (added.find()) {
            String symbol = added.group(1)
                .trim();
            if (UNTRACKED.contains(symbol)) continue;
            if (added.group(2)
                .contains("wrapTracking")) continue;
            untracked.add(symbol);
        }

        assertTrue(
            untracked.isEmpty(),
            untracked + " を wrapTracking で包まずに足している。構造は成立するが getSymbolPositions が空を返すので、"
                + "その記号を指したデコレータは**対象 0 マスで正常終了**する。例外もログも出ない");
    }

    @Test
    @DisplayName("記録しない記号の一覧が古くなっていない")
    public void test免除リストが正しい() {
        String source = JavaSource.stripComments(JavaSource.read(REGISTRY));

        for (String symbol : UNTRACKED) {
            Matcher added = Pattern
                .compile(
                    "builder\\.addElement\\(\\s*" + Pattern.quote(symbol) + "\\s*,\\s*([^;]*?)\\)\\s*;",
                    Pattern.DOTALL)
                .matcher(source);

            assertTrue(added.find(), symbol + " をもう builder に足していない。UNTRACKED から消すこと");
            assertTrue(
                !added.group(1)
                    .contains("wrapTracking"),
                symbol + " は包まれるようになった。UNTRACKED から消すこと");
        }
    }

    @Test
    @DisplayName("走査そのものが空振りしていない")
    public void test走査が効いている() {
        String source = JavaSource.stripComments(JavaSource.read(REGISTRY));
        int added = 0;

        Matcher hits = Pattern.compile("builder\\.addElement\\(")
            .matcher(source);
        while (hits.find()) added++;

        assertTrue(added >= 3, "builder.addElement が 3 箇所未満。走査が壊れている（" + added + " 箇所）");
        assertTrue(source.contains("wrapTracking("), "wrapTracking が消えた。位置を覚える仕組みごと無くなっている");
    }
}
