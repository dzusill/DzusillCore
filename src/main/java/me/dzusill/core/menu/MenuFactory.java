package me.dzusill.core.menu;

import me.dzusill.core.CorePlugin;

/**
 * Creates a fresh {@link Menu} for a player's {@link PlayerMenuContext}. Registered by key with the
 * {@link MenuRegistry}, this is the menu analogue of constructing a {@code CoreCommand} for the
 * command registry — typically just a constructor reference, e.g. {@code ShopMenu::new}.
 */
@FunctionalInterface
public interface MenuFactory {

    Menu create(CorePlugin plugin, PlayerMenuContext context);
}
