package ruiseki.okmodular.api.modular;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * `IPortType` のうち、外に漏れている契約の検証。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * `IPortType.Type` の ordinal がディスクに書かれていたので、長らく
 * 「**要素を末尾以外に足せない**」制約があった。外部ポート設定を name() で
 * 書くようにしたので、その制約は消えた。
 *
 * ただし **`SUPPORTED_TYPES` の並びは今も凍結されている**。理由は enum ではなく
 * レンチにある。`ItemWrench` は選択中のポート種別を
 * `SelectedPortTypeIndex` = **`SUPPORTED_TYPES` の添字**としてアイテム NBT に
 * 保存する（`ItemWrench:243-262`）。並べ替えると、プレイヤーが持っている
 * レンチの選択が別の種別を指す。
 *
 * enum に要素を足しても添字は動かない（この配列は手書きで、enum とは独立）。
 * **危険なのは配列の並べ替えと、途中への挿入だけ。**
 *
 * ============================================
 */
@DisplayName("ポート種別の契約")
public class PortTypeContractTest {

    /**
     * レンチのアイテム NBT が指す添字の凍結。
     *
     * 手書きのリテラルであること自体が仕様。実行時に `Type.values()` から組み立てると、
     * 両辺が一緒に動くので並べ替えを検出できない。
     */
    @Test
    @DisplayName("SUPPORTED_TYPES の並びは凍結されている")
    public void testSupportedTypesの並びが凍結されている() {
        List<String> frozen = Arrays.asList("ITEM", "FLUID", "ENERGY", "MANA", "GAS", "ESSENTIA", "VIS");

        assertEquals(frozen, names(IPortType.SUPPORTED_TYPES), """
            SUPPORTED_TYPES の並びが変わった。
            レンチは選択中の種別を「この配列の添字」としてアイテム NBT に保存しているので、
            並べ替えるとプレイヤーの持っているレンチが別の種別を指す。
            末尾への追加なら、期待値にも末尾に足してよい。""");
    }

    /**
     * 外部ブロックに割り当てられない種別が混ざっていないこと。
     *
     * `BLOCK` はワールドのブロックそのものを指す種別で、`NONE` は種別の不在。
     * どちらもプロキシを作れないので、レンチの選択肢に出てはいけない。
     */
    @Test
    @DisplayName("SUPPORTED_TYPES に BLOCK と NONE は入らない")
    public void testSupportedTypesにBlockとNoneが入らない() {
        List<String> names = names(IPortType.SUPPORTED_TYPES);

        assertFalse(names.contains("BLOCK"), "BLOCK はワールドのブロックを指すのでプロキシを作れない");
        assertFalse(names.contains("NONE"), "NONE は種別の不在なので選択肢にならない");
    }

    @Test
    @DisplayName("SUPPORTED_TYPES に重複が無い")
    public void testSupportedTypesに重複が無い() {
        List<String> names = names(IPortType.SUPPORTED_TYPES);
        Set<String> unique = new HashSet<>(names);

        assertEquals(names.size(), unique.size(), () -> "重複があるとレンチが同じ種別を 2 回通る: " + names);
    }

    /**
     * 述語は ordinal を見ていないこと。
     *
     * `isStorable()` / `hasDirectionalStorage()` は B9 で資源種ごとの式名を生成する
     * 土台になった。ここが ordinal の範囲比較で書かれていると、要素を足した瞬間に
     * 静かに答えが変わる。名前で列挙して固定する。
     */
    @Test
    @DisplayName("isStorable は BLOCK と NONE だけを除く")
    public void testIsStorableの対象() {
        List<String> storable = new ArrayList<>();
        for (IPortType.Type type : IPortType.Type.values()) {
            if (type.isStorable()) storable.add(type.name());
        }

        assertEquals(Arrays.asList("ITEM", "FLUID", "ENERGY", "MANA", "GAS", "ESSENTIA", "VIS"), storable, """
            isStorable() の答えが変わった。
            この述語は資源プロパティの式名（fluid_f / gas_stored など）を生成するので、
            ここに種別が増えると式名も増える。増やすのが意図なら期待値を更新すること。
            注意: 信号強度のような「量ではあるが資源ではない」種別を足すときは、
            isStorable() を素通しにすると redstone のような既存の変数名と衝突する。""");
    }

    @Test
    @DisplayName("hasDirectionalStorage は入出力が別格納の 3 種だけ")
    public void testHasDirectionalStorageの対象() {
        List<String> directional = new ArrayList<>();
        for (IPortType.Type type : IPortType.Type.values()) {
            if (type.hasDirectionalStorage()) directional.add(type.name());
        }

        assertEquals(Arrays.asList("ITEM", "FLUID", "GAS"), directional, """
            hasDirectionalStorage() の答えが変わった。
            この述語は方向つきの式名（fluid_in / item_f_out など）の有無を決める。""");
    }

    private static List<String> names(IPortType.Type[] types) {
        List<String> names = new ArrayList<>();
        for (IPortType.Type type : types) {
            names.add(type.name());
        }
        return names;
    }
}
