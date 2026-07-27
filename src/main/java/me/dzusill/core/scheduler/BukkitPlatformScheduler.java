package me.dzusill.core.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

/**
 * {@link PlatformScheduler} for the single-threaded tick loop — Bukkit, Spigot, Paper and Purpur.
 *
 * <p>
 * Every region/entity-scoped method collapses onto the main thread, which owns all of them there.
 * </p>
 */
final class BukkitPlatformScheduler implements PlatformScheduler {

    private final Plugin plugin;
    private final BukkitScheduler scheduler;

    BukkitPlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.scheduler = Bukkit.getScheduler();
    }

    private static PlatformTask wrap(BukkitTask task) {
        return new PlatformTask() {

            @Override
            public void cancel() {
                task.cancel();
            }

            @Override
            public boolean isCancelled() {
                return task.isCancelled();
            }
        };
    }

    @Override
    public PlatformTask global(Runnable task) {
        return wrap(scheduler.runTask(plugin, task));
    }

    @Override
    public PlatformTask globalLater(Runnable task, long delayTicks) {
        return wrap(scheduler.runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public PlatformTask globalRepeating(Runnable task, long delayTicks, long periodTicks) {
        return wrap(scheduler.runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public PlatformTask globalRepeating(Consumer<PlatformTask> task, long delayTicks, long periodTicks) {
        return selfCancelling(holder -> globalRepeating(() -> task.accept(holder.get()), delayTicks, periodTicks));
    }

    /**
     * Bridges Bukkit's "task handle is only returned after scheduling" model to the self-cancelling shape, where the
     * body needs its own handle. The first run is at least one tick away, so the holder is always populated by then.
     */
    private static PlatformTask selfCancelling(java.util.function.Function<Holder, PlatformTask> schedule) {
        Holder holder = new Holder();
        holder.task = schedule.apply(holder);
        return holder.task;
    }

    private static final class Holder {

        private PlatformTask task;

        private PlatformTask get() {
            return task != null ? task : PlatformTask.NONE;
        }
    }

    @Override
    public PlatformTask atEntity(Entity entity, Runnable task) {
        return global(task);
    }

    @Override
    public PlatformTask atEntity(Entity entity, Runnable task, Runnable retired) {
        return global(() -> {
            if (retired != null && retired(entity)) {
                retired.run();
                return;
            }
            task.run();
        });
    }

    @Override
    public PlatformTask atEntityLater(Entity entity, Runnable task, long delayTicks) {
        return globalLater(task, delayTicks);
    }

    @Override
    public PlatformTask repeatAtEntity(Entity entity, Runnable task, Runnable retired, long delayTicks,
            long periodTicks) {
        // There is no per-entity task lifecycle here; mirror Folia's contract by checking validity each run so the
        // `retired` callback fires on both platforms and callers need only one code path.
        return globalRepeating(() -> {
            if (retired != null && retired(entity)) {
                retired.run();
                return;
            }
            task.run();
        }, delayTicks, periodTicks);
    }

    /**
     * Whether Folia would consider this entity retired. A logged-out {@link Player} is the case that matters here:
     * their entity is gone, so anything scheduled "at" them can never run, and {@code isValid()} alone is not a
     * reliable read of that across server implementations.
     */
    private static boolean retired(Entity entity) {
        return entity instanceof Player player ? !player.isOnline() : !entity.isValid();
    }

    @Override
    public PlatformTask repeatAtEntity(Entity entity, Consumer<PlatformTask> task, Runnable retired, long delayTicks,
            long periodTicks) {
        return selfCancelling(
                holder -> repeatAtEntity(entity, () -> task.accept(holder.get()), retired, delayTicks, periodTicks));
    }

    @Override
    public PlatformTask atRegion(Location location, Runnable task) {
        return global(task);
    }

    @Override
    public PlatformTask atRegionLater(Location location, Runnable task, long delayTicks) {
        return globalLater(task, delayTicks);
    }

    @Override
    public PlatformTask async(Runnable task) {
        return wrap(scheduler.runTaskAsynchronously(plugin, task));
    }

    @Override
    public PlatformTask asyncLater(Runnable task, long delayTicks) {
        return wrap(scheduler.runTaskLaterAsynchronously(plugin, task, delayTicks));
    }

    @Override
    public PlatformTask asyncRepeating(Runnable task, long delayTicks, long periodTicks) {
        return wrap(scheduler.runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public CompletableFuture<Boolean> teleport(Entity entity, Location to) {
        if (Teleports.supported()) {
            return Teleports.teleport(entity, to);
        }
        // Old Spigot: no async teleport at all. Hop to the main thread and do it there.
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        global(() -> future.complete(entity.teleport(to)));
        return future;
    }

    @Override
    public void cancelAll() {
        scheduler.cancelTasks(plugin);
    }
}
