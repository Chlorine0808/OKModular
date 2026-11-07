package ruiseki.okmodular.common.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.command.ICommandSender;

import org.junit.jupiter.api.Test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContext;

/**
 * capture takes [duration] [energy] [group], each optional from the right. Parses only; nothing runs.
 */
class CaptureArgumentsTest {

    private static CommandContext<ICommandSender> parse(String input) {
        CommandDispatcher<ICommandSender> dispatcher = new CommandDispatcher<>();
        dispatcher.register(CommandModularRecipe.captureNode(null));
        ParseResults<ICommandSender> parsed = dispatcher.parse(input, null);
        assertTrue(
            parsed.getExceptions()
                .isEmpty(),
            () -> input + ": " + parsed.getExceptions());
        assertEquals(
            input.length(),
            parsed.getReader()
                .getCursor(),
            input + " was not fully parsed");
        CommandContext<ICommandSender> context = parsed.getContext()
            .build(input);
        assertNotNull(context.getCommand(), input + " has no command to run");
        return context;
    }

    @Test
    void noArgumentsUseTheDefaults() {
        CommandContext<ICommandSender> context = parse("capture");
        assertEquals(CommandModularRecipe.DEFAULT_DURATION, CommandModularRecipe.captureDuration(context));
        assertEquals(0, CommandModularRecipe.captureEnergy(context));
        assertNull(CommandModularRecipe.captureGroup(context));
    }

    @Test
    void durationAlone() {
        CommandContext<ICommandSender> context = parse("capture 40");
        assertEquals(40, CommandModularRecipe.captureDuration(context));
        assertEquals(0, CommandModularRecipe.captureEnergy(context));
    }

    @Test
    void secondArgumentIsEnergyPerTick() {
        CommandContext<ICommandSender> context = parse("capture 40 20");
        assertEquals(40, CommandModularRecipe.captureDuration(context));
        assertEquals(20, CommandModularRecipe.captureEnergy(context));
        assertNull(CommandModularRecipe.captureGroup(context));
    }

    @Test
    void thirdArgumentIsGroup() {
        CommandContext<ICommandSender> context = parse("capture 40 0 Ore_Crusher");
        assertEquals(0, CommandModularRecipe.captureEnergy(context));
        assertEquals("Ore_Crusher", CommandModularRecipe.captureGroup(context));
    }
}
