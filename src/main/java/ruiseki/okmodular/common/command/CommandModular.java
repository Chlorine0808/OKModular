package ruiseki.okmodular.common.command;

import net.minecraft.command.ICommandSender;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import ruiseki.okcore.command.CommandMod;
import ruiseki.okcore.init.ModBase;

/**
 * Modular machinery subcommand handler.
 * Builds: /&lt;modid&gt; modular &lt;reload|list&gt;
 */
public class CommandModular extends CommandMod {

    public static final String NAME = "modular";

    public CommandModular(ModBase mod) {
        super(mod, NAME);
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public LiteralArgumentBuilder<ICommandSender> make() {
        return super.make().then(new CommandModularReload(getMod()).make())
            .then(new CommandModularList(getMod()).make());
    }
}
