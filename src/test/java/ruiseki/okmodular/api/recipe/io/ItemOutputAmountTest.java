package ruiseki.okmodular.api.recipe.io;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import ruiseki.okmodular.api.condition.ConditionContext;

/**
 * ItemOutput の amount 式の検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * EXPRESSION_REFERENCE.md は "amount": "tier * 2" を正しい書き方として
 * 明記しているのに、ItemOutput は ItemJson 経由で int を読むだけだった。
 * ItemJson.parseSafeInt は例外を飲んで 1 を返すので、式で書かれた出力量は
 * **エラーも出さずに黙って 1 個**になっていた。ItemInput は対応済みで、
 * 出力側だけが取り残されていた。
 *
 * 黙って壊れる類のバグなので、式が「保持され、評価され、永続化を越えて
 * 生き残る」ことをそれぞれ確かめる。
 *
 * ============================================
 */
@DisplayName("ItemOutput: amount の式")
public class ItemOutputAmountTest {

    private static ItemOutput read(String json) {
        return ItemOutput.fromJson(
            new JsonParser().parse(json)
                .getAsJsonObject());
    }

    /** 定数だけの式は世界の状態を見ないので、空の context で評価できる。 */
    private static ConditionContext emptyContext() {
        return new ConditionContext(null, 0, 0, 0);
    }

    @Test
    @DisplayName("式で書いた amount が評価される")
    public void test式が評価される() {
        ItemOutput output = read("{ \"item\": \"minecraft:gold_nugget\", \"amount\": \"2 + 3\" }");

        assertEquals(5, output.getRequiredAmount(emptyContext()), "式が評価されるべき");
    }

    @Test
    @DisplayName("【回帰防止】式が黙って 1 個にならない")
    public void test式が黙って1にならない() {
        ItemOutput output = read("{ \"item\": \"minecraft:gold_nugget\", \"amount\": \"4 * 2\" }");

        assertNotEquals(1, output.getRequiredAmount(emptyContext()), "ItemJson の fallback 値がそのまま出てはいけない");
        assertEquals(8, output.getRequiredAmount(emptyContext()));
    }

    @Test
    @DisplayName("数値で書いた amount は従来どおり")
    public void test数値はそのまま() {
        ItemOutput output = read("{ \"item\": \"minecraft:gold_nugget\", \"amount\": 3 }");

        assertEquals(3, output.getRequiredAmount(emptyContext()));
        assertEquals(3, output.getRequiredAmount(), "context なしでも静的値は読めるべき");
    }

    @Test
    @DisplayName("context が無いときは静的な控え値を返す")
    public void test式でcontextがない場合() {
        ItemOutput output = read("{ \"item\": \"minecraft:gold_nugget\", \"amount\": \"2 + 3\" }");

        // NEI のようにマシンが存在しない場所から呼ばれる経路。評価はできないので
        // ItemJson が決めた控え値 1 を返す。例外を投げないことが重要
        assertEquals(1, output.getRequiredAmount(), "context 無しでは控え値");
    }

    @Test
    @DisplayName("式は NBT 往復を越えて生き残る")
    public void test式がNBT往復で保持される() {
        ItemOutput original = read("{ \"item\": \"minecraft:gold_nugget\", \"amount\": \"2 + 3\" }");

        NBTTagCompound nbt = new NBTTagCompound();
        original.writeToNBT(nbt);

        ItemOutput restored = new ItemOutput((net.minecraft.item.ItemStack) null);
        restored.readFromNBT(nbt);

        assertEquals(5, restored.getRequiredAmount(emptyContext()), "ワールド再読込で式が失われてはいけない");
    }

    @Test
    @DisplayName("式は copy() を越えて生き残る")
    public void test式がcopyで保持される() {
        ItemOutput original = read("{ \"item\": \"minecraft:gold_nugget\", \"amount\": \"2 + 3\" }");

        IRecipeOutput copied = original.copy(2);

        assertEquals(5, copied.getRequiredAmount(emptyContext()), "copy で式が失われてはいけない");
    }

    @Test
    @DisplayName("関数を含む式も往復できる")
    public void test関数を含む式が往復する() {
        // floor(...) と括弧つきの算術は、式ツリーの内部ノードが toString を
        // 実装していないと NBT に書いた時点で ArithmeticExpression@1a2b3c になる
        ItemOutput original = read("{ \"item\": \"minecraft:gold_nugget\", \"amount\": \"floor((4 + 6) / 2)\" }");
        assertEquals(5, original.getRequiredAmount(emptyContext()));

        NBTTagCompound nbt = new NBTTagCompound();
        original.writeToNBT(nbt);
        assertFalse(
            nbt.getString("amountExpr")
                .contains("@"),
            "式が Object.toString() で書かれていないべき: " + nbt.getString("amountExpr"));

        ItemOutput restored = new ItemOutput((net.minecraft.item.ItemStack) null);
        restored.readFromNBT(nbt);
        assertEquals(5, restored.getRequiredAmount(emptyContext()));
    }
}
