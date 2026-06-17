package me.dzusill.core.example.menu;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.menu.Menu;
import me.dzusill.core.menu.PlayerMenuContext;
import me.dzusill.core.menu.meta.MenuMeta;
import me.dzusill.core.menu.template.MenuTemplate;
import me.dzusill.core.menu.template.Templates;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.util.ColorUtils;
import me.dzusill.core.util.ItemBuilder;
import org.bukkit.Material;

/**
 * Example menu showing the declarative GUI style that mirrors the command system: title, size and
 * open-permission come from {@link MenuMeta}, and slots are declared with the fluent, permission
 * aware {@link #button(int)} API. The bordered template fills the edges; only the interactive
 * buttons are declared here. The "restock" button carries a permission, so it is automatically
 * hidden from players who lack {@code core.shop.admin}.
 */
@MenuMeta(title = "<dark_purple>Example Shop", size = 27, permission = "core.shop")
public final class ShopMenu extends Menu {

    public ShopMenu(CorePlugin plugin, PlayerMenuContext context) {
        super(plugin, context);
    }

    @Override
    protected MenuTemplate template() {
        return Templates.bordered();
    }

    @Override
    protected void decorate() {
        button(13)
                .icon(new ItemBuilder(Material.DIAMOND)
                        .name("<aqua>Buy a diamond")
                        .lore("<gray>Click to purchase")
                        .glow()
                        .build())
                .onClick(event -> messages().sendComponent(context.player(),
                        ColorUtils.parse("<green>Purchased a diamond!")))
                .add();

        button(15)
                .icon(new ItemBuilder(Material.CHEST)
                        .name("<gold>Restock")
                        .lore("<gray>Admins only")
                        .build())
                .permission("core.shop.admin")
                .onClick(event -> messages().sendComponent(context.player(),
                        ColorUtils.parse("<green>Shop restocked!")))
                .add();
    }

    private MessageService messages() {
        return plugin.services().get(MessageService.class);
    }
}
