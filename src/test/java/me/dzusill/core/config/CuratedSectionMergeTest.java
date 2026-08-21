package me.dzusill.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * How an ignored ("curated") section upgrades.
 *
 * <p>
 * These sections exist so a list the owner maintains — a plugin's tools, its price overrides, its announcements — does
 * not regrow entries they deleted. The original implementation achieved that by skipping the whole branch, which also
 * meant a key added to a shipped entry never reached any server that already had a config: the feature behind it was
 * silently dead, and every install had to be hand-edited. Both halves are pinned here.
 * </p>
 */
class CuratedSectionMergeTest {

    private static final String SHIPPED = """
            tools:
              lumber_axe:
                enabled: true
                max-radius: 3
              new_tool:
                enabled: true
            unmanaged:
              kept: 1
            """;

    private static Config merged(String existing) {
        Config current = Config.loadConfiguration(new ByteArrayInputStream(existing.getBytes(StandardCharsets.UTF_8)),
                "current.yml");
        Config shipped = Config.loadConfiguration(new ByteArrayInputStream(SHIPPED.getBytes(StandardCharsets.UTF_8)),
                "shipped.yml");
        current.mergeForTest(shipped, List.of("tools"));
        return current;
    }

    /** The regression: a key added to an entry the owner kept must actually arrive. */
    @Test
    void aNewKeyReachesAnEntryTheOwnerKept() {
        Config result = merged("""
                tools:
                  lumber_axe:
                    enabled: false
                """);

        assertEquals(3, result.getInt("tools.lumber_axe.max-radius"),
                "a key added to a shipped entry must reach servers that already had a config");
        assertFalse(result.getBoolean("tools.lumber_axe.enabled"),
                "the owner's own value must survive the upgrade untouched");
    }

    /** And the half that must not regress: a deleted entry stays deleted. */
    @Test
    void anEntryTheOwnerDeletedIsNotPutBack() {
        Config result = merged("""
                tools:
                  lumber_axe:
                    enabled: true
                """);

        assertFalse(result.contains("tools.new_tool"), "a curated list must not regrow entries the owner removed");
        assertNull(result.getConfigurationSection("tools.new_tool"));
    }

    @Test
    void anAbsentCuratedSectionIsStillCreatedInFull() {
        Config result = merged("unmanaged:\n  kept: 1\n");

        assertTrue(result.contains("tools.lumber_axe"), "a fresh install must get the shipped entries");
        assertTrue(result.contains("tools.new_tool"));
    }

    @Test
    void sectionsOutsideTheCuratedListMergeAsBefore() {
        Config result = merged("tools:\n  lumber_axe:\n    enabled: true\n");

        assertEquals(1, result.getInt("unmanaged.kept"),
                "an ordinary section still gains everything the resource adds");
    }
}
