package ruiseki.okmodular.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.structure.core.StructureSymbols;
import ruiseki.okmodular.testsupport.JavaSource;

/**
 * 構造の記号が**位置を記録されている**ことと、記録されない記号を**書けない**ことの検証。
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
 * 「ブロックが無いから要らない」は理由になっていなかった
 * ============================================
 *
 * `Q`（コントローラ）と `_`（空気）は長く包まれていなかった。
 * 「機械は自分の座標を知っている」「空気に回収するものは無い」——
 * どちらも**ブロックの話しかしていない**。
 *
 * マスに効果を置く側（パーティクル・音・湧き・そこに何が立っているかの判定）は、
 * そのマスに何が入っていようと**座標が欲しい**。今は 2 つとも包んである。
 *
 * ============================================
 * 空白だけは書いた時点で断る
 * ============================================
 *
 * `' '` は構造の一部ですらない。形状の読み取りで飛ばされ、builder に渡らず、
 * その座標は**一度も訪れられない**。記録しようが無いし、記録できるようにするには
 * 「見なくていい」と言われたマスを engine に歩かせることになる。
 *
 * だから**ロード時に断る**。走らせてから静かに 0 マスで終わるより、書いた瞬間に落ちるほうがいい。
 */
@DisplayName("構造の記号の位置記録")
public class SymbolTrackingCoverageTest {

    private static final Path REGISTRY = JavaSource.MAIN.resolve("structure/CustomStructureRegistry.java");

    /**
     * `"symbol"` を読む場所。**ここを増やしたら `requireAddressable` も通すこと。**
     *
     * 同じ判定を 7 箇所に手書きすると、いずれ 1 箇所だけ古くなる
     * （`findRecipeContext` が private コピー 10 個に増えたのと同じ形）。
     * 述語は `StructureSymbols` の 1 本だけ。
     */
    private static final List<String> SYMBOL_READERS = Arrays.asList(
        "api/recipe/io/BlockInput.java",
        "api/recipe/io/BlockOutput.java",
        "api/recipe/io/StructureInput.java",
        "api/recipe/io/StructureOutput.java",
        "api/recipe/decorator/PerPositionProbabilityDecorator.java",
        "api/recipe/decorator/RandomBlockOutputDecorator.java",
        "api/recipe/expression/NbtExpression.java");

    /**
     * `"symbol"` を読むが、**それは構造の記号ではない**場所。
     *
     * パターンの `shape.symbol` は `layers` を描く文字と同じもので、`mappings` が意味を与える。
     * 構造体の走査が位置を記録するかどうかという話ではないので、`requireAddressable` に
     * 言えることが無い（空白を断る理由も別で、「パターンに含まれない」＝形が空になるから）。
     *
     * **同じ JSON キー名で別の名前空間**なので、上の一覧に混ぜると
     * 「構造の記号を素通ししている」という誤った報告になる。
     */
    private static final List<String> PATTERN_CHARACTER_READERS = Arrays
        .asList("structure/pattern/ShapePrimitives.java");

    @Test
    @DisplayName("builder に足す記号はすべて位置を記録している")
    public void test記録されない記号が無い() {
        String source = JavaSource.stripComments(JavaSource.read(REGISTRY));
        List<String> untracked = new ArrayList<>();

        Matcher added = Pattern.compile("builder\\.addElement\\(\\s*([^,]+?)\\s*,\\s*([^;]*?)\\)\\s*;", Pattern.DOTALL)
            .matcher(source);

        while (added.find()) {
            if (added.group(2)
                .contains("wrapTracking")) continue;
            untracked.add(
                added.group(1)
                    .trim());
        }

        assertTrue(
            untracked.isEmpty(),
            untracked + " を wrapTracking で包まずに足している。構造は成立するが getSymbolPositions が空を返すので、"
                + "その記号を指したデコレータは**対象 0 マスで正常終了**する。例外もログも出ない");
    }

    @Test
    @DisplayName("記号を読む側はすべて requireAddressable を通っている")
    public void test書けない記号を素通ししていない() {
        List<String> unchecked = new ArrayList<>();

        for (String reader : SYMBOL_READERS) {
            String source = JavaSource.stripComments(JavaSource.read(JavaSource.MAIN.resolve(reader)));
            if (!source.contains("\"symbol\"")) {
                unchecked.add(reader + "（もう symbol を読んでいない。SYMBOL_READERS から消すこと）");
                continue;
            }
            if (!source.contains("StructureSymbols.requireAddressable(")) unchecked.add(reader);
        }

        assertTrue(
            unchecked.isEmpty(),
            unchecked + " が \"symbol\" を requireAddressable を通さずに読んでいる。"
                + "位置の記録されない記号がそのまま通ると、実行時に**対象 0 マスで正常終了**する");
    }

    @Test
    @DisplayName("symbol を読む場所の一覧が古くなっていない")
    public void test一覧が漏れていない() {
        List<String> missing = new ArrayList<>();

        for (Path file : JavaSource.allMainSources()) {
            String source = JavaSource.stripComments(JavaSource.read(file));
            // 型推論の has("symbol") とパーサ表の get("symbol") は読み取りではない。
            if (!source.contains(".get(\"symbol\")")) continue;
            if (source.contains("parsers.get(\"symbol\")")) continue;

            String label = JavaSource.MAIN.relativize(file)
                .toString()
                .replace('\\', '/');
            if (SYMBOL_READERS.contains(label) || PATTERN_CHARACTER_READERS.contains(label)) continue;
            missing.add(label);
        }

        assertTrue(
            missing.isEmpty(),
            missing + " が \"symbol\" を読むのにどちらの一覧にも無い。構造の記号なら SYMBOL_READERS に足して "
                + "requireAddressable を通し、パターンの文字なら PATTERN_CHARACTER_READERS に足すこと");
    }

    @Test
    @DisplayName("パターンの文字の一覧が古くなっていない")
    public void testパターン文字の一覧が生きている() {
        for (String reader : PATTERN_CHARACTER_READERS) {
            String source = JavaSource.stripComments(JavaSource.read(JavaSource.MAIN.resolve(reader)));

            assertTrue(
                source.contains(".get(\"symbol\")"),
                reader + " はもう symbol を読んでいない。PATTERN_CHARACTER_READERS から消すこと");
        }
    }

    @Test
    @DisplayName("空白だけが書けない記号")
    public void test書けない記号は空白だけ() {
        assertTrue(StructureSymbols.isAddressable(StructureSymbols.CONTROLLER), "Q は書けるはず。包むようにしたので");
        assertTrue(StructureSymbols.isAddressable(StructureSymbols.AIR), "_ は書けるはず。包むようにしたので");
        assertTrue(!StructureSymbols.isAddressable(StructureSymbols.ANYTHING), "空白が書けることになっている");
        assertTrue(StructureSymbols.isAddressable('G'), "普通の記号が書けなくなっている");
    }

    @Test
    @DisplayName("書けない記号はロード時に落ちる")
    public void test空白はロードで断られる() {
        IllegalArgumentException refused = assertThrows(
            IllegalArgumentException.class,
            () -> StructureSymbols.requireAddressable(StructureSymbols.ANYTHING, "a block output"),
            "空白が通っている。走らせてから静かに 0 マスで終わる");

        assertTrue(
            refused.getMessage()
                .contains("a block output"),
            "どこが書いたのかが message に無い。JSON のどこを直せばいいのか分からない: " + refused.getMessage());

        assertEquals('G', StructureSymbols.requireAddressable('G', "a block output"), "普通の記号まで断っている");
    }

    @Test
    @DisplayName("mappings が予約記号を上書きできない")
    public void test予約記号は上書きできない() {
        String source = JavaSource.stripComments(JavaSource.read(REGISTRY));

        assertTrue(
            source.contains("StructureSymbols.isReserved(symbol)"),
            "mappings の読み飛ばしが isReserved を通らなくなった。予約記号の一覧が 2 箇所に分かれる");

        for (char symbol : new char[] { StructureSymbols.CONTROLLER, StructureSymbols.AIR,
            StructureSymbols.ANYTHING }) {
            assertTrue(StructureSymbols.isReserved(symbol), "'" + symbol + "' が予約でなくなっている");
        }
        assertTrue(!StructureSymbols.isReserved('G'), "普通の記号が予約になっている");
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
        assertTrue(SYMBOL_READERS.size() >= 7, "symbol を読む場所が 7 箇所未満。一覧が削られている");
    }
}
