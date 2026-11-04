package ruiseki.okmodular.api.recipe.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ruiseki.okmodular.api.condition.Conditions;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.parser.JsonKeyRenames.Scope;
import ruiseki.okmodular.common.recipe.MachineryRecipeLoader;

/**
 * Every renamed recipe key lives in one table; the code reads only the new keys.
 *
 * The table is scoped because the same word means different things in different places:
 * the recipe's {@code condition} (an array, now {@code conditions}) is not the
 * {@code requirement} decorator's {@code condition} (one expression, unchanged).
 */
public class JsonKeyRenamesTest {

    private final List<String> warnings = new ArrayList<>();
    private Consumer<String> savedSink;

    @BeforeAll
    public static void setUpAll() {
        Conditions.registerDefaults();
    }

    @BeforeEach
    public void captureWarnings() {
        savedSink = JsonKeyRenames.sink;
        JsonKeyRenames.sink = warnings::add;
        JsonKeyRenames.resetWarnings();
    }

    @AfterEach
    public void restoreSink() {
        JsonKeyRenames.sink = savedSink;
        JsonKeyRenames.resetWarnings();
    }

    private static JsonObject obj(String json) {
        return new JsonParser().parse(json.replace('\'', '"'))
            .getAsJsonObject();
    }

    @Test
    public void oldKeyMoves() {
        JsonObject json = obj("{ 'machine': 'crusher', 'time': 40, 'localizedName': 'X' }");

        JsonKeyRenames.upgrade(json, Scope.RECIPE);

        assertEquals(
            "crusher",
            json.get("group")
                .getAsString());
        assertEquals(
            40,
            json.get("duration")
                .getAsInt());
        assertEquals(
            "X",
            json.get("name")
                .getAsString());
        assertFalse(json.has("machine") || json.has("time") || json.has("localizedName"));
        assertEquals(3, warnings.size());
        assertEquals(
            "JSON key \"machine\" is renamed to \"group\". Read as \"group\"; rename it in the file.",
            warnings.get(0));
    }

    @Test
    public void newKeyWinsWhenBothPresent() {
        JsonObject json = obj("{ 'time': 40, 'duration': 10 }");

        JsonKeyRenames.upgrade(json, Scope.RECIPE);

        assertEquals(
            10,
            json.get("duration")
                .getAsInt());
        assertFalse(json.has("time"));
        assertEquals(
            Collections.singletonList(
                "JSON key \"time\" is renamed to \"duration\". Both \"time\" and \"duration\" are set; \"time\" is ignored."),
            warnings);
    }

    @Test
    public void machineAndGroupBothPresent() {
        JsonObject json = obj("{ 'machine': 'old', 'group': 'new' }");

        JsonKeyRenames.upgrade(json, Scope.RECIPE);

        assertEquals(
            "new",
            json.get("group")
                .getAsString());
        assertFalse(json.has("machine"));
        assertEquals(1, warnings.size());
    }

    @Test
    public void scopesDoNotLeak() {
        JsonObject io = obj("{ 'condition': {}, 'pertick': true }");
        JsonKeyRenames.upgrade(io, Scope.IO);
        assertTrue(io.has("condition"), "IO scope must not touch the recipe-level condition");
        assertTrue(io.has("perTick"));
        assertFalse(io.has("pertick"));

        JsonObject recipe = obj("{ 'condition': [], 'pertick': true }");
        JsonKeyRenames.upgrade(recipe, Scope.RECIPE);
        assertTrue(recipe.has("conditions"));
        assertTrue(recipe.has("pertick"), "RECIPE scope must not touch IO keys");
    }

    @Test
    public void requirementDecoratorConditionIsNeverRenamed() {
        JsonObject recipe = obj(
            "{ 'registryName': 'r', 'group': 'g', 'duration': 10,"
                + " 'decorators': [ { 'requirement': { 'condition': 'tier.glass >= 1' } } ] }");
        List<JsonObject> in = new ArrayList<>();
        in.add(recipe);

        List<IModularRecipe> out = MachineryRecipeLoader.load(in);

        assertEquals(1, out.size());
        assertTrue(
            warnings.stream()
                .noneMatch(w -> w.contains("\"condition\"")),
            warnings.toString());
    }

    @Test
    public void warnsOncePerKeyUntilReset() {
        JsonKeyRenames.upgrade(obj("{ 'time': 1 }"), Scope.RECIPE);
        JsonKeyRenames.upgrade(obj("{ 'time': 2 }"), Scope.RECIPE);
        assertEquals(1, warnings.size());

        JsonKeyRenames.upgrade(obj("{ 'input': [] }"), Scope.RECIPE);
        assertEquals(2, warnings.size());

        JsonKeyRenames.resetWarnings();
        JsonKeyRenames.upgrade(obj("{ 'time': 3 }"), Scope.RECIPE);
        assertEquals(3, warnings.size());

        JsonKeyRenames.warnOnce("selections", "custom");
        JsonKeyRenames.warnOnce("selections", "custom");
        assertEquals(4, warnings.size());
        assertEquals("custom", warnings.get(3));
    }

    @Test
    public void tableKeysAreCamelCase() {
        assertFalse(
            JsonKeyRenames.table()
                .isEmpty());
        for (JsonKeyRenames.Rename rename : JsonKeyRenames.table()) {
            assertNotNull(rename.scope);
            assertTrue(rename.newKey.matches("[a-z][a-zA-Z0-9]*"), rename.newKey);
        }
    }

    @Test
    public void parentAndChildInOldAndNewSpellingsEndUpWithOneGroup() {
        JsonObject parent = obj(
            "{ 'registryName': 'p', 'abstract': true, 'machine': 'crusher', 'time': 20,"
                + " 'input': [ { 'item': 'minecraft:coal' } ] }");
        JsonObject child = obj(
            "{ 'registryName': 'c', 'parent': 'p', 'group': 'crusher',"
                + " 'outputs': [ { 'item': 'minecraft:diamond' } ] }");
        List<JsonObject> in = new ArrayList<>();
        in.add(parent);
        in.add(child);

        List<IModularRecipe> out = MachineryRecipeLoader.load(in);

        assertEquals(1, out.size());
        assertEquals(
            "crusher",
            out.get(0)
                .getRecipeGroup());
        assertFalse(child.has("machine"));
        assertEquals(
            1,
            child.getAsJsonArray("inputs")
                .size());
    }

}
