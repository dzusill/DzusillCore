package me.dzusill.core.nms.version;

import org.bukkit.entity.Player;

import me.dzusill.core.nms.MinecraftVersion;
import me.dzusill.core.nms.NmsAdapter;
import me.dzusill.core.nms.NmsFeature;

/**
 * Fallback adapter selected when no real adapter matches the running version (lenient mode). It reports
 * {@link #isSupported()} {@code false} and {@link #supports(NmsFeature)} {@code false} for everything, and every
 * capability method throws a clear {@link UnsupportedOperationException} — so a plugin that forgets to gate a call
 * fails loudly with a useful message rather than mysteriously.
 */
public final class NoOpNmsAdapter implements NmsAdapter {

    private final MinecraftVersion version;

    public NoOpNmsAdapter(MinecraftVersion version) {
        this.version = version;
    }

    @Override
    public MinecraftVersion version() {
        return version;
    }

    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public boolean supports(NmsFeature feature) {
        return false;
    }

    @Override
    public Object nmsHandle(Object craftBukkitObject) {
        throw unsupported(NmsFeature.NMS_HANDLE);
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        throw unsupported(NmsFeature.PACKET_SENDING);
    }

    @Override
    public int getPing(Player player) {
        throw unsupported(NmsFeature.PLAYER_PING);
    }

    private UnsupportedOperationException unsupported(NmsFeature feature) {
        return new UnsupportedOperationException(feature + " is unavailable: no NMS adapter for server version "
                + version + ". Gate calls with adapter.supports(" + feature + ").");
    }
}
