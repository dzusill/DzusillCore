package me.dzusill.core.example.command;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.CommandException;
import me.dzusill.core.command.CoreCommand;
import me.dzusill.core.command.argument.Arguments;
import me.dzusill.core.command.argument.types.OnlinePlayerArgument;
import me.dzusill.core.command.meta.CommandMeta;
import me.dzusill.core.util.ColorUtils;

/**
 * Example leaf command demonstrating declarative metadata, an optional typed argument with automatic tab-completion,
 * and player-only enforcement. Usage: {@code /heal [target]}.
 */
@CommandMeta(name = "heal", permission = "core.heal", playerOnly = true, description = "Heal yourself or another player")
public final class HealCommand extends CoreCommand {

    public HealCommand() {
        super();
        optionalArg("target", new OnlinePlayerArgument());
    }

    @Override
    public void run(CommandContext context, Arguments args) throws CommandException {
        Player target = args.getOr("target", context.player());
        double maxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        target.setHealth(maxHealth);
        target.setFoodLevel(20);

        context.messages().sendComponent(target, ColorUtils.parse("<green>You have been healed."));
        if (!target.equals(context.sender())) {
            context.messages().sendComponent(context.sender(),
                    ColorUtils.parse("<green>Healed <yellow>" + target.getName() + "</yellow>."));
        }
    }
}
