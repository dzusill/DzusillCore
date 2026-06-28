package me.dzusill.core.menu;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.example.ExamplePlugin;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

/**
 * Regression suite for the "GUI sometimes just doesn't open, no error in console" bug.
 *
 * <p>
 * {@link MenuManager} caches a {@link PlayerMenuContext} per UUID and the context captures the live
 * {@link org.bukkit.entity.Player} object. Before the fix, the only code that released that cache lived in the
 * deletable example package, so every downstream plugin leaked it: after a relog (Bukkit hands out a fresh
 * {@code Player} for the same UUID) the manager kept returning the stale, offline handle and every {@code menu.open()}
 * no-opped silently. The fix moves the cleanup into the manager itself — it self-registers a
 * {@code PlayerQuitEvent -> forget} listener, and {@link MenuManager#context(org.bukkit.entity.Player)} rebinds an
 * existing context to the live player as a backstop. These tests pin both behaviours.
 * </p>
 */
class MenuManagerStaleContextTest {

    private ServerMock server;
    private CorePlugin plugin;
    private MenuManager manager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ExamplePlugin.class);
        manager = new MenuManager(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /**
     * The headline fix. A player opens a menu, disconnects (no plugin-level quit handling), then reconnects with the
     * same UUID. The context handed back must be bound to the reconnected, online player — so the menu actually opens.
     */
    @Test
    void relogBindsContextToReconnectedPlayerViaAutomaticQuitCleanup() {
        UUID id = UUID.randomUUID();

        PlayerMock first = new PlayerMock(server, "Steve", id);
        server.addPlayer(first);
        manager.context(first); // first menu interaction caches the context

        // Disconnect fires PlayerQuitEvent; the manager's self-registered listener releases the context.
        first.disconnect();

        PlayerMock rejoined = new PlayerMock(server, "Steve", id);
        server.addPlayer(rejoined);

        PlayerMenuContext context = manager.context(rejoined);

        assertSame(rejoined, context.player(), "context must be bound to the reconnected player");
        assertTrue(context.player().isOnline(), "menu.open() must target an online handle, not the stale one");
    }

    /**
     * The quit cleanup actually evicts the entry, so a relog starts a fresh session rather than resurrecting the old
     * context (which would still carry the previous session's navigation history and open-menu reference).
     */
    @Test
    void quitEvictsContextSoRelogGetsAFreshSession() {
        UUID id = UUID.randomUUID();

        PlayerMock first = new PlayerMock(server, "Steve", id);
        server.addPlayer(first);
        PlayerMenuContext before = manager.context(first);

        first.disconnect();

        PlayerMock rejoined = new PlayerMock(server, "Steve", id);
        server.addPlayer(rejoined);
        PlayerMenuContext after = manager.context(rejoined);

        assertNotSame(before, after, "a relog must yield a new context, not the leaked pre-relog one");
        assertSame(rejoined, after.player());
    }

    /**
     * Backstop: even if the quit event were somehow missed (hard crash, forced reload), resolving the context for a new
     * live {@code Player} object rebinds it instead of returning the dead handle. Exercises {@link PlayerMenuContext}
     * directly since two {@code Player} objects for one UUID cannot both be online under the server mock.
     */
    @Test
    void bindRebindsOwnerToLivePlayerAndIsANoOpWhenUnchanged() {
        UUID id = UUID.randomUUID();
        PlayerMock first = new PlayerMock(server, "Steve", id);
        PlayerMock rejoined = new PlayerMock(server, "Steve", id);

        PlayerMenuContext context = new PlayerMenuContext(first);

        context.bind(first);
        assertSame(first, context.player(), "binding the same object must not change anything");

        context.bind(rejoined);
        assertSame(rejoined, context.player(), "a new object for the same UUID must rebind to it");
    }

    /**
     * The explicit {@link MenuManager#forget(org.bukkit.entity.Player)} API still evicts and is idempotent (callable
     * twice without error), so existing callers and tests that release contexts manually keep working.
     */
    @Test
    void explicitForgetEvictsAndIsIdempotent() {
        PlayerMock player = server.addPlayer();

        PlayerMenuContext before = manager.context(player);
        manager.forget(player);
        manager.forget(player); // idempotent

        assertNotSame(before, manager.context(player), "forget must drop the cached context");
    }
}
