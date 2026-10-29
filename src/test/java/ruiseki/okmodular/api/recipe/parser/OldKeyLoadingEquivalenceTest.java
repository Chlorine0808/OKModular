package ruiseki.okmodular.api.recipe.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

import ruiseki.okmodular.api.condition.Conditions;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.decorator.RandomBlockOutputDecorator;
import ruiseki.okmodular.api.recipe.decorator.RecipeDecorator;
import ruiseki.okmodular.api.recipe.decorator.WeightedRandomDecorator;
import ruiseki.okmodular.api.recipe.io.EnergyInput;
import ruiseki.okmodular.api.recipe.io.ItemInput;
import ruiseki.okmodular.api.recipe.io.ItemOutput;
import ruiseki.okmodular.common.recipe.JSONLoader;
import ruiseki.okmodular.common.recipe.MachineryJsonReader;
import ruiseki.okmodular.common.recipe.RecipeLoader;

/**
 * A pack written with the old keys loads to the same recipes as the same pack in the new
 * spelling, and each old key warns once.
 *
 * Both packs go through the real reader and loader (file header, recipe, input and output
 * parsers, decorators). The old pack uses every renamed key at least once: machine (file
 * header), time, tier, input, output, condition, localizedName, pertick, nbtmatch, nbtlist,
 * and the decorator keys pool and selections.
 */
public class OldKeyLoadingEquivalenceTest {

    private static final String OLD = "{ 'machine': 'crusher', 'recipes': [ {"
        + " 'localizedName': 'Old', 'time': 40, 'tier': { 'casing': 2 },"
        + " 'condition': { 'type': 'weather', 'weather': 'clear' },"
        + " 'input': [ { 'item': 'minecraft:stone', 'nbtmatch': 'exact',"
        + "     'nbtlist': { 'path': 'ench', 'ops': [ { 'id': 16, 'lvl': '<3' } ] } },"
        + "   { 'energy': 100, 'pertick': false } ],"
        + " 'output': [ { 'item': 'minecraft:cobblestone', 'pertick': 5 } ],"
        + " 'decorators': ["
        + "   { 'type': 'weighted_random', 'pool': [ { 'weight': 3, 'output': { 'item': 'minecraft:flint' } },"
        + "       { 'weight': 1, 'output': { 'item': 'minecraft:gravel' } } ] },"
        + "   { 'type': 'random_block_output', 'count': 2, 'selections': ["
        + "       { 'symbol': 'L', 'output': { 'block': 'minecraft:glass' } } ] } ] } ] }";

    private static final String NEW = "{ 'group': 'crusher', 'recipes': [ {"
        + " 'name': 'Old', 'duration': 40, 'tiers': { 'casing': 2 },"
        + " 'conditions': { 'type': 'weather', 'weather': 'clear' },"
        + " 'inputs': [ { 'item': 'minecraft:stone', 'nbtMatch': 'exact',"
        + "     'nbtList': { 'path': 'ench', 'ops': [ { 'id': 16, 'lvl': '<3' } ] } },"
        + "   { 'energy': 100, 'perTick': false } ],"
        + " 'outputs': [ { 'item': 'minecraft:cobblestone', 'perTick': 5 } ],"
        + " 'decorators': ["
        + "   { 'type': 'weighted_random', 'outputs': [ { 'weight': 3, 'item': 'minecraft:flint' },"
        + "       { 'weight': 1, 'item': 'minecraft:gravel' } ] },"
        + "   { 'type': 'random_block_output', 'count': 2, 'symbol': 'L',"
        + "     'outputs': [ { 'block': 'minecraft:glass' } ] } ] } ] }";

    @TempDir
    Path tmp;

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

    private Path pack(String name, String json) throws IOException {
        Path dir = Files.createDirectory(tmp.resolve(name));
        Files.write(
            dir.resolve("pack.json"),
            json.replace('\'', '"')
                .getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private List<IModularRecipe> load(String name, String json) throws IOException {
        return JSONLoader.loadRecipes(new MachineryJsonReader(pack(name, json).toFile()));
    }

    private static String written(Object io) {
        JsonObject json = new JsonObject();
        if (io instanceof ItemInput item) item.write(json);
        else if (io instanceof EnergyInput energy) energy.write(json);
        else if (io instanceof ItemOutput out) out.write(json);
        return json.toString();
    }

    /** Everything the loader decided about a recipe, as text. */
    private static String describe(IModularRecipe recipe) {
        StringBuilder sb = new StringBuilder();
        sb.append("group=")
            .append(recipe.getRecipeGroup())
            .append(" name=")
            .append(recipe.getName())
            .append(" duration=")
            .append(recipe.getDuration())
            .append(" tier=")
            .append(recipe.getMaxTierRequired())
            .append(" conditions=")
            .append(
                recipe.getConditions()
                    .size())
            .append('\n');
        recipe.getInputs()
            .forEach(
                in -> sb.append("in ")
                    .append(
                        in.getClass()
                            .getSimpleName())
                    .append(' ')
                    .append(written(in))
                    .append('\n'));
        recipe.getOutputs()
            .forEach(
                out -> sb.append("out ")
                    .append(
                        out.getClass()
                            .getSimpleName())
                    .append(' ')
                    .append(written(out))
                    .append('\n'));
        for (IModularRecipe r = recipe; r instanceof RecipeDecorator d; r = d.getInternal()) {
            sb.append("decorator ")
                .append(
                    d.getClass()
                        .getSimpleName());
            if (d instanceof WeightedRandomDecorator w) {
                w.getPool()
                    .forEach(
                        e -> sb.append(" w")
                            .append(e.itemWeight));
            }
            if (d instanceof RandomBlockOutputDecorator c) {
                sb.append(" symbol=")
                    .append(c.getSymbol());
                c.getWeightedOutputs()
                    .forEach(
                        e -> sb.append(" w")
                            .append(e.weight));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Test
    public void oldAndNewSpellingsLoadToTheSameRecipe() throws IOException {
        List<IModularRecipe> fromNew = load("new", NEW);
        assertEquals(0, warnings.size(), "the new spelling must not warn: " + warnings);
        List<IModularRecipe> fromOld = load("old", OLD);

        assertEquals(1, fromNew.size());
        assertEquals(1, fromOld.size());
        String expected = describe(fromNew.get(0));
        assertFalse(expected.contains("group=null"), expected);
        assertEquals(expected, describe(fromOld.get(0)));
    }

    @Test
    public void everyOldKeyWarnsExactlyOnce() throws IOException {
        // Loading the old pack twice in one reload must not repeat a warning
        load("old1", OLD);
        load("old2", OLD);

        List<String> keys = Arrays.asList(
            "machine",
            "time",
            "tier",
            "input",
            "output",
            "condition",
            "localizedName",
            "pertick",
            "nbtmatch",
            "nbtlist",
            "pool",
            "selections");
        for (String key : keys) {
            long hits = warnings.stream()
                .filter(w -> w.contains("\"" + key + "\" is renamed"))
                .count();
            assertEquals(1, hits, key + " should warn once: " + warnings);
        }
        assertEquals(keys.size(), warnings.size(), warnings.toString());
    }

    @Test
    public void headerMachineIsStampedAsGroupOnEveryRecipe() throws IOException {
        Path dir = pack("header", "{ 'machine': 'x', 'recipes': [ { 'duration': 1 }, { 'duration': 2 } ] }");

        List<JsonObject> read = new MachineryJsonReader(dir.toFile()).read();

        assertEquals(2, read.size());
        for (JsonObject recipe : read) {
            assertEquals(
                "x",
                recipe.get("group")
                    .getAsString());
            assertFalse(recipe.has("machine"));
        }
    }

    @Test
    public void startupScanThenLoadAllWarnsOncePerKey() throws IOException {
        Path config = Files.createDirectory(tmp.resolve("config"));
        Path recipes = Files.createDirectory(config.resolve("recipes"));
        Files.write(
            recipes.resolve("pack.json"),
            ("{ 'machine': 'crusher', 'recipes': [ { 'time': 5, 'input': [ { 'energy': 1 } ],"
                + " 'output': [ { 'energy': 1 } ] } ] }").replace("'", "\"")
                    .getBytes(StandardCharsets.UTF_8));

        // The order MachineryModule uses: scan in preInit, load in postInit
        RecipeLoader.scanGroupNames(config.toFile());
        RecipeLoader.getInstance()
            .loadAll(config.toFile());

        assertEquals(4, warnings.size(), warnings.toString());
        RecipeLoader.getInstance()
            .reload(config.toFile());
        assertEquals(8, warnings.size(), "a reload warns again, once per key: " + warnings);
    }
}
