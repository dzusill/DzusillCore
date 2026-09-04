package me.dzusill.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The {@code Presentation} block, as a server owner writes it.
 *
 * <p>
 * The block used to do nothing unless the plugin happened to wire it up and to have categorised its keys, so the same
 * four lines worked in one plugin and were ignored by the next. The framework's own refusals are categorised here,
 * which is what makes "give me a sound on every permission denial" a config change rather than a code change in fifteen
 * plugins.
 * </p>
 */
class ConfiguredPresentationTest {

    private static PresentationSettings settings(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return PresentationSettings.from(config.getConfigurationSection("Presentation"));
    }

    /** Exactly the block now shipped in every Oberon plugin's config.yml. */
    private static final String SHIPPED = """
            Presentation:
              Categories:
                ERROR:
                  Channel: BOTH
                  Sound:
                    Enabled: true
                    Name: ENTITY_VILLAGER_NO
                    Volume: 0.7
                    Pitch: 1.0
                INFO:
                  Channel: CHAT
                  Sound:
                    Enabled: false
              Overrides: {}
            """;

    @Test
    @DisplayName("every framework refusal is filed under ERROR, so one block governs all of them")
    void frameworkKeysAreErrors() {
        var categories = Messages.categories();
        for (String key : new String[]{Messages.NO_PERMISSION, Messages.INVALID_USAGE, Messages.UNKNOWN_COMMAND,
                Messages.PLAYER_NOT_FOUND, Messages.INVALID_NUMBER, Messages.COMMAND_ERROR}) {
            assertEquals(MessageCategory.ERROR, categories.get(key), key + " is a refusal");
        }
        assertEquals(MessageCategory.INFO, categories.get(Messages.RELOAD_SUCCESS), "a success is not an error");
    }

    @Test
    @DisplayName("the shipped block puts refusals in chat and the action bar, with a sound")
    void shippedBlockDrivesErrors() throws Exception {
        Presentation error = settings(SHIPPED).resolve(Messages.NO_PERMISSION,
                Messages.categories().get(Messages.NO_PERMISSION));

        assertEquals(MessageChannel.BOTH, error.channel());
        assertTrue(error.channel().chat() && error.channel().actionBar(), "BOTH means both");
        assertFalse(error.sound().silent(), "a refusal is the one message worth hearing");
        assertEquals("ENTITY_VILLAGER_NO", error.sound().name());
        assertEquals(0.7f, error.sound().volume(), 0.0001f);
    }

    @Test
    @DisplayName("an ordinary message is left in chat, silent")
    void infoStaysPlain() throws Exception {
        Presentation info = settings(SHIPPED).resolve(Messages.RELOAD_SUCCESS, MessageCategory.INFO);

        assertEquals(MessageChannel.CHAT, info.channel());
        assertTrue(info.sound().silent());
    }

    @Test
    @DisplayName("an override beats the category it belongs to, and inherits what it does not state")
    void overrideWinsAndInherits() throws Exception {
        PresentationSettings settings = settings("""
                Presentation:
                  Categories:
                    ERROR:
                      Channel: BOTH
                      Sound:
                        Name: ENTITY_VILLAGER_NO
                  Overrides:
                    no-permission:
                      Channel: ACTION_BAR
                """);

        Presentation overridden = settings.resolve(Messages.NO_PERMISSION, MessageCategory.ERROR);
        assertEquals(MessageChannel.ACTION_BAR, overridden.channel(), "the override decides the channel");
        assertEquals("ENTITY_VILLAGER_NO", overridden.sound().name(),
                "and says nothing about sound, so it keeps the category's");
    }

    @Test
    @DisplayName("a plugin that ships no block behaves exactly as it always did")
    void noBlockIsNotAChange() throws Exception {
        Presentation plain = settings("something-else: true\n").resolve(Messages.NO_PERMISSION, MessageCategory.ERROR);

        assertEquals(MessageChannel.CHAT, plain.channel());
        assertTrue(plain.sound().silent());
    }
}
