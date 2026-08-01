package ruiseki.okmodular.common.tile;

import java.util.List;
import java.util.Set;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.IThemeApi;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import ruiseki.okcore.enums.RedstoneMode;
import ruiseki.okcore.helper.LangHelpers;
import ruiseki.okmodular.api.modular.IModularPort;
import ruiseki.okmodular.api.modular.IPortType;
import ruiseki.okmodular.api.recipe.core.IModularRecipe;
import ruiseki.okmodular.api.recipe.error.ErrorReason;
import ruiseki.okmodular.api.recipe.io.IModularRecipeOutput;
import ruiseki.okmodular.api.recipe.io.IRecipeOutput;
import ruiseki.okmodular.api.recipe.visitor.RecipeExecutionVisitor;
import ruiseki.okmodular.client.gui.widget.RedstoneModeWidget;
import ruiseki.okmodular.client.gui.widget.TileWidget;
import ruiseki.okmodular.common.item.ItemMachineBlueprint;
import ruiseki.okmodular.common.recipe.ProcessAgent;
import ruiseki.okmodular.common.recipe.RecipeLoader;

/**
 * Handles GUI construction and display logic for {@link TEMachineController}.
 */
public class GuiManager {

    private final TEMachineController controller;

    public GuiManager(TEMachineController controller) {
        this.controller = controller;
    }

    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel panel = new ModularPanel("machine_controller_gui");

        // Title
        String title = this.getStructureNameText();
        if (title == null || title.isEmpty()) {
            title = controller.getLocalizedName();
        }
        panel.child(new TileWidget(title));

        // Info Display (Black Rectangle)
        panel.child(
            Flow.column()
                .background(new IDrawable() {

                    @Override
                    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
                        GuiDraw.drawRect(x, y, width, height, 0xFF000000);
                    }
                })
                .pos(7, 6)
                .size(137, 72)
                .padding(4)
                .child(IKey.dynamic(() -> {
                    String name = this.getRecipeNameText();
                    if (name.isEmpty()) return EnumChatFormatting.WHITE + LangHelpers.localize("gui.status.idle");
                    return EnumChatFormatting.GOLD + name;
                })
                    .asWidget()
                    .leftRel(0f)
                    .marginBottom(2))
                .child(
                    IKey.dynamic(() -> EnumChatFormatting.WHITE + this.getStatusText())
                        .asWidget()
                        .leftRel(0f)
                        .marginBottom(2))
                .child(
                    IKey.dynamic(() -> EnumChatFormatting.RED + this.getValidationErrorText())
                        .asWidget()
                        .leftRel(0f)));

        // Blueprint slot
        addBlueprintSlot(panel, 151, 8);

        // Redstone Control Button
        addRedstoneButton(panel, syncManager, 151, 30);

        // Sync progress values
        IntSyncValue progressSyncer = new IntSyncValue(
            () -> (int) controller.getProcessAgent()
                .getProgress(),
            value -> controller.getProcessAgent()
                .setProgress(value));
        IntSyncValue maxProgressSyncer = new IntSyncValue(
            controller.getProcessAgent()::getMaxProgress,
            controller.getProcessAgent()::setMaxProgress);

        syncManager.syncValue("processProgress", progressSyncer);
        syncManager.syncValue("processMaxProgress", maxProgressSyncer);

        EnumSyncValue<ErrorReason, ?> errorReasonSyncer = new EnumSyncValue<>(
            ErrorReason.class,
            controller::getLastProcessErrorReason,
            controller::setLastProcessErrorReason);
        syncManager.syncValue("lastErrorReason", errorReasonSyncer);

        StringSyncValue errorDetailSyncer = new StringSyncValue(
            controller::getLastProcessErrorDetail,
            controller::setLastProcessErrorDetail);
        syncManager.syncValue("lastErrorDetail", errorDetailSyncer);

        StringSyncValue validationErrorSyncer = new StringSyncValue(
            controller::getLastValidationError,
            controller::setLastValidationError);
        syncManager.syncValue("lastValidationError", validationErrorSyncer);

        StringSyncValue recipeNameSyncer = new StringSyncValue(
            controller.getProcessAgent()::getCurrentRecipeName,
            controller.getProcessAgent()::setCurrentRecipeName);
        syncManager.syncValue("processRecipeName", recipeNameSyncer);

        BooleanSyncValue physicallyValidSyncer = new BooleanSyncValue(
            controller::isPhysicallyValid,
            controller::setPhysicallyValid);
        syncManager.syncValue("isPhysicallyValid", physicallyValidSyncer);

        syncManager.bindPlayerInventory(data.getPlayer());
        syncManager.registerSlotGroup("blueprint", 1, true);
        panel.bindPlayerInventory();

        return panel;
    }

    private void addBlueprintSlot(ModularPanel panel, int x, int y) {

        panel.child(
            new ItemSlot()
                .slot(
                    new ModularSlot(controller.getInventory(), TEMachineController.BLUEPRINT_SLOT)
                        .filter(stack -> stack != null && stack.getItem() instanceof ItemMachineBlueprint)
                        .slotGroup("blueprint"))
                .background(
                    (c, x1, y1, w, h, t) -> ((ModularGuiContext) c).getTheme()
                        .getWidgetTheme(IThemeApi.ITEM_SLOT)
                        .getTheme()
                        .getBackground()
                        .draw(c, x1, y1, w, h, t))
                .pos(x, y));
    }

    private void addRedstoneButton(ModularPanel panel, PanelSyncManager syncManager, int x, int y) {

        EnumSyncValue<RedstoneMode, ?> redstoneMode = new EnumSyncValue<>(
            RedstoneMode.class,
            controller::getRedstoneMode,
            controller::setRedstoneMode);
        syncManager.syncValue("redstoneMode", redstoneMode);

        BooleanSyncValue redstonePowered = new BooleanSyncValue(
            controller::isRedstonePowered,
            controller::setRedstonePowered);
        syncManager.syncValue("redstonePowered", redstonePowered);

        panel.child(new RedstoneModeWidget(redstoneMode).pos(x, y));
    }

    private String getStructureNameText() {
        if (controller.isFormed()) {
            String name = controller.getCustomStructureDisplayName();
            if (name != null && !name.isEmpty()) {
                // TODO: return LangHelpers.localize("structure." + name + ".name");
                return name;
            }
        }
        return "";
    }

    /**
     * Determines the primary status message to display in the GUI.
     * Priority order:
     * 1. Blueprint missing
     * 2. Structure not formed
     * 3. Paused by redstone
     * 4. Idle/Error status
     * 5. Processing status
     */
    private String getStatusText() {
        if (!hasBlueprint()) return LangHelpers.localize("gui.status.insert_blueprint");

        if (!controller.isFormed()) {
            if (controller.isPhysicallyValid()) {
                // Physically valid but requirements not met
                return LangHelpers.localize("gui.status.requirements_not_met");
            }
            if (hasValidationError()) return LangHelpers.localize("gui.status.structure_mismatch");
            return LangHelpers.localize("gui.status.structure_not_formed");
        }

        if (!controller.isRedstoneActive()) {
            // Its own text rather than PAUSED's. That one gained a "%s" for the condition
            // that stopped the recipe, and localizing it without one renders as
            // "Format error: Paused, waiting on: %s" - which is what a redstone-disabled
            // machine has been showing since the detail was added.
            return LangHelpers.localize("gui.status.redstone_off");
        }

        ProcessAgent agent = controller.getProcessAgent();
        if (agent.isRunning() || agent.isWaitingForOutput()) {
            return getProcessingStatusMessage(agent);
        }

        return getIdleStatusMessage();
    }

    /**
     * Returns the status message when machine is actively processing.
     * <p>
     * <b>The progress bar is the last thing shown, not the first.</b> This used to name
     * three reasons by hand and fall through to the percentage for everything else, so a
     * recipe stopped mid-run by its own conditions - or by the machine's - kept displaying
     * its frozen progress and said nothing about why it had stopped. The reason itself now
     * decides, the same way it does on the idle path; see
     * {@link ErrorReason#showsWhileRunning()}.
     */
    private String getProcessingStatusMessage(ProcessAgent agent) {
        ErrorReason lastError = controller.getLastProcessErrorReason();

        // Waiting to hand over its results is not an error the reason can describe better:
        // whatever it says, what the player needs is which ports are full.
        if (agent.isWaitingForOutput()) {
            return LangHelpers.localize("gui.status.output_full", diagnoseBlockedOutputs(controller.getOutputPorts()));
        }

        if (lastError.showsWhileRunning()) {
            // The detail goes in unconditionally, for the reason given on the idle path:
            // an extra argument is ignored, a missing one turns the text into "Format error".
            return LangHelpers.localize(lastError.getUnlocalizedName(), runningErrorDetail(lastError));
        }

        int percent = (int) (agent.getProgressPercent() * 100);
        if (agent.getMaxProgress() <= 0) percent = 0;

        return LangHelpers.localize("gui.status.processing", percent);
    }

    /**
     * The {@code %s} a running machine's status text wants.
     * <p>
     * Most reasons carry it on the controller - the condition that stopped the recipe, the
     * port type that could not take the output. The full-output ones are diagnosed here
     * instead, since the answer is a property of the ports right now rather than of the
     * moment the error was recorded.
     */
    private String runningErrorDetail(ErrorReason reason) {
        if (reason == ErrorReason.OUTPUT_FULL || reason == ErrorReason.BLOCK_OUTPUT_FULL) {
            return diagnoseBlockedOutputs(controller.getOutputPorts());
        }
        String detail = controller.getLastProcessErrorDetail();
        return detail == null ? "" : detail;
    }

    /**
     * Returns the status message when machine is idle.
     */
    private String getIdleStatusMessage() {
        if (RecipeLoader.getInstance()
            .getRecipes(controller.getRecipeGroup())
            .isEmpty()) {
            return LangHelpers.localize(ErrorReason.NO_RECIPES.getUnlocalizedName());
        }

        ErrorReason lastError = controller.getLastProcessErrorReason();

        // The reason decides whether it is worth saying; this used to be a hand-written
        // list of five, which silently swallowed the other thirteen.
        if (lastError.showsWhenIdle()) {
            // The detail goes in unconditionally. Some of these texts carry a %s and some
            // do not, and a text that wants one but is localized without it comes out as
            // "Format error: ..." - which is what an idle OUTPUT_FULL used to render as.
            // An extra argument is ignored, a missing one is not.
            String detail = controller.getLastProcessErrorDetail();
            return LangHelpers.localize(lastError.getUnlocalizedName(), detail == null ? "" : detail);
        }

        // Default idle state
        return LangHelpers.localize("gui.status.idle");
    }

    /**
     * Returns validation error text for display below status.
     * Only shown when structure is not formed and there's a specific error.
     */
    private String getValidationErrorText() {
        if (!hasBlueprint()) return "";
        if (controller.isFormed()) return "";
        if (!hasValidationError()) return "";
        return controller.getLastValidationError();
    }

    private boolean hasBlueprint() {
        String name = controller.getCustomStructureName();
        return name != null && !name.isEmpty();
    }

    private boolean hasValidationError() {
        String error = controller.getLastValidationError();
        return error != null && !error.isEmpty();
    }

    private String getRecipeNameText() {
        ProcessAgent agent = controller.getProcessAgent();
        if (agent.isRunning() && !agent.isWaitingForOutput()) {
            IModularRecipe recipe = agent.getCurrentRecipe();
            if (recipe != null) {
                String name = recipe.getName();
                if (name != null && !name.isEmpty()) return name;
            }
            String name = agent.getCurrentRecipeName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
        }
        return "";
    }

    /**
     * Diagnose which output types are blocked when waiting for output.
     */
    private String diagnoseBlockedOutputs(List<IModularPort> outputPorts) {
        ProcessAgent agent = controller.getProcessAgent();
        IModularRecipe currentRecipe = agent.getCurrentRecipe();

        if (currentRecipe != null) {
            StringBuilder blocked = new StringBuilder();
            RecipeExecutionVisitor contextSetter = new RecipeExecutionVisitor(
                RecipeExecutionVisitor.Mode.CHECK,
                outputPorts,
                agent,
                agent.getContext()
                    .getConditionContext());

            for (IRecipeOutput output : currentRecipe.getOutputs()) {
                output.accept(contextSetter); // Provides context implicitly
                if (output instanceof IModularRecipeOutput modularOutput) {
                    if (!modularOutput.process(
                        outputPorts,
                        true,
                        agent.getContext()
                            .getConditionContext())) {
                        if (blocked.length() > 0) blocked.append(", ");
                        blocked.append(
                            LangHelpers.localize(
                                "gui.port_type." + modularOutput.getPortType()
                                    .name()));
                    }
                }
            }
            if (blocked.length() > 0) return blocked.toString();
        }

        // Fallback: use cached output types
        Set<IPortType.Type> cachedTypes = agent.getCachedOutputTypes();
        if (!cachedTypes.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (IPortType.Type type : cachedTypes) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(LangHelpers.localize("gui.port_type." + type.name()));
            }
            return sb.toString();
        }

        return LangHelpers.localize("gui.status.unknown");
    }

}
