package ruiseki.okmodular.common.recipe.capture;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.google.gson.JsonObject;

import ruiseki.okcore.helper.LangHelpers;
import ruiseki.okmodular.MachineryModule;
import ruiseki.okmodular.OKModular;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.common.tile.TEMachineController;

/** Builds a draft from a controller's ports and appends a finished draft to its group file. */
public final class RecipeCapture {

    private RecipeCapture() {}

    /**
     * Reads every port that has a capturer.
     *
     * @param group null for the controller's first recipe group
     * @return null when every captured port is empty
     */
    public static RecipeDraft capture(TEMachineController controller, int duration, String group) {
        RecipeDraft draft = new RecipeDraft(group != null ? group : controller.getRecipeGroup(), duration);
        for (Map.Entry<IPortType.Type, PortCapturer> e : PortCapturers.all()
            .entrySet()) {
            for (IModularPort port : controller.getPortManager()
                .getInputPorts(e.getKey())) {
                for (JsonObject entry : e.getValue()
                    .capture(port, false)) draft.addInput(entry);
            }
            for (IModularPort port : controller.getPortManager()
                .getOutputPorts(e.getKey())) {
                for (JsonObject entry : e.getValue()
                    .capture(port, true)) draft.addOutput(entry);
            }
        }
        return draft.size() == 0 ? null : draft;
    }

    /**
     * Appends the draft to {@code recipes/captured/<group>.json} and reloads recipes.
     * A failed reload does not undo the write; it is reported to the sender.
     *
     * @throws IllegalStateException when the draft is empty
     */
    public static File save(RecipeDraft draft, ICommandSender sender) throws IOException {
        File recipesDir = new File(MachineryModule.getConfigDir(), "recipes");
        CapturedRecipeFile.append(recipesDir, draft.getGroup(), draft.toRecipeJson());
        File file = CapturedRecipeFile.fileFor(recipesDir, draft.getGroup());
        reload(sender);
        return file;
    }

    private static void reload(ICommandSender sender) {
        MachineryModule module = OKModular.instance.getModuleManager()
            .getModuleByType(MachineryModule.class);
        if (module == null || !module.isEnable()) return;
        try {
            module.reload(sender);
        } catch (Exception e) {
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.RED
                        + LangHelpers.localize("command.okmodular.recipe_reload_failed", e.getMessage())));
        }
    }
}
