package me.dzusill.core.example.command;

import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.CoreCommand;
import me.dzusill.core.command.argument.Arguments;
import me.dzusill.core.command.meta.CommandMeta;
import me.dzusill.core.menu.MenuRegistry;

/**
 * Example command that opens a menu by key through the {@link MenuRegistry}, the GUI analogue of
 * how a command is registered once and dispatched by name. The registry resolves the player's
 * context and enforces the menu's own permission.
 */
@CommandMeta(name = "shop", permission = "core.shop", playerOnly = true, description = "Open the example shop")
public final class ShopCommand extends CoreCommand {

    private final MenuRegistry menus;

    public ShopCommand(MenuRegistry menus) {
        super();
        this.menus = menus;
    }

    @Override
    public void run(CommandContext context, Arguments args) {
        menus.open(context.player(), "shop");
    }
}
