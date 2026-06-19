package me.dzusill.core.nms;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.Server;

/**
 * Detects the {@link MinecraftVersion} of the running server, robust across the whole 1.16.5–1.21.x range. The legacy
 * {@code vX_Y_RZ} CraftBukkit package suffix is <em>not</em> used as the primary signal because it disappeared on Paper
 * 1.20.5+; it is only captured opportunistically into {@link MinecraftVersion#craftBukkitTag()} for adapters that still
 * rely on it.
 */
public final class VersionDetector {

    /** Matches the version prefix of {@code Bukkit.getBukkitVersion()} ("1.21.1-R0.1-SNAPSHOT"). */
    private static final Pattern BUKKIT_VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    /** Matches the legacy relocated CraftBukkit package token, e.g. {@code v1_16_R3}. */
    private static final Pattern CRAFTBUKKIT_TAG = Pattern.compile("v\\d+_\\d+_R\\d+");

    private VersionDetector() {
    }

    /**
     * Detects the running server's version.
     *
     * @throws IllegalStateException
     *             if no version string could be parsed (should never happen on a real server; indicates the API
     *             contract changed)
     */
    public static MinecraftVersion detect() {
        return detect(Bukkit.getServer());
    }

    /**
     * Detects the version from an explicit {@link Server}. Exposed for testing.
     */
    static MinecraftVersion detect(Server server) {
        String versionString = paperMinecraftVersion(server).orElseGet(server::getBukkitVersion);
        Matcher matcher = BUKKIT_VERSION.matcher(versionString);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not parse Minecraft version from \"" + versionString + "\"");
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
        return new MinecraftVersion(major, minor, patch, craftBukkitTag(server));
    }

    /**
     * Modern Paper exposes {@code Server#getMinecraftVersion()} returning a clean "1.21.1". We call it reflectively
     * because the framework compiles against Spigot 1.16.5, which has no such method.
     */
    private static Optional<String> paperMinecraftVersion(Server server) {
        try {
            Object value = server.getClass().getMethod("getMinecraftVersion").invoke(server);
            return value instanceof String s && !s.isBlank() ? Optional.of(s) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> craftBukkitTag(Server server) {
        Package pkg = server.getClass().getPackage();
        if (pkg == null) {
            return Optional.empty();
        }
        Matcher matcher = CRAFTBUKKIT_TAG.matcher(pkg.getName());
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }
}
