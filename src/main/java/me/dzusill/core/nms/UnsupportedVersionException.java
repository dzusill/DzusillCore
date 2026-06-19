package me.dzusill.core.nms;

/**
 * Thrown when {@link NmsAdapters} is in strict mode and no registered adapter matches the running
 * {@link MinecraftVersion}. In the default (lenient) mode the registry returns a no-op adapter instead, so plugins keep
 * running with NMS-backed features disabled.
 */
public class UnsupportedVersionException extends RuntimeException {

    public UnsupportedVersionException(MinecraftVersion version) {
        super("No NMS adapter registered for server version " + version);
    }
}
