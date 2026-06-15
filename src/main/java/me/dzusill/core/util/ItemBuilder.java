package me.dzusill.core.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Fluent builder for {@link ItemStack}s. Replaces ad-hoc {@code makeItem}/{@code makeHead}
 * helpers with a single chainable API that handles MiniMessage names/lore, glow, item flags,
 * custom-textured skulls and persistent-data tagging.
 *
 * <pre>{@code
 * ItemStack icon = new ItemBuilder(Material.DIAMOND)
 *         .name("<aqua>Shiny")
 *         .lore("<gray>Click to buy")
 *         .glow()
 *         .build();
 * }</pre>
 */
public final class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack source) {
        this.item = source.clone();
        this.meta = item.getItemMeta();
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    /**
     * Sets the display name from a MiniMessage string.
     */
    public ItemBuilder name(String miniMessage) {
        meta.displayName(ColorUtils.parse(miniMessage));
        return this;
    }

    public ItemBuilder name(Component component) {
        meta.displayName(component);
        return this;
    }

    /**
     * Replaces the lore with the given MiniMessage lines.
     */
    public ItemBuilder lore(String... miniMessageLines) {
        return lore(Arrays.asList(miniMessageLines));
    }

    public ItemBuilder lore(List<String> miniMessageLines) {
        meta.lore(ColorUtils.parse(miniMessageLines));
        return this;
    }

    /**
     * Adds an enchantment glint without showing the enchantment text.
     */
    public ItemBuilder glow() {
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    /**
     * Applies a base64 texture value to a player-head item.
     *
     * @throws IllegalStateException if this builder is not for a {@link SkullMeta} item
     */
    public ItemBuilder skull(String base64Texture) {
        if (!(meta instanceof SkullMeta skullMeta)) {
            throw new IllegalStateException("skull() requires a PLAYER_HEAD material");
        }
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new ProfileProperty("textures", base64Texture));
        skullMeta.setPlayerProfile(profile);
        return this;
    }

    /**
     * Writes a value into the item's persistent data container.
     */
    public <P, C> ItemBuilder pdc(NamespacedKey key, PersistentDataType<P, C> type, C value) {
        meta.getPersistentDataContainer().set(key, type, value);
        return this;
    }

    /**
     * Finalizes the metadata and returns the built item.
     */
    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
