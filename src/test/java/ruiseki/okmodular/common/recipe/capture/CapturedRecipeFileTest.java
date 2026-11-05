package ruiseki.okmodular.common.recipe.capture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Captured recipes go to their own file under captured/, so a hand-written file is never
 * rewritten, and a file that cannot be read is left exactly as it was.
 */
public class CapturedRecipeFileTest {

    @TempDir
    Path tmp;

    private static JsonObject json(String text) {
        return new JsonParser().parse(text.replace('\'', '"'))
            .getAsJsonObject();
    }

    private JsonObject readCaptured(String group) throws IOException {
        File file = CapturedRecipeFile.fileFor(tmp.toFile(), group);
        return new JsonParser().parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
            .getAsJsonObject();
    }

    @Test
    public void firstSaveCreatesTheFileWithTheGroupHeader() throws IOException {
        JsonObject recipe = json("{'duration':20,'inputs':[],'outputs':[]}");

        CapturedRecipeFile.append(tmp.toFile(), "crusher", recipe);

        assertTrue(
            tmp.resolve("captured")
                .resolve("crusher.json")
                .toFile()
                .isFile());
        assertEquals(
            json(
                "{'group':'crusher','recipes':[{'registryName':'captured_crusher_1','duration':20,'inputs':[],'outputs':[]}]}"),
            readCaptured("crusher"));
    }

    @Test
    public void laterSavesAppendAndKeepEarlierRecipes() throws IOException {
        CapturedRecipeFile.append(tmp.toFile(), "crusher", json("{'duration':1}"));
        CapturedRecipeFile.append(tmp.toFile(), "crusher", json("{'duration':2}"));

        assertEquals(
            json(
                "{'group':'crusher','recipes':[{'registryName':'captured_crusher_1','duration':1},"
                    + "{'registryName':'captured_crusher_2','duration':2}]}"),
            readCaptured("crusher"));
    }

    // The loader drops a recipe without a registryName, so every captured recipe gets one.
    @Test
    public void generatedNameSkipsANameAlreadyInTheFile() throws IOException {
        File file = CapturedRecipeFile.fileFor(tmp.toFile(), "crusher");
        file.getParentFile()
            .mkdirs();
        Files.write(
            file.toPath(),
            "{\"group\":\"crusher\",\"recipes\":[{\"registryName\":\"captured_crusher_2\"}]}"
                .getBytes(StandardCharsets.UTF_8));

        CapturedRecipeFile.append(tmp.toFile(), "crusher", json("{'duration':1}"));

        assertEquals(
            "captured_crusher_3",
            readCaptured("crusher").getAsJsonArray("recipes")
                .get(1)
                .getAsJsonObject()
                .get("registryName")
                .getAsString());
    }

    @Test
    public void givenNameIsKept() throws IOException {
        CapturedRecipeFile.append(tmp.toFile(), "crusher", json("{'registryName':'mine','duration':1}"));

        assertEquals(
            json("{'group':'crusher','recipes':[{'registryName':'mine','duration':1}]}"),
            readCaptured("crusher"));
    }

    @Test
    public void malformedFileIsLeftUntouched() throws IOException {
        for (String bad : new String[] { "{ broken", "{\"group\":\"x\"}" }) {
            File file = CapturedRecipeFile.fileFor(tmp.toFile(), "x");
            file.getParentFile()
                .mkdirs();
            byte[] bytes = bad.getBytes(StandardCharsets.UTF_8);
            Files.write(file.toPath(), bytes);

            assertThrows(IOException.class, () -> CapturedRecipeFile.append(tmp.toFile(), "x", json("{'duration':1}")));

            assertArrayEquals(bytes, Files.readAllBytes(file.toPath()), bad);
            assertEquals(
                1,
                file.getParentFile()
                    .list().length,
                "no temp file left behind");
        }
    }

    // The header keeps the real group so the machine still finds the recipe; only the file name is made safe.
    @Test
    public void groupThatCannotBeAFileNameIsSavedUnderASafeName() throws IOException {
        String[][] cases = { { "Ore Crusher", "Ore_Crusher" }, { "mod:group", "mod_group" }, { "../evil", ".._evil" },
            { "a\\b", "a_b" }, { "..", "_.." }, { ".", "_." }, { "con", "_con" }, { "NUL", "_NUL" },
            { "Aux.txt", "_Aux.txt" }, { "com1", "_com1" }, { "console", "console" }, { "com10", "com10" } };
        for (String[] c : cases) {
            CapturedRecipeFile.append(tmp.toFile(), c[0], json("{'duration':1}"));

            File file = tmp.resolve("captured")
                .resolve(c[1] + ".json")
                .toFile();
            assertTrue(file.isFile(), c[0]);
            JsonObject root = new JsonParser()
                .parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
                .getAsJsonObject();
            assertEquals(
                c[0],
                root.get("group")
                    .getAsString(),
                c[0]);
            assertEquals(
                "captured_" + c[1] + "_1",
                root.getAsJsonArray("recipes")
                    .get(0)
                    .getAsJsonObject()
                    .get("registryName")
                    .getAsString(),
                c[0]);
        }
        assertEquals(
            1,
            tmp.toFile()
                .list().length,
            "everything stays under captured/");
    }

    @Test
    public void blankGroupIsRefused() {
        for (String bad : new String[] { null, "", "  " }) {
            assertFalse(CapturedRecipeFile.isValidGroup(bad), String.valueOf(bad));
            assertThrows(
                IllegalArgumentException.class,
                () -> CapturedRecipeFile.append(tmp.toFile(), bad, json("{'duration':1}")));
        }
        assertEquals(
            0,
            tmp.toFile()
                .list().length,
            "nothing created");
    }

    // Two groups can share a safe file name, and a hand-edited header would move every recipe in it.
    @Test
    public void fileWhoseHeaderNamesAnotherGroupIsLeftUntouched() throws IOException {
        for (String header : new String[] { "\"group\":\"Ore_Crusher\",", "" }) {
            File file = CapturedRecipeFile.fileFor(tmp.toFile(), "Ore Crusher");
            file.getParentFile()
                .mkdirs();
            byte[] bytes = ("{" + header + "\"recipes\":[]}").getBytes(StandardCharsets.UTF_8);
            Files.write(file.toPath(), bytes);

            assertThrows(
                IOException.class,
                () -> CapturedRecipeFile.append(tmp.toFile(), "Ore Crusher", json("{'duration':1}")),
                header);

            assertArrayEquals(bytes, Files.readAllBytes(file.toPath()), header);
        }
    }

    @Test
    public void handWrittenFilesAreNotTouched() throws IOException {
        Path hand = tmp.resolve("crusher.json");
        byte[] bytes = "{ \"group\": \"crusher\", \"recipes\": [] }".getBytes(StandardCharsets.UTF_8);
        Files.write(hand, bytes);

        CapturedRecipeFile.append(tmp.toFile(), "crusher", json("{'duration':1}"));

        assertArrayEquals(bytes, Files.readAllBytes(hand));
    }
}
