package me.dzusill.core.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.example.ExamplePlugin;
import me.dzusill.core.menu.meta.MenuMeta;
import me.dzusill.core.util.ColorUtils;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import net.kyori.adventure.text.Component;

/**
 * Verifies {@link MenuMeta} drives {@code title()/size()/permission()} the same way {@code @CommandMeta} drives a
 * command node, while leaving the override path intact.
 */
class MenuMetaTest {

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

    private PlayerMenuContext context() {
        return new PlayerMenuContext(server.addPlayer());
    }

    @Test
    void annotationDrivesTitleSizeAndPermission() {
        Annotated menu = new Annotated(plugin, context());

        assertEquals(ColorUtils.parse("<red>Hi"), menu.title());
        assertEquals(9, menu.size());
        assertEquals("core.test", menu.permission());
    }

    @Test
    void overridesStillWorkWithoutAnnotation() {
        Overridden menu = new Overridden(plugin, context());

        assertEquals(Component.text("X"), menu.title());
        assertEquals(18, menu.size());
        assertEquals("", menu.permission());
    }

    @Test
    void neitherAnnotationNorOverrideThrows() {
        Bare menu = new Bare(plugin, context());

        assertThrows(IllegalStateException.class, menu::title);
        assertThrows(IllegalStateException.class, menu::size);
    }

    @MenuMeta(title = "<red>Hi", size = 9, permission = "core.test")
    private static final class Annotated extends Menu {
        Annotated(CorePlugin plugin, PlayerMenuContext context) {
            super(plugin, context);
        }

        @Override
        protected void decorate() {
        }
    }

    private static final class Overridden extends Menu {
        Overridden(CorePlugin plugin, PlayerMenuContext context) {
            super(plugin, context);
        }

        @Override
        public Component title() {
            return Component.text("X");
        }

        @Override
        public int size() {
            return 18;
        }

        @Override
        protected void decorate() {
        }
    }

    private static final class Bare extends Menu {
        Bare(CorePlugin plugin, PlayerMenuContext context) {
            super(plugin, context);
        }

        @Override
        protected void decorate() {
        }
    }
}
