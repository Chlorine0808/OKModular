package ruiseki.okmodular.common.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * The crafting-table recipes that let survival reach the first machine.
 *
 * Every machine part is made in the Assembler, but the Assembler is itself a machine, so the
 * blocks it is built from have to come from a crafting table. Nothing registered a crafting
 * recipe before this, so a survival world could not form a single machine. The set here is
 * exactly what the bundled Assembler structure needs (controller, plain casing, item input and
 * output, energy input) plus the wrench its recipes use as a catalyst.
 */
class MachineryRecipesTest {

    private static String outputKey(MachineryRecipes.Shape shape) {
        return shape.output + ":" + shape.meta;
    }

    @Test
    void coversTheAssemblerBootstrap() {
        Set<String> outputs = MachineryRecipes.SHAPES.stream()
            .map(MachineryRecipesTest::outputKey)
            .collect(Collectors.toSet());
        for (String needed : Arrays.asList(
            "okmodular:modular_machine_controller:0",
            "okmodular:casing_plain:0",
            "okmodular:modular_item_input:0",
            "okmodular:modular_item_output:0",
            "okmodular:modular_energy_input:0",
            "okmodular:wrench:0")) {
            assertTrue(outputs.contains(needed), "no crafting recipe makes " + needed);
        }
    }

    @Test
    void everyShapeIsWellFormed() {
        for (MachineryRecipes.Shape shape : MachineryRecipes.SHAPES) {
            String name = outputKey(shape);
            assertEquals(3, shape.rows.length, name);
            Set<Character> used = new HashSet<>();
            for (String row : shape.rows) {
                assertEquals(3, row.length(), name + " row '" + row + "'");
                for (char c : row.toCharArray()) if (c != ' ') used.add(c);
            }
            assertEquals(used, shape.key.keySet(), name + ": every symbol needs a material and vice versa");
            assertTrue(shape.count > 0, name);
        }
    }

    @Test
    void inputAndOutputPortsDiffer() {
        List<MachineryRecipes.Shape> ports = MachineryRecipes.SHAPES.stream()
            .filter(s -> s.output.startsWith("okmodular:modular_item_"))
            .collect(Collectors.toList());
        assertEquals(2, ports.size());
        assertFalse(
            Arrays.equals(ports.get(0).rows, ports.get(1).rows) && ports.get(0).key.equals(ports.get(1).key),
            "the item input and output ports share a recipe, so one of them can never be crafted");
    }

    @Test
    void outputsAreDistinctRecipes() {
        Set<String> layouts = new HashSet<>();
        for (MachineryRecipes.Shape shape : MachineryRecipes.SHAPES) {
            String layout = String.join("/", shape.rows) + shape.key;
            assertTrue(layouts.add(layout), outputKey(shape) + " collides with another recipe");
        }
        assertNotEquals(0, layouts.size());
    }

    @Test
    void initRunsInModuleInit() {
        String init = JavaSource.methodBody(
            JavaSource.stripComments(JavaSource.read(JavaSource.MAIN.resolve("MachineryModule.java"))),
            "public void init(FMLInitializationEvent event)");
        assertTrue(init.contains("MachineryRecipes.init()"), "MachineryModule.init never registers the recipes");
    }
}
