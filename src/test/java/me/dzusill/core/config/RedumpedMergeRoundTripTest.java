package me.dzusill.core.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The whole loop, end to end: a config Bukkit has re-dumped, merged against the resource it came from.
 *
 * <p>
 * This is the shape every server file eventually takes — anything that calls {@code save()} rewrites the file in
 * Bukkit's own style, which puts list items at their key's indent rather than deeper — and the merge runs against it on
 * every single load. Whatever comes out must still be YAML, or the plugin silently falls back to defaults.
 * </p>
 */
class RedumpedMergeRoundTripTest {

    private static void roundTrip(String resourceText) throws Exception {
        // What the owner's file looks like after anything has saved it.
        YamlConfiguration model = new YamlConfiguration();
        model.loadFromString(resourceText);
        String redumped = model.saveToString();

        String merged = TextMerge.merge(redumped, resourceText, List.of());
        // Null means "nothing to add", which is the correct answer for a file that already holds every key.
        String result = merged == null ? redumped : merged;

        // The assertion that matters: it still parses. A merge that produces invalid YAML takes the whole file out.
        new YamlConfiguration().loadFromString(result);
    }

    @Test
    @DisplayName("a re-dumped config merged against its own resource stays valid YAML")
    void listsOfColonBearingStrings() throws Exception {
        roundTrip("""
                whitelist:
                  enabled: true
                  denied-actions:
                    - 'message: &cYou don''t have permission to use this.'
                    - 'actionbar: &cYou don''t have permission to use this.'
                    - 'playsound: entity.villager.no;1.0;1.0'
                  blocked-actions:
                    - 'message: &cUnknown command.'
                  commands:
                    - '/pl'
                    - '/bukkit:pl'
                messages:
                  prefix: '&8[&bWL&8] '
                """);
    }

    @Test
    @DisplayName("the same, against every config this framework ships")
    void everyBundledConfig() throws Exception {
        Path resources = Path.of("src/main/resources");
        try (var files = Files.list(resources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".yml")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                assertNotNull(text);
                roundTrip(text);
            }
        }
    }
}
