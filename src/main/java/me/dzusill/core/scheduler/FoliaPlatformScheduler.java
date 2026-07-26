package me.dzusill.core.scheduler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * {@link PlatformScheduler} for Folia's regionised tick loop.
 *
 * <p>
 * Bound reflectively: DzusillCore compiles against the Spigot 1.16.5 API surface, which knows nothing about
 * {@code io.papermc.paper.threadedregions.scheduler}. Resolution happens once in a static initialiser; this class is
 * only ever loaded when {@link Platforms#isFolia()} is true, so a missing class here is a genuine error rather than a
 * normal absent-platform case.
 * </p>
 */
final class FoliaPlatformScheduler implements PlatformScheduler {

    private static final String PKG = "io.papermc.paper.threadedregions.scheduler.";

    private static final Object GLOBAL;
    private static final Object REGION;
    private static final Object ASYNC;

    private static final Method ENTITY_SCHEDULER;

    private static final Method GLOBAL_RUN;
    private static final Method GLOBAL_DELAYED;
    private static final Method GLOBAL_FIXED_RATE;
    private static final Method GLOBAL_CANCEL;

    private static final Method REGION_RUN;
    private static final Method REGION_DELAYED;

    private static final Method ASYNC_NOW;
    private static final Method ASYNC_DELAYED;
    private static final Method ASYNC_FIXED_RATE;
    private static final Method ASYNC_CANCEL;

    private static final Method ENTITY_RUN;
    private static final Method ENTITY_DELAYED;
    private static final Method ENTITY_FIXED_RATE;

    private static final Method TASK_CANCEL;
    private static final Method TASK_IS_CANCELLED;

    static {
        try {
            Class<?> global = Class.forName(PKG + "GlobalRegionScheduler");
            Class<?> region = Class.forName(PKG + "RegionScheduler");
            Class<?> async = Class.forName(PKG + "AsyncScheduler");
            Class<?> entity = Class.forName(PKG + "EntityScheduler");
            Class<?> task = Class.forName(PKG + "ScheduledTask");

            GLOBAL = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            REGION = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            ASYNC = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            ENTITY_SCHEDULER = Entity.class.getMethod("getScheduler");

            GLOBAL_RUN = global.getMethod("run", Plugin.class, Consumer.class);
            GLOBAL_DELAYED = global.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            GLOBAL_FIXED_RATE = global.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class,
                    long.class);
            GLOBAL_CANCEL = global.getMethod("cancelTasks", Plugin.class);

            REGION_RUN = region.getMethod("run", Plugin.class, Location.class, Consumer.class);
            REGION_DELAYED = region.getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class);

            ASYNC_NOW = async.getMethod("runNow", Plugin.class, Consumer.class);
            ASYNC_DELAYED = async.getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class);
            ASYNC_FIXED_RATE = async.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class,
                    TimeUnit.class);
            ASYNC_CANCEL = async.getMethod("cancelTasks", Plugin.class);

            ENTITY_RUN = entity.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            ENTITY_DELAYED = entity.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
            ENTITY_FIXED_RATE = entity.getMethod("runAtFixedRate", Plugin.class, Consumer.class, Runnable.class,
                    long.class, long.class);

            TASK_CANCEL = task.getMethod("cancel");
            TASK_IS_CANCELLED = task.getMethod("isCancelled");
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Folia detected but its scheduler API could not be bound", ex);
        }
    }

    private final Plugin plugin;

    FoliaPlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Folia rejects a delay or period below one tick; callers pass 0 freely on Bukkit. */
    private static long atLeastOneTick(long ticks) {
        return Math.max(1L, ticks);
    }

    private static long ticksToMillis(long ticks) {
        return Math.max(1L, ticks) * 50L;
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Folia scheduler call rejected: " + method.getName(), ex);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Folia scheduler call failed: " + method.getName(), cause);
        }
    }

    /** Adapts a plain {@link Runnable} to Folia's {@code Consumer<ScheduledTask>} callback shape. */
    private static Consumer<Object> callback(Runnable task) {
        return ignored -> task.run();
    }

    /**
     * Adapts a self-cancelling body to Folia's callback shape. Folia hands the running task to the callback, so the
     * handle is genuine rather than reconstructed.
     */
    private static Consumer<Object> callback(Consumer<PlatformTask> task) {
        return scheduled -> task.accept(wrap(scheduled));
    }

    private static PlatformTask wrap(Object scheduledTask) {
        if (scheduledTask == null) {
            // Folia returns null when the owning entity has already been removed.
            return PlatformTask.NONE;
        }
        return new PlatformTask() {

            @Override
            public void cancel() {
                invoke(TASK_CANCEL, scheduledTask);
            }

            @Override
            public boolean isCancelled() {
                return Boolean.TRUE.equals(invoke(TASK_IS_CANCELLED, scheduledTask));
            }
        };
    }

    private static Object schedulerOf(Entity entity) {
        return invoke(ENTITY_SCHEDULER, entity);
    }

    @Override
    public PlatformTask global(Runnable task) {
        return wrap(invoke(GLOBAL_RUN, GLOBAL, plugin, callback(task)));
    }

    @Override
    public PlatformTask globalLater(Runnable task, long delayTicks) {
        return wrap(invoke(GLOBAL_DELAYED, GLOBAL, plugin, callback(task), atLeastOneTick(delayTicks)));
    }

    @Override
    public PlatformTask globalRepeating(Runnable task, long delayTicks, long periodTicks) {
        return wrap(invoke(GLOBAL_FIXED_RATE, GLOBAL, plugin, callback(task), atLeastOneTick(delayTicks),
                atLeastOneTick(periodTicks)));
    }

    @Override
    public PlatformTask globalRepeating(Consumer<PlatformTask> task, long delayTicks, long periodTicks) {
        return wrap(invoke(GLOBAL_FIXED_RATE, GLOBAL, plugin, callback(task), atLeastOneTick(delayTicks),
                atLeastOneTick(periodTicks)));
    }

    @Override
    public PlatformTask atEntity(Entity entity, Runnable task) {
        return wrap(invoke(ENTITY_RUN, schedulerOf(entity), plugin, callback(task), null));
    }

    @Override
    public PlatformTask atEntityLater(Entity entity, Runnable task, long delayTicks) {
        return wrap(
                invoke(ENTITY_DELAYED, schedulerOf(entity), plugin, callback(task), null, atLeastOneTick(delayTicks)));
    }

    @Override
    public PlatformTask repeatAtEntity(Entity entity, Runnable task, Runnable retired, long delayTicks,
            long periodTicks) {
        return wrap(invoke(ENTITY_FIXED_RATE, schedulerOf(entity), plugin, callback(task), retired,
                atLeastOneTick(delayTicks), atLeastOneTick(periodTicks)));
    }

    @Override
    public PlatformTask repeatAtEntity(Entity entity, Consumer<PlatformTask> task, Runnable retired, long delayTicks,
            long periodTicks) {
        return wrap(invoke(ENTITY_FIXED_RATE, schedulerOf(entity), plugin, callback(task), retired,
                atLeastOneTick(delayTicks), atLeastOneTick(periodTicks)));
    }

    @Override
    public PlatformTask atRegion(Location location, Runnable task) {
        return wrap(invoke(REGION_RUN, REGION, plugin, location, callback(task)));
    }

    @Override
    public PlatformTask atRegionLater(Location location, Runnable task, long delayTicks) {
        return wrap(invoke(REGION_DELAYED, REGION, plugin, location, callback(task), atLeastOneTick(delayTicks)));
    }

    @Override
    public PlatformTask async(Runnable task) {
        return wrap(invoke(ASYNC_NOW, ASYNC, plugin, callback(task)));
    }

    @Override
    public PlatformTask asyncLater(Runnable task, long delayTicks) {
        return wrap(
                invoke(ASYNC_DELAYED, ASYNC, plugin, callback(task), ticksToMillis(delayTicks), TimeUnit.MILLISECONDS));
    }

    @Override
    public PlatformTask asyncRepeating(Runnable task, long delayTicks, long periodTicks) {
        return wrap(invoke(ASYNC_FIXED_RATE, ASYNC, plugin, callback(task), ticksToMillis(delayTicks),
                ticksToMillis(periodTicks), TimeUnit.MILLISECONDS));
    }

    @Override
    public CompletableFuture<Boolean> teleport(Entity entity, Location to) {
        return Teleports.teleport(entity, to);
    }

    @Override
    public void cancelAll() {
        invoke(GLOBAL_CANCEL, GLOBAL, plugin);
        invoke(ASYNC_CANCEL, ASYNC, plugin);
    }
}
