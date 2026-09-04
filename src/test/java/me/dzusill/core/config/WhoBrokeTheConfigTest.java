package me.dzusill.core.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which version of the merge damages a real config: the one before the list-item fix, or the one after.
 *
 * <p>
 * Run against OberonWhitelist's own bundled config, whose {@code denied-actions} entries are strings that contain a
 * colon ({@code 'message: &cYou don''t have permission'}) — the exact shape the server reported as
 * {@code expected <block end>, but found '-'}.
 * </p>
 */
class WhoBrokeTheConfigTest {

    private static final Path WHITELIST_CONFIG = Path.of("../OberonWhitelist/paper/src/main/resources/config.yml");

    /** The owner's file as it is after anything has called {@code save()} on it. */
    private static String redumped(String resourceText) throws Exception {
        YamlConfiguration model = new YamlConfiguration();
        model.loadFromString(resourceText);
        return model.saveToString();
    }

    @Test
    @DisplayName("the merge BEFORE the fix turns OberonWhitelist's own config into invalid YAML")
    void legacyMergeBreaksIt() throws Exception {
        String resource = Files.readString(WHITELIST_CONFIG, StandardCharsets.UTF_8);
        String merged = TextMergeLegacy.merge(redumped(resource), resource, List.of());

        assertNotNull(merged, "the pre-fix merge thinks list items are missing keys and splices them back in");
        InvalidConfigurationException failure = assertThrows(InvalidConfigurationException.class,
                () -> new YamlConfiguration().loadFromString(merged));
        // Same words the server printed.
        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("expected <block end>"),
                "and this is the server's error, reproduced: " + failure.getMessage());
    }

    @Test
    @DisplayName("and the damage the old merge already wrote to disk is repairable, byte for byte")
    void legacyDamageIsRepaired() throws Exception {
        String resource = Files.readString(WHITELIST_CONFIG, StandardCharsets.UTF_8);
        String owners = redumped(resource);
        String damaged = TextMergeLegacy.merge(owners, resource, List.of());

        YamlRepair.Result repaired = YamlRepair.repair(damaged);

        org.junit.jupiter.api.Assertions.assertTrue(repaired.changed(), "there is damage to undo");
        new YamlConfiguration().loadFromString(repaired.text());
        org.junit.jupiter.api.Assertions.assertEquals(owners, repaired.text(),
                "repair must land exactly on the file the owner had before the merge touched it");
    }

    @Test
    @DisplayName("the merge AFTER the fix leaves it alone")
    void fixedMergeLeavesItAlone() throws Exception {
        String resource = Files.readString(WHITELIST_CONFIG, StandardCharsets.UTF_8);
        String merged = TextMerge.merge(redumped(resource), resource, List.of());

        // Nothing is missing from a re-dump of the resource itself, so there is nothing to splice.
        String result = merged == null ? redumped(resource) : merged;
        new YamlConfiguration().loadFromString(result);
    }
}
