package me.dzusill.core.prompt;

import java.util.function.Consumer;

import org.bukkit.entity.Player;

import me.dzusill.core.service.Service;

/**
 * Asks a player for a line of text.
 *
 * <h2>The contract</h2>
 *
 * <p>
 * <strong>{@code onResult} runs exactly once, always</strong> - with {@code ""} when the player cancels, times out or
 * disconnects. Callers can therefore reopen their menu unconditionally in the callback, with no separate cancel path
 * and no risk of a menu that never comes back.
 * </p>
 *
 * <p>
 * That guarantee is the whole point. Four of the six per-plugin implementations this replaces silently dropped the
 * callback on cancel, so the caller's menu simply never reopened; two more had no timeout at all, leaving a player
 * captured indefinitely. One had a stale-timeout bug where an expiring old prompt would clobber a newly-issued one.
 * </p>
 */
public interface PromptService extends Service {

    /**
     * Prompts the player and captures their next chat line.
     *
     * <p>
     * Starting a second prompt for the same player cancels the first, resolving its callback as cancelled.
     * </p>
     */
    void prompt(Player player, PromptOptions options, Consumer<String> onResult);

    default void prompt(Player player, String question, Consumer<String> onResult) {
        prompt(player, PromptOptions.of(question), onResult);
    }

    /**
     * @return whether this player currently has a prompt waiting
     */
    boolean isPending(Player player);

    /**
     * Cancels a pending prompt, resolving its callback with {@code ""}.
     *
     * @return whether there was one to cancel
     */
    boolean cancel(Player player);

    /**
     * @return a short identifier for startup logging, e.g. {@code "chat"} - mirrors how the ecosystem's existing prompt
     *         abstractions announce which implementation is live
     */
    String kind();
}
