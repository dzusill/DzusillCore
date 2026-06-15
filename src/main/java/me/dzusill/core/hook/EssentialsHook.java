package me.dzusill.core.hook;

import com.earth2me.essentials.IEssentials;
import org.bukkit.Bukkit;

/**
 * Integration with EssentialsX. When active, exposes the {@link IEssentials} API for reading
 * Essentials user data (homes, warps, balances, etc.).
 */
public final class EssentialsHook extends PluginHook {

    private IEssentials essentials;

    public EssentialsHook() {
        super("Essentials");
    }

    @Override
    protected void setup() {
        this.essentials = (IEssentials) Bukkit.getPluginManager().getPlugin("Essentials");
    }

    /**
     * @return the Essentials API, or {@code null} if not hooked
     */
    public IEssentials essentials() {
        return essentials;
    }
}
