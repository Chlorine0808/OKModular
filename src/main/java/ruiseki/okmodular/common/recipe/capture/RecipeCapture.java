package ruiseki.okmodular.common.recipe.capture;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import com.google.gson.JsonObject;

import ruiseki.okmodular.MachineryModule;
import ruiseki.okmodular.OKModular;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.common.network.PacketReloadNEI;
import ruiseki.okmodular.common.recipe.RecipeLoader;
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
     * Appends the draft to its file under {@code recipes/captured/}. Recipes are not reloaded.
     *
     * @throws IllegalStateException when the draft is empty
     */
    public static File save(RecipeDraft draft) throws IOException {
        File recipesDir = new File(MachineryModule.getConfigDir(), "recipes");
        CapturedRecipeFile.append(recipesDir, draft.getGroup(), draft.toRecipeJson());
        return CapturedRecipeFile.fileFor(recipesDir, draft.getGroup());
    }

    /** Reloads only the recipes, since a save changes nothing else, and refreshes NEI on every client. */
    public static void reloadRecipes() throws Exception {
        RecipeLoader.getInstance()
            .reload(MachineryModule.getConfigDir());
        OKModular.instance.getPacketHandler()
            .sendToAll(new PacketReloadNEI());
    }
}
