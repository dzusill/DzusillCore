package me.dzusill.core.nms;

import me.dzusill.core.nms.version.NoOpNmsAdapter;
import me.dzusill.core.nms.version.ReflectiveNmsAdapter;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NmsAdaptersTest {

    private static MinecraftVersion of(int major, int minor, int patch) {
        return new MinecraftVersion(major, minor, patch, Optional.empty());
    }

    @Test
    void defaultsSelectReflectiveAdapterForSupportedVersions() {
        NmsAdapter adapter = NmsAdapters.defaults().select(of(1, 21, 1));

        assertInstanceOf(ReflectiveNmsAdapter.class, adapter);
        assertTrue(adapter.isSupported());
        assertTrue(adapter.supports(NmsFeature.NMS_HANDLE));
        assertTrue(adapter.supports(NmsFeature.PLAYER_PING));
        assertFalse(adapter.supports(NmsFeature.PACKET_SENDING),
                "reflective default leaves packets to a mapped per-version adapter");
    }

    @Test
    void defaultsFallBackToNoOpBelowTheSupportedFloor() {
        NmsAdapter adapter = NmsAdapters.defaults().select(of(1, 8, 8));

        assertInstanceOf(NoOpNmsAdapter.class, adapter);
        assertFalse(adapter.isSupported());
    }

    @Test
    void strictModeThrowsWhenNothingMatches() {
        NmsAdapters registry = NmsAdapters.empty().strict();

        assertThrows(UnsupportedVersionException.class, () -> registry.select(of(1, 21, 1)));
    }

    @Test
    void onlyTheMatchingFactoryIsInvoked() {
        AtomicBoolean nonMatchingInvoked = new AtomicBoolean(false);

        NmsAdapter adapter = NmsAdapters.empty()
                .register(v -> v.minor() == 21, NoOpNmsAdapter::new)
                .register(v -> v.minor() == 8, v -> {
                    nonMatchingInvoked.set(true);
                    return new NoOpNmsAdapter(v);
                })
                .select(of(1, 21, 1));

        assertInstanceOf(NoOpNmsAdapter.class, adapter);
        assertFalse(nonMatchingInvoked.get(), "a non-matching registration's factory must never run");
    }

    @Test
    void lastRegisteredMatchWins() {
        NmsAdapter override = new NoOpNmsAdapter(of(1, 21, 1));

        NmsAdapter selected = NmsAdapters.defaults()           // reflective for 1.16+
                .register(v -> v.isAtLeast(1, 21), v -> override) // fork override for 1.21+
                .select(of(1, 21, 1));

        assertTrue(selected == override, "a later registration overrides the built-in default");
    }
}
