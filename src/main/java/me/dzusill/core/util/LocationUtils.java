package me.dzusill.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Serializes {@link Location}s to and from a compact string form suitable for YAML storage:
 * {@code world,x,y,z,yaw,pitch}. Keeping this in one place avoids subtly different formats
 * across plugins.
 */
public final class LocationUtils {

    private static final String SEPARATOR = ",";

    private LocationUtils() {
    }

    /**
     * Serializes a location to {@code world,x,y,z,yaw,pitch}.
     */
    public static String serialize(Location location) {
        return String.join(SEPARATOR,
                location.getWorld().getName(),
                Double.toString(location.getX()),
                Double.toString(location.getY()),
                Double.toString(location.getZ()),
                Float.toString(location.getYaw()),
                Float.toString(location.getPitch()));
    }

    /**
     * Parses a serialized location.
     *
     * @return the location, or {@code null} if the string is malformed or the world is not loaded
     */
    public static Location deserialize(String serialized) {
        if (serialized == null) {
            return null;
        }
        String[] parts = serialized.split(SEPARATOR);
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
