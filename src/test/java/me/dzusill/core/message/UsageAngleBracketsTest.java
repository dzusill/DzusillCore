package me.dzusill.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.dzusill.core.util.ColorUtils;

/**
 * A usage line survives being rendered, angle brackets and all.
 *
 * <p>
 * Every usage message is substituted into a MiniMessage template — {@code "Usage: {usage}"} — carrying text like
 * {@code <material>} or {@code <prices|playerdata>}. Those are exactly the shape of a MiniMessage tag, so the rendered
 * message depends on what the parser does with a tag it does not know. The default instance leaves it as literal text,
 * which is the behaviour every usage message in every plugin quietly relies on; a strict parser, or a resolver that
 * started claiming those names, would blank the useful half of the line.
 * </p>
 */
class UsageAngleBracketsTest {

    private static String rendered(String miniMessage) {
        return ColorUtils.strip(miniMessage);
    }

    @Test
    void anArgumentPlaceholderSurvivesAsLiteralText() {
        assertEquals("Usage: /worth <material>", rendered("<red>Usage: <white>/worth <material>"));
    }

    /** The enum form, which also carries a pipe. */
    @Test
    void aChoiceListSurvivesToo() {
        assertTrue(rendered("<red>Usage: <white>/oberonsell migrate <prices|playerdata>")
                .endsWith("/oberonsell migrate <prices|playerdata>"));
    }

    /** Optional arguments use square brackets, which were never at risk — pinned so the pair stays covered. */
    @Test
    void anOptionalArgumentIsUntouched() {
        assertEquals("Usage: /selltop [money|items] [page]",
                rendered("<red>Usage: <white>/selltop [money|items] [page]"));
    }

    /** Colours around the usage still apply — the brackets must not swallow the rest of the line. */
    @Test
    void theRestOfTheLineStillRenders() {
        assertEquals("Usage: /worth <material> — try again",
                rendered("<red>Usage: <white>/worth <material> <gray>— try again"));
    }
}
