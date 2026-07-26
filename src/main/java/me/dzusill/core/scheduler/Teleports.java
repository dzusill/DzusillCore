package me.dzusill.core.scheduler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Bridges to Paper's {@code Entity#teleportAsync(Location)}.
 *
 * <p>
 * Resolved reflectively because DzusillCore compiles against the Spigot 1.16.5 API surface, which has no such method.
 * Where it is missing (plain old Spigot) the caller must already be on the main thread, so a plain
 * {@link Entity#teleport(Location)} is the correct fallback.
 * </p>
 */
final class Teleports {

    private static final Method TELEPORT_ASYNC = resolve();

    private Teleports() {
    }

    private static Method resolve() {
        try {
            return Entity.class.getMethod("teleportAsync", Location.class);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /** @return {@code true} when the running server exposes {@code teleportAsync} */
    static boolean supported() {
        return TELEPORT_ASYNC != null;
    }

    /**
     * Calls {@code teleportAsync} when available, otherwise teleports synchronously and reports the result. Never
     * throws: a reflective failure is surfaced as a completed-exceptionally future.
     */
    @SuppressWarnings("unchecked")
    static CompletableFuture<Boolean> teleport(Entity entity, Location to) {
        if (TELEPORT_ASYNC == null) {
            return CompletableFuture.completedFuture(entity.teleport(to));
        }
        try {
            return (CompletableFuture<Boolean>) TELEPORT_ASYNC.invoke(entity, to);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            CompletableFuture<Boolean> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex instanceof InvocationTargetException ? ex.getCause() : ex);
            return failed;
        }
    }
}
