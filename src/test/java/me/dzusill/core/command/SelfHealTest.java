package me.dzusill.core.command;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.DzusillCorePlugin;
import me.dzusill.core.command.argument.Arguments;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

/**
 * Holding a name against a plugin that takes it back <em>after</em> we already claimed it.
 *
 * <p>
 * Reported from a live server: {@code /oberonstaff status} read {@code /tp -> the server (taken on use)} and
 * {@code /tphere -> DonutTPA (taken on use)}, even though both were claimed correctly at startup. Something else took
 * the plain name back later than {@link org.bukkit.event.server.ServerLoadEvent} - exactly the moment the one-shot
 * reclaim used to run and stop caring. A single claim cannot defend against a competitor whose own timing is not ours
 * to control; only checking again, indefinitely, can.
 * </p>
 *
 * <p>
 * The takeover is forced directly into the command map rather than through {@code CommandMap.register}, which will not
 * by itself steal an already-taken bare name - as it should not; that guarantee is what makes the reported bug worth
 * taking seriously. Whatever mechanism actually did it live (another plugin's own reflection, or a resync this one did
 * not initiate), the property under test is the same: the map ends up pointing elsewhere, and self-heal must notice.
 * </p>
 */
class SelfHealTest {

    private ServerMock server;
    private CorePlugin plugin;
    private CommandRegistry registry;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DzusillCorePlugin.class);
        registry = plugin.services().get(CommandRegistry.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static CoreCommand fakeTeleport() {
        CoreCommand command = new CoreCommand("tp") {

            @Override
            public void run(CommandContext context, Arguments args) {
                // Only the registration matters here.
            }
        };
        command.takeNameFromOtherPlugins(true);
        return command;
    }

    private static Command competingCommand(String label) {
        return new Command(label) {

            @Override
            public boolean execute(CommandSender sender, String label2, String[] args) {
                return true;
            }
        };
    }

    /**
     * Forces {@code label} to point at {@code command} directly in the live map - the same reflective route
     * {@link CommandRegistry} itself uses. Standard {@code CommandMap.register} refuses to steal an already-taken bare
     * name, which is correct and is exactly why the live bug needs a more direct cause than a second plugin politely
     * asking for the name.
     */
    @SuppressWarnings("unchecked")
    private void forceTakeover(String label, Command command) throws ReflectiveOperationException {
        java.lang.reflect.Method accessor = server.getCommandMap().getClass().getMethod("getKnownCommands");
        java.util.Map<String, Command> known = (java.util.Map<String, Command>) accessor.invoke(server.getCommandMap());
        known.put(label, command);
    }

    @Test
    void aLaterTakeoverIsUndoneWithinOneInterval() throws ReflectiveOperationException {
        registry.register(fakeTeleport());
        server.getScheduler().performTicks(2); // the startup-time claim settles.

        assertTrue(
                registry.ownershipReport().stream()
                        .anyMatch(line -> line.startsWith("/tp -> ") && !line.contains("taken on use")),
                "our claim should hold right after startup");

        // Something takes the plain name back, exactly as observed on the live server - some time after ours.
        Command competitor = competingCommand("tp");
        forceTakeover("tp", competitor);
        assertSame(competitor, server.getCommandMap().getCommand("tp"), "the takeover happened");

        // One self-heal interval (100 ticks) passes.
        server.getScheduler().performTicks(101);

        Command held = server.getCommandMap().getCommand("tp");
        assertTrue(registry.claimed().contains("tp"), "the label is still tracked as ours to hold");
        assertTrue(held != competitor, "the self-heal task must have taken the name back from the competitor");
    }

    @Test
    void aJoiningPlayerGetsAFreshTreeEvenIfSomethingTookTheNameBeforeTheyConnected()
            throws ReflectiveOperationException {
        registry.register(fakeTeleport());
        server.getScheduler().performTicks(2);

        Command competitor = competingCommand("tp");
        forceTakeover("tp", competitor);
        assertSame(competitor, server.getCommandMap().getCommand("tp"), "the takeover happened");

        PlayerMock player = server.addPlayer("Tester");
        server.getScheduler().performTicks(2); // the join-time reclaim is deferred by one tick.

        Command held = server.getCommandMap().getCommand("tp");
        assertTrue(held != competitor, "a joining player's reclaim must run even if the takeover happened earlier");
    }
}
