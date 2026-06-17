package me.dzusill.core.nms;

import me.dzusill.core.service.Service;
import org.bukkit.entity.Player;

/**
 * The stable contract a plugin codes against for version-sensitive ({@code net.minecraft.server})
 * behaviour. Exactly one implementation is selected per running server version by {@link NmsAdapters}
 * and published into the {@code ServiceRegistry} by {@link NmsModule}; consumers resolve it with
 * {@code service(NmsAdapter.class)} and never reference a concrete version impl directly.
 *
 * <p>This interface intentionally exposes only the low-level <em>primitives</em> (handle unwrapping,
 * packet sending, ping) that higher-level features build on. A fork extends it by adding methods here
 * and implementing them in its adapters — the plugin code keeps depending on the interface, so adding
 * a new server version never touches feature code.</p>
 *
 * <p>Always gate calls with {@link #supports(NmsFeature)}: on an unmapped/untested version the active
 * adapter may be a {@link me.dzusill.core.nms.version.NoOpNmsAdapter} whose capability methods throw.</p>
 */
public interface NmsAdapter extends Service {

    /**
     * @return the detected server version this adapter is serving
     */
    MinecraftVersion version();

    /**
     * @return {@code true} if this is a real, version-matched adapter; {@code false} for the no-op
     * fallback used on unsupported versions
     */
    boolean isSupported();

    /**
     * @return {@code true} if the given capability is implemented for the running version
     */
    boolean supports(NmsFeature feature);

    /**
     * Unwraps a CraftBukkit wrapper to its underlying NMS handle (e.g. {@code CraftPlayer} →
     * {@code ServerPlayer}/{@code EntityPlayer}).
     *
     * @throws UnsupportedOperationException if {@link NmsFeature#NMS_HANDLE} is not supported
     */
    Object nmsHandle(Object craftBukkitObject);

    /**
     * Sends a raw NMS packet object to the player's connection.
     *
     * @param packet an NMS {@code Packet} instance (typically built reflectively or via a fork's
     *               version module)
     * @throws UnsupportedOperationException if {@link NmsFeature#PACKET_SENDING} is not supported
     */
    void sendPacket(Player player, Object packet);

    /**
     * @return the player's connection latency in milliseconds
     * @throws UnsupportedOperationException if {@link NmsFeature#PLAYER_PING} is not supported
     */
    int getPing(Player player);
}
