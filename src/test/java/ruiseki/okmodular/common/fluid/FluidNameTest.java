package ruiseki.okmodular.common.fluid;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.api.modular.IPortType;

/**
 * fluid の名前。
 *
 * ============================================
 * なぜこのテストがあるか
 * ============================================
 *
 * fluid 名は Forge 全体で共有され、同じ名前は同じ fluid になる。`steam` や `oil` のような
 * 一般名は、他の mod の fluid と混ぜて使えるよう、わざと共有している（release_freeze F-8）。
 *
 * `mana` だけは共有を避けた。Thermal Foundation の液体マナと同じ名前で、しかも
 * この mod の資源種 `mana`（式の `mana` 変数、レシピの `"mana": 100`）とも同じ綴りになる。
 * `fluid("mana")` と `mana` が別物だと、名前からは読み取れない。
 *
 * 資源種と同じ名前の fluid を足すと、同じ紛らわしさがまた生まれるので、ここで止める。
 */
@DisplayName("fluid の名前")
public class FluidNameTest {

    @Test
    @DisplayName("資源種と同じ名前の fluid は無い")
    public void test資源種と重ならない() {
        List<String> clashes = new ArrayList<>();
        for (EnumFluidMaterial material : EnumFluidMaterial.values()) {
            for (IPortType.Type kind : IPortType.Type.values()) {
                if (kind.name()
                    .toLowerCase(Locale.ROOT)
                    .equals(material.getName())) {
                    clashes.add(material.getName());
                }
            }
        }

        assertTrue(clashes.isEmpty(), clashes + " は資源種と同じ名前。fluid(\"…\") と資源の変数が同じ綴りになる");
    }

    @Test
    @DisplayName("液体マナは magical_liquid という名前")
    public void test液体マナの名前() {
        assertTrue(
            EnumFluidMaterial.MAGICAL_LIQUID.getName()
                .equals("magical_liquid"));
    }
}
