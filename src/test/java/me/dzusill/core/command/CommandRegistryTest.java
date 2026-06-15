package me.dzusill.core.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.dzusill.core.CorePlugin;
import me.dzusill.core.example.ExamplePlugin;
import org.bukkit.attribute.Attribute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandRegistryTest {

    private ServerMock server;
    private CorePlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ExamplePlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void healCommandRestoresFullHealth() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        double max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        player.setHealth(max / 2);

        player.performCommand("heal");

        assertEquals(max, player.getHealth());
    }
}
