package ruiseki.okmodular.common.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChunkCoordinates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import ruiseki.okmodular.api.enums.EnumIO;
import ruiseki.okmodular.api.modular.IPortType;

/**
 * 外部ポート設定の NBT 読み書きの検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `TEMachineController` の `externalPortConfigs` には **読み手しか無かった**。
 * 書き手が一度も存在しなかったので、レンチでプレイヤーが設定した外部ポート設定は
 * セーブに残らない（構造 JSON の `fixedExternalPorts` から毎回再構築されるものだけが
 * 動いて見えていた）。
 *
 * 書き手を足すのがこの作業の本体だが、**読み手を先に切り出した**。理由が 2 つある。
 *
 * 1. `TEMachineController` は `MockWorld` がコンストラクタで NPE するのでユニットテストで
 * 組めない（`MachineTierRecognitionTest` が `@Disabled` なのはこれ）。
 * `NBTTagCompound` は World 非依存なので、**codec に切り出せばここでテストできる**
 * 2. 改修前の書式を読めることを先に凍結しておかないと、書き手を足したときに
 * 「旧セーブが読めなくなった」を検出できない
 *
 * ============================================
 * ディスク上の書式が 3 つある
 * ============================================
 *
 * <pre>
 * 現行:   {"types": [{"type": "FLUID", "io": "OUTPUT"}]}  name() で書く
 * 改修前: {"types": [{"type": 1b,      "io": 2b}]}        enum の **ordinal を byte で** 書いていた
 * 最古:   {"io": 1b}                                      types リストが無く、Type.ITEM 固定
 * </pre>
 *
 * 書けるのは現行だけで、読めるのは 3 つすべて。
 *
 * ordinal がディスク上の表現だったことが「`IPortType.Type` に要素を挿入できない」
 * 制約の正体だった。**この制約を消すのが目的**なので、ordinal → enum の対応は
 * `Type.values()[i]` ではなく **codec が持つ凍結テーブル**でなければならない。
 * `values()` を引くと、要素を挿した瞬間にテーブルもずれて旧セーブが化ける。
 *
 * 下の 2 本の `@CsvSource` が**その凍結テーブルそのもの**。リテラルで書いてあるのは
 * ディスクに書かれた実際の数値だからで、`ordinal()` を呼んで組み立ててはいけない
 * （両辺が一緒にずれて、検出したいことが検出できなくなる）。
 *
 * ============================================
 */
@DisplayName("外部ポート設定の NBT 読み書き")
public class ExternalPortConfigCodecTest {

    // ========== 旧書式の凍結 ==========

    @ParameterizedTest(name = "type={0} -> {1}")
    @CsvSource({ "0, ITEM", "1, FLUID", "2, ENERGY", "3, MANA", "4, GAS", "5, ESSENTIA", "6, VIS", "7, BLOCK",
        "8, NONE" })
    @DisplayName("旧書式の type ordinal が同じ Type に戻る")
    public void test旧書式のtypeOrdinal(int ordinal, String expectedType) {
        Map<IPortType.Type, EnumIO> types = readSingle(root(port(1, 2, 3, ordinalType(ordinal, 1))));

        assertEquals(
            EnumIO.INPUT,
            types.get(IPortType.Type.valueOf(expectedType)),
            () -> "ディスクの type=" + ordinal + " は " + expectedType + " として読まれなければならない");
    }

    @ParameterizedTest(name = "io={0} -> {1}")
    @CsvSource({ "0, NONE", "1, INPUT", "2, OUTPUT", "3, BOTH" })
    @DisplayName("旧書式の io ordinal が同じ EnumIO に戻る")
    public void test旧書式のioOrdinal(int ordinal, String expectedIo) {
        Map<IPortType.Type, EnumIO> types = readSingle(root(port(1, 2, 3, ordinalType(1, ordinal))));

        assertEquals(
            EnumIO.valueOf(expectedIo),
            types.get(IPortType.Type.FLUID),
            () -> "ディスクの io=" + ordinal + " は " + expectedIo + " として読まれなければならない");
    }

    @Test
    @DisplayName("types が無く io だけの更に古い形は Type.ITEM になる")
    public void testLegacyIoキーのみ() {
        NBTTagCompound port = port(4, 5, 6);
        port.setByte("io", (byte) 3);

        Map<IPortType.Type, EnumIO> types = readSingle(root(port));

        assertEquals(1, types.size(), "1 件だけ入るべき");
        assertEquals(EnumIO.BOTH, types.get(IPortType.Type.ITEM), "Type.ITEM に割り当てられるべき");
    }

    // ========== 新書式 ==========

    @Test
    @DisplayName("name() で書かれた形が読める")
    public void test新書式が読める() {
        Map<IPortType.Type, EnumIO> types = readSingle(root(port(1, 2, 3, namedType("GAS", "OUTPUT"))));

        assertEquals(EnumIO.OUTPUT, types.get(IPortType.Type.GAS));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "ITEM", "FLUID", "ENERGY", "MANA", "GAS", "ESSENTIA", "VIS", "BLOCK", "NONE" })
    @DisplayName("すべての Type が name() で往復できる")
    public void test全Typeが新書式で読める(String name) {
        Map<IPortType.Type, EnumIO> types = readSingle(root(port(1, 2, 3, namedType(name, "BOTH"))));

        assertEquals(EnumIO.BOTH, types.get(IPortType.Type.valueOf(name)), () -> name + " が読めない");
    }

    // ========== 壊れた入力を捨てる ==========

    /**
     * 未知の名前で throw してはいけない。
     *
     * `@NBTPersist` の汎用 enum ハンドラは `Enum.valueOf` を使うので、**未知の名前で
     * IllegalArgumentException を投げる**（`NBTClassType:743`）。TE の読み込み中に
     * これが飛ぶとチャンクごと失われる。codec は捨てる側に倒す。
     */
    @ParameterizedTest(name = "type=\"{0}\"")
    @ValueSource(strings = { "REDSTONE", "item", "", "  ", "FLUID " })
    @DisplayName("未知の type 名は捨てる（throw しない）")
    public void test未知のtype名を捨てる(String unknown) {
        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> result = read(
            root(port(1, 2, 3, namedType(unknown, "INPUT"))));

        assertTrue(result.isEmpty(), () -> "'" + unknown + "' は捨てられるべきだが " + result + " が残った");
    }

    @ParameterizedTest(name = "io=\"{0}\"")
    @ValueSource(strings = { "SIDEWAYS", "input", "" })
    @DisplayName("未知の io 名は捨てる")
    public void test未知のio名を捨てる(String unknown) {
        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> result = read(
            root(port(1, 2, 3, namedType("FLUID", unknown))));

        assertTrue(result.isEmpty(), () -> "'" + unknown + "' は捨てられるべき");
    }

    /**
     * 範囲外の ordinal を捨てる。
     *
     * これは仮定の話ではない。**要素を挿入できるようにするのが B6 の目的**なので、
     * 新しい Type を足した後のセーブを古いバージョンで開く経路が生まれる。
     * そのとき ordinal は凍結テーブルの外を指す。
     */
    @ParameterizedTest(name = "type={0}")
    @ValueSource(ints = { 9, 10, 127, 255 })
    @DisplayName("凍結テーブルの外の type ordinal は捨てる")
    public void test範囲外のtypeOrdinalを捨てる(int ordinal) {
        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> result = read(root(port(1, 2, 3, ordinalType(ordinal, 1))));

        assertTrue(result.isEmpty(), () -> "type=" + ordinal + " は捨てられるべき");
    }

    @ParameterizedTest(name = "io={0}")
    @ValueSource(ints = { 4, 99, 255 })
    @DisplayName("範囲外の io ordinal は捨てる")
    public void test範囲外のioOrdinalを捨てる(int ordinal) {
        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> result = read(root(port(1, 2, 3, ordinalType(1, ordinal))));

        assertTrue(result.isEmpty(), () -> "io=" + ordinal + " は捨てられるべき");
    }

    @Test
    @DisplayName("読めた type が 1 つも無い座標は Map に入らない")
    public void test空の座標は入らない() {
        NBTTagCompound root = root(port(1, 2, 3), port(4, 5, 6, namedType("ITEM", "INPUT")));

        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> result = read(root);

        assertEquals(1, result.size(), "types も io も無い (1,2,3) は入らないべき");
        assertTrue(result.containsKey(new ChunkCoordinates(4, 5, 6)));
    }

    // ========== Map の扱い ==========

    @Test
    @DisplayName("キーが無ければ空になり、事前の内容は消える")
    public void testキーが無ければクリアされる() {
        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> target = new HashMap<>();
        Map<IPortType.Type, EnumIO> stale = new HashMap<>();
        stale.put(IPortType.Type.ITEM, EnumIO.BOTH);
        target.put(new ChunkCoordinates(9, 9, 9), stale);

        ExternalPortConfigCodec.read(new NBTTagCompound(), target);

        assertTrue(target.isEmpty(), "読み込みは既存の内容を置き換える。残ると形成解除が効かなくなる");
    }

    @Test
    @DisplayName("1 つの座標が複数の Type を持てる")
    public void test複数Type() {
        Map<IPortType.Type, EnumIO> types = readSingle(
            root(port(1, 2, 3, namedType("ITEM", "INPUT"), namedType("FLUID", "OUTPUT"), ordinalType(2, 3))));

        assertEquals(3, types.size());
        assertEquals(EnumIO.INPUT, types.get(IPortType.Type.ITEM));
        assertEquals(EnumIO.OUTPUT, types.get(IPortType.Type.FLUID));
        assertEquals(EnumIO.BOTH, types.get(IPortType.Type.ENERGY), "新旧の書式が同じリストに混在してもよい");
    }

    @Test
    @DisplayName("複数の座標が読める")
    public void test複数座標() {
        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> result = read(
            root(
                port(1, 2, 3, namedType("ITEM", "INPUT")),
                port(-4, 5, -6, namedType("FLUID", "OUTPUT")),
                port(0, 0, 0, namedType("ENERGY", "BOTH"))));

        assertEquals(3, result.size());
        assertEquals(
            EnumIO.INPUT,
            result.get(new ChunkCoordinates(1, 2, 3))
                .get(IPortType.Type.ITEM));
        assertEquals(
            EnumIO.OUTPUT,
            result.get(new ChunkCoordinates(-4, 5, -6))
                .get(IPortType.Type.FLUID));
        assertEquals(
            EnumIO.BOTH,
            result.get(new ChunkCoordinates(0, 0, 0))
                .get(IPortType.Type.ENERGY));
    }

    @Test
    @DisplayName("リストではないキーを無視する")
    public void test型違いのキーを無視する() {
        NBTTagCompound root = new NBTTagCompound();
        root.setString(ExternalPortConfigCodec.KEY, "not a list");

        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> result = read(root);

        assertTrue(result.isEmpty(), "型が違うキーで落ちてはいけない");
    }

    @Test
    @DisplayName("EnumIO.NONE も読める（BlockResolver 側で捨てられる値）")
    public void testNoneも読める() {
        Map<IPortType.Type, EnumIO> types = readSingle(root(port(1, 2, 3, namedType("ITEM", "NONE"))));

        assertEquals(EnumIO.NONE, types.get(IPortType.Type.ITEM), "NONE は「読めなかった」ではなく「無効に設定されている」");
        assertFalse(types.isEmpty());
    }

    // ========== 書き出し ==========

    /**
     * 全組み合わせの往復。
     *
     * 生きた列挙（`Type.values()` × `EnumIO.values()`）を回している。凍結リストではない
     * のは意図的で、**新しい Type を足したときに自動的に検査対象に入る**のが狙い。
     * 「消えたこと」を検出したい旧書式の凍結（上の `@CsvSource`）とは役割が逆。
     */
    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("すべてのTypeとIoの組")
    @DisplayName("すべての Type × EnumIO が往復する")
    public void test全組み合わせが往復する(IPortType.Type type, EnumIO io) {
        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> result = roundTrip(
            new Configs().at(1, 2, 3, type, io)
                .build());

        assertEquals(1, result.size(), () -> type + "/" + io + " の座標が失われた");
        assertEquals(
            io,
            result.get(new ChunkCoordinates(1, 2, 3))
                .get(type),
            () -> type + " / " + io + " が往復しない");
    }

    private static Stream<Arguments> すべてのTypeとIoの組() {
        return Stream.of(IPortType.Type.values())
            .flatMap(
                type -> Stream.of(EnumIO.values())
                    .map(io -> Arguments.of(type, io)));
    }

    /**
     * 名前で書くこと。
     *
     * ここが B6 の本題。ordinal で書くのをやめたので、`IPortType.Type` に要素を
     * **挿入**できるようになる。数値で書いていたら要素の挿入が既存セーブを化かす。
     */
    @Test
    @DisplayName("type と io は名前で書かれる")
    public void test名前で書かれる() {
        NBTTagCompound nbt = write(
            new Configs().at(1, 2, 3, IPortType.Type.FLUID, EnumIO.OUTPUT)
                .build());

        NBTTagCompound typeTag = firstTypeTag(nbt);
        assertEquals("FLUID", typeTag.getString("type"), "type は name() で書かれるべき");
        assertEquals("OUTPUT", typeTag.getString("io"), "io は name() で書かれるべき");
    }

    @Test
    @DisplayName("数値としては書かれない")
    public void test数値では書かれない() {
        NBTTagCompound nbt = write(
            new Configs().at(1, 2, 3, IPortType.Type.FLUID, EnumIO.OUTPUT)
                .build());

        NBTTagCompound typeTag = firstTypeTag(nbt);
        assertFalse(typeTag.hasKey("type", 99), "type が数値タグとして残っていると ordinal 依存が消えていない");
        assertFalse(typeTag.hasKey("io", 99), "io が数値タグとして残っていると ordinal 依存が消えていない");
    }

    @Test
    @DisplayName("空の設定はキーを作らない")
    public void test空の設定はキーを作らない() {
        NBTTagCompound nbt = write(new HashMap<>());

        assertFalse(nbt.hasKey(ExternalPortConfigCodec.KEY), "設定が無いのにキーを作ると、無意味なチャンク保存が増える");
    }

    @Test
    @DisplayName("type マップが空の座標は書かない")
    public void test空の座標は書かない() {
        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> configs = new HashMap<>();
        configs.put(new ChunkCoordinates(1, 2, 3), new HashMap<>());

        NBTTagCompound nbt = write(configs);

        assertFalse(nbt.hasKey(ExternalPortConfigCodec.KEY), "読めば消える座標を書く意味は無い");
    }

    @Test
    @DisplayName("負の座標も往復する")
    public void test負の座標も往復する() {
        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> result = roundTrip(
            new Configs().at(-1000, 5, -2000, IPortType.Type.ITEM, EnumIO.INPUT)
                .build());

        assertEquals(
            EnumIO.INPUT,
            result.get(new ChunkCoordinates(-1000, 5, -2000))
                .get(IPortType.Type.ITEM));
    }

    @Test
    @DisplayName("1 座標に複数 Type がある設定も往復する")
    public void test複数Typeが往復する() {
        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> result = roundTrip(
            new Configs().at(1, 2, 3, IPortType.Type.ITEM, EnumIO.INPUT)
                .at(1, 2, 3, IPortType.Type.FLUID, EnumIO.OUTPUT)
                .at(1, 2, 3, IPortType.Type.ENERGY, EnumIO.BOTH)
                .at(9, 8, 7, IPortType.Type.GAS, EnumIO.INPUT)
                .build());

        assertEquals(2, result.size());
        assertEquals(
            3,
            result.get(new ChunkCoordinates(1, 2, 3))
                .size());
        assertEquals(
            EnumIO.INPUT,
            result.get(new ChunkCoordinates(9, 8, 7))
                .get(IPortType.Type.GAS));
    }

    /**
     * 同じ内容なら同じ NBT になること。
     *
     * `HashMap` の反復順に任せると、内容が変わっていないのに NBT が変わり、
     * チャンクが無用に dirty になる。書き出しは座標順・`Type.values()` 順に固定する。
     */
    @Test
    @DisplayName("投入順が違っても同じ NBT になる")
    public void test書き出しは決定的() {
        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> forward = new Configs()
            .at(1, 2, 3, IPortType.Type.ITEM, EnumIO.INPUT)
            .at(1, 2, 3, IPortType.Type.VIS, EnumIO.OUTPUT)
            .at(0, 0, 0, IPortType.Type.GAS, EnumIO.BOTH)
            .build();

        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> backward = new Configs()
            .at(0, 0, 0, IPortType.Type.GAS, EnumIO.BOTH)
            .at(1, 2, 3, IPortType.Type.VIS, EnumIO.OUTPUT)
            .at(1, 2, 3, IPortType.Type.ITEM, EnumIO.INPUT)
            .build();

        assertEquals(written(forward), written(backward), "投入順で NBT が変わると、内容が同じでもチャンクが dirty になる");
    }

    @Test
    @DisplayName("書き出しは既存のキーを置き換える")
    public void test書き出しは既存キーを置き換える() {
        NBTTagCompound nbt = root(port(9, 9, 9, namedType("ITEM", "BOTH")));

        ExternalPortConfigCodec.write(
            nbt,
            new Configs().at(1, 2, 3, IPortType.Type.FLUID, EnumIO.INPUT)
                .build());

        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> result = read(nbt);
        assertEquals(1, result.size(), "古い座標が残ると、ポートを外しても設定が消えない");
        assertTrue(result.containsKey(new ChunkCoordinates(1, 2, 3)));
    }

    @Test
    @DisplayName("設定が空になったらキーを消す")
    public void test空になったらキーを消す() {
        NBTTagCompound nbt = root(port(9, 9, 9, namedType("ITEM", "BOTH")));

        ExternalPortConfigCodec.write(nbt, new HashMap<>());

        assertFalse(nbt.hasKey(ExternalPortConfigCodec.KEY), "全部外したのに残ると、設定が消せなくなる");
    }

    // ========== 補助 ==========

    /** 座標と Type を足していく設定ビルダ。投入順を保つので決定性のテストに使える。 */
    private static final class Configs {

        private final Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> map = new LinkedHashMap<>();

        Configs at(int x, int y, int z, IPortType.Type type, EnumIO io) {
            map.computeIfAbsent(new ChunkCoordinates(x, y, z), k -> new LinkedHashMap<>())
                .put(type, io);
            return this;
        }

        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> build() {
            return map;
        }
    }

    private static NBTTagCompound write(Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> configs) {
        NBTTagCompound nbt = new NBTTagCompound();
        ExternalPortConfigCodec.write(nbt, configs);
        return nbt;
    }

    private static Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> roundTrip(
        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> configs) {
        return read(write(configs));
    }

    /** 書き出した内容を読める文字列の列にする。順序も含めて比べられる。 */
    private static List<String> written(Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> configs) {
        List<String> lines = new ArrayList<>();
        NBTTagList ports = write(configs).getTagList(ExternalPortConfigCodec.KEY, 10);
        for (int i = 0; i < ports.tagCount(); i++) {
            NBTTagCompound port = ports.getCompoundTagAt(i);
            String pos = port.getInteger("x") + "," + port.getInteger("y") + "," + port.getInteger("z");
            NBTTagList types = port.getTagList("types", 10);
            for (int j = 0; j < types.tagCount(); j++) {
                NBTTagCompound type = types.getCompoundTagAt(j);
                lines.add(pos + " " + type.getString("type") + "=" + type.getString("io"));
            }
        }
        return lines;
    }

    private static NBTTagCompound firstTypeTag(NBTTagCompound nbt) {
        NBTTagList ports = nbt.getTagList(ExternalPortConfigCodec.KEY, 10);
        assertEquals(1, ports.tagCount(), "座標が 1 件だけ書かれているべき");
        NBTTagList types = ports.getCompoundTagAt(0)
            .getTagList("types", 10);
        assertEquals(1, types.tagCount(), "type が 1 件だけ書かれているべき");
        return types.getCompoundTagAt(0);
    }

    private static Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> read(NBTTagCompound nbt) {
        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> result = new HashMap<>();
        ExternalPortConfigCodec.read(nbt, result);
        return result;
    }

    /** 座標が 1 つだけ入っていることを確かめて、その type マップを返す。 */
    private static Map<IPortType.Type, EnumIO> readSingle(NBTTagCompound nbt) {
        Map<ChunkCoordinates, Map<IPortType.Type, EnumIO>> result = read(nbt);
        assertEquals(1, result.size(), () -> "座標が 1 つ読めるはずが " + result.size() + " 件: " + result);
        return result.values()
            .iterator()
            .next();
    }

    private static NBTTagCompound root(NBTTagCompound... ports) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound port : ports) {
            list.appendTag(port);
        }
        root.setTag(ExternalPortConfigCodec.KEY, list);
        return root;
    }

    private static NBTTagCompound port(int x, int y, int z, NBTTagCompound... types) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("x", x);
        tag.setInteger("y", y);
        tag.setInteger("z", z);
        if (types.length > 0) {
            NBTTagList list = new NBTTagList();
            for (NBTTagCompound type : types) {
                list.appendTag(type);
            }
            tag.setTag("types", list);
        }
        return tag;
    }

    /** 改修前の書式。**リテラルの数値を渡すこと**（`ordinal()` を呼ぶと凍結の意味が消える）。 */
    private static NBTTagCompound ordinalType(int typeOrdinal, int ioOrdinal) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("type", (byte) typeOrdinal);
        tag.setByte("io", (byte) ioOrdinal);
        return tag;
    }

    private static NBTTagCompound namedType(String type, String io) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("type", type);
        tag.setString("io", io);
        return tag;
    }
}
