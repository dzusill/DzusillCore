package me.dzusill.core;

import me.dzusill.core.module.CoreModule;
import me.dzusill.core.module.ModuleManager;
import me.dzusill.core.service.ServiceRegistry;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Abstract base for every plugin built on this framework. Concrete plugins extend it and return
 * their ordered list of {@link CoreModule modules} from {@link #modules()}; the base class owns
 * the shared {@link ServiceRegistry}, drives module startup/shutdown through the
 * {@link ModuleManager}, and exposes a typed singleton accessor.
 *
 * <p>Subclasses should not override {@link #onEnable()} / {@link #onDisable()} directly. Instead
 * they override {@link #modules()} and, optionally, {@link #banner()} and the
 * {@link #onPreEnable()} / {@link #onPostEnable()} hooks.</p>
 */
public abstract class CorePlugin extends JavaPlugin {

    private static CorePlugin instance;

    private final ServiceRegistry services = new ServiceRegistry();
    private ModuleManager moduleManager;

    @Override
    public final void onEnable() {
        instance = this;
        printBanner();
        onPreEnable();

        this.moduleManager = new ModuleManager(this);
        moduleManager.enableAll(modules());

        onPostEnable();
        getLogger().info("Enabled successfully.");
    }

    @Override
    public final void onDisable() {
        onPreDisable();
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        services.clear();
        instance = null;
    }

    /**
     * @return the ordered list of modules that make up this plugin. Modules are enabled in this
     *         order and disabled in reverse.
     */
    protected abstract CoreModule[] modules();

    /**
     * @return the lines of the startup banner printed to the console. Override to customize.
     */
    protected String[] banner() {
        return new String[]{
                "",
                "  " + getName() + " v" + getPluginMeta().getVersion(),
                "  Powered by DzusillCore",
                ""
        };
    }

    /** Hook invoked before any module is enabled. */
    protected void onPreEnable() {
    }

    /** Hook invoked after all modules are enabled. */
    protected void onPostEnable() {
    }

    /** Hook invoked before any module is disabled. */
    protected void onPreDisable() {
    }

    private void printBanner() {
        for (String line : banner()) {
            getServer().getConsoleSender().sendMessage(line);
        }
    }

    /**
     * @return the shared service registry used by all modules
     */
    public ServiceRegistry services() {
        return services;
    }

    /**
     * @return the active plugin instance, or {@code null} when not enabled
     */
    public static CorePlugin instance() {
        return instance;
    }
}
