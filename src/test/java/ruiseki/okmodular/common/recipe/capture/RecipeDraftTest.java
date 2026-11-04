package ruiseki.okmodular.common.recipe.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.core.ModularRecipe;
import ruiseki.okmodular.api.recipe.io.EnergyInput;
import ruiseki.okmodular.api.recipe.io.IRecipeInput;
import ruiseki.okmodular.api.recipe.parser.RecipeParserRegistry;

/**
 * The draft holds plain JSON entries, so these tests need no item or fluid registry.
 * Items and fluids are covered by the in-game selftest.
 */
public class RecipeDraftTest {

    private static JsonObject json(String text) {
        return new JsonParser().parse(text.replace('\'', '"'))
            .getAsJsonObject();
    }

    private static long amountOf(RecipeDraft draft, int number) {
        return draft.entry(number)
            .get("amount")
            .getAsLong();
    }

    @Test
    public void sameItemTwiceMergesIntoOneEntry() {
        RecipeDraft draft = new RecipeDraft("g", 20);
        draft.addInput(json("{'item':'minecraft:stone','amount':3}"));
        draft.addInput(json("{'item':'minecraft:stone'}"));

        assertEquals(1, draft.size());
        assertEquals(4, amountOf(draft, 1));
    }

    @Test
    public void differentMetaStaysApart() {
        RecipeDraft draft = new RecipeDraft("g", 20);
        draft.addInput(json("{'item':'minecraft:wool','meta':1}"));
        draft.addInput(json("{'item':'minecraft:wool'}"));

        assertEquals(2, draft.size());
    }

    @Test
    public void fluidsMergeByName() {
        RecipeDraft draft = new RecipeDraft("g", 20);
        draft.addInput(json("{'fluid':'water','amount':1000}"));
        draft.addInput(json("{'fluid':'water','amount':1000}"));

        assertEquals(1, draft.size());
        assertEquals(2000, amountOf(draft, 1));
    }

    @Test
    public void mergeThatOverflowsIntIsRefusedAndLeavesTheDraftAlone() {
        RecipeDraft draft = new RecipeDraft("g", 20);
        draft.addInput(json("{'item':'a','amount':2147483647}"));

        assertThrows(IllegalArgumentException.class, () -> draft.addInput(json("{'item':'a'}")));
        assertEquals(Integer.MAX_VALUE, amountOf(draft, 1));
    }

    @Test
    public void numbersRunThroughInputsThenOutputs() {
        RecipeDraft draft = new RecipeDraft("g", 20);
        draft.addInput(json("{'item':'a'}"));
        draft.addInput(json("{'item':'b'}"));
        draft.addOutput(json("{'item':'c'}"));

        assertFalse(draft.isOutput(2));
        assertTrue(draft.isOutput(3));
        draft.setAmount(3, 7);
        assertEquals(7, amountOf(draft, 3));
        assertEquals(1, amountOf(draft, 2));
    }

    @Test
    public void removeShiftsLaterNumbers() {
        RecipeDraft draft = new RecipeDraft("g", 20);
        draft.addInput(json("{'item':'a'}"));
        draft.addInput(json("{'item':'b'}"));
        draft.addOutput(json("{'item':'c'}"));

        draft.remove(1);

        assertEquals(
            "b",
            draft.entry(1)
                .get("item")
                .getAsString());
        assertEquals(
            "c",
            draft.entry(2)
                .get("item")
                .getAsString());
        assertTrue(draft.isOutput(2));
    }

    @Test
    public void badNumbersAndAmountsAreRefusedAndChangeNothing() {
        RecipeDraft draft = new RecipeDraft("g", 20);
        draft.addInput(json("{'item':'a'}"));
        draft.addOutput(json("{'item':'c'}"));
        JsonObject before = draft.toRecipeJson();

        assertThrows(IllegalArgumentException.class, () -> draft.setAmount(0, 1));
        assertThrows(IllegalArgumentException.class, () -> draft.setAmount(draft.size() + 1, 1));
        assertThrows(IllegalArgumentException.class, () -> draft.setAmount(1, 0));
        assertThrows(IllegalArgumentException.class, () -> draft.setAmount(1, -5));
        assertThrows(IllegalArgumentException.class, () -> draft.remove(-1));
        assertThrows(IllegalArgumentException.class, () -> draft.remove(draft.size() + 1));
        assertThrows(IllegalArgumentException.class, () -> draft.entry(0));
        assertThrows(IllegalArgumentException.class, () -> draft.setDuration(0));
        assertThrows(IllegalArgumentException.class, () -> draft.setEnergy(-1, true));

        assertEquals(before, draft.toRecipeJson());
    }

    @Test
    public void entryReturnsACopy() {
        RecipeDraft draft = new RecipeDraft("g", 20);
        draft.addInput(json("{'item':'a'}"));

        draft.entry(1)
            .addProperty("amount", 99);

        assertEquals(1, amountOf(draft, 1));
    }

    @Test
    public void itemAmountAbove64IsKept() {
        RecipeDraft draft = new RecipeDraft("g", 20);
        draft.addInput(json("{'item':'a'}"));

        draft.setAmount(1, 200);

        assertEquals(
            200,
            draft.toRecipeJson()
                .getAsJsonArray("inputs")
                .get(0)
                .getAsJsonObject()
                .get("amount")
                .getAsLong());
    }

    @Test
    public void energyIsLastInputAndZeroClearsIt() {
        RecipeDraft draft = new RecipeDraft("g", 20);
        draft.addInput(json("{'item':'a'}"));
        draft.addOutput(json("{'item':'c'}"));

        draft.setEnergy(100, true);

        JsonObject expected = new JsonObject();
        new EnergyInput(100, true).write(expected);
        JsonArray inputs = draft.toRecipeJson()
            .getAsJsonArray("inputs");
        assertEquals(2, inputs.size());
        assertEquals(expected, inputs.get(1));
        assertEquals(2, draft.size());

        draft.setEnergy(0, false);

        assertEquals(
            1,
            draft.toRecipeJson()
                .getAsJsonArray("inputs")
                .size());
        assertEquals(0, draft.getEnergy());
    }

    @Test
    public void energyOnlyDraftRefusesToSerialize() {
        RecipeDraft draft = new RecipeDraft("g", 20);
        draft.addInput(json("{'item':'a'}"));
        draft.setEnergy(100, true);
        draft.remove(1);

        assertFalse(draft.hasEntries());
        assertThrows(IllegalStateException.class, draft::toRecipeJson);
    }

    @Test
    public void emptyDraftRefusesToSerialize() {
        assertThrows(IllegalStateException.class, () -> new RecipeDraft("g", 20).toRecipeJson());
    }

    @Test
    public void roundTripThroughTheParser() {
        RecipeDraft draft = new RecipeDraft("g", 20);
        draft.addOutput(json("{'item':'minecraft:stone'}"));
        draft.setEnergy(50, true);
        draft.setDuration(40);

        JsonObject json = draft.toRecipeJson();
        ModularRecipe.Builder builder = ModularRecipe.builder()
            .recipeGroup("g")
            .registryName("x");
        for (Map.Entry<String, JsonElement> e : new ArrayList<>(json.entrySet())) {
            RecipeParserRegistry.parse(builder, e.getKey(), e.getValue());
        }
        IModularRecipe recipe = builder.build();

        assertEquals(40, recipe.getDuration());
        IRecipeInput input = recipe.getInputs()
            .get(0);
        assertTrue(input instanceof EnergyInput);
        assertEquals(50, ((EnergyInput) input).getAmount());
        assertTrue(input.isPerTick());
    }
}
