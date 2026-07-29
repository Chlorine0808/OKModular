package ruiseki.okmodular.api.modular;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * ポートを色で群に分ける規則の検証。
 *
 * ============================================
 * 決めた規則
 * ============================================
 *
 * <pre>
 * 1. 色つきポートは、自分の色の群にだけ入る
 * 2. **塗っていないポートはすべての群に入る**（共有）
 * 3. 群の順序は PortColor の宣言順 = 白 → 黒
 * 4. **塗っていないポートだけの群は最後**
 * 5. Type.BLOCK のポート（= コントローラ自身）は色に関係なく全群に入る
 * </pre>
 *
 * 2 を「共有」にしたのは、そうしないと**色を使った瞬間にエネルギーハッチも
 * 色数だけ必要になる**から。代償として、塗っていない入力ポートに入れた材料は
 * どの群からも見える = 色による隔離は完全ではない。**これは仕様**で、
 * 完全に隔離したいならすべてのポートを塗る。
 *
 * 4 が要るのは 2 の帰結。共有だと「塗っていないポートだけの群」は
 * **全ポートを含む群**になるのでほぼ何にでもマッチする。先に評価すると色分けが無意味になる。
 *
 * 5 が要るのは `getContextualInputPorts()` がコントローラ自身をリストに足しているから
 * （`getPortType()` が `BLOCK`）。落とすと `BlockInput` / `BlockOutput` を使う
 * レシピがどの群でも動かなくなる。
 *
 * ============================================
 * いちばん大事なテスト
 * ============================================
 *
 * **「1 つも塗っていない機械は 1 群になる」**。これが崩れると、
 * 色を使っていない既存のワールドの挙動が変わる。
 *
 * ============================================
 */
@DisplayName("ポートの色による群分け")
public class PortColorGroupingTest {

    /** テスト用のポート。表示名を持つので、どの群に何が入ったかが読める。 */
    private static final class StubPort implements IModularPort {

        private final String name;
        private final IPortType.Type type;
        private PortColor color;

        StubPort(String name, IPortType.Type type, PortColor color) {
            this.name = name;
            this.type = type;
            this.color = color;
        }

        static StubPort item(String name, PortColor color) {
            return new StubPort(name, IPortType.Type.ITEM, color);
        }

        static StubPort energy(String name, PortColor color) {
            return new StubPort(name, IPortType.Type.ENERGY, color);
        }

        /** コントローラの代役。`getPortType()` が BLOCK であることが効く。 */
        static StubPort controller() {
            return new StubPort("controller", IPortType.Type.BLOCK, PortColor.NONE);
        }

        @Override
        public int getTier() {
            return 0;
        }

        @Override
        public void setTier(int tier) {}

        @Override
        public IPortType.Type getPortType() {
            return type;
        }

        @Override
        public IPortType.Direction getPortDirection() {
            return IPortType.Direction.BOTH;
        }

        @Override
        public PortColor getPortColor() {
            return color;
        }

        @Override
        public void setPortColor(PortColor color) {
            this.color = color;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // ========== 色を使っていない機械 ==========

    @Test
    @DisplayName("1 つも塗っていなければ 1 群になる")
    public void test無色だけなら1群() {
        List<IModularPort> inputs = ports(StubPort.item("in1", PortColor.NONE), StubPort.item("in2", PortColor.NONE));
        List<IModularPort> outputs = ports(StubPort.item("out1", PortColor.NONE));

        List<PortColorGrouping.Group> groups = PortColorGrouping.group(inputs, outputs);

        assertEquals(1, groups.size(), "色を使っていない機械は改修前と同じ 1 群でなければならない");
        assertSame(
            PortColor.NONE,
            groups.get(0)
                .getColor());
        assertEquals(
            names("in1", "in2"),
            names(
                groups.get(0)
                    .getInputs()));
        assertEquals(
            names("out1"),
            names(
                groups.get(0)
                    .getOutputs()));
    }

    @Test
    @DisplayName("ポートが無くても 1 群になる")
    public void testポートが無くても1群() {
        List<IModularPort> inputs = ports(StubPort.controller());
        List<IModularPort> outputs = ports(StubPort.controller());

        List<PortColorGrouping.Group> groups = PortColorGrouping.group(inputs, outputs);

        assertEquals(1, groups.size());
        assertEquals(
            names("controller"),
            names(
                groups.get(0)
                    .getInputs()));
    }

    @Test
    @DisplayName("空のリストでも落ちない")
    public void test空のリスト() {
        List<PortColorGrouping.Group> groups = PortColorGrouping
            .group(Collections.emptyList(), Collections.emptyList());

        assertEquals(1, groups.size(), "空でも 1 群返す。呼び出し側が空を特別扱いしなくて済む");
        assertTrue(
            groups.get(0)
                .getInputs()
                .isEmpty());
    }

    // ========== 共有 ==========

    @Test
    @DisplayName("塗っていないポートは色群にも入る")
    public void test無色は色群にも入る() {
        List<IModularPort> inputs = ports(
            StubPort.item("red_in", PortColor.RED),
            StubPort.energy("shared_energy", PortColor.NONE));
        List<IModularPort> outputs = ports(StubPort.item("red_out", PortColor.RED));

        List<PortColorGrouping.Group> groups = PortColorGrouping.group(inputs, outputs);

        assertEquals(2, groups.size(), "赤の群と、塗っていないポートだけの群");
        assertSame(
            PortColor.RED,
            groups.get(0)
                .getColor());
        assertEquals(
            names("red_in", "shared_energy"),
            names(
                groups.get(0)
                    .getInputs()),
            "塗っていないエネルギーハッチは赤の群にも入る。入らないと色ごとにハッチが必要になる");
    }

    @Test
    @DisplayName("色つきポートは他の色の群に入らない")
    public void test色つきは他の色に入らない() {
        List<IModularPort> inputs = ports(
            StubPort.item("red_in", PortColor.RED),
            StubPort.item("blue_in", PortColor.BLUE));

        List<PortColorGrouping.Group> groups = PortColorGrouping.group(inputs, ports());

        assertEquals(2, groups.size(), "全ポートが塗られているので、赤と青だけ。中身が空になる無色の群は作らない");
        for (PortColorGrouping.Group group : groups) {
            if (group.getColor() == PortColor.RED) {
                assertEquals(names("red_in"), names(group.getInputs()), "赤の群に青が漏れている");
            } else if (group.getColor() == PortColor.BLUE) {
                assertEquals(names("blue_in"), names(group.getInputs()), "青の群に赤が漏れている");
            }
        }
    }

    @Test
    @DisplayName("塗っていないポートが無ければ無色の群は出ない")
    public void test無色ポートが無ければ無色群は出ない() {
        List<IModularPort> inputs = ports(StubPort.item("red_in", PortColor.RED), StubPort.controller());
        List<IModularPort> outputs = ports(StubPort.item("red_out", PortColor.RED), StubPort.controller());

        List<PortColorGrouping.Group> groups = PortColorGrouping.group(inputs, outputs);

        assertEquals(1, groups.size(), "コントローラしか無色でないなら、無色の群は赤の群の複製にしかならない");
        assertSame(
            PortColor.RED,
            groups.get(0)
                .getColor());
    }

    // ========== BLOCK ポート ==========

    @Test
    @DisplayName("BLOCK ポートは全群に入る")
    public void testBlockポートは全群に入る() {
        List<IModularPort> inputs = ports(
            StubPort.item("red_in", PortColor.RED),
            StubPort.item("blue_in", PortColor.BLUE),
            StubPort.controller());

        List<PortColorGrouping.Group> groups = PortColorGrouping.group(inputs, ports());

        for (PortColorGrouping.Group group : groups) {
            assertTrue(
                names(group.getInputs()).contains("controller"),
                () -> group.getColor() + " の群にコントローラが入っていない。BlockInput / BlockOutput が動かなくなる");
        }
    }

    /**
     * 塗られた BLOCK ポートも全群に入る。
     *
     * 今はコントローラのブロックを塗る道が無いが、後で塗れるようにしたときに
     * ブロック変換レシピが静かに止まるのを防ぐ。
     */
    @Test
    @DisplayName("塗られていても BLOCK ポートは全群に入る")
    public void test塗られたBlockポートも全群に入る() {
        StubPort painted = new StubPort("painted_controller", IPortType.Type.BLOCK, PortColor.GREEN);
        List<IModularPort> inputs = ports(StubPort.item("red_in", PortColor.RED), painted);

        List<PortColorGrouping.Group> groups = PortColorGrouping.group(inputs, ports());

        for (PortColorGrouping.Group group : groups) {
            assertTrue(
                names(group.getInputs()).contains("painted_controller"),
                () -> group.getColor() + " の群に BLOCK ポートが入っていない");
        }
    }

    // ========== 順序 ==========

    @Test
    @DisplayName("群は宣言順（白から黒）に並ぶ")
    public void test群は宣言順に並ぶ() {
        List<IModularPort> inputs = ports(
            StubPort.item("black", PortColor.BLACK),
            StubPort.item("white", PortColor.WHITE),
            StubPort.item("red", PortColor.RED),
            StubPort.item("orange", PortColor.ORANGE),
            StubPort.energy("shared", PortColor.NONE));

        List<PortColorGrouping.Group> groups = PortColorGrouping.group(inputs, ports());

        List<PortColor> order = new ArrayList<>();
        for (PortColorGrouping.Group group : groups) {
            order.add(group.getColor());
        }

        assertEquals(
            Arrays.asList(PortColor.WHITE, PortColor.ORANGE, PortColor.RED, PortColor.BLACK, PortColor.NONE),
            order,
            "評価順は色の宣言順。無色は最後");
    }

    @Test
    @DisplayName("無色の群は必ず最後")
    public void test無色の群は最後() {
        List<IModularPort> inputs = ports(
            StubPort.item("shared", PortColor.NONE),
            StubPort.item("white", PortColor.WHITE));

        List<PortColorGrouping.Group> groups = PortColorGrouping.group(inputs, ports());

        assertSame(
            PortColor.NONE,
            groups.get(groups.size() - 1)
                .getColor(),
            "無色の群は全ポートを含むのでほぼ何でもマッチする。先に評価すると色分けが無意味になる");
    }

    @Test
    @DisplayName("群の中の順序は元のリスト順を保つ")
    public void test群の中の順序を保つ() {
        List<IModularPort> inputs = ports(
            StubPort.item("first", PortColor.NONE),
            StubPort.item("second", PortColor.RED),
            StubPort.item("third", PortColor.NONE),
            StubPort.item("fourth", PortColor.RED));

        List<PortColorGrouping.Group> groups = PortColorGrouping.group(inputs, ports());

        assertEquals(
            names("second", "fourth", "first", "third"),
            names(
                groups.get(0)
                    .getInputs()),
            "色つきが先、次に無色。それぞれの中では元の順序を保つ。順序が揺れるとレシピの選択が非決定になる");
    }

    // ========== 入力と出力の対応 ==========

    @Test
    @DisplayName("入力と出力が同じ色で対応する")
    public void test入力と出力が対応する() {
        List<IModularPort> inputs = ports(
            StubPort.item("red_in", PortColor.RED),
            StubPort.item("blue_in", PortColor.BLUE));
        List<IModularPort> outputs = ports(
            StubPort.item("red_out", PortColor.RED),
            StubPort.item("blue_out", PortColor.BLUE));

        List<PortColorGrouping.Group> groups = PortColorGrouping.group(inputs, outputs);

        for (PortColorGrouping.Group group : groups) {
            if (!group.getColor()
                .isColored()) continue;
            String expected = group.getColor() == PortColor.RED ? "red" : "blue";
            assertEquals(names(expected + "_in"), names(group.getInputs()));
            assertEquals(names(expected + "_out"), names(group.getOutputs()));
        }
    }

    @Test
    @DisplayName("出力側にだけある色も群になる")
    public void test出力側だけの色も群になる() {
        List<IModularPort> inputs = ports(StubPort.item("shared_in", PortColor.NONE));
        List<IModularPort> outputs = ports(StubPort.item("red_out", PortColor.RED));

        List<PortColorGrouping.Group> groups = PortColorGrouping.group(inputs, outputs);

        assertEquals(2, groups.size(), "赤の出力タンクにしか入らないレシピは、赤の群でなければ通せない");
        assertSame(
            PortColor.RED,
            groups.get(0)
                .getColor());
        assertEquals(
            names("shared_in"),
            names(
                groups.get(0)
                    .getInputs()));
        assertEquals(
            names("red_out"),
            names(
                groups.get(0)
                    .getOutputs()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PortColor.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("どの色でも群になる")
    public void test全色が群になる(PortColor color) {
        List<IModularPort> inputs = ports(StubPort.item("painted", color), StubPort.item("shared", PortColor.NONE));

        List<PortColorGrouping.Group> groups = PortColorGrouping.group(inputs, ports());

        assertEquals(2, groups.size(), () -> color + " の群が作られていない");
        assertSame(
            color,
            groups.get(0)
                .getColor());
    }

    // ========== 補助 ==========

    private static List<IModularPort> ports(StubPort... ports) {
        return new ArrayList<>(Arrays.asList(ports));
    }

    private static List<String> names(String... names) {
        return Arrays.asList(names);
    }

    private static List<String> names(List<IModularPort> ports) {
        List<String> result = new ArrayList<>();
        for (IModularPort port : ports) {
            result.add(port.toString());
        }
        return result;
    }
}
