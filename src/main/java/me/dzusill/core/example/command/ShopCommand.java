package me.dzusill.core.example.command;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.CoreCommand;
import me.dzusill.core.command.argument.Arguments;
import me.dzusill.core.command.meta.CommandMeta;
import me.dzusill.core.example.menu.ShopMenu;
import me.dzusill.core.menu.MenuManager;

/**
 * Example command that opens a menu, showing how commands and the menu system connect through the
 * per-player {@link me.dzusill.core.menu.PlayerMenuContext}.
 */
@CommandMeta(name = "shop", permission = "core.shop", playerOnly = true, description = "Open the example shop")
public final class ShopCommand extends CoreCommand {

    private final CorePlugin plugin;
    private final MenuManager menus;

    public ShopCommand(CorePlugin plugin, MenuManager menus) {
        super();
        this.plugin = plugin;
        this.menus = menus;
    }

    @Override
    public void run(CommandContext context, Arguments args) {
        new ShopMenu(plugin, menus.context(context.player())).open();
    }
}
