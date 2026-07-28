package ruiseki.okmodular.common.command;

import net.minecraft.command.ICommandSender;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import ruiseki.okcore.command.CommandMod;
import ruiseki.okcore.init.ModBase;

/**
 * Structure wand subcommand handler.
 * Builds: /&lt;modid&gt; wand save &lt;name&gt;
 */
public class CommandModularWand extends CommandMod {

    public static final String NAME = "wand";

    public CommandModularWand(ModBase mod) {
        super(mod, NAME);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public LiteralArgumentBuilder<ICommandSender> make() {
        return super.make().then(new CommandModularWandSave(getMod()).make());
    }
}
