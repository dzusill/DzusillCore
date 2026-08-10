package me.dzusill.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Legacy colour codes arriving from outside — a LuckPerms prefix, an old config — becoming MiniMessage. */
class LegacyCodesTest {

    @Test
    void anAmpersandColourBecomesATag() {
        assertEquals("<red>[Admin] ", LegacyCodes.toMiniMessage("&c[Admin] "));
    }

    @Test
    void aSectionSignWorksToo() {
        // Some plugins hand back § directly rather than &.
        assertEquals("<gold>[VIP]", LegacyCodes.toMiniMessage("§6[VIP]"));
    }

    @Test
    void formattingCodesAreCovered() {
        assertEquals("<bold><italic>x<reset>", LegacyCodes.toMiniMessage("&l&ox&r"));
    }

    @Test
    void theCaseOfTheCodeDoesNotMatter() {
        assertEquals("<red><bold>A", LegacyCodes.toMiniMessage("&C&LA"));
    }

    @Test
    void spigotHexBecomesAHexTag() {
        assertEquals("<#c21807>Owner", LegacyCodes.toMiniMessage("&x&C&2&1&8&0&7Owner"));
    }

    @Test
    void hexIsMatchedBeforeItsSixPieces() {
        // Read left to right as single codes this would come out as six separate colours.
        assertFalse(LegacyCodes.toMiniMessage("&x&C&2&1&8&0&7Owner").contains("<red>"));
    }

    @Test
    void aMiniMessageTagAlreadyThereIsLeftAlone() {
        // The reason this is not "deserialize legacy, serialize MiniMessage": that would escape this tag.
        String mixed = "<gradient:#C21807:#F11800>Owner</gradient>";

        assertEquals(mixed, LegacyCodes.toMiniMessage(mixed));
    }

    @Test
    void bothFormsSurviveInOneString() {
        assertEquals("<red>[Admin] <bold>Steve", LegacyCodes.toMiniMessage("&c[Admin] <bold>Steve"));
    }

    @Test
    void textWithNoCodesIsUnchanged() {
        assertEquals("plain text", LegacyCodes.toMiniMessage("plain text"));
    }

    @Test
    void anAmpersandThatIsNotACodeIsLeftAlone() {
        assertEquals("Rock & Roll &z", LegacyCodes.toMiniMessage("Rock & Roll &z"));
    }

    @Test
    void nullAndEmptyAreHandled() {
        assertEquals(null, LegacyCodes.toMiniMessage(null));
        assertEquals("", LegacyCodes.toMiniMessage(""));
    }

    @Test
    void detectionAgreesWithTheRewrite() {
        assertTrue(LegacyCodes.hasLegacyCodes("&c[Admin]"));
        assertTrue(LegacyCodes.hasLegacyCodes("&x&C&2&1&8&0&7"));
        assertFalse(LegacyCodes.hasLegacyCodes("<red>Admin"));
        assertFalse(LegacyCodes.hasLegacyCodes("plain"));
    }
}
