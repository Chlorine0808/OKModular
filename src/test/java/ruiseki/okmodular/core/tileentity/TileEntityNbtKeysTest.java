package ruiseki.okmodular.core.tileentity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ruiseki.okmodular.core.persist.nbt.NBTPersist;

/**
 * Pins the @NBTPersist keys each concrete tile entity writes, walking the class chain the same
 * way NBTProviderComponent does. Moving a field to another layer keeps its key; renaming it or
 * dropping a layer that owns it does not, and an old save silently loses that value.
 * A key that disappears on purpose should be removed here in the same commit.
 */
@DisplayName("TileEntity の NBT キー")
public class TileEntityNbtKeysTest {

    private static final String TILE = "ruiseki.okmodular.common.tile.";

    /** Keys every tile entity writes, from TileEntityOK and AbstractTE. Listed once, omitted per row. */
    private static final List<String> COMMON = Arrays.asList(
        "assignedIndex:int",
        "forward:ForgeDirection",
        "portColor:PortColor",
        "redstoneLevel:int",
        "redstoneMode:RedstoneMode",
        "redstonePowered:boolean",
        "rotatable:Boolean",
        "up:ForgeDirection");

    private static Map<String, String> expected() {
        Map<String, String> map = new LinkedHashMap<>();
        // spotless:off
        map.put("TEMachineController", "activeRecipeColor:PortColor, currentRecipeStartTick:long, isFormed:boolean, player:GameProfile, timeContinuousCount:long, timePlacedCount:long");
        map.put("energy.input.TEEnergyInputPort", "energyMode:EnergyMode, energyStorage:EnergyStorage, sides:EnumIO[], tier:int, useIC2Compat:boolean");
        map.put("energy.output.TEEnergyOutputPort", "energyMode:EnergyMode, energyStorage:EnergyStorage, sides:EnumIO[], tier:int, useIC2Compat:boolean");
        map.put("essentia.input.TEEssentiaInputPort", "maxCapacityPerAspect:int, sides:EnumIO[], tier:int");
        map.put("essentia.input.TEEssentiaInputPortME", "maxCapacityPerAspect:int, sides:EnumIO[], tier:int");
        map.put("essentia.output.TEEssentiaOutputPort", "maxCapacityPerAspect:int, sides:EnumIO[], tier:int");
        map.put("fluid.input.TEFluidInputPort", "sides:EnumIO[], tank:SmartTank, tier:int");
        map.put("fluid.output.TEFluidOutputPort", "sides:EnumIO[], tank:SmartTank, tier:int");
        map.put("fluid.output.TEFluidOutputPortME", "sides:EnumIO[], tank:SmartTank");
        map.put("gas.input.TEGasInputPort", "sides:EnumIO[], tank:SmartGasTank, tier:int");
        map.put("gas.output.TEGasOutputPort", "sides:EnumIO[], tank:SmartGasTank, tier:int");
        map.put("item.input.TEItemInputPort", "item_inv:ItemStackHandlerBase, sides:EnumIO[], tier:int");
        map.put("item.output.TEItemOutputPort", "item_inv:ItemStackHandlerBase, sides:EnumIO[], tier:int");
        map.put("item.output.TEItemOutputPortME", "item_inv:ItemStackHandlerBase, sides:EnumIO[], tier:int");
        map.put("mana.input.TEManaInputPort", "manaStorage:ManaStorage, tier:int");
        map.put("mana.output.TEManaOutputPort", "manaStorage:ManaStorage, tier:int");
        map.put("vis.input.TEVisInputPort", "maxVisPerAspect:int, sides:EnumIO[], tier:int");
        map.put("vis.output.TEVisOutputPort", "maxVisPerAspect:int, sides:EnumIO[], tier:int");
        // spotless:on
        return map;
    }

    @Test
    @DisplayName("具象 TE ごとの @NBTPersist キーが変わらない")
    public void testKeysArePinned() throws Exception {
        Map<String, String> expected = expected();
        Map<String, String> actual = new LinkedHashMap<>();
        for (String name : expected.keySet()) {
            actual.put(name, keysOf(Class.forName(TILE + name, false, getClass().getClassLoader())));
        }
        assertEquals(render(expected), render(actual));
    }

    private static String keysOf(Class<?> leaf) {
        TreeSet<String> keys = new TreeSet<>();
        for (Class<?> c = leaf; c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                NBTPersist persist = field.getAnnotation(NBTPersist.class);
                if (persist == null) continue;
                String key = persist.value()
                    .isEmpty() ? field.getName() : persist.value();
                keys.add(
                    key + ":"
                        + field.getType()
                            .getSimpleName());
            }
        }
        if (!keys.containsAll(COMMON)) return "missing common keys: " + String.join(", ", keys);
        keys.removeAll(COMMON);
        return String.join(", ", keys);
    }

    private static String render(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        map.forEach(
            (k, v) -> sb.append(k)
                .append(" = ")
                .append(v)
                .append('\n'));
        return sb.toString();
    }
}
