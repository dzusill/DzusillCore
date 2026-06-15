package me.dzusill.core.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Integration with Vault's economy service. When active, exposes the registered {@link Economy}
 * provider so the plugin can read balances and charge players without depending on a specific
 * economy implementation.
 */
public final class VaultHook extends PluginHook {

    private Economy economy;

    public VaultHook() {
        super("Vault");
    }

    @Override
    protected void setup() {
        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration != null) {
            this.economy = registration.getProvider();
        }
    }

    /**
     * @return the economy provider, or {@code null} if none is registered
     */
    public Economy economy() {
        return economy;
    }
}
