package me.dzusill.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.DzusillCorePlugin;

import be.seeseemelk.mockbukkit.MockBukkit;

/**
 * The MiniMessage source of a list-valued message, unrendered.
 *
 * <p>
 * Needed when whatever draws the text is not this plugin — an external hologram plugin, say, which parses the markup
 * itself and takes {@code List<String>}. Handing such a consumer a rendered {@link net.kyori.adventure.text.Component}
 * is impossible across a relocated Adventure anyway, and handing it a legacy string throws the tags away.
 * </p>
 */
class RawListTest {

    private CorePlugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(DzusillCorePlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Writes a messages.yml into the plugin's data folder and returns a service reading it. */
    private MessageService serviceReading(String yaml) throws Exception {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
        return new MessageService(plugin);
    }

    @Test
    @DisplayName("the lines come back exactly as written, tags and all")
    void markupIsNotTouched() throws Exception {
        MessageService messages = serviceReading("""
                hologram:
                  locked:
                    - "<gradient:#C21807:#F11800>Legendary</gradient>"
                    - "<#8e8e8e>Unlocks in <#C21807>{time}"
                """);

        assertEquals(List.of("<gradient:#C21807:#F11800>Legendary</gradient>", "<#8e8e8e>Unlocks in <#C21807>{time}"),
                messages.rawList("hologram.locked"));
    }

    @Test
    @DisplayName("<prefix> is left for the eventual renderer, not expanded here")
    void prefixIsNotExpanded() throws Exception {
        MessageService messages = serviceReading("""
                prefix: "<red>Core </red>"
                banner:
                  - "<prefix>line one"
                """);

        assertEquals(List.of("<prefix>line one"), messages.rawList("banner"));
    }

    @Test
    @DisplayName("a missing key is an empty list, not a list holding the key")
    void aMissingKeyIsEmpty() throws Exception {
        // raw() falls back to the key so the problem shows up in-game. A list has no such shape, and a
        // caller looping over ["hologram.locked"] would draw the key name as though it were content.
        MessageService messages = serviceReading("something: \"else\"\n");

        assertTrue(messages.rawList("hologram.locked").isEmpty());
    }

    @Test
    @DisplayName("rawList and getList read the same key")
    void bothAccessorsAgreeOnTheKey() throws Exception {
        MessageService messages = serviceReading("""
                lines:
                  - "<red>one"
                  - "<red>two"
                """);

        assertEquals(messages.getList("lines", Placeholder.empty()).size(), messages.rawList("lines").size());
    }
}
