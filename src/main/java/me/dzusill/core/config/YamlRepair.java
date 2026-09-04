package me.dzusill.core.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Puts a config file back together when it has stopped parsing, but only where the answer is not a guess.
 *
 * <p>
 * A file that does not parse is worse than a file with a wrong value in it: every getter falls back to its default, so
 * the plugin runs on settings nobody chose and the only evidence is one line in the startup log. Two faults account for
 * nearly all of it, and both have exactly one correct repair.
 * </p>
 *
 * <ul>
 * <li><b>A tab on a blank line.</b> Pasting an indented block out of a chat client or an editor set to tabs leaves tabs
 * behind, and YAML refuses a file that holds one anywhere. A line that is blank apart from whitespace carries no
 * meaning, so emptying it cannot change what the file says.</li>
 * <li><b>A tab indenting a real line.</b> The same paste, landing on a line that carries content. The width a tab was
 * meant to stand for is not written down anywhere, but the file says it: a line cannot be a child of a key that already
 * holds a value, and a list item that follows another list item sits where that one sits. See
 * {@link #intendedIndent}.</li>
 * <li><b>A list item repeated at a different indent.</b> Written by this framework's own merge before it learned that a
 * list item whose value contains a colon is not a key. The duplicate is byte-identical to a sibling once trimmed, so
 * dropping the one that does not share the list's indentation restores exactly what the owner had.</li>
 * </ul>
 *
 * <p>
 * Every repair is verified before it is kept: the caller re-parses, and a repair that does not produce a loadable file
 * is discarded rather than written. Anything else — a genuine syntax error in a value, indentation that is content
 * rather than an accident — is left alone and reported, because repairing it would mean inventing what the owner meant.
 * </p>
 */
final class YamlRepair {

    /** One thing that was put right, phrased for the server log. */
    record Fix(String description) {
    }

    /** A repaired file and what was done to it. {@code text} is unchanged when nothing was. */
    record Result(String text, List<Fix> fixes) {

        boolean changed() {
            return !fixes.isEmpty();
        }
    }

    private YamlRepair() {
    }

    static Result repair(String text) {
        List<Fix> fixes = new ArrayList<>();
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));

        int tabbed = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() && line.indexOf('\t') >= 0) {
                lines.set(i, "");
                tabbed++;
            }
        }
        if (tabbed > 0) {
            fixes.add(new Fix("removed tabs from " + tabbed + " otherwise blank line(s) — YAML allows no tab "
                    + "anywhere, and one is enough to void the whole file"));
        }

        // Before the duplicate pass, so that pass measures indentation in spaces like everything else.
        List<Integer> retabbed = new ArrayList<>();
        int step = majorityIndentStep(lines);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int content = line.length() - line.stripLeading().length();
            if (content == 0 || line.lastIndexOf('\t', content - 1) < 0) {
                continue;
            }
            lines.set(i, " ".repeat(intendedIndent(lines, i, step)) + line.stripLeading());
            retabbed.add(i + 1);
        }
        if (!retabbed.isEmpty()) {
            fixes.add(new Fix("replaced tab indentation with spaces on line(s) " + join(retabbed)
                    + " — the width came from the line each one hangs off, so check those lines sit where you meant"));
        }

        int stray = 0;
        int houseStyle = majorityItemDelta(lines);
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (isStrayDuplicateItem(lines, i, houseStyle)) {
                lines.remove(i);
                stray++;
            }
        }
        if (stray > 0) {
            fixes.add(new Fix("dropped " + stray + " duplicated list item(s) that sat at the wrong indentation — a "
                    + "list cannot hold two, and an identical sibling is already there"));
        }

        return new Result(String.join("\n", lines), fixes);
    }

    /**
     * @return how far this file's list items normally sit in from the key that owns them. Bukkit's dumper writes them
     *         at the key's own indent, a hand-written or bundled file two further in; either is fine, but one file only
     *         ever does one of them, and that is what makes the odd item out identifiable at all
     */
    private static int majorityItemDelta(List<String> lines) {
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String body = lines.get(i).stripLeading();
            if (!body.startsWith("- ")) {
                continue;
            }
            int keyIndent = owningKeyIndent(lines, i);
            if (keyIndent >= 0) {
                counts.merge(lines.get(i).length() - body.length() - keyIndent, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey)
                .orElse(0);
    }

    /** @return the indent of the key line this item hangs off, or -1 when there is none above it */
    private static int owningKeyIndent(List<String> lines, int item) {
        for (int i = item - 1; i >= 0; i--) {
            String line = lines.get(i);
            String body = line.stripLeading();
            if (body.isEmpty() || body.startsWith("#") || body.startsWith("- ")) {
                continue;
            }
            return line.length() - body.length();
        }
        return -1;
    }

    /**
     * @return true when line {@code i} is a list item that repeats a sibling word for word AND sits at an indent this
     *         file does not otherwise use for items — which is exactly the residue the old merge left, and is not
     *         something anyone writes on purpose. Both conditions are required: a list may legitimately repeat a value,
     *         and an unusual indent on a unique item is content, not damage.
     */
    private static boolean isStrayDuplicateItem(List<String> lines, int i, int houseStyle) {
        String line = lines.get(i);
        String body = line.stripLeading();
        if (!body.startsWith("- ")) {
            return false;
        }
        int keyIndent = owningKeyIndent(lines, i);
        if (keyIndent < 0 || line.length() - body.length() - keyIndent == houseStyle) {
            // Written the way this file writes items: whatever else is wrong, this line is not the intruder.
            return false;
        }
        for (int step = -1; step <= 1; step += 2) {
            for (int j = i + step; j >= 0 && j < lines.size(); j += step) {
                String otherBody = lines.get(j).stripLeading();
                if (!otherBody.startsWith("- ")) {
                    break;
                }
                if (otherBody.equals(body)) {
                    return true;
                }
            }
        }
        return false;
    }
}
