package me.dzusill.core.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.DzusillCorePlugin;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;

/**
 * {@link CommandRegistry#owns(String)} — the question a plugin that inspects commands before they run has to be able to
 * ask.
 *
 * <p>
 * The registry rewrites a label into its namespaced form before dispatch. A whitelist or audit layer watching
 * {@code PlayerCommandPreprocessEvent} therefore sees {@code /dzusillcore:core} where the player typed {@code /core},
 * and without this method it cannot tell that apart from someone typing the namespaced form themselves to slip past a
 * list. These tests pin the shapes such a caller passes in.
 * </p>
 *
 * <p>
 * Separate from {@link CommandOwnershipTest}, which asks a different question: that one is about whether a name another
 * plugin or the server holds is ours to take, this one about whether a label we are shown is one we registered.
 * </p>
 */
class CommandRegistryOwnsTest {

    private CorePlugin plugin;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        plugin = MockBukkit.load(DzusillCorePlugin.class);
        assertNotNull(server.getCommandMap().getCommand("core"), "precondition: /core is registered");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private CommandRegistry registry() {
        return plugin.services().get(CommandRegistry.class);
    }

    @Test
    void recognisesARegisteredName() {
        assertTrue(registry().owns("core"));
    }

    @Test
    @DisplayName("accepts every shape a preprocess handler might pass in")
    void toleratesSlashNamespaceArgumentsAndCase() {
        assertTrue(registry().owns("/core"), "leading slash");
        assertTrue(registry().owns("/core reload"), "arguments attached");
        assertTrue(registry().owns("/CORE"), "upper case");
        assertTrue(registry().owns("/dzusillcore:core"), "the registry's own rewrite");
        assertTrue(registry().owns("  /core  "), "surrounding whitespace");
    }

    @Test
    void doesNotClaimNamesItNeverRegistered() {
        assertFalse(registry().owns("msg"));
        assertFalse(registry().owns("/essentials:fly"));
        assertFalse(registry().owns(""));
        assertFalse(registry().owns(null));
    }
}
