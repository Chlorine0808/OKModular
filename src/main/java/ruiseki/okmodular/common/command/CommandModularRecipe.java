package ruiseki.okmodular.common.command;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import ruiseki.okcore.command.CommandMod;
import ruiseki.okcore.helper.LangHelpers;
import ruiseki.okcore.init.ModBase;
import ruiseki.okmodular.common.recipe.capture.CapturedRecipeFile;
import ruiseki.okmodular.common.recipe.capture.RecipeCapture;
import ruiseki.okmodular.common.recipe.capture.RecipeDraft;
import ruiseki.okmodular.common.recipe.capture.RecipeDraftManager;
import ruiseki.okmodular.common.tile.TEMachineController;

/**
 * Captures the ports of the machine in view into a draft, edits it and saves it as a recipe.
 * Builds: /&lt;modid&gt; recipe &lt;capture|show|amount|remove|duration|energy|save|discard&gt;
 */
public class CommandModularRecipe extends CommandMod {

    public static final String NAME = "recipe";

    private static final double REACH = 5.0;

    public CommandModularRecipe(ModBase mod) {
        super(mod, NAME);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public LiteralArgumentBuilder<ICommandSender> make() {
        return super.make().then(captureNode())
            .then(literal("show").executes(ctx -> edit(ctx, draft -> {})))
            .then(
                literal("amount").then(
                    intArg("number").then(
                        RequiredArgumentBuilder
                            .<ICommandSender, Integer>argument("amount", IntegerArgumentType.integer(1))
                            .executes(
                                ctx -> edit(
                                    ctx,
                                    draft -> draft.setAmount(
                                        IntegerArgumentType.getInteger(ctx, "number"),
                                        IntegerArgumentType.getInteger(ctx, "amount")))))))
            .then(
                literal("remove").then(
                    intArg("number").executes(
                        ctx -> edit(ctx, draft -> draft.remove(IntegerArgumentType.getInteger(ctx, "number"))))))
            .then(
                literal("duration").then(
                    RequiredArgumentBuilder.<ICommandSender, Integer>argument("ticks", IntegerArgumentType.integer(1))
                        .executes(
                            ctx -> edit(
                                ctx,
                                draft -> draft.setDuration(IntegerArgumentType.getInteger(ctx, "ticks"))))))
            .then(energyNode())
            .then(literal("save").executes(this::save))
            .then(literal("discard").executes(this::discard));
    }

    private LiteralArgumentBuilder<ICommandSender> captureNode() {
        return literal("capture").then(
            RequiredArgumentBuilder.<ICommandSender, Integer>argument("duration", IntegerArgumentType.integer(1))
                .executes(ctx -> capture(ctx, null))
                .then(
                    RequiredArgumentBuilder.<ICommandSender, String>argument("group", StringArgumentType.word())
                        .executes(ctx -> capture(ctx, StringArgumentType.getString(ctx, "group")))));
    }

    private LiteralArgumentBuilder<ICommandSender> energyNode() {
        return literal("energy").then(
            RequiredArgumentBuilder.<ICommandSender, Integer>argument("amount", IntegerArgumentType.integer(0))
                .executes(
                    ctx -> edit(ctx, draft -> draft.setEnergy(IntegerArgumentType.getInteger(ctx, "amount"), true)))
                .then(
                    RequiredArgumentBuilder.<ICommandSender, Boolean>argument("perTick", BoolArgumentType.bool())
                        .executes(
                            ctx -> edit(
                                ctx,
                                draft -> draft.setEnergy(
                                    IntegerArgumentType.getInteger(ctx, "amount"),
                                    BoolArgumentType.getBool(ctx, "perTick"))))));
    }

    private static LiteralArgumentBuilder<ICommandSender> literal(String name) {
        return LiteralArgumentBuilder.<ICommandSender>literal(name);
    }

    private static RequiredArgumentBuilder<ICommandSender, Integer> intArg(String name) {
        return RequiredArgumentBuilder.<ICommandSender, Integer>argument(name, IntegerArgumentType.integer());
    }

    @Override
    public int run(CommandContext<ICommandSender> context) {
        printErrorToChat(context.getSource(), LangHelpers.localize("command.okmodular.recipe_usage"));
        return Command.SINGLE_SUCCESS;
    }

    private int capture(CommandContext<ICommandSender> context, String group) {
        ICommandSender sender = context.getSource();
        if (!(sender instanceof EntityPlayer player)) return playerOnly(sender);

        if (group != null && !CapturedRecipeFile.isValidGroup(group)) {
            printErrorToChat(sender, LangHelpers.localize("command.okmodular.recipe_bad_group", group));
            return Command.SINGLE_SUCCESS;
        }
        TEMachineController controller = lookedAtController(player);
        if (controller == null) {
            printErrorToChat(sender, LangHelpers.localize("command.okmodular.recipe_no_machine"));
            return Command.SINGLE_SUCCESS;
        }
        if (!controller.isFormed()) {
            printErrorToChat(sender, LangHelpers.localize("command.okmodular.recipe_not_formed"));
            return Command.SINGLE_SUCCESS;
        }
        int duration = IntegerArgumentType.getInteger(context, "duration");
        RecipeDraft draft = RecipeCapture.capture(controller, duration, group);
        if (draft == null) {
            printErrorToChat(sender, LangHelpers.localize("command.okmodular.recipe_nothing_captured"));
            return Command.SINGLE_SUCCESS;
        }
        RecipeDraftManager.getInstance()
            .put(player.getUniqueID(), draft);
        sendList(sender, draft);
        return Command.SINGLE_SUCCESS;
    }

    /** Applies an edit to the sender's draft, then prints the list. A rejected edit leaves the draft as it was. */
    private int edit(CommandContext<ICommandSender> context, Consumer<RecipeDraft> edit) {
        ICommandSender sender = context.getSource();
        if (!(sender instanceof EntityPlayer player)) return playerOnly(sender);
        RecipeDraft draft = RecipeDraftManager.getInstance()
            .get(player.getUniqueID());
        if (draft == null) return noDraft(sender);
        try {
            edit.accept(draft);
        } catch (IllegalArgumentException e) {
            printErrorToChat(sender, LangHelpers.localize("command.okmodular.recipe_edit_failed", e.getMessage()));
        }
        sendList(sender, draft);
        return Command.SINGLE_SUCCESS;
    }

    private int save(CommandContext<ICommandSender> context) {
        ICommandSender sender = context.getSource();
        if (!(sender instanceof EntityPlayer player)) return playerOnly(sender);
        UUID id = player.getUniqueID();
        RecipeDraft draft = RecipeDraftManager.getInstance()
            .get(id);
        if (draft == null) return noDraft(sender);
        if (!draft.hasEntries()) {
            printErrorToChat(sender, LangHelpers.localize("command.okmodular.recipe_empty_draft"));
            return Command.SINGLE_SUCCESS;
        }
        try {
            File file = RecipeCapture.save(draft, sender);
            RecipeDraftManager.getInstance()
                .clear(id);
            sender.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GREEN + LangHelpers.localize("command.okmodular.recipe_saved", file.getPath())));
        } catch (IOException | IllegalStateException e) {
            printErrorToChat(sender, LangHelpers.localize("command.okmodular.recipe_save_failed", e.getMessage()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int discard(CommandContext<ICommandSender> context) {
        ICommandSender sender = context.getSource();
        if (!(sender instanceof EntityPlayer player)) return playerOnly(sender);
        RecipeDraftManager.getInstance()
            .clear(player.getUniqueID());
        sender.addChatMessage(
            new ChatComponentText(
                EnumChatFormatting.YELLOW + LangHelpers.localize("command.okmodular.recipe_discarded")));
        return Command.SINGLE_SUCCESS;
    }

    private int playerOnly(ICommandSender sender) {
        printErrorToChat(sender, LangHelpers.localize("command.okmodular.recipe_player_only"));
        return Command.SINGLE_SUCCESS;
    }

    private int noDraft(ICommandSender sender) {
        printErrorToChat(sender, LangHelpers.localize("command.okmodular.recipe_no_draft"));
        return Command.SINGLE_SUCCESS;
    }

    private static void sendList(ICommandSender sender, RecipeDraft draft) {
        for (IChatComponent line : RecipeDraftChat.lines(draft)) sender.addChatMessage(line);
    }

    /** Server-side ray trace from the eyes, because EntityLivingBase.rayTrace is client-only in 1.7.10. */
    private static TEMachineController lookedAtController(EntityPlayer player) {
        Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + 1.62 - player.yOffset, player.posZ);
        Vec3 look = player.getLook(1.0F);
        Vec3 end = eye.addVector(look.xCoord * REACH, look.yCoord * REACH, look.zCoord * REACH);
        MovingObjectPosition hit = player.worldObj.rayTraceBlocks(eye, end);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        TileEntity te = player.worldObj.getTileEntity(hit.blockX, hit.blockY, hit.blockZ);
        return te instanceof TEMachineController controller ? controller : null;
    }
}
