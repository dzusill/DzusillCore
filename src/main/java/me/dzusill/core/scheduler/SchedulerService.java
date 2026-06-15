package me.dzusill.core.scheduler;

import me.dzusill.core.service.Service;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Thin, intention-revealing wrapper over {@link BukkitScheduler}. Provides sync/async/delayed/
 * repeating helpers plus an async-to-sync bridge for the common pattern of doing blocking work
 * off the main thread and then applying the result back on it.
 */
public final class SchedulerService implements Service {

    private final Plugin plugin;
    private final BukkitScheduler scheduler;

    public SchedulerService(Plugin plugin) {
        this.plugin = plugin;
        this.scheduler = Bukkit.getScheduler();
    }

    /**
     * Runs the task on the main server thread as soon as possible.
     */
    public BukkitTask sync(Runnable task) {
        return scheduler.runTask(plugin, task);
    }

    /**
     * Runs the task off the main thread. The Bukkit API must not be mutated from here.
     */
    public BukkitTask async(Runnable task) {
        return scheduler.runTaskAsynchronously(plugin, task);
    }

    /**
     * Runs the task on the main thread after {@code delayTicks} ticks (20 ticks = 1 second).
     */
    public BukkitTask later(Runnable task, long delayTicks) {
        return scheduler.runTaskLater(plugin, task, delayTicks);
    }

    public BukkitTask laterAsync(Runnable task, long delayTicks) {
        return scheduler.runTaskLaterAsynchronously(plugin, task, delayTicks);
    }

    /**
     * Runs the task repeatedly on the main thread.
     */
    public BukkitTask repeating(Runnable task, long delayTicks, long periodTicks) {
        return scheduler.runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    public BukkitTask repeatingAsync(Runnable task, long delayTicks, long periodTicks) {
        return scheduler.runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
    }

    /**
     * Computes {@code supplier} asynchronously, then passes its result to {@code consumer} on the
     * main thread. Use for heavy I/O whose result must touch the Bukkit API.
     *
     * @param supplier blocking work, executed off the main thread
     * @param consumer result handler, executed on the main thread
     * @param <T>      result type
     */
    public <T> void asyncThenSync(Supplier<T> supplier, Consumer<T> consumer) {
        async(() -> {
            T result = supplier.get();
            sync(() -> consumer.accept(result));
        });
    }

    /**
     * @return an {@link Executor} that runs tasks off the main thread. Suitable as the async
     *         executor for {@link java.util.concurrent.CompletableFuture} stages (e.g. database I/O).
     */
    public Executor asyncExecutor() {
        return this::async;
    }

    /**
     * @return an {@link Executor} that runs tasks on the main server thread. Use as the executor
     *         for {@code CompletableFuture} stages that must touch the Bukkit API.
     */
    public Executor mainThreadExecutor() {
        return this::sync;
    }
}
