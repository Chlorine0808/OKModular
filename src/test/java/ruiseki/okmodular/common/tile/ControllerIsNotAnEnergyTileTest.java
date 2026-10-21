package ruiseki.okmodular.common.tile;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The controller draws energy through its energy ports only. When it inherited the port's energy
 * base it also carried an unused buffer, registered with IC2 as a sink and looked like an RF
 * receiver to pipes and to other machines' external energy proxies.
 */
@DisplayName("コントローラはエネルギーの受け手ではない")
public class ControllerIsNotAnEnergyTileTest {

    private static final String CONTROLLER = "ruiseki.okmodular.common.tile.TEMachineController";

    @ParameterizedTest
    @ValueSource(
        strings = { "ic2.api.energy.tile.IEnergySink", "ic2.api.energy.tile.IEnergySource",
            "cofh.api.energy.IEnergyReceiver", "cofh.api.energy.IEnergyProvider",
            "ruiseki.okmodular.core.energy.IOKEnergyTile",
            "ruiseki.okmodular.common.tile.energy.AbstractEnergyIOPortTE" })
    @DisplayName("エネルギーの型を継承しない")
    public void testHasNoEnergyType(String type) throws Exception {
        Class<?> controller = Class.forName(CONTROLLER, false, getClass().getClassLoader());
        assertFalse(supertypesOf(controller).contains(type), "TEMachineController still inherits " + type);
    }

    private static Set<String> supertypesOf(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        collect(type, names);
        return names;
    }

    private static void collect(Class<?> type, Set<String> names) {
        if (type == null || !names.add(type.getName())) return;
        collect(type.getSuperclass(), names);
        for (Class<?> iface : type.getInterfaces()) {
            collect(iface, names);
        }
    }
}
