package me.dzusill.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * How a {@code Presentation} block becomes a decision about one message.
 *
 * <p>
 * The rules worth pinning are the inheritance ones. A config that states four things must not silently decide the other
 * forty, and an override that mentions only a channel must not take the sound away with it — both are the kind of
 * mistake that shows up as "the sound stopped working" weeks later, with nothing in a log.
 * </p>
 */
class PresentationSettingsTest {

    private static ConfigurationSection parse(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (Exception invalid) {
            throw new IllegalArgumentException(invalid);
        }
        return config.getConfigurationSection("Presentation");
    }

    private static final String FULL = """
            Presentation:
              Categories:
                TOGGLE:
                  Channel: ACTION_BAR
                  Sound:
                    Enabled: true
                    Name: entity.experience_orb.pickup
                    Volume: 0.6
                    Pitch: 1.6
                ERROR:
                  Channel: BOTH
                  Sound:
                    Enabled: true
                    Name: entity.villager.no
              Overrides:
                teleport.here:
                  Channel: BOTH
                staffchat.enabled:
                  Sound:
                    Enabled: false
            """;

    @Test
    void noBlockAtAllLeavesEverythingInChat() {
        PresentationSettings settings = PresentationSettings.from(null);

        Presentation resolved = settings.resolve("anything", MessageCategory.TOGGLE);

        assertTrue(settings.isEmpty());
        assertEquals(MessageChannel.CHAT, resolved.channel());
        assertTrue(resolved.sound().silent());
    }

    @Test
    void aCategoryDecidesEveryKeyFiledUnderIt() {
        PresentationSettings settings = PresentationSettings.from(parse(FULL));

        Presentation resolved = settings.resolve("tptoggle.blocked", MessageCategory.TOGGLE);

        assertEquals(MessageChannel.ACTION_BAR, resolved.channel());
        assertEquals("entity.experience_orb.pickup", resolved.sound().name());
        assertEquals(0.6f, resolved.sound().volume(), 0.0001f);
        assertEquals(1.6f, resolved.sound().pitch(), 0.0001f);
    }

    @Test
    void anUnconfiguredCategoryStaysInChat() {
        PresentationSettings settings = PresentationSettings.from(parse(FULL));

        // TELEPORT is not in the block above.
        Presentation resolved = settings.resolve("teleport.to", MessageCategory.TELEPORT);

        assertEquals(MessageChannel.CHAT, resolved.channel());
        assertTrue(resolved.sound().silent());
    }

    @Test
    void anUncategorisedKeyIsInfo() {
        PresentationSettings settings = PresentationSettings.from(parse(FULL));

        assertEquals(MessageChannel.CHAT, settings.resolve("some.key", null).channel());
    }

    /** The reading a server owner expects when they write two lines and not four. */
    @Test
    void anOverrideThatNamesOnlyAChannelKeepsTheCategorySound() {
        PresentationSettings settings = PresentationSettings.from(parse(FULL));

        Presentation resolved = settings.resolve("teleport.here", MessageCategory.TOGGLE);

        assertEquals(MessageChannel.BOTH, resolved.channel());
        assertEquals("entity.experience_orb.pickup", resolved.sound().name());
    }

    /** Switched off is a decision, and must not inherit the sound back. */
    @Test
    void anOverrideCanSilenceOneMessageWithoutChangingItsChannel() {
        PresentationSettings settings = PresentationSettings.from(parse(FULL));

        Presentation resolved = settings.resolve("staffchat.enabled", MessageCategory.TOGGLE);

        assertEquals(MessageChannel.ACTION_BAR, resolved.channel());
        assertTrue(resolved.sound().silent());
    }

    @Test
    void overridesAreMatchedIgnoringCase() {
        PresentationSettings settings = PresentationSettings.from(parse(FULL));

        assertEquals(MessageChannel.BOTH, settings.resolve("TELEPORT.HERE", MessageCategory.TOGGLE).channel());
    }

    @Test
    void aChannelNameNobodyKnowsFallsBackRatherThanThrowing() {
        PresentationSettings settings = PresentationSettings.from(parse("""
                Presentation:
                  Categories:
                    TOGGLE:
                      Channel: SKYWRITING
                """));

        assertEquals(MessageChannel.CHAT, settings.resolve("x", MessageCategory.TOGGLE).channel());
    }

    @Test
    void aCategoryNameNobodyKnowsIsIgnoredRatherThanFatal() {
        PresentationSettings settings = PresentationSettings.from(parse("""
                Presentation:
                  Categories:
                    SHOUTING:
                      Channel: ACTION_BAR
                    ERROR:
                      Channel: BOTH
                """));

        assertNotNull(settings);
        assertEquals(MessageChannel.BOTH, settings.resolve("x", MessageCategory.ERROR).channel());
    }

    @Test
    void channelsKnowWhereTheyGo() {
        assertTrue(MessageChannel.BOTH.chat());
        assertTrue(MessageChannel.BOTH.actionBar());
        assertTrue(MessageChannel.CHAT.chat());
        assertFalse(MessageChannel.CHAT.actionBar());
        assertFalse(MessageChannel.NONE.chat());
        assertFalse(MessageChannel.NONE.actionBar());
    }

    /** Both spellings appear in the wild, and neither is obviously the one to demand. */
    @Test
    void aSoundNameIsAcceptedInEitherSpelling() {
        assertFalse(new SoundSpec("ENTITY_VILLAGER_NO", 1f, 1f).silent());
        assertFalse(new SoundSpec("entity.villager.no", 1f, 1f).silent());
        assertTrue(new SoundSpec("", 1f, 1f).silent());
        assertTrue(SoundSpec.NONE.silent());
    }
}
