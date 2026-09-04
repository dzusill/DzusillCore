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

    /**
     * The shape that reached a client's server: a tier customised by hand, with the added block indented by a tab
     * because it was pasted. One tab voided the file, so the plugin loaded no tiers at all and spawned nothing.
     */
    private static final String TAB_INDENTED = """
            tiers:
              stellar:
                weight: 60
                unlock-seconds: -1
            \tcommands:
                  - "eco give {player} 1000000"
            \t  - "stardust give {player} 1000"
                loot:
                  guaranteed:
                    - material: IRON_INGOT
            """;

    @Test
    @DisplayName("the tab fixture really is what the server rejected")
    void tabFixtureIsTheRealFailure() {
        InvalidConfigurationException failure = assertThrows(InvalidConfigurationException.class,
                () -> new YamlConfiguration().loadFromString(TAB_INDENTED));
        assertTrue(failure.getMessage().contains("\\t"), failure.getMessage());
    }

    @Test
    @DisplayName("tab indentation becomes the indent the line above says it meant")
    void tabIndentedLinesAreRealigned() throws Exception {
        YamlRepair.Result result = YamlRepair.repair(TAB_INDENTED);

        assertTrue(result.changed());
        assertFalse(result.text().contains("\t"));
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(result.text());

        assertEquals(List.of("eco give {player} 1000000", "stardust give {player} 1000"),
                loaded.getStringList("tiers.stellar.commands"),
                "the pasted block lands on the tier that owns it, both entries intact");
        assertEquals(60, loaded.getInt("tiers.stellar.weight"), "and the rest of the tier is untouched");
        assertNotNull(loaded.getConfigurationSection("tiers.stellar.loot"));
    }

    @Test
    @DisplayName("a key after one that already holds a value can only be its sibling")
    void tabAfterAScalarBecomesASibling() throws Exception {
        YamlRepair.Result result = YamlRepair.repair("a:\n  b: 1\n\tc: 2\n");

        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(result.text());
        assertEquals(1, loaded.getInt("a.b"));
        assertEquals(2, loaded.getInt("a.c"), "c is b's sibling — a scalar cannot own children");
    }

    @Test
    @DisplayName("a key after one that opens a block becomes its child")
    void tabAfterABlockOpenerBecomesAChild() throws Exception {
        YamlRepair.Result result = YamlRepair.repair("a:\n  b:\n\tc: 2\n");

        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(result.text());
        assertEquals(2, loaded.getInt("a.b.c"), "b has no value, so the indented line is what fills it");
    }

    @Test
    @DisplayName("the file's own indent step is used, not a tab width")
    void indentStepComesFromTheFile() throws Exception {
        // Four-space file: expanding the tab to two columns would parse and quietly reparent the key.
        YamlRepair.Result result = YamlRepair.repair("root:\n    a:\n\t    b: 1\n");

        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(result.text());
        assertEquals(1, loaded.getInt("root.a.b"), "b belongs to a, at this file's four-column step");
    }

    @Test
    @DisplayName("a tab inside a value is not indentation and is left alone")
    void tabsInsideValuesSurvive() {
        String inValue = "message: \"a\tb\"\n";
        assertFalse(YamlRepair.repair(inValue).changed(), "only leading whitespace is indentation");
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
