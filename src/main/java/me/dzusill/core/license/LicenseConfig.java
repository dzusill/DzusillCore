package me.dzusill.core.license;

import org.bukkit.plugin.Plugin;

import me.dzusill.core.config.AbstractConfig;

/**
 * Typed view over {@code license.yml}. Only the key and the verify URL live here; enforcement itself is a build flag
 * ({@link BuildInfo}), never a customer-visible toggle.
 *
 * <p>
 * Both may be blank. An empty verify URL means "use the default compiled into {@link LicenseModule}", and an empty key
 * is normal on a BuiltByBit purchase, where the key was injected into the jar at download time and the buyer never sees
 * this file at all.
 * </p>
 */
public final class LicenseConfig extends AbstractConfig {

    public LicenseConfig(Plugin plugin) {
        super(plugin, "license.yml");
    }

    public String key() {
        return raw().getString("key", "").trim();
    }

    public String verifyUrl() {
        return raw().getString("verify-url", "").trim();
    }
}
