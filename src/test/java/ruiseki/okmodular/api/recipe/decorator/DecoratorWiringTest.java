package ruiseki.okmodular.api.recipe.decorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.recipe.expression.ConstantExpression;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.testsupport.JavaSource;

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
 * なぜ processOutputs を復活させなかったのか
 * ============================================
 *
 * `ProcessAgent` から `processOutputs(ports, false)` を呼ぶと、その先頭で
 * **レシピ本来の出力をもう一度払い出す**（`cachedOutputs` で払い済み）。全レシピが 2 倍になる。
 *
 * また量を開始時に確定させる仕組み（`resolveAmount` → `cachedOutputs`）も壊せない。
 * 稼働中に Tier が変わった機械は、**始めた時の Tier で**払い出す必要がある。
 *
 * だから `produceExtraOutputs` という別の口にした。「本来の出力の上に足す分」だけを持つ。
 * `processInputs` / `processOutputs` の方はそのあと検査専用に畳んで消えている
 * （`matchesInput` / `canOutput` が中身を引き取った）。**実行経路に見える死んだ関数は、無いより悪い。**
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
     * まだ呼ぶ側が居ない実装。`ファイル名#メソッド名` で書く。**今は空**。
     *
     * `harvest_block` が最後まで残っていた。出力適用後に走る `produceExtraOutputs` には
     * 乗らない（採るはずのブロックがもう上書きされている）ので、
     * **専用の口が 2 つ**要ったため。項目 S として片付いた:
     *
     * <pre>
     * canStart              硬すぎるブロックがあれば断る（断ってよい唯一の瞬間）
     * beforeInputsConsumed  BlockInput が持っていくブロックを採る
     * beforeOutputsApplied  BlockOutput が上書きするブロックを採る
     * </pre>
     *
     * 空のまま維持するのが目的で、足す場所ではない。
     */
    private static final List<String> KNOWN_DEAD = Arrays.<String>asList();

    @Test
    @DisplayName("ProcessAgent が完成時に呼んでいる")
    public void test呼ぶ側が居る() {
        String body = JavaSource.methodBody(JavaSource.read(PROCESS_AGENT), "protected boolean produceOutputs(");

        assertTrue(
            body.contains("produceExtraOutputs"),
            "ProcessAgent.produceOutputs がデコレータの口を呼んでいない。" + "デコレータは例外もログも出さずに何もしなくなるので、"
                + "レシピを書いた側からは「確率が低い」と見分けが付かない");
    }

    @Test
    @DisplayName("ブロックが消える前の口を呼んでいる")
    public void test消える前に呼ばれている() {
        String source = JavaSource.stripComments(JavaSource.read(PROCESS_AGENT));

        // 入力側。消費する visitor より**前**でなければ、採るはずのブロックがもう無い。
        String start = JavaSource.methodBody(source, "public boolean startRecipe(");
        int harvest = start.indexOf("beforeInputsConsumed");
        int consume = start.indexOf("Mode.CONSUME");
        assertTrue(harvest >= 0, "startRecipe が beforeInputsConsumed を呼んでいない");
        assertTrue(consume >= 0, "startRecipe の消費 visitor が見つからない。このテストの前提が崩れている");
        assertTrue(harvest < consume, "beforeInputsConsumed が消費より後にある。呼ばれた時点でブロックはもう消えている");

        // 出力側。適用より**前**でなければ、上書きされた後を採ることになる。
        String produce = JavaSource.methodBody(source, "protected boolean produceOutputs(");
        int before = produce.indexOf("beforeOutputsApplied");
        int apply = produce.indexOf("o.apply(outputPorts");
        assertTrue(before >= 0, "produceOutputs が beforeOutputsApplied を呼んでいない");
        assertTrue(apply >= 0, "produceOutputs の適用が見つからない。このテストの前提が崩れている");
        assertTrue(before < apply, "beforeOutputsApplied が適用より後にある。上書き後のブロックを採ることになる");
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
        // 免除は無い。ぶら下がる先の processInputs / processOutputs ごと消したので、
        // デコレータに `!simulate` が生えたら、それは新しく作られた死に方。
        List<String> offenders = new ArrayList<>();

        for (Path file : JavaSource.javaFilesUnder(DECORATORS)) {
            String source = JavaSource.read(file)
                .replaceAll("\\s+", "");
            // 宣言そのものではなく「!simulate で分岐している」形だけを見る。
            if (source.contains("if(!simulate)")) offenders.add(
                file.getFileName()
                    .toString());
        }

        assertTrue(
            offenders.isEmpty(),
            offenders + " が !simulate の中に効果を持っている。レシピ側に残っている simulate 付きの口は"
                + "検査専用なので、そこに書いたものは動かない。produceExtraOutputs に置くこと");
    }

    @Test
    @DisplayName("止まっている実装の一覧が正しい")
    public void test積み残しの一覧が正しい() {
        // 免除リストが古くなると「直したのに免除されたまま」「消したのに残っている」になる。
        for (String entry : KNOWN_DEAD) {
            String[] parts = entry.split("#", 2);
            Path path = DECORATORS.resolve(parts[0]);
            String method = parts[1];

            assertTrue(Files.exists(path), entry + " のファイルはもう無い。KNOWN_DEAD から消すこと");
            assertTrue(
                JavaSource.stripComments(JavaSource.read(path))
                    .contains(method + "("),
                entry + " のメソッドはもう無い。KNOWN_DEAD から消すこと");
            assertEquals(0, callersOf(method), entry + " に呼ぶ側が付いた。動くようになったなら KNOWN_DEAD から消すこと");
        }
    }

    /** main 全体で、その名前が宣言以外に何回現れるか。 */
    private static int callersOf(String method) {
        Pattern occurrence = Pattern.compile("\\b" + Pattern.quote(method) + "\\s*\\(");
        int found = 0;

        for (Path file : JavaSource.allMainSources()) {
            Matcher hits = occurrence.matcher(JavaSource.stripComments(JavaSource.read(file)));
            while (hits.find()) found++;
        }
        return found - 1; // 宣言そのものを差し引く
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

}
