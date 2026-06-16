package me.dzusill.core.menu;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.message.Messages;
import me.dzusill.core.service.Service;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Registers menus by key so they can be opened from anywhere by name, the GUI analogue of
 * {@link me.dzusill.core.command.CommandRegistry}. Register a factory once (typically a constructor
 * reference) and open it later with {@link #open(Player, String)}; the registry resolves the
 * per-player {@link PlayerMenuContext}, enforces the menu's {@code @MenuMeta} permission (replying
 * with {@link Messages#NO_PERMISSION} on denial) and opens it.
 */
public final class MenuRegistry implements Service {

    private final CorePlugin plugin;
    private final MenuManager menus;
    private final MessageService messages;
    private final Map<String, MenuFactory> factories = new HashMap<>();

    public MenuRegistry(CorePlugin plugin, MenuManager menus, MessageService messages) {
        this.plugin = plugin;
        this.menus = menus;
        this.messages = messages;
    }

    /**
     * Registers a menu factory under {@code key} (case-insensitive).
     */
    public void register(String key, MenuFactory factory) {
        factories.put(key.toLowerCase(Locale.ROOT), factory);
    }

    public boolean isRegistered(String key) {
        return factories.containsKey(key.toLowerCase(Locale.ROOT));
    }

    /**
     * Opens the menu registered under {@code key} for {@code player}.
     *
     * @return {@code true} if the menu was opened, {@code false} if the key is unknown or the player
     *         lacks the menu's permission
     */
    public boolean open(Player player, String key) {
        return open(player, key, context -> {
        });
    }

    /**
     * Opens the menu registered under {@code key}, first running {@code seed} against the player's
     * context (e.g. to pass the item being edited) so the menu can read it in {@code decorate()}.
     *
     * @return {@code true} if the menu was opened, {@code false} if the key is unknown or the player
     *         lacks the menu's permission
     */
    public boolean open(Player player, String key, Consumer<PlayerMenuContext> seed) {
        MenuFactory factory = factories.get(key.toLowerCase(Locale.ROOT));
        if (factory == null) {
            return false;
        }
        PlayerMenuContext context = menus.context(player);
        seed.accept(context);
        Menu menu = factory.create(plugin, context);
        if (!menu.permission().isEmpty() && !player.hasPermission(menu.permission())) {
            messages.send(player, Messages.NO_PERMISSION);
            return false;
        }
        menu.open();
        return true;
    }
}
