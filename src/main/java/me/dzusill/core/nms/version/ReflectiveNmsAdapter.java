package me.dzusill.core.nms.version;

import me.dzusill.core.nms.MinecraftVersion;
import me.dzusill.core.nms.NmsAdapter;
import me.dzusill.core.nms.NmsFeature;
import me.dzusill.core.nms.reflect.Reflection;
import org.bukkit.entity.Player;

/**
 * Reflection-based default adapter covering the whole supported range without compiling against any
 * server jar. It implements the primitives that reflection can do <em>reliably</em> across the 1.17
 * package move and the 1.20.5 mapping change:
 *
 * <ul>
 *   <li>{@link NmsFeature#NMS_HANDLE} — {@code getHandle()} exists on every CraftBukkit wrapper.</li>
 *   <li>{@link NmsFeature#PLAYER_PING} — Bukkit-native {@code Player#getPing()} from 1.17; the NMS
 *       {@code EntityPlayer.ping} field before that.</li>
 * </ul>
 *
 * <p>{@link NmsFeature#PACKET_SENDING} is deliberately <b>not</b> supported here: from 1.17 the NMS
 * connection field/method names are obfuscated per version, so reliable packet work needs a mapped
 * per-version adapter. That is the documented extension point — a fork registers its own
 * {@link NmsAdapter} on {@link me.dzusill.core.nms.NmsAdapters} and overrides this behaviour. See
 * {@code docs/nms/extending.md}.</p>
 */
public final class ReflectiveNmsAdapter implements NmsAdapter {

    private final MinecraftVersion version;

    public ReflectiveNmsAdapter(MinecraftVersion version) {
        this.version = version;
    }

    @Override
    public MinecraftVersion version() {
        return version;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public boolean supports(NmsFeature feature) {
        return switch (feature) {
            case NMS_HANDLE, PLAYER_PING -> true;
            case PACKET_SENDING -> false;
        };
    }

    @Override
    public Object nmsHandle(Object craftBukkitObject) {
        return Reflection.getHandle(craftBukkitObject);
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        throw new UnsupportedOperationException(
                "Packet sending is not supported by the reflective default adapter (NMS connection "
                        + "names are obfuscated per version). Register a mapped per-version adapter — "
                        + "see docs/nms/extending.md.");
    }

    @Override
    public int getPing(Player player) {
        if (version.isAtLeast(1, 17)) {
            // Bukkit added Player#getPing() in 1.17; we call it reflectively because the framework
            // compiles against Spigot 1.16.5, whose API doesn't declare it.
            return (int) Reflection.invoke(player, Reflection.method(player.getClass(), "getPing"));
        }
        // Pre-1.17: read the int ping field straight off the NMS EntityPlayer handle.
        Object handle = Reflection.getHandle(player);
        return (int) Reflection.getFieldValue(handle, Reflection.field(handle.getClass(), "ping"));
    }
}
