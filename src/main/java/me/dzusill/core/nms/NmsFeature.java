package me.dzusill.core.nms;

/**
 * Optional capabilities an {@link NmsAdapter} may provide. Callers should gate version-sensitive code with
 * {@link NmsAdapter#supports(NmsFeature)} instead of catching exceptions, so a server on an unmapped/untested version
 * degrades gracefully rather than erroring.
 *
 * <p>
 * This is a deliberately small starter set covering the primitives most plugins reach for NMS to do. Forks add their
 * own constants here as they grow the {@link NmsAdapter} contract.
 * </p>
 */
public enum NmsFeature {

    /** Unwrap a CraftBukkit wrapper (e.g. {@code CraftPlayer}) to its underlying NMS handle. */
    NMS_HANDLE,

    /** Send a raw NMS packet to a player's connection. */
    PACKET_SENDING,

    /** Read a player's connection latency (Bukkit-native from 1.17, NMS reflection before). */
    PLAYER_PING
}
