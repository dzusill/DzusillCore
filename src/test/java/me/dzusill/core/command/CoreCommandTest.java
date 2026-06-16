package me.dzusill.core.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import me.dzusill.core.CorePlugin;
import me.dzusill.core.command.argument.Arguments;
import me.dzusill.core.command.meta.CommandMeta;
import me.dzusill.core.example.ExamplePlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A command body can throw something other than {@link CommandException} (a bug, or — as happened
 * in production — a {@link NoClassDefFoundError} from an optional dependency reached at runtime).
 * {@link CoreCommand#onCommand} must contain that, not let it propagate into Bukkit/Brigadier where
 * it would otherwise reach the server's tick loop as an unhandled {@code Error} and crash the
 * whole server instead of just failing the one command.
 */
class CoreCommandTest {

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

    @CommandMeta(name = "boom", description = "Throws to verify the server never crashes")
    private static final class BoomCommand extends CoreCommand {
        @Override
        public void run(CommandContext context, Arguments args) {
            throw new NoClassDefFoundError("some/missing/Class");
        }
    }

    @Test
    void unhandledErrorDoesNotPropagateAndRepliesWithCommandError() {
        plugin.services().get(CommandRegistry.class).register(new BoomCommand());
        PlayerMock player = server.addPlayer();

        assertDoesNotThrow(() -> player.performCommand("boom"));

        String message = player.nextMessage();
        assertNotNull(message);
        assertTrue(message.contains("error"), "expected command-error message, got: " + message);
    }
}
