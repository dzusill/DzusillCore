package me.dzusill.core.util;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Applies a base64 {@code textures} value to a {@link SkullMeta} via reflection, instead of
 * Paper's {@code PlayerProfile}/{@code ProfileProperty} API (which doesn't exist on plain
 * Spigot/CraftBukkit). This is the same {@code GameProfile} + private-field trick cross-version
 * head plugins have used since 1.8; different CraftBukkit forks/versions have occasionally
 * renamed the backing field, so a couple of known names are tried before giving up.
 */
final class SkullTextures {

    private static final String[] KNOWN_PROFILE_FIELDS = {"profile", "field_178243_b"};

    private SkullTextures() {
    }

    static void apply(SkullMeta meta, String base64Texture) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "");
        profile.getProperties().put("textures", new Property("textures", base64Texture));

        Field field = findProfileField(meta.getClass());
        if (field == null) {
            throw new IllegalStateException(
                    "Could not locate the profile field on " + meta.getClass().getName()
                            + " - skull texture reflection trick may need a new field name for this server version");
        }

        try {
            field.setAccessible(true);
            // 1.21.4+: CraftMetaSkull.profile is ResolvableProfile (NMS), not GameProfile.
            // ResolvableProfile has a ResolvableProfile(GameProfile) constructor, so wrap it.
            Object value = field.getType().isAssignableFrom(GameProfile.class)
                    ? profile
                    : field.getType().getDeclaredConstructor(GameProfile.class).newInstance(profile);
            field.set(meta, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set skull texture via reflection", e);
        }
    }

    private static Field findProfileField(Class<?> type) {
        for (String name : KNOWN_PROFILE_FIELDS) {
            try {
                return type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // try the next known name
            }
        }
        // Fallback: any field whose type is GameProfile or has a GameProfile constructor
        for (Field field : type.getDeclaredFields()) {
            if (GameProfile.class.isAssignableFrom(field.getType())) {
                return field;
            }
            try {
                field.getType().getDeclaredConstructor(GameProfile.class);
                return field; // type wraps GameProfile (e.g. ResolvableProfile)
            } catch (NoSuchMethodException ignored) {
                // not a wrapper type
            }
        }
        return null;
    }
}
