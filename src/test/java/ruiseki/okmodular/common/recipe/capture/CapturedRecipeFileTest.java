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

    @Test
    public void groupThatEscapesTheFolderIsRefused() {
        for (String bad : new String[] { "../evil", "a/b", "a\\b", "..", ".", "" }) {
            assertFalse(CapturedRecipeFile.isValidGroup(bad), bad);
            assertThrows(
                IllegalArgumentException.class,
                () -> CapturedRecipeFile.append(tmp.toFile(), bad, json("{'duration':1}")));
        }
        assertFalse(CapturedRecipeFile.isValidGroup(null));
        assertTrue(CapturedRecipeFile.isValidGroup("my_machine-2.x"));
        assertEquals(
            0,
            tmp.toFile()
                .list().length,
            "nothing created");
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
