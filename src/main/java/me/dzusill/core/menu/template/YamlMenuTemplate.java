package me.dzusill.core.menu.template;

import me.dzusill.core.config.Config;
import me.dzusill.core.menu.Menu;
import me.dzusill.core.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/**
 * A {@link MenuTemplate} defined entirely in {@code menus.yml}, so layouts (filler, border and
 * fixed decorative items) can be tuned without recompiling. This is what lets a server owner
 * restyle GUIs and a developer avoid hard-coding every decorative item.
 *
 * <p>Expected YAML shape under the given path:
 * <pre>
 * example:
 *   filler:
 *     material: GRAY_STAINED_GLASS_PANE
 *     name: " "
 *   border: true        # if true, filler is applied to the border; otherwise to all empty slots
 *   items:
 *     info:
 *       material: BOOK
 *       name: "&lt;aqua&gt;Info"
 *       lore: ["&lt;gray&gt;Line one"]
 *       slots: [4]
 * </pre>
 */
public final class YamlMenuTemplate extends AbstractMenuTemplate {

    private final Config config;
    private final String path;

    public YamlMenuTemplate(Config config, String path) {
        this.config = config;
        this.path = path;
    }

    @Override
    public void apply(Menu menu) {
        ConfigurationSection root = config.getConfigurationSection(path);
        if (root == null) {
            return;
        }

        applyFiller(menu, root);
        applyItems(menu, root.getConfigurationSection("items"));
    }

    private void applyFiller(Menu menu, ConfigurationSection root) {
        ConfigurationSection filler = root.getConfigurationSection("filler");
        if (filler == null) {
            return;
        }
        ItemStack item = buildItem(filler);
        if (root.getBoolean("border", false)) {
            applyBorder(menu, item);
        } else {
            applyFill(menu, item);
        }
    }

    private void applyItems(Menu menu, ConfigurationSection items) {
        if (items == null) {
            return;
        }
        for (String key : items.getKeys(false)) {
            ConfigurationSection section = items.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            ItemStack item = buildItem(section);
            for (int slot : section.getIntegerList("slots")) {
                menu.setItem(slot, item);
            }
        }
    }

    private ItemStack buildItem(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }
        ItemBuilder builder = new ItemBuilder(material);
        if (section.contains("name")) {
            builder.name(section.getString("name"));
        }
        if (section.isList("lore")) {
            builder.lore(section.getStringList("lore"));
        }
        return builder.build();
    }
}
