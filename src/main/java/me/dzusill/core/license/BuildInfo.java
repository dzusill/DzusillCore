package me.dzusill.core.license;

import java.io.InputStream;
import java.util.Properties;

import org.bukkit.plugin.Plugin;

/**
 * Reads the build-time enforcement flag Maven filters into a plugin's {@code license.properties}. Market builds run
 * with {@code -Plicensed} (enforce=true); a developer's own build defaults to false and skips the licence check
 * entirely, so there is no second branch to maintain.
 *
 * <p>
 * <b>The flag is read from the plugin's jar, not this one.</b> The obvious spelling —
 * {@code BuildInfo.class.getResourceAsStream("/license.properties")} — resolves against the class loader that loaded
 * <em>this</em> class, which is DzusillCore's. DzusillCore is free and is never built with {@code -Plicensed}, so every
 * downstream plugin would inherit {@code enforce=false} and the licence check would never run. Nothing would fail,
 * nothing would be logged, and the first sign of it would be the jar circulating unprotected.
 * {@link Plugin#getResource(String)} reads from the calling plugin's own jar, which is the question actually being
 * asked.
 * </p>
 *
 * <p>
 * A cracker can flip this constant, as they can any client-side check. It exists for the developer's convenience, not
 * as a security boundary.
 * </p>
 */
public final class BuildInfo {

    private BuildInfo() {
    }

    /**
     * @param plugin
     *            the plugin whose jar to read — the one being licensed, never DzusillCore
     * @return whether this build should enforce its licence
     */
    public static boolean enforces(Plugin plugin) {
        try (InputStream stream = plugin.getResource("license.properties")) {
            if (stream == null) {
                // No file at all means the plugin was never set up for licensing. Development builds and the free
                // tier both look like this, and both should run.
                return false;
            }
            Properties props = new Properties();
            props.load(stream);
            return Boolean.parseBoolean(props.getProperty("enforce", "false"));
        } catch (Exception unreadable) {
            return false;
        }
    }
}
