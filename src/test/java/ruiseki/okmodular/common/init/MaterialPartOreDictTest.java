package ruiseki.okmodular.common.init;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import ruiseki.okmodular.testsupport.JavaSource;

/**
 * Every material part item must also put its stacks in the ore dictionary.
 *
 * The bundled example recipes take parts by ore name ({@code plateIron}, {@code gearSteel}),
 * and other mods' recipes do the same. A part registered as an item but not as an ore name is
 * craftable nowhere except by exact item ID. GEAR was commented out of MachineryItems when the
 * module was imported, even though textures for every material shipped; the example Metal Former
 * needs it back.
 */
class MaterialPartOreDictTest {

    private static final Pattern PART = Pattern.compile("new ItemMaterialPart\\(\"(\\w+)\"\\)");

    private static List<String> partTypes() {
        String items = JavaSource
            .stripComments(JavaSource.read(JavaSource.MAIN.resolve("common/init/MachineryItems.java")));
        List<String> parts = new ArrayList<>();
        Matcher m = PART.matcher(items);
        while (m.find()) parts.add(m.group(1));
        return parts;
    }

    @Test
    void gearIsAPartItem() {
        assertTrue(partTypes().contains("gear"), "GEAR must be registered, not commented out");
    }

    @Test
    void everyPartItemRegistersItsOreDictPrefix() {
        String oreDict = JavaSource
            .stripComments(JavaSource.read(JavaSource.MAIN.resolve("common/init/MachineryOreDict.java")));
        List<String> parts = partTypes();
        assertFalse(parts.isEmpty());
        for (String part : parts) {
            assertTrue(oreDict.contains("registerOre(\"" + part + "\""), part + " has no ore dictionary registration");
        }
    }
}
