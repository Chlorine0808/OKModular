package ruiseki.okmodular.common.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import com.google.gson.JsonObject;

import ruiseki.okcore.helper.LangHelpers;
import ruiseki.okmodular.common.recipe.capture.RecipeDraft;

/** Renders a {@link RecipeDraft} as chat lines with clickable edit fields. */
final class RecipeDraftChat {

    private static final String COMMAND = "/okmodular recipe ";

    private RecipeDraftChat() {}

    static List<IChatComponent> lines(RecipeDraft draft) {
        List<IChatComponent> lines = new ArrayList<>();
        lines.add(
            new ChatComponentText(
                EnumChatFormatting.YELLOW + LangHelpers.localize(
                    "command.okmodular.recipe_header",
                    draft.getGroup(),
                    draft.getDuration(),
                    energyText(draft))));
        for (int number = 1; number <= draft.size(); number++) {
            lines.add(entryLine(draft, number));
        }
        return lines;
    }

    private static String energyText(RecipeDraft draft) {
        if (draft.getEnergy() == 0) return LangHelpers.localize("command.okmodular.recipe_energy_none");
        return LangHelpers.localize(
            draft.isEnergyPerTick() ? "command.okmodular.recipe_energy_per_tick"
                : "command.okmodular.recipe_energy_total",
            draft.getEnergy());
    }

    private static IChatComponent entryLine(RecipeDraft draft, int number) {
        JsonObject entry = draft.entry(number);
        boolean output = draft.isOutput(number);
        String side = LangHelpers.localize(output ? "command.okmodular.recipe_out" : "command.okmodular.recipe_in");
        IChatComponent line = new ChatComponentText(
            EnumChatFormatting.GRAY + " "
                + number
                + ". "
                + side
                + " "
                + EnumChatFormatting.WHITE
                + describe(entry)
                + " ");
        line.appendSibling(
            click(
                "command.okmodular.recipe_click_amount",
                EnumChatFormatting.AQUA,
                ClickEvent.Action.SUGGEST_COMMAND,
                COMMAND + "amount " + number + " "));
        line.appendSibling(new ChatComponentText(" "));
        line.appendSibling(
            click(
                "command.okmodular.recipe_click_remove",
                EnumChatFormatting.RED,
                ClickEvent.Action.RUN_COMMAND,
                COMMAND + "remove " + number));
        return line;
    }

    private static IChatComponent click(String key, EnumChatFormatting color, ClickEvent.Action action,
        String command) {
        ChatComponentText text = new ChatComponentText(LangHelpers.localize(key));
        text.setChatStyle(
            new ChatStyle().setColor(color)
                .setChatClickEvent(new ClickEvent(action, command)));
        return text;
    }

    private static String describe(JsonObject entry) {
        long amount = entry.has("amount") ? entry.get("amount")
            .getAsLong() : 1;
        if (entry.has("fluid")) {
            return entry.get("fluid")
                .getAsString() + " "
                + LangHelpers.localize("command.okmodular.recipe_fluid_amount", amount);
        }
        String id = entry.has("item") ? entry.get("item")
            .getAsString() : "?";
        if (entry.has("meta")) id += ":" + entry.get("meta")
            .getAsInt();
        return id + " x" + amount;
    }
}
