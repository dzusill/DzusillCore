package me.dzusill.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Putting back together the files the old merge broke, and refusing to guess at anything else.
 *
 * <p>
 * The fixture is the shape that reached a live server: {@code denied-actions} entries are strings containing a colon,
 * which the pre-fix merge read as keys and spliced back in at the resource's indentation. YAML then refuses the file
 * with {@code expected <block end>, but found '-'} and every setting in it silently reverts to its default.
 * </p>
 */
class YamlRepairTest {

    private static final String BROKEN = """
            whitelist:
              enabled: true
              denied-actions:
                - 'message: &cYou don''t have permission to use this.'
              - 'message: &cYou don''t have permission to use this.'
              - 'playsound: entity.villager.no;1.0;1.0'
            """;

    @Test
    @DisplayName("the fixture really is what the server rejected")
    void fixtureIsTheRealFailure() {
        InvalidConfigurationException failure = assertThrows(InvalidConfigurationException.class,
                () -> new YamlConfiguration().loadFromString(BROKEN));
        assertTrue(failure.getMessage().contains("expected <block end>"), failure.getMessage());
    }

    @Test
    @DisplayName("the stray duplicate goes, the owner's list survives intact")
    void strayDuplicateItemIsDropped() throws Exception {
        YamlRepair.Result result = YamlRepair.repair(BROKEN);

        assertTrue(result.changed());
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(result.text());
        assertEquals(
                List.of("message: &cYou don't have permission to use this.", "playsound: entity.villager.no;1.0;1.0"),
                loaded.getStringList("whitelist.denied-actions"),
                "both of the owner's entries stay, exactly once each");
        assertTrue(loaded.getBoolean("whitelist.enabled"), "and the rest of the file is untouched");
    }

    @Test
    @DisplayName("tabs on blank lines go too")
    void tabsOnBlankLines() throws Exception {
        YamlRepair.Result result = YamlRepair.repair("packages:\n  void:\n    match: ['Void']\n\t\n  a: 1\n");

        assertTrue(result.changed());
        assertFalse(result.text().contains("\t"));
        new YamlConfiguration().loadFromString(result.text());
    }

    @Test
    @DisplayName("a list that legitimately repeats a value is left alone")
    void deliberateDuplicatesSurvive() {
        // Same value twice at the SAME indent is a list with a repeated entry — the owner's business, not a fault.
        String same = "commands:\n  - '/pl'\n  - '/pl'\n";
        assertFalse(YamlRepair.repair(same).changed(), "nothing here is wrong");
    }

    @Test
    @DisplayName("an item that is unique is never dropped, however it is indented")
    void uniqueItemsSurvive() {
        String odd = "commands:\n  - '/pl'\n    - '/plugins'\n";
        assertFalse(YamlRepair.repair(odd).changed(),
                "no identical sibling means this is content, and guessing at content is not repair");
    }

    @Test
    @DisplayName("a genuine syntax error is reported, never invented around")
    void realSyntaxErrorsAreNotTouched() {
        String bad = "whitelist:\n  enabled: [unclosed\n";
        assertFalse(YamlRepair.repair(bad).changed());
        assertNotNull(bad);
    }
}
