package me.dzusill.core.command;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.DzusillCorePlugin;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;

/**
 * What the framework itself puts in a server's command map.
 *
 * <p>
 * The answer has to stay "one administration command and nothing else". It used to also register a demo {@code /shop},
 * {@code /heal} and {@code /coredialog} — so every server that installed the framework as a dependency got an "Example
 * Shop" selling a diamond, open to every player by default. These tests exist so that cannot come back unnoticed.
 * </p>
 */
class CommandRegistryTest {

    private ServerMock server;
    private CorePlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DzusillCorePlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theAdministrationCommandIsRegistered() {
        assertNotNull(server.getCommandMap().getCommand("core"));
    }

    @Test
    void noDemoCommandsAreRegistered() {
        for (String demo : new String[]{"shop", "heal", "coredialog"}) {
            assertNull(server.getCommandMap().getCommand(demo),
                    "/" + demo + " is a demo; a framework must not put it on a production server");
        }
    }

    @Test
    void theRegistryIsAvailableToDownstreamPlugins() {
        // The point of the module: other plugins resolve this rather than reaching for Bukkit's command map.
        assertNotNull(plugin.services().get(CommandRegistry.class));
    }
}
