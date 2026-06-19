package me.dzusill.core.hook;

import org.bukkit.Bukkit;

/**
 * Base class for an integration with an optional (soft-dependency) plugin. Encapsulates the presence check and one-time
 * setup, so callers interact with hooks uniformly and never sprinkle {@code isPluginEnabled} checks throughout the
 * codebase.
 */
public abstract class PluginHook {

    private final String pluginName;
    private boolean active;

    protected PluginHook(String pluginName) {
        this.pluginName = pluginName;
    }

    /**
     * @return {@code true} if the target plugin is installed and enabled
     */
    public final boolean isPresent() {
        return Bukkit.getPluginManager().isPluginEnabled(pluginName);
    }

    /**
     * @return {@code true} if the hook was successfully set up
     */
    public final boolean isActive() {
        return active;
    }

    public final String pluginName() {
        return pluginName;
    }

    /**
     * Attempts setup if the target plugin is present. Called by the {@link HookManager}.
     */
    final void tryEnable() {
        if (isPresent()) {
            setup();
            active = true;
        }
    }

    /**
     * Performs the integration-specific wiring. Only called when the target plugin is present.
     */
    protected abstract void setup();
}
