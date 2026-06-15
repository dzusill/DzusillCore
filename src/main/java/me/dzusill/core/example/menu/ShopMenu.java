package me.dzusill.core.example.menu;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.menu.Menu;
import me.dzusill.core.menu.MenuItem;
import me.dzusill.core.menu.PlayerMenuContext;
import me.dzusill.core.menu.template.MenuTemplate;
import me.dzusill.core.menu.template.Templates;
import me.dzusill.core.util.ColorUtils;
import me.dzusill.core.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/**
 * Example menu showing how little a concrete GUI needs: a title, a size, an opt-in layout
 * {@link MenuTemplate}, and per-item click handlers. The bordered template fills the edges; only
 * the interactive items are declared here.
 */
public final class ShopMenu extends Menu {

    public ShopMenu(CorePlugin plugin, PlayerMenuContext context) {
        super(plugin, context);
    }

    @Override
    public Component title() {
        return ColorUtils.parse("<dark_purple>Example Shop");
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    protected MenuTemplate template() {
        return Templates.bordered();
    }

    @Override
    protected void decorate() {
        set(13, MenuItem.of(
                new ItemBuilder(Material.DIAMOND)
                        .name("<aqua>Buy a diamond")
                        .lore("<gray>Click to purchase")
                        .glow()
                        .build(),
                event -> context.player().sendMessage(ColorUtils.parse("<green>Purchased a diamond!"))));
    }
}
