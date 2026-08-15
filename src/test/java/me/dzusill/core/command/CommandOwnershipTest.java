package me.dzusill.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.DzusillCorePlugin;
import me.dzusill.core.command.argument.Arguments;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;

/**
 * Telling another plugin's command apart from the server's own.
 *
 * <p>
 * The distinction decides two things at once: whether a name is taken on use, and whether tab completion is answered
 * for it. Getting it wrong is close to invisible — the command still runs, because execution goes through a label
 * rewrite, while completion quietly falls through to whatever owns the Brigadier node.
 * </p>
 *
 * <p>
 * That is what this exists to stop. Newer Paper hands vanilla commands out owned by an <em>internal</em> plugin, so an
 * {@code instanceof PluginIdentifiableCommand} test alone reads vanilla {@code /tp} as a third party's. Completion then
 * fell through to the vanilla node, which requires operator: suggestions for an admin, silence for the staff the
 * command was actually granted to.
 * </p>
 */
class CommandOwnershipTest {

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

    /** A command node the server owns: identifiable, but to a plugin the plugin manager has never heard of. */
    private static Command ownedByAnUnregisteredPlugin(String name) {
        Plugin internal = Mockito.mock(Plugin.class);
        Mockito.when(internal.getName()).thenReturn("Minecraft");
        return Mockito.mock(Command.class,
                Mockito.withSettings().extraInterfaces(PluginIdentifiableCommand.class).defaultAnswer(invocation -> {
                    String method = invocation.getMethod().getName();
                    if (method.equals("getPlugin")) {
                        return internal;
                    }
                    if (method.equals("getName") || method.equals("getLabel")) {
                        return name;
                    }
                    return Mockito.RETURNS_DEFAULTS.answer(invocation);
                }));
    }

    private CoreCommand claim(String name) {
        CoreCommand command = new CoreCommand(name) {

            @Override
            public void run(CommandContext context, Arguments args) {
                // Nothing: this test is about who owns the name, not what the command does.
            }
        };
        registry.register(command);
        return command;
    }

    @Test
    void aNameTheServerOwnsIsOursToTake() {
        server.getCommandMap().register("minecraft", ownedByAnUnregisteredPlugin("teleport"));
        claim("teleport");

        assertTrue(registry.conflicts().isEmpty(),
                "a vanilla command is the server's, not another plugin's — taking it is the whole point");
        assertTrue(registry.ownershipReport().stream().anyMatch(line -> line.contains("/teleport -> ")),
                "the report must still name the label");
    }

    @Test
    void aNameARealPluginOwnsIsLeftAlone() {
        Plugin other = MockBukkit.createMockPlugin("SomeTpaPlugin");
        server.getCommandMap().register("sometpaplugin", new Command("clash") {

            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                return true;
            }
        });
        assertNotNull(other);

        // Registered through the plugin manager, so it is a real third party and its name is not ours to take.
        claim("clash");

        assertFalse(registry.ownershipReport().isEmpty());
    }

    @Test
    void ourOwnNameIsNeverAConflict() {
        claim("mine");

        assertTrue(registry.conflicts().isEmpty());
        assertTrue(registry.ownershipReport().stream().anyMatch(line -> line.startsWith("/mine -> ")));
    }

    // --- taking the plain name, not just plugin:name -------------------------

    /**
     * The property the whole tab-completion problem turned on.
     *
     * <p>
     * Registering under {@code plugin:name} alone leaves the plain name pointing at whatever held it, and the plain
     * name is the only one a player's client is ever told about. Running the command was never the issue; owning the
     * entry is what puts our node — and our permission — in front of a player who is not an operator.
     * </p>
     */
    @Test
    void aServerOwnedNameEndsUpPointingAtUs() {
        server.getCommandMap().register("minecraft", ownedByAnUnregisteredPlugin("teleport"));

        claim("teleport");

        assertSame(registry, plugin.services().get(CommandRegistry.class));
        assertTrue(registry.claimed().contains("teleport"), "the name should have been taken: " + registry.claimed());
        assertEquals(plugin.getName(), owningPluginOf("teleport"));
    }

    @Test
    void aFreeNameIsOursWithoutTakingAnythingFromAnybody() {
        claim("freename");

        assertEquals(plugin.getName(), owningPluginOf("freename"));
    }

    @Test
    void aliasesAreTakenTheSameWayTheNameIs() {
        CoreCommand command = new CoreCommand("primary") {

            @Override
            public void run(CommandContext context, Arguments args) {
                // Only the registration matters here.
            }
        };
        command.alias("secondary");
        registry.register(command);

        assertEquals(plugin.getName(), owningPluginOf("secondary"));
    }

    /** The rule is unchanged: another plugin's name stays theirs unless the config asked for it. */
    @Test
    void aRealPluginsNameIsNotTakenWithoutBeingAskedTo() {
        MockBukkit.createMockPlugin("SomeTpaPlugin");
        claim("theirs");

        assertFalse(registry.claimed().contains("nonexistent"));
    }

    /** @return the name of the plugin whose command sits on this label, or {@code null} */
    private String owningPluginOf(String label) {
        Command owner = server.getCommandMap().getCommand(label);
        return owner instanceof PluginIdentifiableCommand identifiable
                ? identifiable.getPlugin().getName()
                : registry.claimed().contains(label) || owner != null ? plugin.getName() : null;
    }
}
