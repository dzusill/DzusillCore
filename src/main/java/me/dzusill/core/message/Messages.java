package me.dzusill.core.message;

/**
 * Centralized, typed keys for the framework's built-in messages. Keeping keys here (rather than inlining raw strings at
 * call sites) means a rename or restructure of {@code messages.yml} touches exactly one place. Plugins built on the
 * framework should define their own equivalent constants class.
 */
public final class Messages {

    public static final String NO_PERMISSION = "no-permission";
    public static final String PLAYERS_ONLY = "players-only";
    public static final String CONSOLE_ONLY = "console-only";
    public static final String UNKNOWN_COMMAND = "unknown-command";
    public static final String INVALID_USAGE = "invalid-usage";
    public static final String INVALID_NUMBER = "invalid-number";
    public static final String PLAYER_NOT_FOUND = "player-not-found";
    public static final String RELOAD_SUCCESS = "reload-success";
    public static final String RELOAD_FAILED = "reload-failed";
    public static final String COMMAND_ERROR = "command-error";

    private Messages() {
    }
}
