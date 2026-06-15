package me.dzusill.core.config;

import be.seeseemelk.mockbukkit.MockBukkit;
import me.dzusill.core.CorePlugin;
import me.dzusill.core.example.ExamplePlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSyncTest {

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
    void defaultsAreLoadedFromBundledResource() {
        ConfigManager configs = plugin.services().get(ConfigManager.class);
        SettingsConfig settings = configs.get(SettingsConfig.class);

        assertNotNull(settings.prefix());
        assertFalse(settings.prefix().isEmpty());
        assertFalse(settings.debug());
    }

    @Test
    void reloadKeepsValuesAvailable() {
        ConfigManager configs = plugin.services().get(ConfigManager.class);
        configs.reload();

        assertTrue(configs.get(SettingsConfig.class).prefix().contains("Core"));
    }
}
