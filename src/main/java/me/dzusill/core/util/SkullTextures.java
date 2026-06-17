package me.dzusill.core.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Applies a base64 textures value to a SkullMeta across Spigot/Paper 1.16.5–1.21.x.
 *
 * Two strategies:
 * 1. Bukkit PlayerProfile API (Spigot 1.18.1+): decodes base64 → skin URL → setOwnerProfile.
 *    Looked up via the SkullMeta interface (not CraftMetaSkull) to avoid IllegalAccessException
 *    from Paper's module restrictions on unexported CraftBukkit packages.
 * 2. GameProfile field reflection (1.16.5–1.17.x fallback): direct private-field set.
 *    coerceProfile wraps GameProfile into ResolvableProfile on 1.20.5+ Paper where the field
 *    type changed.
 */
final class SkullTextures {

    private static final String[] KNOWN_PROFILE_FIELDS = {"profile", "field_178243_b"};

    private SkullTextures() {
    }

    static void apply(SkullMeta meta, String base64Texture) {
        if (applyViaOwnerProfile(meta, base64Texture)) {
            return;
        }
        applyViaGameProfileField(meta, base64Texture);
    }

    /**
     * Bukkit-API path (Spigot 1.18.1+). Decodes the base64 JSON to extract the skin URL, builds
     * a PlayerProfile via Server#createProfile, sets the texture, then calls setOwnerProfile via
     * the SkullMeta interface handle (not CraftMetaSkull) to stay in accessible API packages.
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
            Object profile = server.getClass()
                    .getMethod("createProfile", UUID.class, String.class)
                    .invoke(server, UUID.randomUUID(), "");

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
     * Fallback for 1.16.5–1.17.x where PlayerProfile API is absent. Sets the private profile
     * field on CraftMetaSkull directly, wrapping GameProfile into ResolvableProfile when needed.
     */
    private static void applyViaGameProfileField(SkullMeta meta, String base64Texture) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "");
        profile.getProperties().put("textures", new Property("textures", base64Texture));

        Field field = findProfileField(meta.getClass());
        if (field == null) {
            throw new IllegalStateException(
                    "Could not locate profile field on " + meta.getClass().getName()
                            + " - needs a new field name for this server version");
        }

        try {
            field.setAccessible(true);
            field.set(meta, coerceProfile(field.getType(), profile));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set skull texture via reflection", e);
        }
    }

    private static URL extractSkinUrl(String base64Texture) {
        try {
            String json = new String(Base64.getDecoder().decode(base64Texture), StandardCharsets.UTF_8);
            int start = json.indexOf("\"url\":\"") + 7;
            if (start < 7) {
                return null;
            }
            int end = json.indexOf('"', start);
            if (end < 0) {
                return null;
            }
            return new URL(json.substring(start, end));
        } catch (Exception e) {
            return null;
        }
    }

    private static Object coerceProfile(Class<?> fieldType, GameProfile profile)
            throws ReflectiveOperationException {
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
