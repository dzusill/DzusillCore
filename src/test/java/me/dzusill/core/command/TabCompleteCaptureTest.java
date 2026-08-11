package me.dzusill.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.event.server.TabCompleteEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.DzusillCorePlugin;
import me.dzusill.core.command.argument.Arguments;
import me.dzusill.core.command.meta.CommandMeta;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

/**
 * Tab completion for a command name the server, or another plugin, also owns.
 *
 * <p>
 * The registry rewrites the label on execution, but completion never passes through
 * {@code PlayerCommandPreprocessEvent} — so before this, a captured name <em>ran</em> as ours while <em>completing</em>
 * as theirs. It was reported exactly that way: "the commands that don't tab complete still work when they are used".
 * </p>
 */
class TabCompleteCaptureTest {

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
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Fires the event the same way the server does when somebody presses tab. */
    private List<String> complete(String buffer) {
        TabCompleteEvent event = new TabCompleteEvent(player, buffer, new java.util.ArrayList<>());
        server.getPluginManager().callEvent(event);
        return event.getCompletions();
    }

    @Test
    void ourCommandCompletesItsArguments() {
        registry.register(new ProbeCommand());

        assertTrue(complete("/probe ").contains("elz1one"), "the registry should have answered for its own command");
    }

    @Test
    void completionsAreFilteredByWhatIsTyped() {
        registry.register(new ProbeCommand());

        List<String> completions = complete("/probe elza");

        assertEquals(List.of("elzabeth"), completions);
    }

    @Test
    void anAliasCompletesTheSameWay() {
        registry.register(new ProbeCommand());

        assertTrue(complete("/prb elz").contains("elz1one"));
    }

    @Test
    void aNamespacedFormCompletesToo() {
        registry.register(new ProbeCommand());

        assertTrue(complete("/dzusillcore:probe elz").contains("elz1one"),
                "somebody who types the long form should not be punished for it");
    }

    @Test
    void aCommandWeNeverRegisteredIsLeftAlone() {
        registry.register(new ProbeCommand());

        assertTrue(complete("/somethingelse ").isEmpty(), "answering for another command would be a bug of its own");
    }

    @Test
    void theCommandNameItselfIsLeftToTheServer() {
        registry.register(new ProbeCommand());

        // No space yet: the player is choosing a command, and the server's own list is the right answer.
        assertTrue(complete("/prob").isEmpty());
    }

    @Test
    void chatIsNotTouched() {
        registry.register(new ProbeCommand());

        assertTrue(complete("probe elz").isEmpty(), "a buffer with no slash is chat, not a command");
    }

    /** A command with one online-player argument, which is what the teleports look like. */
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
