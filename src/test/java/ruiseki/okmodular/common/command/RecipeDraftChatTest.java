package ruiseki.okmodular.common.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import ruiseki.okmodular.common.recipe.capture.RecipeDraft;

/**
 * Numbering only: outputs continue after the inputs, and each entry line carries the click
 * fields for its own number. The wording comes from lang keys and is not asserted.
 */
class RecipeDraftChatTest {

    private static JsonObject entry(String key, String id, long amount) {
        JsonObject json = new JsonObject();
        json.addProperty(key, id);
        json.addProperty("amount", amount);
        return json;
    }

    private static List<ClickEvent> clicks(IChatComponent line) {
        List<ClickEvent> found = new ArrayList<>();
        collect(line, found);
        return found;
    }

    @SuppressWarnings("unchecked")
    private static void collect(IChatComponent component, List<ClickEvent> into) {
        ClickEvent click = component.getChatStyle()
            .getChatClickEvent();
        if (click != null) into.add(click);
        for (IChatComponent sibling : (List<IChatComponent>) component.getSiblings()) collect(sibling, into);
    }

    @Test
    void output_after_two_inputs_is_number_three() {
        RecipeDraft draft = new RecipeDraft("default", 100);
        draft.addInput(entry("item", "minecraft:stone", 1));
        draft.addInput(entry("fluid", "water", 1000));
        draft.addOutput(entry("item", "minecraft:dirt", 2));

        List<IChatComponent> lines = RecipeDraftChat.lines(draft);
        List<ClickEvent> clicks = clicks(lines.get(lines.size() - 1));

        assertEquals(2, clicks.size());
        assertEquals(
            ClickEvent.Action.SUGGEST_COMMAND,
            clicks.get(0)
                .getAction());
        assertEquals(
            "/okmodular recipe amount 3 ",
            clicks.get(0)
                .getValue());
        assertEquals(
            ClickEvent.Action.SUGGEST_COMMAND,
            clicks.get(1)
                .getAction());
        assertEquals(
            "/okmodular recipe remove 3",
            clicks.get(1)
                .getValue());
    }
}
