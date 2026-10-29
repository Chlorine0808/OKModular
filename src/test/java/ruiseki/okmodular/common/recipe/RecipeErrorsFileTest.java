package ruiseki.okmodular.common.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Recipe errors went to OKCore's json_errors.txt at the config root, and only /okmodular reload
// wrote it, so a mistake in a recipe was invisible after a plain server start (selftest
// s5.5-structure-input-rejected). Every load now writes recipes/errors.txt.
class RecipeErrorsFileTest {

    @TempDir
    File configDir;

    @AfterEach
    void tearDown() {
        RecipeLoader.getInstance()
            .clearGroup("errors_file_test");
    }

    private File write(String name, String json) throws IOException {
        File recipes = new File(configDir, "recipes");
        recipes.mkdirs();
        File f = new File(recipes, name);
        Files.write(f.toPath(), json.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    @Test
    @DisplayName("読み込みで捨てたレシピを recipes/errors.txt に書く")
    void aFailedRecipeIsReportedBesideTheRecipes() throws IOException {
        write(
            "broken.json",
            "{\"group\":\"errors_file_test\",\"recipes\":[{\"registryName\":\"errors_file_broken\","
                + "\"duration\":20,\"inputs\":[{\"type\":\"no_such_input\"}],\"outputs\":[]}]}");

        RecipeLoader.getInstance()
            .loadAll(configDir);

        File errors = new File(configDir, "recipes/errors.txt");
        assertTrue(errors.isFile(), "recipes/errors.txt was not written");
        String text = new String(Files.readAllBytes(errors.toPath()), StandardCharsets.UTF_8);
        assertTrue(text.contains("errors_file_broken"), text);
    }

    @Test
    @DisplayName("直った後の読み込みは古い errors.txt を消す")
    void aCleanLoadRemovesTheOldFile() throws IOException {
        File recipes = new File(configDir, "recipes");
        recipes.mkdirs();
        File stale = new File(recipes, "errors.txt");
        Files.write(stale.toPath(), "old".getBytes(StandardCharsets.UTF_8));

        RecipeLoader.getInstance()
            .loadAll(configDir);

        assertFalse(stale.exists());
    }
}
