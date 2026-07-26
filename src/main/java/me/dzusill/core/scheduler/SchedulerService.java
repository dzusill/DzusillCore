package me.dzusill.core.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import me.dzusill.core.service.Service;

/**
 * Intention-revealing scheduling facade, portable across Bukkit/Spigot/Paper/Purpur and Folia.
 *
 * <p>
 * Delegates to a {@link PlatformScheduler} chosen once at construction. On a regionised (Folia) server there is no
 * single main thread, so the classic {@link #sync(Runnable)} family maps to the <em>global</em> region — correct for
 * plugin-owned state, but <strong>not</strong> for touching a specific player or block. For those, use the
 * entity-scoped ({@link #atEntity}) and region-scoped ({@link #atRegion}) methods; on non-Folia servers they collapse
 * back onto the main thread, so one code path serves every platform.
 * </p>
 */
public final class SchedulerService implements Service {

    private final PlatformScheduler platform;

    public SchedulerService(Plugin plugin) {
        this.platform = Platforms.isFolia() ? new FoliaPlatformScheduler(plugin) : new BukkitPlatformScheduler(plugin);
    }

    /** @return the underlying platform scheduler, for the rare call that needs it directly */
    public PlatformScheduler platform() {
        return platform;
    }

    /** @return {@code true} when running on Folia */
    public boolean isFolia() {
        return Platforms.isFolia();
    }

    // ------------------------------------------------------------ global / "sync"

    /**
     * Runs on the main thread (global region on Folia) as soon as possible.
     *
     * <p>
     * Safe for plugin-owned state and server-wide API. If the task touches a specific player or entity, use
     * {@link #atEntity(Entity, Runnable)} instead — on Folia this method does not own them.
     * </p>
     */
    public PlatformTask sync(Runnable task) {
        return platform.global(task);
    }

    /** Runs on the main thread / global region after {@code delayTicks} ticks (20 ticks = 1 second). */
    public PlatformTask later(Runnable task, long delayTicks) {
        return platform.globalLater(task, delayTicks);
    }

    /** Runs repeatedly on the main thread / global region. */
    public PlatformTask repeating(Runnable task, long delayTicks, long periodTicks) {
        return platform.globalRepeating(task, delayTicks, periodTicks);
    }

    /** Runs repeatedly on the main thread / global region, handing the task its own handle so it can stop itself. */
    public PlatformTask repeating(Consumer<PlatformTask> task, long delayTicks, long periodTicks) {
        return platform.globalRepeating(task, delayTicks, periodTicks);
    }

    // ------------------------------------------------------------------- async

    /** Runs off the tick thread. The Bukkit API must not be mutated from here. */
    public PlatformTask async(Runnable task) {
        return platform.async(task);
    }

    public PlatformTask laterAsync(Runnable task, long delayTicks) {
        return platform.asyncLater(task, delayTicks);
    }

    public PlatformTask repeatingAsync(Runnable task, long delayTicks, long periodTicks) {
        return platform.asyncRepeating(task, delayTicks, periodTicks);
    }

    // ------------------------------------------------------------------ entity

    /** Runs on the thread owning {@code entity} — required on Folia before touching that entity at all. */
    public PlatformTask atEntity(Entity entity, Runnable task) {
        return platform.atEntity(entity, task);
    }

    /** Runs on the entity's owning thread after a delay. */
    public PlatformTask atEntityLater(Entity entity, Runnable task, long delayTicks) {
        return platform.atEntityLater(entity, task, delayTicks);
    }

    /**
     * Runs repeatedly on the entity's owning thread, following it across regions.
     *
     * @param retired
     *            invoked if the entity is removed before the task is cancelled; may be {@code null}
     */
    public PlatformTask repeatAtEntity(Entity entity, Runnable task, Runnable retired, long delayTicks,
            long periodTicks) {
        return platform.repeatAtEntity(entity, task, retired, delayTicks, periodTicks);
    }

    /**
     * Runs repeatedly on the entity's owning thread, handing the task its own handle so it can stop itself. The shape
     * teleport warm-up countdowns need: follow the player across regions, and end when the countdown does.
     *
     * @param retired
     *            invoked if the entity is removed before the task is cancelled; may be {@code null}
     */
    public PlatformTask repeatAtEntity(Entity entity, Consumer<PlatformTask> task, Runnable retired, long delayTicks,
            long periodTicks) {
        return platform.repeatAtEntity(entity, task, retired, delayTicks, periodTicks);
    }

    // ------------------------------------------------------------------ region

    /** Runs on the thread owning {@code location}'s chunk — required before reading or mutating its blocks. */
    public PlatformTask atRegion(Location location, Runnable task) {
        return platform.atRegion(location, task);
    }

    public PlatformTask atRegionLater(Location location, Runnable task, long delayTicks) {
        return platform.atRegionLater(location, task, delayTicks);
    }

    // ---------------------------------------------------------------- teleport

    /**
     * Teleports region-safely on every platform. A plain {@code Entity#teleport} throws on Folia when the destination
     * belongs to another region.
     */
    public CompletableFuture<Boolean> teleport(Entity entity, Location to) {
        return platform.teleport(entity, to);
    }

    // ------------------------------------------------------------------ bridge

    /**
     * Computes {@code supplier} off-thread, then passes its result to {@code consumer} on the main thread / global
     * region. Use for heavy I/O whose result touches plugin state or server-wide API.
     *
     * <p>
     * If the result handler touches a specific player, use {@link #asyncThenAtEntity} instead.
     * </p>
     */
    public <T> void asyncThenSync(Supplier<T> supplier, Consumer<T> consumer) {
        async(() -> {
            T result = supplier.get();
            sync(() -> consumer.accept(result));
        });
    }

    /**
     * Computes {@code supplier} off-thread, then passes its result to {@code consumer} on the thread owning
     * {@code entity}. The Folia-correct form of {@link #asyncThenSync} whenever the handler messages a player, opens a
     * GUI, or otherwise touches that entity.
     */
    public <T> void asyncThenAtEntity(Entity entity, Supplier<T> supplier, Consumer<T> consumer) {
        async(() -> {
            T result = supplier.get();
            atEntity(entity, () -> consumer.accept(result));
        });
    }

    // --------------------------------------------------------------- executors

    /**
     * @return an {@link Executor} that runs tasks off the tick thread. Suitable as the async executor for
     *         {@link CompletableFuture} stages (e.g. database I/O).
     */
    public Executor asyncExecutor() {
        return this::async;
    }

    /**
     * @return an {@link Executor} that runs tasks on the main thread / global region. Use for {@code CompletableFuture}
     *         stages that must touch plugin state or server-wide API.
     */
    public Executor mainThreadExecutor() {
        return this::sync;
    }

    /**
     * @return an {@link Executor} bound to {@code entity}'s owning thread. Use for {@code CompletableFuture} stages
     *         that must touch that entity.
     */
    public Executor entityExecutor(Entity entity) {
        return task -> atEntity(entity, task);
    }

    // ----------------------------------------------------------------- control

    /** Cancels every task this plugin still has scheduled. */
    public void cancelAll() {
        platform.cancelAll();
    }
}
