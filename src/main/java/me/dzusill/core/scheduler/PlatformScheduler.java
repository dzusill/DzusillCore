package me.dzusill.core.scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * Thread-model abstraction over the single-threaded Bukkit/Paper/Purpur tick loop and Folia's regionised one.
 *
 * <p>
 * On Folia there is no "the main thread". Work must be submitted to the scheduler that owns the data it touches:
 * </p>
 *
 * <table border="1">
 * <caption>Choosing a method</caption>
 * <tr>
 * <th>Touching…</th>
 * <th>Use</th>
 * </tr>
 * <tr>
 * <td>a specific player/entity (messages, GUIs, inventories, potion effects)</td>
 * <td>{@link #atEntity}, {@link #atEntityLater}, {@link #repeatAtEntity}</td>
 * </tr>
 * <tr>
 * <td>blocks or entities at a location</td>
 * <td>{@link #atRegion}, {@link #atRegionLater}</td>
 * </tr>
 * <tr>
 * <td>plugin-owned state only, or server-wide API (weather, time, broadcast)</td>
 * <td>{@link #global}, {@link #globalLater}, {@link #globalRepeating}</td>
 * </tr>
 * <tr>
 * <td>blocking I/O (database, HTTP)</td>
 * <td>{@link #async}, {@link #asyncLater}, {@link #asyncRepeating}</td>
 * </tr>
 * </table>
 *
 * <p>
 * On Bukkit/Paper/Purpur every non-async method collapses onto the main thread, so code written against this interface
 * behaves identically there.
 * </p>
 */
public interface PlatformScheduler {

    // ---------------------------------------------------------------- global

    /** Runs on the global region (Folia) or the main thread (everything else), as soon as possible. */
    PlatformTask global(Runnable task);

    /** Runs on the global region after {@code delayTicks} ticks (20 ticks = 1 second). */
    PlatformTask globalLater(Runnable task, long delayTicks);

    /** Runs repeatedly on the global region. */
    PlatformTask globalRepeating(Runnable task, long delayTicks, long periodTicks);

    /**
     * Runs repeatedly on the global region, handing the task its own handle so it can stop itself — the usual shape for
     * countdowns and other finite loops.
     */
    PlatformTask globalRepeating(Consumer<PlatformTask> task, long delayTicks, long periodTicks);

    // ---------------------------------------------------------------- entity

    /**
     * Runs on the thread that owns {@code entity}. Required on Folia before touching that entity at all — including
     * sending it a message or opening a GUI.
     *
     * @return a handle, or {@link PlatformTask#NONE} if the entity was already removed
     */
    PlatformTask atEntity(Entity entity, Runnable task);

    /**
     * Runs on the thread that owns {@code entity}, with a fallback for the entity going away first.
     *
     * <p>
     * The one-shot analogue of {@link #repeatAtEntity}'s {@code retired} hook. Anything that hands a result back to a
     * player after off-thread work needs it: a player who logs out mid-request owns no thread any more, so the task is
     * simply dropped and whatever state it was going to settle (a cooldown, a lock) would hang.
     * </p>
     *
     * @param retired
     *            invoked instead of {@code task} if the entity was removed (a player logged out) before it could run;
     *            may be {@code null}
     */
    PlatformTask atEntity(Entity entity, Runnable task, Runnable retired);

    /** Runs on the entity's owning thread after a delay. Values below 1 tick are raised to 1 (Folia rejects 0). */
    PlatformTask atEntityLater(Entity entity, Runnable task, long delayTicks);

    /**
     * Runs repeatedly on the entity's owning thread, following the entity across region boundaries.
     *
     * @param retired
     *            invoked if the entity is removed before the task is cancelled; may be {@code null}
     */
    PlatformTask repeatAtEntity(Entity entity, Runnable task, Runnable retired, long delayTicks, long periodTicks);

    /**
     * Runs repeatedly on the entity's owning thread, handing the task its own handle so it can stop itself. The usual
     * shape for teleport warm-up countdowns, which must both follow the player across regions and end themselves.
     *
     * @param retired
     *            invoked if the entity is removed before the task is cancelled; may be {@code null}
     */
    PlatformTask repeatAtEntity(Entity entity, Consumer<PlatformTask> task, Runnable retired, long delayTicks,
            long periodTicks);

    // ---------------------------------------------------------------- region

    /** Runs on the thread that owns {@code location}'s chunk — required before reading or mutating its blocks. */
    PlatformTask atRegion(Location location, Runnable task);

    /** Runs on the region owning {@code location} after a delay. */
    PlatformTask atRegionLater(Location location, Runnable task, long delayTicks);

    // ----------------------------------------------------------------- async

    /** Runs off any tick thread. The Bukkit API must not be mutated from here. */
    PlatformTask async(Runnable task);

    /** Runs off-thread after a delay. */
    PlatformTask asyncLater(Runnable task, long delayTicks);

    /** Runs repeatedly off-thread. */
    PlatformTask asyncRepeating(Runnable task, long delayTicks, long periodTicks);

    // -------------------------------------------------------------- teleport

    /**
     * Teleports region-safely on every platform. A plain {@code Entity#teleport} throws on Folia whenever the
     * destination belongs to another region.
     *
     * @return a future completing with the teleport success flag, on an unspecified thread
     */
    CompletableFuture<Boolean> teleport(Entity entity, Location to);

    // --------------------------------------------------------------- control

    /** Cancels every task this plugin still has scheduled. Called on disable. */
    void cancelAll();
}
