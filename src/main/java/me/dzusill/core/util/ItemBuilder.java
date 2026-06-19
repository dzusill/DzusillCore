package me.dzusill.core.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Fluent builder for {@link ItemStack}s. Replaces ad-hoc {@code makeItem}/{@code makeHead} helpers with a single
 * chainable API that handles MiniMessage names/lore, glow, item flags, custom-textured skulls and persistent-data
 * tagging.
 *
 * <pre>{@code
 * ItemStack icon = new ItemBuilder(Material.DIAMOND).name("<aqua>Shiny").lore("<gray>Click to buy").glow().build();
 * }</pre>
 */
public final class ItemBuilder {

    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

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

    /**
     * Starts a builder for a custom-textured player head from a base64 textures value (the long string used by head
     * databases / warp GUIs, encoding {@code {"textures":{"SKIN":{"url":...}}}}). Convenience for
     * {@code new ItemBuilder(Material.PLAYER_HEAD).skull(base64Texture)}.
     *
     * <pre>{@code
     * ItemStack plus = ItemBuilder.head("eyJ0ZXh0dXJlcyI6...").name("<green>Add").build();
     * }</pre>
     */
    public static ItemBuilder head(String base64Texture) {
        return new ItemBuilder(Material.PLAYER_HEAD).skull(base64Texture);
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    /**
     * Sets the display name from a MiniMessage string.
     */
    public ItemBuilder name(String miniMessage) {
        return name(ColorUtils.parse(miniMessage));
    }

    /**
     * Sets the display name from a component, serialized to a legacy section-sign string.
     * {@code ItemMeta#displayName(Component)} is a Paper-only overload; legacy {@code setDisplayName(String)} renders
     * identically on the client and exists on every Bukkit implementation back to 1.8.
     */
    public ItemBuilder name(Component component) {
        meta.setDisplayName(LEGACY_SECTION.serialize(component));
        return this;
    }

    /**
     * Replaces the lore with the given MiniMessage lines.
     */
    public ItemBuilder lore(String... miniMessageLines) {
        return lore(Arrays.asList(miniMessageLines));
    }

    public ItemBuilder lore(List<String> miniMessageLines) {
        List<Component> parsed = ColorUtils.parse(miniMessageLines);
        List<String> legacy = new ArrayList<>(parsed.size());
        for (Component line : parsed) {
            legacy.add(LEGACY_SECTION.serialize(line));
        }
        meta.setLore(legacy);
        return this;
    }

    /**
     * Adds an enchantment glint without showing the enchantment text.
     */
    public ItemBuilder glow() {
        // The Java constant for "Unbreaking" is named DURABILITY on Spigot API 1.16.5 and
        // UNBREAKING from ~1.20.5 onward - NOT a deprecated-alias situation, the old name is
        // gone on newer servers. getByKey() with the stable vanilla "minecraft:unbreaking" id
        // resolves correctly on every version in between.
        meta.addEnchant(Enchantment.getByKey(NamespacedKey.minecraft("unbreaking")), 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        meta.addItemFlags(flags);
        return this;
    }

    /**
     * Sets the skull owner by UUID so the server renders that player's skin.
     *
     * @throws IllegalStateException
     *             if this builder is not for a {@link SkullMeta} item
     */
    public ItemBuilder skullOwner(UUID uuid) {
        if (!(meta instanceof SkullMeta skullMeta)) {
            throw new IllegalStateException("skullOwner() requires a PLAYER_HEAD material");
        }
        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        return this;
    }

    /**
     * Applies a base64 texture value to a player-head item.
     *
     * @throws IllegalStateException
     *             if this builder is not for a {@link SkullMeta} item
     */
    public ItemBuilder skull(String base64Texture) {
        if (!(meta instanceof SkullMeta skullMeta)) {
            throw new IllegalStateException("skull() requires a PLAYER_HEAD material");
        }
        SkullTextures.apply(skullMeta, base64Texture);
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
