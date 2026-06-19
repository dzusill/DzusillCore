package me.dzusill.core.nms;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.example.ExamplePlugin;

import be.seeseemelk.mockbukkit.MockBukkit;

class NmsModuleTest {

    private CorePlugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(ExamplePlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void publishesAnAdapterResolvableByOtherModules() {
        NmsAdapter adapter = plugin.services().get(NmsAdapter.class);

        assertTrue(adapter.isSupported(), "MockBukkit's version is within the reflective adapter's range");
        assertTrue(adapter.supports(NmsFeature.NMS_HANDLE));
    }
}
