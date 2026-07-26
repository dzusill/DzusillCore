package me.dzusill.core.scheduler;

/**
 * Platform-neutral handle to a scheduled task.
 *
 * <p>
 * Replaces {@code org.bukkit.scheduler.BukkitTask} in public API so callers compile and run unchanged on Folia, where
 * {@code BukkitTask} is never produced.
 * </p>
 */
public interface PlatformTask {

    /** A handle for work that was never scheduled (e.g. the target entity was already gone). */
    PlatformTask NONE = new PlatformTask() {

        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };

    /** Cancels the task. Safe to call more than once, and safe from any thread. */
    void cancel();

    /** @return {@code true} once {@link #cancel()} has been called or the task has otherwise stopped */
    boolean isCancelled();
}
