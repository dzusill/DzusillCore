package me.dzusill.core.module;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;

import me.dzusill.core.CorePlugin;

/**
 * Owns the lifecycle of every {@link CoreModule} in the plugin.
 *
 * <p>
 * Modules are enabled in the exact order they are registered and disabled in strict reverse order, mirroring resource
 * acquisition/release semantics. If a module fails to enable, all already-enabled modules are rolled back so the plugin
 * never runs in a half-initialized state.
 * </p>
 */
public final class ModuleManager {

    private final CorePlugin plugin;
    private final Deque<CoreModule> enabled = new ArrayDeque<>();

    public ModuleManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enables the given modules in order. On the first failure, every module that was already enabled in this call is
     * disabled again and the failure is rethrown.
     *
     * @throws IllegalStateException
     *             wrapping the original cause if any module fails to enable
     */
    public void enableAll(CoreModule... modules) {
        for (CoreModule module : modules) {
            try {
                plugin.getLogger().info("Enabling module: " + module.name());
                module.onEnable();
                enabled.push(module);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to enable module '" + module.name() + "', rolling back",
                        ex);
                disableAll();
                throw new IllegalStateException("Module bootstrap failed: " + module.name(), ex);
            }
        }
    }

    /**
     * Disables all enabled modules in reverse order. Each module is isolated so a faulty shutdown in one module never
     * prevents the others from being disabled.
     */
    public void disableAll() {
        while (!enabled.isEmpty()) {
            CoreModule module = enabled.pop();
            try {
                plugin.getLogger().info("Disabling module: " + module.name());
                module.onDisable();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Error while disabling module '" + module.name() + "'", ex);
            }
        }
    }
}
