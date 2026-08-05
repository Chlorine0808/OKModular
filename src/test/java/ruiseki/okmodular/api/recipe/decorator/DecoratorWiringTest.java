package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.recipe.expression.ConstantExpression;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;

/**
 * デコレータの効果に**呼ぶ側が居る**ことの検証。
 *
 * ============================================
 * 何が起きていたか
 * ============================================
 *
 * デコレータ 6 種が**一度も動いていなかった**。効果はすべて
 *
 * <pre>
 * public boolean processOutputs(List&lt;IModularPort&gt; ports, boolean simulate) {
 *     if (!internal.processOutputs(ports, simulate)) return false;
 *     if (!simulate) { ...ここに効果... }
 *     return true;
 * }
 * </pre>
 *
 * の形で書かれていたが、`processOutputs` の呼び出し元は main 全体で
 * **`ModularRecipe.canOutput` の 1 箇所だけ**で、しかも `simulate = true` 固定。
 * 完成時の払い出しは `ProcessAgent.produceOutputs` が `cachedOutputs` を直接適用しており、
 * レシピ本体に「出せ」と聞くことが無い。**`if (!simulate)` の中は全部デッドコード**だった。
 *
 * 実機では「ボーナスが一度も出ない」「床が一度も変わらない」として出た。
 * 例外もログも出ないので、**確率が低いのか壊れているのかを区別できない**のが最悪の点。
 *
 * ============================================
 * なぜ processOutputs を復活させないのか
 * ============================================
 *
 * `ProcessAgent` から `processOutputs(ports, false)` を呼ぶと、その先頭で
 * **レシピ本来の出力をもう一度払い出す**（`cachedOutputs` で払い済み）。全レシピが 2 倍になる。
 *
 * また量を開始時に確定させる仕組み（`resolveAmount` → `cachedOutputs`）も壊せない。
 * 稼働中に Tier が変わった機械は、**始めた時の Tier で**払い出す必要がある。
 *
 * だから `produceExtraOutputs` という別の口にした。「本来の出力の上に足す分」だけを持つ。
 *
 * ============================================
 * 何を縛るのか
 * ============================================
 *
 * 1. `ProcessAgent.produceOutputs` が新しい口を呼んでいること（呼ぶ側の存在）
 * 2. 鎖が下まで伝わること（途中のデコレータが握り潰さない）
 * 3. **`if (!simulate)` にぶら下がった効果が復活しないこと**（同じ穴を 7 個目に開けない）
 */
@DisplayName("デコレータの効果に呼ぶ側が居る")
public class DecoratorWiringTest {

    private static final Path DECORATORS = Paths.get("src/main/java/ruiseki/okmodular/api/recipe/decorator");
    private static final Path PROCESS_AGENT = Paths
        .get("src/main/java/ruiseki/okmodular/common/recipe/ProcessAgent.java");

    /**
     * まだ移せていないデコレータ。**空にするのが目標**で、増やすものではない。
     *
     * `harvest_block` は「ブロックが書き換わる**前**に採掘する」ので、
     * 出力適用後に走る `produceExtraOutputs` には乗らない。入力側の口も別に要る。
     * → `run/StructureIO_todos.md` の項目 S。
     */
    private static final List<String> KNOWN_DEAD = Arrays.asList("HarvestBlockDecorator.java");

    @Test
    @DisplayName("ProcessAgent が完成時に呼んでいる")
    public void test呼ぶ側が居る() {
        String body = methodBody(read(PROCESS_AGENT), "protected boolean produceOutputs(");

        assertTrue(
            body.contains("produceExtraOutputs"),
            "ProcessAgent.produceOutputs がデコレータの口を呼んでいない。" + "デコレータは例外もログも出さずに何もしなくなるので、"
                + "レシピを書いた側からは「確率が低い」と見分けが付かない");
    }

    @Test
    @DisplayName("鎖が下まで伝わる")
    public void test鎖を握り潰さない() {
        // 効果を持つデコレータが super を呼び忘れると、その下のデコレータが黙って消える。
        Bottom bottom = new Bottom();
        BonusOutputDecorator top = new BonusOutputDecorator(
            bottom,
            new ConstantExpression(0.0),
            new ArrayList<IRecipeOutput>(),
            null);

        top.produceExtraOutputs(Collections.<IModularPort>emptyList(), new ConditionContext(null, 0, 0, 0, null, 1L));

        assertEquals(1, bottom.calls, "上のデコレータが鎖を止めた");
    }

    @Test
    @DisplayName("効果が simulate フラグの裏に残っていない")
    public void testデッドな効果が残っていない() {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(DECORATORS)) {
            files.filter(
                p -> p.toString()
                    .endsWith(".java"))
                .forEach(p -> {
                    String name = p.getFileName()
                        .toString();
                    if (KNOWN_DEAD.contains(name)) return;

                    String source = read(p).replaceAll("\\s+", "");
                    // 宣言そのものではなく「!simulate で分岐している」形だけを見る。
                    if (source.contains("if(!simulate)")) offenders.add(name);
                });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        assertTrue(
            offenders.isEmpty(),
            offenders + " が !simulate の中に効果を持っている。processOutputs は simulate=true でしか"
                + "呼ばれないので、そこに書いたものは動かない。produceExtraOutputs に置くこと");
    }

    @Test
    @DisplayName("移せていないデコレータの一覧が正しい")
    public void test積み残しの一覧が正しい() {
        // 免除リストが古くなると「直したのに免除されたまま」「消したのに残っている」になる。
        for (String name : KNOWN_DEAD) {
            Path path = DECORATORS.resolve(name);
            assertTrue(Files.exists(path), name + " はもう無い。KNOWN_DEAD から消すこと");
            assertTrue(
                read(path).replaceAll("\\s+", "")
                    .contains("if(!simulate)"),
                name + " はもう !simulate に効果を持っていない。移し終わったなら KNOWN_DEAD から消すこと");
        }
    }

    /** 鎖の一番下。`internal` を持たないので super を呼ばない。 */
    private static final class Bottom extends RecipeDecorator {

        int calls = 0;

        Bottom() {
            super(null);
        }

        @Override
        public void produceExtraOutputs(List<IModularPort> outputPorts, ConditionContext context) {
            calls++;
        }
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new IllegalStateException("メソッドが見つからない: " + signature);

        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            if (source.charAt(i) == '}') {
                depth--;
                if (depth == 0) return source.substring(brace, i + 1);
            }
        }
        throw new IllegalStateException("メソッドの終わりが見つからない: " + signature);
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("読めない: " + path, e);
        }
    }
}
