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
    /** More than one online player starts with what was typed; asks for another letter rather than guessing. */
    public static final String PLAYER_AMBIGUOUS = "player-ambiguous";
    public static final String RELOAD_SUCCESS = "reload-success";
    public static final String RELOAD_FAILED = "reload-failed";
    public static final String COMMAND_ERROR = "command-error";

    private Messages() {
    }

    /**
     * What kind of message each built-in key is.
     *
     * <p>
     * Applied by {@link MessageService} the moment it is constructed, so writing {@code Presentation.Categories.ERROR}
     * into a config governs the framework's own refusals — "you do not have permission", a usage line, an unknown
     * subcommand — without every plugin having to categorise them again. A plugin that files the same key elsewhere
     * still wins: it categorises after construction.
     * </p>
     *
     * @return every framework key mapped to its category, for {@link MessageService#categorise(java.util.Map)}
     */
    public static java.util.Map<String, MessageCategory> categories() {
        java.util.Map<String, MessageCategory> categories = new java.util.LinkedHashMap<>();
        // Every one of these is a refusal or a failure: the command did not do what was asked. That is the kind
        // worth being able to make audible, which is what ERROR is for.
        for (String key : new String[]{NO_PERMISSION, PLAYERS_ONLY, CONSOLE_ONLY, UNKNOWN_COMMAND, INVALID_USAGE,
                INVALID_NUMBER, PLAYER_NOT_FOUND, PLAYER_AMBIGUOUS, RELOAD_FAILED, COMMAND_ERROR}) {
            categories.put(key, MessageCategory.ERROR);
        }
        categories.put(RELOAD_SUCCESS, MessageCategory.INFO);
        return categories;
    }
}
