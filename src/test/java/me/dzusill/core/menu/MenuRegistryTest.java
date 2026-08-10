package me.dzusill.core.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.DzusillCorePlugin;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

/**
 * Exercises register / open-by-key / permission gating, the GUI analogue of {@code CommandRegistryTest}. The framework
 * registers no menus itself - menus belong to whichever plugin needs them.
 *
 * <p>
 * The allow/deny paths use a {@link TestMenu} whose {@code open()} is overridden to a no-op: MockBukkit cannot build a
 * custom-holder inventory (it throws), so the test menu lets us assert the registry's routing and permission decision
 * deterministically.
 * </p>
 */
class MenuRegistryTest {

    private ServerMock server;
    private CorePlugin plugin;
    private MenuRegistry registry;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DzusillCorePlugin.class);
        registry = plugin.services().get(MenuRegistry.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theFrameworkRegistersNoMenusOfItsOwn() {
        // A demo "shop" menu used to be registered here by MenuModule, on every server using the framework.
        assertFalse(registry.isRegistered("shop"));
    }

    @Test
    void aRegisteredKeyIsFoundCaseInsensitively() {
        registry.register("Vault", (pl, ctx) -> new TestMenu(pl, ctx, ""));

        assertTrue(registry.isRegistered("vault"));
        assertTrue(registry.isRegistered("VAULT"));
        assertFalse(registry.isRegistered("does-not-exist"));
    }

    @Test
    void openUnknownKeyReturnsFalse() {
        PlayerMock player = server.addPlayer();
        assertFalse(registry.open(player, "nope"));
    }

    @Test
    void openInvokesFactoryAndOpensWhenAllowed() {
        PlayerMock player = server.addPlayer();
        boolean[] created = {false};
        TestMenu[] built = new TestMenu[1];
        registry.register("allowed", (pl, ctx) -> {
            created[0] = true;
            built[0] = new TestMenu(pl, ctx, "");
            return built[0];
        });

        assertTrue(registry.open(player, "allowed"));
        assertTrue(created[0]);
        assertTrue(built[0].opened);
    }

    @Test
    void openIsDeniedWithoutPermission() {
        PlayerMock player = server.addPlayer();
        // MockBukkit grants every permission by default, so deny it explicitly.
        player.addAttachment(plugin, "perm.needed", false);
        TestMenu[] built = new TestMenu[1];
        registry.register("gated", (pl, ctx) -> {
            built[0] = new TestMenu(pl, ctx, "perm.needed");
            return built[0];
        });

        assertFalse(registry.open(player, "gated"));
        assertFalse(built[0].opened);
    }

    /**
     * A menu whose {@code open()} records the call instead of building a (MockBukkit-unsupported) inventory, and whose
     * permission is supplied per-instance.
     */
    private static final class TestMenu extends Menu {

        private final String perm;
        boolean opened = false;

        TestMenu(CorePlugin plugin, PlayerMenuContext context, String perm) {
            super(plugin, context);
            this.perm = perm;
        }

        @Override
        public String permission() {
            return perm;
        }

        @Override
        public void open() {
            this.opened = true;
        }

        @Override
        protected void decorate() {
        }
    }
}
