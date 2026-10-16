package ruiseki.okmodular.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ruiseki.okmodular.OKMObjects;
import ruiseki.okmodular.api.condition.Conditions;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.decorator.RecipeDecorator;
import ruiseki.okmodular.api.structure.core.IStructureEntry;
import ruiseki.okmodular.api.structure.io.StructureJsonReader;
import ruiseki.okmodular.api.structure.visitor.StructureValidationVisitor;
import ruiseki.okmodular.common.fluid.EnumFluidMaterial;
import ruiseki.okmodular.common.item.EnumMaterial;
import ruiseki.okmodular.common.recipe.MachineryRecipeLoader;
import ruiseki.okmodular.structure.pattern.StructurePatternLoader;

/**
 * Every bundled example goes through the parser that will read it in game.
 *
 * The examples are the first thing a pack author copies, and a fresh install's only machines.
 * The loaders do not fail on a bad entry: they log it and drop it. Outside the game nobody reads
 * that log, so a typo in an example would ship as a machine that silently has no recipes. These
 * tests turn each kind of silent drop into a failure: a structure that does not validate, a
 * recipe the loader skipped, a pattern with errors, a recipe group no structure serves, a
 * pattern name nothing defines, and two recipes whose generated registry names collide (the
 * loader keys recipes by that name across all files, so the later one replaces the earlier).
 *
 * The examples must load with OKModular alone, so only minecraft: and okmodular: IDs are
 * allowed, and recipe files are read in the platform charset, so they must be ASCII.
 */
class BundledDefaultsTest {

    private static final Path RESOURCE_ROOT = Paths.get("src/main/resources/assets/okmodular/defaults");
    private static final Pattern NAMESPACED = Pattern.compile("\"([A-Za-z0-9_]+):[A-Za-z_]");

    @TempDir
    static File patternDir;

    @BeforeAll
    static void loadPatterns() throws IOException {
        Conditions.registerDefaults();
        for (String file : DefaultConfigWriter.MANIFEST.get(DefaultConfigWriter.STRUCTURE_IO)) {
            Files.write(new File(patternDir, file).toPath(), bytes(DefaultConfigWriter.STRUCTURE_IO, file));
        }
        StructurePatternLoader.getInstance()
            .clear();
        StructurePatternLoader.getInstance()
            .loadFrom(patternDir);
    }

    private static byte[] bytes(String directory, String file) {
        String path = DefaultConfigWriter.resourcePath(directory, file);
        try (InputStream in = BundledDefaultsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "bundled example missing: " + path);
            return readAll(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static JsonElement json(String directory, String file) {
        return new JsonParser().parse(new String(bytes(directory, file), StandardCharsets.UTF_8));
    }

    private static Map<String, IStructureEntry> structures() {
        Map<String, IStructureEntry> all = new LinkedHashMap<>();
        for (String file : DefaultConfigWriter.MANIFEST.get(DefaultConfigWriter.STRUCTURES)) {
            all.putAll(StructureJsonReader.readFile(json(DefaultConfigWriter.STRUCTURES, file)).structures);
        }
        return all;
    }

    /** Recipes as MachineryJsonReader hands them to the loader: group stamped as machine. */
    private static List<JsonObject> recipeJsons() {
        List<JsonObject> recipes = new ArrayList<>();
        for (String file : DefaultConfigWriter.MANIFEST.get(DefaultConfigWriter.RECIPES)) {
            JsonObject root = json(DefaultConfigWriter.RECIPES, file).getAsJsonObject();
            String group = root.get("group")
                .getAsString();
            JsonArray array = root.getAsJsonArray("recipes");
            for (JsonElement element : array) {
                JsonObject recipe = element.getAsJsonObject();
                if (!recipe.has("machine") && !recipe.has("group")) recipe.addProperty("machine", group);
                recipes.add(recipe);
            }
        }
        return recipes;
    }

    private static boolean isAbstract(JsonObject recipe) {
        return recipe.has("abstract") && recipe.get("abstract")
            .getAsBoolean();
    }

    private static String group(JsonObject recipe) {
        return (recipe.has("group") ? recipe.get("group") : recipe.get("machine")).getAsString();
    }

    @Test
    void manifestIsNotEmpty() {
        assertFalse(
            DefaultConfigWriter.MANIFEST.get(DefaultConfigWriter.STRUCTURES)
                .isEmpty());
        assertFalse(
            DefaultConfigWriter.MANIFEST.get(DefaultConfigWriter.RECIPES)
                .isEmpty());
    }

    @Test
    void manifestMatchesResources() throws IOException {
        Set<String> listed = new TreeSet<>();
        DefaultConfigWriter.MANIFEST.forEach((dir, files) -> files.forEach(f -> listed.add(dir + "/" + f)));
        Set<String> present;
        try (Stream<Path> files = Files.walk(RESOURCE_ROOT)) {
            present = files.filter(Files::isRegularFile)
                .map(
                    p -> RESOURCE_ROOT.relativize(p)
                        .toString()
                        .replace('\\', '/'))
                .collect(Collectors.toCollection(TreeSet::new));
        }
        assertEquals(listed, present);
    }

    @Test
    void everyStructureValidates() {
        for (String file : DefaultConfigWriter.MANIFEST.get(DefaultConfigWriter.STRUCTURES)) {
            StructureJsonReader.FileData data = StructureJsonReader
                .readFile(json(DefaultConfigWriter.STRUCTURES, file));
            assertFalse(data.structures.isEmpty(), file + " holds no structure");
            for (IStructureEntry entry : data.structures.values()) {
                StructureValidationVisitor visitor = new StructureValidationVisitor();
                visitor.setExternalMappings(data.defaultMappings);
                entry.accept(visitor);
                assertEquals(new ArrayList<String>(), visitor.getErrors(), entry.getName() + " in " + file);
            }
        }
    }

    @Test
    void everyRecipeLoads() {
        List<JsonObject> recipes = recipeJsons();
        long concrete = recipes.stream()
            .filter(r -> !isAbstract(r))
            .count();
        assertEquals(
            concrete,
            MachineryRecipeLoader.load(recipes)
                .size(),
            "the loader dropped a recipe; see the log");
    }

    /** An unrecognised decorator is skipped with a log line while its recipe still loads. */
    @Test
    void everyDecoratorIsApplied() throws ReflectiveOperationException {
        List<JsonObject> concrete = recipeJsons().stream()
            .filter(r -> !isAbstract(r))
            .collect(Collectors.toList());
        List<IModularRecipe> loaded = MachineryRecipeLoader.load(recipeJsons());
        assertEquals(concrete.size(), loaded.size());

        java.lang.reflect.Field internal = RecipeDecorator.class.getDeclaredField("internal");
        internal.setAccessible(true);
        for (int i = 0; i < concrete.size(); i++) {
            JsonObject json = concrete.get(i);
            int written = json.has("decorators") ? json.getAsJsonArray("decorators")
                .size() : 0;
            int applied = 0;
            Object recipe = loaded.get(i);
            while (recipe instanceof RecipeDecorator) {
                applied++;
                recipe = internal.get(recipe);
            }
            assertEquals(written, applied, json.get("registryName") + " lost a decorator");
        }
    }

    /**
     * Pairs where the earlier recipe is meant to win: a higher tier replacing a lower one.
     */
    private static final Set<String> INTENDED_OVERRIDES = new HashSet<>(
        java.util.Arrays
            .asList("crusher_iron_ore_byproduct > crusher_iron_ore", "crusher_gold_ore_byproduct > crusher_gold_ore"));

    /**
     * A machine runs the first recipe, in sort order, whose inputs are there; it does not fall
     * back to a later one. So when an earlier recipe needs nothing the later one does not also
     * supply, the later one can never run - or, when only the amounts differ, runs only while
     * the port holds less than the earlier one wants. Review found two examples like that: the basic
     * circuitry casing lost to the tier 1 plain casing, and Metal Former plates lost to gears.
     */
    @Test
    void noRecipeIsShadowed() {
        List<JsonObject> concrete = recipeJsons().stream()
            .filter(r -> !isAbstract(r))
            .collect(Collectors.toList());
        List<IModularRecipe> loaded = MachineryRecipeLoader.load(recipeJsons());
        assertEquals(concrete.size(), loaded.size());

        List<String> shadowed = new ArrayList<>();
        for (int s = 0; s < concrete.size(); s++) {
            for (int r = 0; r < concrete.size(); r++) {
                if (s == r || !group(concrete.get(s)).equals(group(concrete.get(r)))) continue;
                if (loaded.get(s)
                    .compareTo(loaded.get(r)) >= 0) continue;
                if (!suppliedBy(inputsOf(loaded.get(s), concrete.get(s)), inputsOf(loaded.get(r), concrete.get(r))))
                    continue;
                String pair = concrete.get(s)
                    .get("registryName")
                    .getAsString() + " > "
                    + concrete.get(r)
                        .get("registryName")
                        .getAsString();
                if (!INTENDED_OVERRIDES.contains(pair)) shadowed.add(pair);
            }
        }
        assertEquals(new ArrayList<String>(), shadowed, "the right-hand recipe can never run");
    }

    /**
     * An item held as a catalyst (consume false) must not be eaten by another recipe of the
     * same machine. The Tuned Wrench recipe used to take a wrench: once nothing else matched it
     * ran, moved the catalyst to the output port, and every assembly after it stopped.
     */
    @Test
    void noRecipeConsumesACatalyst() {
        List<JsonObject> recipes = recipeJsons();
        for (JsonObject holder : recipes) {
            for (JsonElement i : holder.getAsJsonArray("inputs")) {
                JsonObject catalyst = i.getAsJsonObject();
                if (!catalyst.has("item") || !catalyst.has("consume")
                    || catalyst.get("consume")
                        .getAsBoolean())
                    continue;
                for (JsonObject other : recipes) {
                    if (!group(other).equals(group(holder))) continue;
                    for (JsonElement j : other.getAsJsonArray("inputs")) {
                        JsonObject eaten = j.getAsJsonObject();
                        boolean consumes = !eaten.has("consume") || eaten.get("consume")
                            .getAsBoolean();
                        assertFalse(
                            consumes && catalyst.get("item")
                                .equals(eaten.get("item")),
                            other.get("registryName") + " eats the catalyst " + catalyst.get("item"));
                    }
                }
            }
        }
    }

    /**
     * A recipe that only places a block (no replace) needs an empty cell, and a machine does
     * not fall back when the chosen recipe has nowhere to put its output: it reports output full.
     * So such a recipe must sort after the recipes that work on what is already in the cells,
     * or a filled cell plus the placing recipe's items stalls the machine.
     */
    @Test
    void placingRecipesSortAfterCellRecipes() {
        List<JsonObject> concrete = recipeJsons().stream()
            .filter(r -> !isAbstract(r))
            .collect(Collectors.toList());
        List<IModularRecipe> loaded = MachineryRecipeLoader.load(recipeJsons());
        for (int p = 0; p < concrete.size(); p++) {
            if (!placesWithoutReplacing(concrete.get(p))) continue;
            for (int c = 0; c < concrete.size(); c++) {
                if (c == p || !group(concrete.get(c)).equals(group(concrete.get(p)))) continue;
                if (!readsCells(concrete.get(c))) continue;
                assertTrue(
                    loaded.get(c)
                        .compareTo(loaded.get(p)) < 0,
                    concrete.get(p)
                        .get("registryName") + " is tried before "
                        + concrete.get(c)
                            .get("registryName"));
            }
        }
    }

    private static boolean placesWithoutReplacing(JsonObject recipe) {
        for (JsonElement o : recipe.getAsJsonArray("outputs")) {
            JsonObject out = o.getAsJsonObject();
            if (out.has("symbol") && out.has("block") && !out.has("replace")) return true;
        }
        return false;
    }

    private static boolean readsCells(JsonObject recipe) {
        for (JsonElement i : recipe.getAsJsonArray("inputs")) {
            if (i.getAsJsonObject()
                .has("symbol")) return true;
        }
        return false;
    }

    /** Inputs after inheritance: the loader has merged the parent's into the child's JSON. */
    private static JsonArray inputsOf(IModularRecipe recipe, JsonObject json) {
        return json.getAsJsonArray("inputs");
    }

    private static boolean suppliedBy(JsonArray needs, JsonArray has) {
        for (JsonElement n : needs) {
            JsonObject need = n.getAsJsonObject();
            if (need.has("energy")) continue;
            boolean found = false;
            for (JsonElement h : has) {
                JsonObject have = h.getAsJsonObject();
                for (String key : new String[] { "item", "ore", "fluid" }) {
                    if (need.has(key) && have.has(key)
                        && need.get(key)
                            .equals(have.get(key)))
                        found = true;
                }
                if (!need.has("item") && !need.has("ore") && !need.has("fluid") && need.equals(have)) found = true;
            }
            if (!found) return false;
        }
        return true;
    }

    @Test
    void registryNamesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (JsonObject recipe : recipeJsons()) {
            String name = recipe.has("registryName") ? recipe.get("registryName")
                .getAsString()
                : recipe.get("name")
                    .getAsString();
            assertTrue(seen.add(name.toLowerCase()), "two recipes share the name " + name);
        }
    }

    @Test
    void everyPatternLoads() {
        assertEquals(
            new ArrayList<>(),
            StructurePatternLoader.getInstance()
                .getErrors()
                .stream()
                .map(Throwable::getMessage)
                .collect(Collectors.toList()));
    }

    @Test
    void everyRecipeGroupHasRecipes() {
        Set<String> served = new TreeSet<>();
        for (IStructureEntry entry : structures().values()) served.addAll(entry.getRecipeGroup());
        Set<String> withRecipes = recipeJsons().stream()
            .filter(r -> !isAbstract(r))
            .map(BundledDefaultsTest::group)
            .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(served, withRecipes, "every machine needs recipes, and every recipe group a machine");
    }

    @Test
    void everyPatternReferenceResolves() {
        Set<String> known = StructurePatternLoader.getInstance()
            .getNames();
        for (JsonObject recipe : recipeJsons()) {
            for (String key : new String[] { "inputs", "outputs" }) {
                if (!recipe.has(key)) continue;
                for (JsonElement io : recipe.getAsJsonArray(key)) {
                    JsonObject o = io.getAsJsonObject();
                    if (o.has("pattern")) {
                        String pattern = o.get("pattern")
                            .getAsString();
                        assertTrue(known.contains(pattern), "unknown pattern " + pattern);
                    }
                }
            }
        }
    }

    @Test
    void onlyVanillaAndOkmodularIds() {
        DefaultConfigWriter.MANIFEST.forEach((dir, files) -> {
            for (String file : files) {
                Matcher m = NAMESPACED.matcher(new String(bytes(dir, file), StandardCharsets.UTF_8));
                while (m.find()) {
                    String mod = m.group(1);
                    assertTrue(
                        mod.equals("minecraft") || mod.equals("okmodular"),
                        dir + "/" + file + " needs another mod: " + mod);
                }
            }
        });
    }

    /**
     * Item IDs only resolve in game; outside it an unknown one is a warning and the recipe still
     * loads. So the okmodular: half is checked here against the names this mod registers.
     */
    @Test
    void okmodularIdsExist() {
        Set<String> blocks = new HashSet<>();
        for (OKMObjects object : OKMObjects.values()) blocks.add(object.name);
        for (String design : new String[] { "plain", "reinforced", "vented", "circuitry" }) {
            blocks.add("casing_" + design);
        }
        Set<String> items = new HashSet<>(java.util.Arrays.asList("wrench", "structure_wand", "fluid_canister"));
        Set<String> parts = new HashSet<>(java.util.Arrays.asList("ingot", "plate", "gear", "dust"));
        Pattern id = Pattern.compile("\"okmodular:([a-z_]+)(?::(\\d+|\\*))?\"");

        DefaultConfigWriter.MANIFEST.forEach((dir, files) -> {
            for (String file : files) {
                Matcher m = id.matcher(new String(bytes(dir, file), StandardCharsets.UTF_8));
                while (m.find()) {
                    String name = m.group(1);
                    String meta = m.group(2);
                    String where = dir + "/" + file + ": " + m.group();
                    if (parts.contains(name)) {
                        assertNotNull(meta, where + " names a part without a material");
                        EnumMaterial material = EnumMaterial.byMetadata(Integer.parseInt(meta));
                        assertNotNull(material, where + " is no material");
                        assertTrue(material.supportsForm(name), where + " has no " + name + " form");
                    } else if (blocks.contains(name)) {
                        if (meta != null && !meta.equals("*")) {
                            assertTrue(Integer.parseInt(meta) < 16, where + " is past the 16 tiers");
                        }
                    } else {
                        assertTrue(items.contains(name), where + " is not registered by this mod");
                    }
                }
            }
        });
    }

    /** Fluids resolve only in game too; this mod's own and vanilla's are the ones allowed. */
    @Test
    void fluidsExist() {
        Set<String> known = new HashSet<>(java.util.Arrays.asList("water", "lava"));
        for (EnumFluidMaterial fluid : EnumFluidMaterial.values()) known.add(fluid.getName());
        for (JsonObject recipe : recipeJsons()) {
            for (String key : new String[] { "inputs", "outputs" }) {
                if (!recipe.has(key)) continue;
                for (JsonElement io : recipe.getAsJsonArray(key)) {
                    JsonObject o = io.getAsJsonObject();
                    if (!o.has("fluid")) continue;
                    String fluid = o.get("fluid")
                        .getAsString();
                    assertTrue(known.contains(fluid), recipe.get("registryName") + " uses unknown fluid " + fluid);
                }
            }
        }
    }

    @Test
    void isAscii() {
        DefaultConfigWriter.MANIFEST.forEach((dir, files) -> {
            for (String file : files) {
                for (byte b : bytes(dir, file)) {
                    assertTrue(b >= 0, dir + "/" + file + " is not ASCII");
                }
            }
        });
    }
}
