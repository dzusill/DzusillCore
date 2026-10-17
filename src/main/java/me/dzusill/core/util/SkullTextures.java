package me.dzusill.core.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

/**
 * Applies a base64 textures value to a SkullMeta across Spigot/Paper 1.16.5–26.x.
 *
 * Three strategies, in order: 1. Paper's profile API: the textures value goes in as a {@code ProfileProperty}, exactly
 * as given, with no URL to parse. 2. Bukkit PlayerProfile API (Spigot 1.18.1+): decodes base64 → skin URL →
 * setOwnerProfile. Looked up via the SkullMeta interface (not CraftMetaSkull) to avoid IllegalAccessException from
 * Paper's module restrictions on unexported CraftBukkit packages. 3. GameProfile field reflection (1.16.5–1.17.x
 * fallback): direct private-field set. coerceProfile wraps GameProfile into ResolvableProfile on 1.20.5+ Paper where
 * the field type changed.
 *
 * <p>
 * The third path links against authlib, whose {@code GameProfile} became a record on Paper 26.3
 * ({@code getProperties()} is gone). It used to be reached for every real player skin, because Mojang's textures JSON
 * is pretty-printed ({@code "url" : "..."}) and the URL parse in path 2 expected no spaces — so a menu showing a real
 * player's head threw {@code NoSuchMethodError} and never opened. Path 1 never parses, path 2 now tolerates the
 * spacing, and a linkage failure in path 3 surfaces as an {@link IllegalStateException} like every other failure here.
 * </p>
 */
final class SkullTextures {

    private static final String[] KNOWN_PROFILE_FIELDS = {"profile", "field_178243_b"};

    private SkullTextures() {
    }

    private static final Pattern SKIN_URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    static void apply(SkullMeta meta, String base64Texture) {
        if (applyViaPaperProfile(meta, base64Texture)) {
            return;
        }
        if (applyViaOwnerProfile(meta, base64Texture)) {
            return;
        }
        try {
            applyViaGameProfileField(meta, base64Texture);
        } catch (LinkageError incompatible) {
            throw new IllegalStateException("No skull texture strategy works on this server version", incompatible);
        }
    }

    /**
     * Paper path. Builds a profile with {@code Server#createProfile}, puts the textures value on it as a
     * {@code ProfileProperty} and hands it to {@code SkullMeta#setPlayerProfile}. All by reflection: the core compiles
     * against the Spigot API, where none of these exist.
     */
    private static boolean applyViaPaperProfile(SkullMeta meta, String base64Texture) {
        try {
            Class<?> paperProfileClass = Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
            Class<?> propertyClass = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
            Method setPlayerProfile = SkullMeta.class.getMethod("setPlayerProfile", paperProfileClass);

            Object server = Bukkit.getServer();
            Object profile = server.getClass().getMethod("createProfile", UUID.class, String.class).invoke(server,
                    UUID.randomUUID(), "");
            if (!paperProfileClass.isInstance(profile)) {
                return false;
            }
            Object property = propertyClass.getConstructor(String.class, String.class).newInstance("textures",
                    base64Texture);
            paperProfileClass.getMethod("setProperty", propertyClass).invoke(profile, property);
            setPlayerProfile.invoke(meta, profile);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Missing on Spigot, or a server (MockBukkit) that declares the API and does not implement it: the next
            // strategy gets its turn.
            return false;
        }
    }

    /**
     * Bukkit-API path (Spigot 1.18.1+). Decodes the base64 JSON to extract the skin URL, builds a PlayerProfile via
     * Server#createProfile, sets the texture, then calls setOwnerProfile via the SkullMeta interface handle (not
     * CraftMetaSkull) to stay in accessible API packages.
     */
    private static boolean applyViaOwnerProfile(SkullMeta meta, String base64Texture) {
        try {
            Class<?> playerProfileClass = Class.forName("org.bukkit.profile.PlayerProfile");
            Class<?> playerTexturesClass = Class.forName("org.bukkit.profile.PlayerTextures");

            URL skinUrl = extractSkinUrl(base64Texture);
            if (skinUrl == null) {
                return false;
            }

            Object server = Bukkit.getServer();
            Object profile = server.getClass().getMethod("createProfile", UUID.class, String.class).invoke(server,
                    UUID.randomUUID(), "");

            Object textures = playerProfileClass.getMethod("getTextures").invoke(profile);
            playerTexturesClass.getMethod("setSkin", URL.class).invoke(textures, skinUrl);
            playerProfileClass.getMethod("setTextures", playerTexturesClass).invoke(profile, textures);

            // Look up via SkullMeta interface (org.bukkit.inventory.meta — accessible, exported)
            // rather than CraftMetaSkull (org.bukkit.craftbukkit — Paper restricts module access).
            SkullMeta.class.getMethod("setOwnerProfile", playerProfileClass).invoke(meta, profile);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return false;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set skull texture via Bukkit owner profile API", e);
        }
    }

    /**
     * Fallback for 1.16.5–1.17.x where PlayerProfile API is absent. Sets the private profile field on CraftMetaSkull
     * directly, wrapping GameProfile into ResolvableProfile when needed.
     */
    private static void applyViaGameProfileField(SkullMeta meta, String base64Texture) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "");
        profile.getProperties().put("textures", new Property("textures", base64Texture));

        Field field = findProfileField(meta.getClass());
        if (field == null) {
            throw new IllegalStateException("Could not locate profile field on " + meta.getClass().getName()
                    + " - needs a new field name for this server version");
        }

        try {
            field.setAccessible(true);
            field.set(meta, coerceProfile(field.getType(), profile));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set skull texture via reflection", e);
        }
    }

    /**
     * @return the skin URL in a textures value, or null. Mojang's own values are pretty-printed
     *         ({@code "url" : "http://..."}); head databases write them compact. Both parse.
     */
    static URL extractSkinUrl(String base64Texture) {
        try {
            String json = new String(Base64.getDecoder().decode(base64Texture.trim()), StandardCharsets.UTF_8);
            Matcher matcher = SKIN_URL.matcher(json);
            return matcher.find() ? new URL(matcher.group(1)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Object coerceProfile(Class<?> fieldType, GameProfile profile) throws ReflectiveOperationException {
        if (fieldType.isAssignableFrom(GameProfile.class)) {
            return profile;
        }
        Constructor<?> wrapper = fieldType.getDeclaredConstructor(GameProfile.class);
        wrapper.setAccessible(true);
        return wrapper.newInstance(profile);
    }

    private static Field findProfileField(Class<?> type) {
        for (String name : KNOWN_PROFILE_FIELDS) {
            try {
                return type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (GameProfile.class.isAssignableFrom(field.getType())) {
                return field;
            }
            try {
                field.getType().getDeclaredConstructor(GameProfile.class);
                return field;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
