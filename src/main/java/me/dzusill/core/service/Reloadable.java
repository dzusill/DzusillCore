package me.dzusill.core.service;

/**
 * Implemented by any component whose state can be refreshed at runtime, typically in response to a {@code /core reload}
 * command. Implementations must be safe to call multiple times and should never leave the component in a
 * partially-loaded state on failure.
 */
public interface Reloadable {

    /**
     * Re-reads the underlying source (config file, remote data, ...) and applies it.
     *
     * @throws Exception
     *             if reloading fails; callers are expected to report the failure without crashing the plugin.
     */
    void reload() throws Exception;
}
