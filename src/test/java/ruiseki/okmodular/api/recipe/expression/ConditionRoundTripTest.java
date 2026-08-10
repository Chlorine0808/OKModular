package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * `&&` / `||` / `!` を含む式が保存を越えられることの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * **`OpAnd` / `OpOr` / `OpNot` が `toString()` を持っていなかった。**
 *
 * 式は `toString()` で NBT に書き出して読み直される:
 *
 * <pre>
 * nbt.setString("amountExpr", amountExpr.toString());
 * </pre>
 *
 * override が無いので `Object.toString()` に落ち、**`OpAnd@1f2e3d` が NBT に書かれていた**。
 * 読み戻すと「Dotted names are no longer NBT paths」で throw する。
 *
 * つまり `"amount": "is_day && tier > 2"` と書いた機械は、**動いている最中に保存すると
 * 読めなくなる**。ロード時に断られるのではないので、書いた人には理由が分からない。
 *
 * ============================================
 * getDescription() があったのに直っていなかった理由
 * ============================================
 *
 * `getDescription()` は存在した。ただし出すのは `(a AND b)` / `NOT (x)` で、
 * **`AND` も `NOT` も文法のトークンではない**。読み物用の文と、読み戻せるソースは別物。
 * `ComparisonCondition` だけが両者が一致する（`tier >= 5`）ので気づかれていなかった。
 *
 * 2 つの読み手が別のものを欲しがっているので、メソッドも別にしてある:
 * `getDescription()` はプレイヤーに見せる文（`MachineConditionGate` が使う）、
 * `toString()` は読み戻せるソース。
 *
 * ============================================
 * 「@ が入っていないこと」を見ている理由
 * ============================================
 *
 * 往復して値が一致するかだけでは足りない。`Object.toString()` の結果は
 * **パースできずに throw する**ので往復テストは落ちるが、
 * 落ち方が「読めない」であって「クラス名を吐いている」ではないため、
 * 原因に辿り着きにくい。症状そのものを別に縛る。
 *
 * ============================================
 */
@DisplayName("条件を含む式の往復")
public class ConditionRoundTripTest {

    /** 文法が組み立てられる条件。ここに無いものは JSON の `conditions` 専用で、式には書けない。 */
    // spotless:off
    private static final String[] WRITABLE = {
        "tier > 1",
        "tier > 1 && tier < 9",
        "tier > 9 || tier == 3",
        "!(tier > 9)",
        "tier > 1 && tier < 9 && tier != 5",
        "(tier > 9 || tier == 3) && !(tier == 1)",
        "1 + (tier > 1 && tier < 9)",
        "max(tier > 1 && tier < 9, 0)",
    };
    // spotless:on

    @Test
    @DisplayName("クラス名とハッシュを吐かない")
    public void testオブジェクトのtoStringに落ちていない() {
        for (String source : WRITABLE) {
            String written = ExpressionParser.parseExpression(source)
                .toString();

            assertFalse(
                written.contains("@"),
                "\"" + source + "\" の toString() が Object.toString() に落ちている: " + written);
        }
    }

    @Test
    @DisplayName("読み物用の綴りを吐かない")
    public void test文法に無い綴りを吐いていない() {
        for (String source : WRITABLE) {
            String written = ExpressionParser.parseExpression(source)
                .toString();

            assertFalse(written.contains(" AND "), "\"" + source + "\" が getDescription() の綴りを吐いている: " + written);
            assertFalse(written.contains(" OR "), "\"" + source + "\" が getDescription() の綴りを吐いている: " + written);
            assertFalse(written.contains("NOT "), "\"" + source + "\" が getDescription() の綴りを吐いている: " + written);
        }
    }

    @Test
    @DisplayName("書き出して読み直しても同じ答え")
    public void test往復する() {
        for (String source : WRITABLE) {
            IExpression once = ExpressionParser.parseExpression(source);
            IExpression twice = assertDoesNotThrow(
                () -> ExpressionParser.parseExpression(once.toString()),
                () -> "\"" + source + "\" を書き出した \"" + once + "\" が読み戻せない");

            assertEquals(
                once.evaluateDouble(StubMachineContext.withMachine()),
                twice.evaluateDouble(StubMachineContext.withMachine()),
                "\"" + source + "\" が往復で値を変える（書き出し: " + once + "）");

            // 2 周目で形が動かないこと。1 周ごとに括弧が増える書き方だと NBT が膨らむ
            assertEquals(once.toString(), twice.toString(), "\"" + source + "\" が往復のたびに形を変える");
        }
    }

    /**
     * 文法が組み立てる条件クラスには `toString()` が要る。
     * <p>
     * `xor` / `nand` / `nor` は JSON の `conditions` 専用で式の構文が無いため、
     * 読み戻せるソースというものが存在しない。**だからこのテストはパーサが
     * `new` している分だけを見る** — 将来そこに演算子が足されたら、ここが要求する。
     */
    @Test
    @DisplayName("パーサが組み立てる条件は全部 toString を持っている")
    public void test新しい条件を足したら要求される() {
        Path parser = JavaSource.MAIN.resolve("api/recipe/expression/ExpressionParser.java");
        String source = JavaSource.stripComments(JavaSource.read(parser));

        List<String> missing = new ArrayList<>();
        for (String name : new String[] { "OpAnd", "OpOr", "OpNot", "OpXor", "OpNand", "OpNor",
            "ComparisonCondition" }) {
            if (!source.contains("new " + name + "(")) continue;

            String condition = JavaSource.read(JavaSource.MAIN.resolve("api/condition/" + name + ".java"));
            if (!condition.contains("public String toString()")) missing.add(name);
        }

        assertEquals(List.of(), missing, missing + " をパーサが組み立てているのに toString() が無い。式に書けるものは NBT に書き出される");
    }

    @Test
    @DisplayName("走査そのものが空振りしていない")
    public void test走査が効いている() {
        String source = JavaSource
            .stripComments(JavaSource.read(JavaSource.MAIN.resolve("api/recipe/expression/ExpressionParser.java")));

        assertFalse(source.isEmpty(), "パーサのソースが読めていない");
        assertEquals(true, source.contains("new OpAnd("), "パーサが OpAnd を組み立てている形跡が無い。走査が壊れている");
    }
}
