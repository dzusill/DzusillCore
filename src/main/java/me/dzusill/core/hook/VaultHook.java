package me.dzusill.core.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.milkbowl.vault.economy.Economy;

/**
 * Integration with Vault's economy service. When active, exposes the registered {@link Economy} provider so the plugin
 * can read balances and charge players without depending on a specific economy implementation.
 *
 * <p>
 * Compiled against VaultUnlockedAPI, which ships both the classic {@code net.milkbowl.vault} interfaces used here and
 * the newer {@code net.milkbowl.vault2} ones. VaultUnlocked registers itself under the plugin name {@code Vault}, so
 * this hook binds to either implementation unchanged — but only VaultUnlocked runs on Folia, hence
 * {@link #isUnlocked()}.
 * </p>
 */
public final class VaultHook extends PluginHook {

    private Economy economy;

    public VaultHook() {
        super("Vault");
    }

    /**
     * @return {@code true} when the installed provider is VaultUnlocked (the only Folia-capable one), detected by the
     *         presence of its {@code vault2} API
     */
    public static boolean isUnlocked() {
        try {
            Class.forName("net.milkbowl.vault2.economy.Economy");
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    @Override
    protected void setup() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
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
