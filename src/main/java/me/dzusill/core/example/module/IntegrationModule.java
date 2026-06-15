package me.dzusill.core.example.module;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.hook.EssentialsHook;
import me.dzusill.core.hook.HookManager;
import me.dzusill.core.hook.PlaceholderApiHook;
import me.dzusill.core.hook.VaultHook;
import me.dzusill.core.module.AbstractModule;

/**
 * Wires up optional third-party integrations. Hooks are registered lazily by plugin name, so a
 * hook class (and the soft-dependency API types it imports) is only loaded when that plugin is
 * actually installed. This keeps the template safe whether the server runs none of these plugins
 * or all of them.
 */
public final class IntegrationModule extends AbstractModule {

    public IntegrationModule(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "Integrations";
    }

    @Override
    public void onEnable() {
        HookManager hooks = new HookManager(plugin);
        hooks.register("Vault", VaultHook::new);
        hooks.register("PlaceholderAPI", PlaceholderApiHook::new);
        hooks.register("Essentials", EssentialsHook::new);
        provide(HookManager.class, hooks);
    }
}
