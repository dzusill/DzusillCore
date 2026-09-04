package me.dzusill.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What an update does to a config a server owner has already edited.
 *
 * <p>
 * Two promises are being held here at once, and they pull against each other. Everything the owner has must survive
 * exactly as they left it, including the parts YAML has no model for. Everything a new version added must arrive,
 * including inside lists the owner curates.
 * </p>
 */
class CuratedSectionMergeTest {

    private static String merge(String server, String resource, String... ignored) {
        return TextMerge.merge(server, resource, List.of(ignored));
    }

    @Nested
    @DisplayName("nothing the owner wrote is lost")
    class Preserving {

        @Test
        @DisplayName("a comment between list items survives")
        void commentInsideAList() {
            String server = """
                    sources:
                      - CHEST
                      # Custom GUIs, matched on their title:
                      - "Faction Chest"
                    """;
            String resource = """
                    sources:
                      - CHEST
                      # Custom GUIs, matched on their title:
                      - "Faction Chest"
                    added: 1
                    """;
            String out = merge(server, resource);
            assertNotNull(out);
            assertTrue(out.contains("  # Custom GUIs, matched on their title:"),
                    "the in-list comment must still be there");
            assertTrue(out.contains("  - \"Faction Chest\""), "quoting must not be normalised away");
        }

        @Test
        @DisplayName("a section whose only content is a commented-out example is kept")
        void sectionThatIsEmptyToYaml() {
            String server = """
                    Overrides:
                      # Example - uncomment to silence one message:
                      # sellaxe.nothing:
                      #   Channel: NONE
                    """;
            String out = merge(server, server + "added: 1\n");
            assertNotNull(out);
            assertTrue(out.contains("Overrides:"), "an empty-to-YAML section is still the owner's");
        }

        @Test
        @DisplayName("a list item whose value contains a colon is never mistaken for a key")
        void listItemWithAColonIsNotAKey() {
            // From a live failure. Bukkit's own YAML dump writes list items at the SAME indent as their key, while
            // the bundled resource indents them further. Read as a key — which "winners: {gg_winners}" invites — the
            // two files disagree about where it sits, the item looks missing, and the merge splices a second copy in
            // at the resource's indent. One list, two indents: YAML then refuses the whole file, and every
            // announcement it configured silently stops.
            String server = """
                    events:
                      gg-end:
                        chat:
                          enabled: true
                          lines:
                          - '<#C21807>GG Wave ended — winners: <#00FC00>{gg_winners}'
                    """;
            String resource = """
                    events:
                      gg-end:
                        chat:
                          enabled: true
                          lines:
                            - '<#C21807>GG Wave ended — winners: <#00FC00>{gg_winners}'
                    """;

            assertNull(merge(server, resource), "the owner already has this list; nothing may be spliced in");
        }

        @Test
        @DisplayName("a key added beside a re-dumped list still arrives, and the list is left alone")
        void keysStillArriveAroundARedumpedList() {
            String server = """
                    events:
                      gg-end:
                        chat:
                          lines:
                          - 'ended — winners: {gg_winners}'
                    """;
            String out = merge(server, """
                    events:
                      gg-end:
                        chat:
                          lines:
                            - 'ended — winners: {gg_winners}'
                        sound:
                          enabled: true
                    """);
            assertNotNull(out, "the new sound block is genuinely missing and must be added");
            assertTrue(out.contains("    sound:"), "the new key arrives: " + out);
            assertEquals(1, out.lines().filter(line -> line.contains("gg_winners")).count(),
                    "and the list keeps exactly the one item the owner had: " + out);
        }

        @Test
        @DisplayName("a key the plugin no longer uses is left alone")
        void unusedKeysStay() {
            String out = merge("removed-long-ago: true\nkept: 1\n", "kept: 1\nadded: 2\n");
            assertNotNull(out);
            assertTrue(out.contains("removed-long-ago: true"));
        }

        @Test
        @DisplayName("the owner's value is never replaced by the shipped one")
        void ownerValuesWin() {
            String out = merge("size: 777\n", "size: 100\nadded: 1\n");
            assertNotNull(out);
            assertTrue(out.contains("size: 777"));
            assertFalse(out.contains("size: 100"));
        }

        @Test
        @DisplayName("indentation and quoting are untouched")
        void formattingSurvives() {
            String server = """
                    bar:
                        filled: "*"
                        ratio: 0.20
                    """;
            String out = merge(server, server + "added: 1\n");
            assertNotNull(out);
            assertTrue(out.contains("    filled: \"*\""), "four-space indent and quotes stay");
            assertTrue(out.contains("ratio: 0.20"), "0.20 must not become 0.2");
        }
    }

    @Nested
    @DisplayName("what a new version adds arrives")
    class Adding {

        @Test
        @DisplayName("a new key comes with the comment written above it")
        void newKeyBringsItsComment() {
            String out = merge("existing: 1\n", """
                    existing: 1
                    # How the GUI asks for a search term.
                    search: sign
                    """);
            assertNotNull(out);
            assertTrue(out.contains("# How the GUI asks for a search term."), "the comment travels with the key");
            assertTrue(out.contains("search: sign"));
        }

        @Test
        @DisplayName("a key added in the middle of a section lands in the middle, not at the end")
        void positionFollowsTheResource() {
            String out = merge("top:\n  a: 1\n  c: 3\n", "top:\n  a: 1\n  b: 2\n  c: 3\n");
            assertNotNull(out);
            List<String> keys = out.lines().map(String::strip)
                    .filter(l -> l.startsWith("a:") || l.startsWith("b:") || l.startsWith("c:")).toList();
            assertEquals(List.of("a: 1", "b: 2", "c: 3"), keys, "b belongs between a and c");
        }

        @Test
        @DisplayName("a whole new section arrives with its body")
        void wholeSection() {
            String out = merge("kept: 1\n", "kept: 1\nfresh:\n  inner: true\n  other: 2\n");
            assertNotNull(out);
            assertTrue(out.contains("fresh:"));
            assertTrue(out.contains("  inner: true"));
            assertTrue(out.contains("  other: 2"));
        }

        @Test
        @DisplayName("nothing is written when the file is already current")
        void noChangeMeansNoWrite() {
            assertNull(merge("a: 1\nb:\n  c: 2\n", "a: 1\nb:\n  c: 2\n"), "an unchanged file must not be rewritten");
        }
    }

    @Nested
    @DisplayName("curated sections")
    class Curated {

        private static final String SHIPPED = """
                tools:
                  pickaxe:
                    enabled: true
                    threshold: 10
                  shovel:
                    enabled: true
                """;

        @Test
        @DisplayName("a new key reaches an entry the owner kept")
        void aNewKeyReachesAnEntryTheOwnerKept() {
            String out = merge("tools:\n  pickaxe:\n    enabled: true\n  shovel:\n    enabled: true\n", SHIPPED,
                    "tools");
            assertNotNull(out);
            assertTrue(out.contains("    threshold: 10"), "a key added to a kept entry still arrives");
        }

        @Test
        @DisplayName("an entry the owner deleted is not put back")
        void anEntryTheOwnerDeletedIsNotPutBack() {
            String out = merge("tools:\n  pickaxe:\n    enabled: true\n    threshold: 10\n", SHIPPED, "tools");
            assertNull(out, "nothing to add: shovel was deleted on purpose and stays deleted");
        }

        @Test
        @DisplayName("a curated section absent entirely is a fresh install, so it is created in full")
        void anAbsentCuratedSectionIsStillCreatedInFull() {
            String out = merge("other: 1\n", "other: 1\n" + SHIPPED, "tools");
            assertNotNull(out);
            assertTrue(out.contains("  pickaxe:"), "absent means new, not deleted");
            assertTrue(out.contains("  shovel:"));
        }

        @Test
        @DisplayName("sections outside the curated list merge normally")
        void sectionsOutsideTheCuratedListMergeAsBefore() {
            String out = merge("plain:\n  a: 1\n", "plain:\n  a: 1\n  b: 2\n", "tools");
            assertNotNull(out);
            assertTrue(out.contains("  b: 2"));
        }
    }
}
