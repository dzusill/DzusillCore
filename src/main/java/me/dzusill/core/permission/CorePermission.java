package me.dzusill.core.permission;

/**
 * Central catalogue of permission nodes owned by the framework. Defining nodes as constants
 * keeps them consistent across commands and {@code plugin.yml}, and makes refactors safe.
 * Plugins built on the framework should mirror this with their own constants class.
 */
public final class CorePermission {

    /** Wildcard administration node. */
    public static final String ADMIN = "core.admin";

    /** Allows reloading the plugin configuration. */
    public static final String RELOAD = "core.reload";

    private CorePermission() {
    }
}
