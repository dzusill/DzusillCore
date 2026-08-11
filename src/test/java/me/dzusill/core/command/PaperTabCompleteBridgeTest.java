package me.dzusill.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.DzusillCorePlugin;
import me.dzusill.core.command.argument.Arguments;
import me.dzusill.core.command.meta.CommandMeta;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

/**
 * The event a player's tab completion actually fires on Paper.
 *
 * <p>
 * Bukkit's {@code TabCompleteEvent} covers the case where our command already owns the Brigadier node — which never
 * needed help. This one covers the case that does: a name vanilla or another plugin owns, taken on execution. Reading
 * {@code ServerGamePacketListenerImpl} is what settled which event to use; these tests keep the wiring honest.
 * </p>
 */
class PaperTabCompleteBridgeTest {

    private ServerMock server;
    private CorePlugin plugin;
    private CommandRegistry registry;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DzusillCorePlugin.class);
        registry = plugin.services().get(CommandRegistry.class);
        player = server.addPlayer("Tester");
        server.addPlayer("elz1one");
        server.addPlayer("elzabeth");
        registry.register(new ProbeCommand());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * Fires the event off the main thread, as the server does — it is an async event and Bukkit refuses to call one
     * from the main thread at all.
     */
    private AsyncTabCompleteEvent fire(String buffer) {
        AsyncTabCompleteEvent event = new AsyncTabCompleteEvent(player, new ArrayList<>(), buffer, true, null);
        Thread thread = new Thread(() -> server.getPluginManager().callEvent(event), "tab-complete");
        thread.start();
        try {
            thread.join(5000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return event;
    }

    @Test
    void ourCommandIsAnsweredOnThePaperEvent() {
        AsyncTabCompleteEvent event = fire("/probe elz");

        assertEquals(List.of("elz1one", "elzabeth"), event.getCompletions());
    }

    @Test
    void theEventIsMarkedHandled() {
        // Without this Paper lets the Brigadier node answer as well — which is where the other plugin's
        // suggestions were coming from.
        assertTrue(fire("/probe elz").isHandled());
    }

    @Test
    void aCommandWeNeverRegisteredIsLeftAlone() {
        AsyncTabCompleteEvent event = fire("/somethingelse ");

        assertTrue(event.getCompletions().isEmpty());
        assertFalse(event.isHandled(), "claiming another command's completion would be a bug of its own");
    }

    @Test
    void theCommandNameItselfIsLeftToTheServer() {
        assertFalse(fire("/prob").isHandled());
    }

    @Test
    void chatIsNotTouched() {
        assertFalse(fire("probe elz").isHandled());
    }

    @Test
    void anAliasIsAnsweredToo() {
        assertEquals(List.of("elz1one", "elzabeth"), fire("/prb elz").getCompletions());
    }

    @CommandMeta(name = "probe", aliases = {"prb"}, description = "probe")
    public static final class ProbeCommand extends CoreCommand {

        public ProbeCommand() {
            super();
            arg("player", new me.dzusill.core.command.argument.types.OnlinePlayerArgument());
        }

        @Override
        public void run(CommandContext context, Arguments args) {
        }
    }
}
