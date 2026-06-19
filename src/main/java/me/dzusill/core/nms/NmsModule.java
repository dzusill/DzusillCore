package me.dzusill.core.nms;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.module.AbstractModule;

/**
 * Detects the server version, selects the matching {@link NmsAdapter}, and publishes it into the service registry.
 * Place this right after the foundation module so any later module can resolve the adapter with
 * {@code service(NmsAdapter.class)}.
 *
 * <p>
 * By default it uses {@link NmsAdapters#defaults()}. A fork that ships its own mapped adapters passes a customised
 * registry to the constructor:
 * </p>
 *
 * <pre>{@code
 * new NmsModule(this, NmsAdapters.defaults().register(v -> v.isAtLeast(1, 21), Mapped1_21Adapter::new));
 * }</pre>
 */
public final class NmsModule extends AbstractModule {

    private final NmsAdapters adapters;

    public NmsModule(CorePlugin plugin) {
        this(plugin, NmsAdapters.defaults());
    }

    public NmsModule(CorePlugin plugin, NmsAdapters adapters) {
        super(plugin);
        this.adapters = adapters;
    }

    @Override
    public String name() {
        return "NMS";
    }

    @Override
    public void onEnable() {
        MinecraftVersion version = VersionDetector.detect();
        NmsAdapter adapter = adapters.select(version);
        provide(NmsAdapter.class, adapter);
        plugin.getLogger().info("NMS adapter: " + adapter.getClass().getSimpleName() + " for " + version
                + (adapter.isSupported() ? "" : " (unsupported — features degraded)"));
    }
}
