package me.dzusill.core.nms.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;

/**
 * Small reflection toolkit shared by NMS adapters. It resolves CraftBukkit and {@code net.minecraft} classes across the
 * two breakpoints in the supported range and caches member lookups so adapters stay terse. This is the same
 * {@code getHandle()} + private-field style used by {@code me.dzusill.core.util.SkullTextures}, generalised for reuse.
 *
 * <p>
 * The CraftBukkit base package is resolved lazily on first use (not in a static initialiser) so the class can load
 * under MockBukkit, where {@link Bukkit#getServer()} may be unset until a test mocks it.
 * </p>
 */
public final class Reflection {

    private static final AtomicReference<String> CRAFT_BUKKIT_PACKAGE = new AtomicReference<>();
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    private Reflection() {
    }

    /**
     * @return the server's CraftBukkit base package, e.g. {@code org.bukkit.craftbukkit.v1_16_R3} (≤1.20.4) or
     *         {@code org.bukkit.craftbukkit} (1.20.5+)
     */
    public static String craftBukkitPackage() {
        String cached = CRAFT_BUKKIT_PACKAGE.get();
        if (cached == null) {
            cached = Bukkit.getServer().getClass().getPackage().getName();
            CRAFT_BUKKIT_PACKAGE.set(cached);
        }
        return cached;
    }

    /**
     * Loads a CraftBukkit class by its package-relative name, e.g. {@code "entity.CraftPlayer"}.
     */
    public static Class<?> craftBukkitClass(String relativeName) {
        return classForName(craftBukkitPackage() + "." + relativeName);
    }

    /**
     * Loads a class by fully-qualified name, returning empty instead of throwing when absent. Useful for trying a
     * Mojang-mapped name and falling back to a Spigot-mapped one.
     */
    public static Optional<Class<?>> optionalClass(String fullyQualifiedName) {
        try {
            return Optional.of(Class.forName(fullyQualifiedName));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Invokes {@code getHandle()} on a CraftBukkit wrapper to obtain its NMS handle.
     */
    public static Object getHandle(Object craftBukkitObject) {
        return invoke(craftBukkitObject, method(craftBukkitObject.getClass(), "getHandle"));
    }

    /**
     * Resolves a (possibly inherited, possibly non-public) method, caching the result.
     */
    public static Method method(Class<?> owner, String name, Class<?>... parameterTypes) {
        String key = owner.getName() + "#" + name + paramKey(parameterTypes);
        return METHOD_CACHE.computeIfAbsent(key, ignored -> {
            for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
                try {
                    Method m = type.getDeclaredMethod(name, parameterTypes);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException ignore) {
                    // walk up the hierarchy
                }
            }
            throw new IllegalStateException("No method " + name + " on " + owner.getName());
        });
    }

    /**
     * Resolves a (possibly inherited, possibly non-public) field, caching the result.
     */
    public static Field field(Class<?> owner, String name) {
        String key = owner.getName() + "#" + name;
        return FIELD_CACHE.computeIfAbsent(key, ignored -> {
            for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
                try {
                    Field f = type.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignore) {
                    // walk up the hierarchy
                }
            }
            throw new IllegalStateException("No field " + name + " on " + owner.getName());
        });
    }

    public static Object invoke(Object target, Method method, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to invoke " + method, e);
        }
    }

    public static Object getFieldValue(Object target, Field field) {
        try {
            return field.get(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to read " + field, e);
        }
    }

    private static Class<?> classForName(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class not found: " + name, e);
        }
    }

    private static String paramKey(Class<?>[] parameterTypes) {
        if (parameterTypes.length == 0) {
            return "()";
        }
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> type : parameterTypes) {
            sb.append(type.getName()).append(',');
        }
        return sb.append(')').toString();
    }
}
