package ruiseki.okmodular.api.recipe.expression;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NBT アクセス記法の検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * NBT へのアクセスを `nbt(...)` 関数に一本化し、ドット記法を廃止した。
 * 指針は「似たものを指定する時は同じやり方で指定する」。
 *
 * 廃止した理由は書きやすさではなく **曖昧さ**:
 * `S.energy` は「シンボル S の energy」とも「自分の NBT の S.energy」とも読める。
 * パーサは後者を選び、NbtExpression.toString() は前者を出力していたので、
 * 永続化して読み直すと**別の TileEntity を指していた**。
 *
 * 着手前は NBT 式が 42 件失敗していた（レシピは捨てられないので誰も気付かない）。
 * うち 2 系統は「実装済みなのに繋がっていなかった」もの:
 * - 複合代入は parseComparison にあったが、手前の加算層が `+` を先に食べていた
 * - 型変換は NBTTypeInference にあったが、呼び出し元が無かった
 *
 * ============================================
 */
@DisplayName("NBT アクセス記法")
public class NbtAccessSyntaxTest {

    private static IExpression parse(String script) {
        return ExpressionParser.parseExpression(script);
    }

    /** 代入を実行して、書き込まれた NBT を返す。 */
    private static NBTTagCompound applyAssignment(String script) {
        IExpression expr = parse(script);
        assertInstanceOf(INBTWriteExpression.class, expr, "代入は NBT 書き込み式になるべき: " + script);

        NBTTagCompound nbt = new NBTTagCompound();
        ((INBTWriteExpression) expr).applyToNBT(nbt, null);
        return nbt;
    }

    // ========================================
    // 対象の指定
    // ========================================

    @Test
    @DisplayName("nbt('key') はマシン自身を指す")
    public void testマシン自身() {
        IExpression expr = parse("nbt('energy')");

        assertInstanceOf(NbtExpression.class, expr);
        assertEquals('\0', ((NbtExpression) expr).getSymbol(), "symbol 無しはマシン自身");
    }

    @Test
    @DisplayName("nbt('S', 'key') はシンボル S の位置を指す")
    public void testシンボル指定() {
        IExpression expr = parse("nbt('S', 'stored_energy')");

        assertInstanceOf(NbtExpression.class, expr);
        NbtExpression nbtExpr = (NbtExpression) expr;
        assertEquals('S', nbtExpr.getSymbol(), "第 1 引数が symbol になるべき");
        assertEquals("stored_energy", nbtExpr.getNbtKey(), "第 2 引数が key になるべき");
    }

    @Test
    @DisplayName("階層パスは引数の文字列の中に書く")
    public void test階層パス() {
        NbtExpression expr = (NbtExpression) parse("nbt('display.Name')");

        assertEquals(
            2,
            expr.getPathSegments()
                .size(),
            "ドットで分解されるべき");
        assertEquals(
            "display",
            expr.getPathSegments()
                .get(0));
        assertEquals(
            "Name",
            expr.getPathSegments()
                .get(1));
    }

    @Test
    @DisplayName("シンボル + 階層パスも書ける")
    public void testシンボルと階層パスの併用() {
        NbtExpression expr = (NbtExpression) parse("nbt('S', 'a.b.c')");

        assertEquals('S', expr.getSymbol());
        assertEquals(
            3,
            expr.getPathSegments()
                .size());
    }

    // ========================================
    // 往復（永続化）
    // ========================================

    @Test
    @DisplayName("【回帰防止】シンボル指定が往復で別の対象にならない")
    public void testシンボルが往復で保たれる() {
        // 旧実装は toString() が "S.energy" を返し、再パースすると
        // 「マシン自身の S.energy パス」= 別の TileEntity になっていた
        NbtExpression original = (NbtExpression) parse("nbt('S', 'energy')");

        String text = original.toString();
        assertFalse(text.equals("S.energy"), "曖昧なドット形式で書き出してはいけない: " + text);

        NbtExpression restored = (NbtExpression) parse(text);
        assertEquals('S', restored.getSymbol(), "往復してもシンボルが保たれるべき");
        assertEquals("energy", restored.getNbtKey());
    }

    @Test
    @DisplayName("階層パスも往復できる")
    public void test階層パスが往復する() {
        NbtExpression original = (NbtExpression) parse("nbt('display.Name')");
        NbtExpression restored = (NbtExpression) parse(original.toString());

        assertEquals("display.Name", restored.getNbtKey());
        assertEquals('\0', restored.getSymbol());
    }

    // ========================================
    // ドット記法の廃止
    // ========================================

    @Test
    @DisplayName("【廃止】裸のドット記法は拒否され、代替を案内する")
    public void testドット記法は拒否される() {
        RecipeScriptException e = assertThrows(RecipeScriptException.class, () -> parse("display.Name"));

        assertTrue(
            e.getMessage()
                .contains("nbt('display.Name')"),
            "書き換え先を案内するべき: " + e.getMessage());
    }

    @Test
    @DisplayName("tier.component は NBT ではないので残る")
    public void testTierComponentは残る() {
        IExpression expr = parse("tier.glass");

        assertInstanceOf(ComponentTierExpression.class, expr, "tier.* は ComponentTierExpression のまま");
    }

    // ========================================
    // 代入
    // ========================================

    @Test
    @DisplayName("【回帰防止】トップレベルキーに代入できる")
    public void testトップレベルキーへの代入() {
        // 旧実装ではドットが無いと「変数」と解釈され、Unknown variable で落ちていた
        NBTTagCompound nbt = applyAssignment("nbt('temperature') = 300");

        assertTrue(nbt.hasKey("temperature"));
        assertEquals(300.0, nbt.getDouble("temperature"));
    }

    @Test
    @DisplayName("machine property と同名のキーにも代入できる")
    public void testMachinePropertyと同名のキー() {
        // energy は machine property として変数解決に成功してしまうため、
        // 旧実装では「NBT パスでない」と拒否されていた
        NBTTagCompound nbt = applyAssignment("nbt('energy') = 5000");

        assertEquals(5000.0, nbt.getDouble("energy"));
    }

    @Test
    @DisplayName("階層パスへの代入は中間の compound を作る")
    public void test階層パスへの代入() {
        NBTTagCompound nbt = applyAssignment("nbt('customData.level') = 7");

        assertTrue(nbt.hasKey("customData"), "中間の compound が作られるべき");
        assertEquals(
            7.0,
            nbt.getCompoundTag("customData")
                .getDouble("level"));
    }

    @Test
    @DisplayName("シンボル経由の代入は拒否される")
    public void testシンボル経由の代入は拒否() {
        // 書き込み先は文脈が持つ NBT（出力スタック等）なので、
        // 「読む対象」を指すシンボルは代入先になり得ない
        RecipeScriptException e = assertThrows(RecipeScriptException.class, () -> parse("nbt('S', 'energy') = 100"));

        assertTrue(
            e.getMessage()
                .contains("symbol"),
            "理由を述べるべき: " + e.getMessage());
    }

    // ========================================
    // 複合代入
    // ========================================

    @Test
    @DisplayName("【回帰防止】複合代入が加算層に食われない")
    public void test複合代入() {
        // parseComparison は元から += を扱えたが、parseAdditiveExpression が
        // '+' を二項演算子として先に食べ、'=' が取り残されていた
        assertEquals(15.0, applyAssignment("nbt('x') += 15").getDouble("x"), "空の NBT なら 0 + 15");

        NBTTagCompound base = new NBTTagCompound();
        base.setDouble("x", 10);
        ((INBTWriteExpression) parse("nbt('x') += 5")).applyToNBT(base, null);
        assertEquals(15.0, base.getDouble("x"), "既存値に加算されるべき");
    }

    @Test
    @DisplayName("4 種類の複合代入すべてが通る")
    public void test4種類の複合代入() {
        for (String op : new String[] { "+=", "-=", "*=", "/=" }) {
            assertDoesNotThrow(() -> parse("nbt('x') " + op + " 2"), op + " が通るべき");
        }
    }

    @Test
    @DisplayName("通常の加減乗除は壊れていない")
    public void test通常の演算は壊れていない() {
        assertEquals(15.0, parse("10 + 5").evaluateDouble(null));
        assertEquals(5.0, parse("10 - 5").evaluateDouble(null));
        assertEquals(50.0, parse("10 * 5").evaluateDouble(null));
        assertEquals(2.0, parse("10 / 5").evaluateDouble(null));
        assertEquals(1.0, parse("11 % 5").evaluateDouble(null));
    }

    // ========================================
    // 型サフィックス
    // ========================================

    @Test
    @DisplayName("【回帰防止】型サフィックスが読める")
    public void test型サフィックスが読める() {
        assertDoesNotThrow(() -> parse("127b"), "以前は Unexpected token: 'b' で落ちていた");
    }

    @Test
    @DisplayName("サフィックスごとに正しい NBT タグ型で書かれる")
    public void testサフィックスが型を決める() {
        assertInstanceOf(NBTTagByte.class, applyAssignment("nbt('v') = 127b").getTag("v"), "b は byte");
        assertInstanceOf(NBTTagShort.class, applyAssignment("nbt('v') = 32767s").getTag("v"), "s は short");
        assertInstanceOf(NBTTagInt.class, applyAssignment("nbt('v') = 2147483647i").getTag("v"), "i は int");
        assertInstanceOf(NBTTagLong.class, applyAssignment("nbt('v') = 9223372036854775807L").getTag("v"), "L は long");
        assertInstanceOf(NBTTagFloat.class, applyAssignment("nbt('v') = 3.14159f").getTag("v"), "f は float");
    }

    @Test
    @DisplayName("サフィックスが無ければ従来どおり double")
    public void testサフィックス無しはDouble() {
        NBTTagCompound nbt = applyAssignment("nbt('v') = 42");

        assertEquals(42.0, nbt.getDouble("v"));
    }

    @Test
    @DisplayName("型サフィックス付き literal も往復できる")
    public void testサフィックスが往復する() {
        IExpression expr = parse("127b");

        assertEquals("127b", expr.toString(), "サフィックスを保って書き出すべき");
        assertDoesNotThrow(() -> parse(expr.toString()));
    }
}
