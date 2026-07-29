package ruiseki.okmodular.common.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.condition.ConditionContext;
import ruiseki.okmodular.api.condition.ICondition;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.modular.PortColor;
import ruiseki.okmodular.api.modular.PortColorGrouping;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.io.IRecipeInput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.visitor.IRecipeVisitor;

/**
 * 色群を順に試してレシピを選ぶ規則の検証。
 *
 * ============================================
 * 決めた規則
 * ============================================
 *
 * <pre>
 * 1. 群を順に試し、**最初に「マッチして、かつ出力が入る」群**を選ぶ
 * 2. マッチしても出力が塞がっている群は**飛ばして次を試す**
 * 3. どの群も走らせられないときは、**最初に塞がった群**のエラーを報告する
 * 4. どこにもマッチしなければレシピ無しを返す
 * </pre>
 *
 * 2 が「飛ばす」なのは、色群の目的が**独立した小機械として動かすこと**だから。
 * 赤の出力タンクが満杯でも青は動けるべきで、赤で止まると色分けの意味が薄れる。
 * 改修前は単一の群しか無かったので「マッチしたら出力を見て、駄目ならそこで終わり」でよかった。
 *
 * 3 が要るのは、全部駄目だったときに何を GUI に出すかを決めないといけないから。
 * 「最初に塞がった群」= 優先度の高い色のエラーを出すのが、プレイヤーの意図に近い。
 *
 * ============================================
 * なぜコントローラから切り出したか
 * ============================================
 *
 * `TEMachineController` はユニットテストで組めない（`MockWorld` が NPE）。
 * この選択規則はレシピの中身にも World にも依存しないので、
 * **レシピを探す関数を引数で受ける**形にすれば全部ここで縛れる。
 *
 * ============================================
 */
@DisplayName("色群ごとのレシピ選択")
public class ColoredRecipeSearchTest {

    // ========== 見つかる場合 ==========

    @Test
    @DisplayName("最初の群でマッチしたらそれを選ぶ")
    public void test最初の群を選ぶ() {
        StubRecipe recipe = StubRecipe.runnable("red_recipe");
        List<PortColorGrouping.Group> groups = groups(PortColor.RED, PortColor.BLUE);

        ColoredRecipeSearch.Selection selection = ColoredRecipeSearch.search(groups, inputs -> recipe);

        assertTrue(selection.isRunnable());
        assertSame(recipe, selection.getRecipe());
        assertSame(PortColor.RED, selection.getColor());
    }

    @Test
    @DisplayName("選んだ群の入出力リストが返る")
    public void test選んだ群の入出力が返る() {
        StubRecipe recipe = StubRecipe.runnable("r");
        List<IModularPort> blueIn = ports("blue_in");
        List<IModularPort> blueOut = ports("blue_out");
        List<PortColorGrouping.Group> groups = Arrays
            .asList(group(PortColor.RED, ports("red_in"), ports("red_out")), group(PortColor.BLUE, blueIn, blueOut));

        // 赤ではマッチせず、青でマッチする
        ColoredRecipeSearch.Selection selection = ColoredRecipeSearch
            .search(groups, inputs -> inputs == blueIn ? recipe : null);

        assertSame(PortColor.BLUE, selection.getColor());
        assertSame(blueIn, selection.getInputs(), "レシピを開始するのは選んだ群の入力ポートに対して");
        assertSame(blueOut, selection.getOutputs(), "出力も同じ群に行かなければ色分けの意味が無い");
    }

    @Test
    @DisplayName("群は与えられた順に試される")
    public void test群は順に試される() {
        List<PortColorGrouping.Group> groups = groups(PortColor.WHITE, PortColor.RED, PortColor.NONE);
        List<String> tried = new ArrayList<>();

        ColoredRecipeSearch.search(groups, inputs -> {
            tried.add(
                inputs.get(0)
                    .toString());
            return null;
        });

        assertEquals(Arrays.asList("WHITE_in", "RED_in", "NONE_in"), tried, "順序が崩れると「色の順で優先的に処理される」が成り立たない");
    }

    // ========== 出力が塞がっている群を飛ばす ==========

    @Test
    @DisplayName("出力容量が足りない群は飛ばして次を試す")
    public void test容量不足の群を飛ばす() {
        StubRecipe tooBig = StubRecipe.insufficientCapacity("too_big", IPortType.Type.FLUID);
        StubRecipe fits = StubRecipe.runnable("fits");
        List<IModularPort> blueIn = ports("blue_in");
        List<PortColorGrouping.Group> groups = Arrays.asList(
            group(PortColor.RED, ports("red_in"), ports("red_out")),
            group(PortColor.BLUE, blueIn, ports("blue_out")));

        ColoredRecipeSearch.Selection selection = ColoredRecipeSearch
            .search(groups, inputs -> inputs == blueIn ? fits : tooBig);

        assertTrue(selection.isRunnable(), "赤が容量不足でも青は動けるべき");
        assertSame(fits, selection.getRecipe());
        assertSame(PortColor.BLUE, selection.getColor());
    }

    @Test
    @DisplayName("出力が満杯の群は飛ばして次を試す")
    public void test満杯の群を飛ばす() {
        StubRecipe full = StubRecipe.outputFull("full");
        StubRecipe fits = StubRecipe.runnable("fits");
        List<IModularPort> blueIn = ports("blue_in");
        List<PortColorGrouping.Group> groups = Arrays.asList(
            group(PortColor.RED, ports("red_in"), ports("red_out")),
            group(PortColor.BLUE, blueIn, ports("blue_out")));

        ColoredRecipeSearch.Selection selection = ColoredRecipeSearch
            .search(groups, inputs -> inputs == blueIn ? fits : full);

        assertTrue(selection.isRunnable());
        assertSame(PortColor.BLUE, selection.getColor());
    }

    // ========== 全部駄目だった場合 ==========

    @Test
    @DisplayName("全群が塞がっていたら最初に塞がった群を報告する")
    public void test最初に塞がった群を報告する() {
        StubRecipe redFull = StubRecipe.insufficientCapacity("red_full", IPortType.Type.ITEM);
        StubRecipe blueFull = StubRecipe.outputFull("blue_full");
        List<IModularPort> blueIn = ports("blue_in");
        List<PortColorGrouping.Group> groups = Arrays.asList(
            group(PortColor.RED, ports("red_in"), ports("red_out")),
            group(PortColor.BLUE, blueIn, ports("blue_out")));

        ColoredRecipeSearch.Selection selection = ColoredRecipeSearch
            .search(groups, inputs -> inputs == blueIn ? blueFull : redFull);

        assertFalse(selection.isRunnable());
        assertSame(redFull, selection.getRecipe(), "優先度の高い色のエラーを出す方がプレイヤーの意図に近い");
        assertSame(PortColor.RED, selection.getColor());
        assertSame(IPortType.Type.ITEM, selection.getInsufficientType());
        assertFalse(selection.isOutputFull());
    }

    @Test
    @DisplayName("満杯だけのときは満杯として報告される")
    public void test満杯として報告される() {
        StubRecipe full = StubRecipe.outputFull("full");
        List<PortColorGrouping.Group> groups = groups(PortColor.RED);

        ColoredRecipeSearch.Selection selection = ColoredRecipeSearch.search(groups, inputs -> full);

        assertFalse(selection.isRunnable());
        assertSame(full, selection.getRecipe());
        assertNull(selection.getInsufficientType(), "容量ではなく満杯。両方を立てると GUI の文言が二重になる");
        assertTrue(selection.isOutputFull());
    }

    @Test
    @DisplayName("どこにもマッチしなければレシピ無し")
    public void testマッチしなければレシピ無し() {
        List<PortColorGrouping.Group> groups = groups(PortColor.RED, PortColor.NONE);

        ColoredRecipeSearch.Selection selection = ColoredRecipeSearch.search(groups, inputs -> null);

        assertFalse(selection.isRunnable());
        assertNull(selection.getRecipe(), "「マッチしなかった」と「動かせなかった」は別のエラーになる");
        assertNull(selection.getInsufficientType());
        assertFalse(selection.isOutputFull());
    }

    @Test
    @DisplayName("群が 1 つも無くても落ちない")
    public void test群が無い() {
        ColoredRecipeSearch.Selection selection = ColoredRecipeSearch
            .search(Collections.emptyList(), inputs -> StubRecipe.runnable("never"));

        assertFalse(selection.isRunnable());
        assertNull(selection.getRecipe());
    }

    // ========== 補助 ==========

    private static List<PortColorGrouping.Group> groups(PortColor... colors) {
        List<PortColorGrouping.Group> groups = new ArrayList<>();
        for (PortColor color : colors) {
            groups.add(group(color, ports(color.name() + "_in"), ports(color.name() + "_out")));
        }
        return groups;
    }

    private static PortColorGrouping.Group group(PortColor color, List<IModularPort> inputs,
        List<IModularPort> outputs) {
        return new PortColorGrouping.Group(color, inputs, outputs);
    }

    private static List<IModularPort> ports(String name) {
        List<IModularPort> ports = new ArrayList<>();
        ports.add(new NamedPort(name));
        return ports;
    }

    /** リストの中身は使われないので、名前だけ持つ。 */
    private static final class NamedPort implements IModularPort {

        private final String name;

        NamedPort(String name) {
            this.name = name;
        }

        @Override
        public int getTier() {
            return 0;
        }

        @Override
        public void setTier(int tier) {}

        @Override
        public IPortType.Type getPortType() {
            return IPortType.Type.ITEM;
        }

        @Override
        public IPortType.Direction getPortDirection() {
            return IPortType.Direction.BOTH;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * 出力の判定だけを制御できるレシピ。
     *
     * `checkOutputCapacity` と `canOutput` 以外は、この規則が読まないので既定値でよい。
     */
    private static final class StubRecipe implements IModularRecipe {

        private final String name;
        private final IPortType.Type insufficientType;
        private final boolean canOutput;

        private StubRecipe(String name, IPortType.Type insufficientType, boolean canOutput) {
            this.name = name;
            this.insufficientType = insufficientType;
            this.canOutput = canOutput;
        }

        static StubRecipe runnable(String name) {
            return new StubRecipe(name, null, true);
        }

        static StubRecipe insufficientCapacity(String name, IPortType.Type type) {
            return new StubRecipe(name, type, true);
        }

        static StubRecipe outputFull(String name) {
            return new StubRecipe(name, null, false);
        }

        @Override
        public IPortType.Type checkOutputCapacity(List<IModularPort> outputPorts) {
            return insufficientType;
        }

        @Override
        public boolean canOutput(List<IModularPort> outputPorts) {
            return canOutput;
        }

        @Override
        public boolean matchesInput(List<IModularPort> inputPorts) {
            return true;
        }

        @Override
        public boolean processInputs(List<IModularPort> inputPorts, boolean simulate) {
            return true;
        }

        @Override
        public boolean processOutputs(List<IModularPort> outputPorts, boolean simulate) {
            return true;
        }

        @Override
        public String getRegistryName() {
            return name;
        }

        @Override
        public String getRecipeGroup() {
            return "test";
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getDuration() {
            return 1;
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public List<IRecipeInput> getInputs() {
            return Collections.emptyList();
        }

        @Override
        public List<IRecipeOutput> getOutputs() {
            return Collections.emptyList();
        }

        @Override
        public List<ICondition> getConditions() {
            return Collections.emptyList();
        }

        @Override
        public boolean isConditionMet(ConditionContext context) {
            return true;
        }

        @Override
        public void onTick(ConditionContext context) {}

        @Override
        public void accept(IRecipeVisitor visitor) {}

        @Override
        public Map<String, Integer> getRequiredComponentTiers() {
            return Collections.emptyMap();
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
