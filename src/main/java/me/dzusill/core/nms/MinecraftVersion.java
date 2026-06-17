package me.dzusill.core.nms;

import java.util.Optional;

/**
 * Immutable, parsed view of the running Minecraft server version (e.g. {@code 1.20.4}).
 *
 * <p>Adapters branch on {@link #isAtLeast(int, int)} / {@link #compareTo(MinecraftVersion)} rather
 * than on package strings, because the two historical breakpoints in the supported range cannot be
 * derived from the CraftBukkit package alone:</p>
 * <ul>
 *   <li><b>1.17</b> — NMS moved from {@code net.minecraft.server.v1_16_R3.*} to {@code net.minecraft.*}.</li>
 *   <li><b>1.20.5</b> — Paper stopped relocating CraftBukkit into a {@code vX_Y_RZ} package and switched
 *       to Mojang mappings, so {@link #craftBukkitTag()} is empty from then on.</li>
 * </ul>
 *
 * @param major          major version (always {@code 1} for current Minecraft)
 * @param minor          minor version (e.g. {@code 20} in {@code 1.20.4})
 * @param patch          patch version (e.g. {@code 4} in {@code 1.20.4}; {@code 0} when absent)
 * @param craftBukkitTag the legacy {@code vMAJOR_MINOR_RREVISION} package token (e.g. {@code v1_16_R3}),
 *                       present only on servers up to 1.20.4 and empty on 1.20.5+
 */
public record MinecraftVersion(int major, int minor, int patch, Optional<String> craftBukkitTag)
        implements Comparable<MinecraftVersion> {

    /**
     * @return {@code true} if this version is at least {@code major.minor} (patch ignored)
     */
    public boolean isAtLeast(int major, int minor) {
        return this.major > major || (this.major == major && this.minor >= minor);
    }

    /**
     * @return {@code true} if this version is at least {@code major.minor.patch}
     */
    public boolean isAtLeast(int major, int minor, int patch) {
        return compareTo(new MinecraftVersion(major, minor, patch, Optional.empty())) >= 0;
    }

    /**
     * @return {@code true} if this version is strictly older than {@code major.minor}
     */
    public boolean isBefore(int major, int minor) {
        return !isAtLeast(major, minor);
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) {
            return byMinor;
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return patch == 0 ? major + "." + minor : major + "." + minor + "." + patch;
    }
}
