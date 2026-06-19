package me.dzusill.core.module;

/**
 * A self-contained unit of plugin functionality with its own lifecycle.
 *
 * <p>
 * Modules are enabled in registration order during {@code onEnable} and disabled in the reverse order during
 * {@code onDisable}. Each module publishes the services it owns and resolves dependencies through the
 * {@code ServiceRegistry}, so the registration order is the single source of truth for startup sequencing.
 * </p>
 */
public interface CoreModule {

    /**
     * @return a short, stable identifier used in startup logging and diagnostics
     */
    String name();

    /**
     * Initializes the module. Implementations should register their services here and may resolve services published by
     * previously-enabled modules.
     *
     * @throws Exception
     *             if initialization fails; the bootstrap will treat this as fatal
     */
    void onEnable() throws Exception;

    /**
     * Releases any resources held by the module. Must be safe to call even if {@link #onEnable()} failed partway
     * through.
     */
    void onDisable();
}
