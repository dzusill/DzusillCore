package me.dzusill.core.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds the keys a newer version introduced to a server's own config file, by editing its text rather than rewriting it.
 *
 * <h2>Why not do this through the YAML model</h2>
 *
 * <p>
 * Parsing a config into a {@code YamlConfiguration}, adding what is missing and saving it back does merge correctly,
 * but it re-serialises the whole file from an object graph that never held most of what was in it. Everything the model
 * has no place for is silently lost: comments that sit between list items, sections whose only content is a commented
 * out example, the owner's quoting, their indentation, and the position of every key, since anything added lands at the
 * end. An owner who opens their config after an update finds it reformatted and their notes gone.
 * </p>
 *
 * <p>
 * This class never re-serialises. It reads both files as lines, works out which paths the server file does not have,
 * and splices in exactly those blocks from the bundled resource, comments and all. Every other byte of the owner's file
 * is left where it was, including keys the plugin no longer uses.
 * </p>
 *
 * <h2>What counts as missing</h2>
 *
 * <p>
 * A path the resource has and the server file does not. Values are never compared, so an owner's setting cannot be
 * reverted by an update. Sections named as curated keep their existing behaviour: an entry the owner deleted from one
 * is not put back, while keys added inside an entry they kept still arrive.
 * </p>
 */
final class TextMergeLegacy {

    /**
     * A mapping key at the start of a line: optional indent, a key, a colon, then end of line or a space.
     *
     * <p>
     * The key part deliberately excludes {@code :} so a value containing one ({@code line-format: "a: b"}) cannot be
     * mistaken for a nested key. List items are matched separately because a {@code -} prefix changes what the line is.
     * </p>
     */
    private static final Pattern KEY = Pattern.compile("^(\\s*)([^\\s#:][^:]*?)\\s*:(\\s.*|)$");
    /** A block-sequence entry: "- value", or a bare "-" opening a nested item. */
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*-(\\s|$)");

    private TextMergeLegacy() {
    }

    /**
     * @return the merged text, or {@code null} when the server file already has everything the resource does
     */
    static String merge(String serverText, String resourceText, List<String> ignored) {
        List<String> serverLines = lines(serverText);
        Node serverRoot = parse(serverLines);
        Node resourceRoot = parse(lines(resourceText));

        List<Insert> inserts = new ArrayList<>();
        collect(resourceRoot, serverRoot, ignored, inserts);
        if (inserts.isEmpty()) {
            return null;
        }

        // Applied bottom-up so an earlier insert cannot shift the line numbers a later one was computed against.
        inserts.sort((a, b) -> Integer.compare(b.at, a.at));
        List<String> out = new ArrayList<>(serverLines);
        for (Insert insert : inserts) {
            out.addAll(insert.at, insert.block);
        }
        return String.join("\n", out) + "\n";
    }

    private static List<String> lines(String text) {
        List<String> out = new ArrayList<>(List.of(text.split("\n", -1)));
        // split leaves a trailing empty element for a file ending in a newline; it is not a line of its own.
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    /** Walks the resource against the server file and records the blocks that have to be spliced in. */
    private static void collect(Node resource, Node server, List<String> ignored, List<Insert> inserts) {
        boolean curated = server.path != null && ignored.contains(server.path);
        Node previousPresent = null;

        for (Node child : resource.children) {
            Node match = server.child(child.key);
            if (match != null) {
                collect(child, match, ignored, inserts);
                previousPresent = match;
                continue;
            }
            if (curated) {
                // An entry the owner removed from a list they maintain. Putting it back is the failure mode that
                // makes a merging plugin impossible to configure, so it stays gone.
                continue;
            }
            inserts.add(new Insert(insertionPoint(server, previousPresent), block(child, server.bodyIndent())));
        }
    }

    /**
     * @return the line the new block goes in front of
     *
     *         <p>
     *         Directly after the sibling that precedes it in the resource, so a key added in the middle of a section
     *         arrives in the middle of that section rather than at the bottom of the file. With no such sibling it goes
     *         at the top of the parent, right under its own key line.
     *         </p>
     */
    private static int insertionPoint(Node parent, Node previousPresent) {
        if (previousPresent != null) {
            return previousPresent.end + 1;
        }
        if (!parent.children.isEmpty()) {
            return parent.children.get(0).start;
        }
        return parent.end + 1;
    }

    /** The resource block, re-indented if the server file nests this section differently. */
    private static List<String> block(Node node, int targetIndent) {
        int shift = targetIndent - node.indent;
        List<String> out = new ArrayList<>(node.end - node.start + 1);
        for (int i = node.start; i <= node.end; i++) {
            String line = node.lines.get(i);
            if (line.isBlank()) {
                out.add("");
            } else if (shift > 0) {
                out.add(" ".repeat(shift) + line);
            } else if (shift < 0) {
                out.add(line.substring(Math.min(-shift, line.length() - line.stripLeading().length())));
            } else {
                out.add(line);
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Outline parsing
    // -------------------------------------------------------------------------

    /**
     * Builds the key tree, recording for every key the line range that belongs to it.
     *
     * <p>
     * A key owns the comment and blank lines directly above it, so a block moved into another file carries its own
     * documentation. It owns everything below it that is more indented, which is how a whole section travels as one
     * piece.
     * </p>
     */
    private static Node parse(List<String> lines) {
        Node root = new Node(null, "", -1, lines);
        root.start = 0;
        root.end = lines.size() - 1;
        List<Node> stack = new ArrayList<>();
        stack.add(root);

        int pendingFrom = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                if (pendingFrom < 0) {
                    pendingFrom = i;
                }
                continue;
            }
            Matcher m = KEY.matcher(line);
            if (!m.matches()) {
                // A wrapped value: part of whatever key is open.
                pendingFrom = -1;
                continue;
            }
            int indent = m.group(1).length();
            String key = strip(m.group(2));

            while (stack.size() > 1 && stack.get(stack.size() - 1).indent >= indent) {
                close(stack.remove(stack.size() - 1), pendingFrom >= 0 ? pendingFrom - 1 : i - 1);
            }
            Node parent = stack.get(stack.size() - 1);
            Node node = new Node(parent, key, indent, lines);
            node.start = pendingFrom >= 0 ? pendingFrom : i;
            parent.children.add(node);
            stack.add(node);
            pendingFrom = -1;
        }
        int last = pendingFrom >= 0 ? pendingFrom - 1 : lines.size() - 1;
        while (stack.size() > 1) {
            close(stack.remove(stack.size() - 1), last);
        }
        return root;
    }

    private static void close(Node node, int end) {
        node.end = Math.max(end, node.start);
    }

    /** Keys may be quoted in one file and bare in the other; they are the same key either way. */
    private static String strip(String key) {
        String k = key.trim();
        if (k.length() >= 2 && (k.charAt(0) == '"' || k.charAt(0) == '\'') && k.charAt(k.length() - 1) == k.charAt(0)) {
            k = k.substring(1, k.length() - 1);
        }
        return k;
    }

    private static final class Node {

        private final Node parent;
        private final String key;
        private final int indent;
        private final List<String> lines;
        private final List<Node> children = new ArrayList<>();
        private final String path;

        private int start;
        private int end;

        private Node(Node parent, String key, int indent, List<String> lines) {
            this.parent = parent;
            this.key = key;
            this.indent = indent;
            this.lines = lines;
            this.path = parent == null || parent.path == null || parent.path.isEmpty()
                    ? (parent == null ? null : key)
                    : parent.path + "." + key;
        }

        private Node child(String name) {
            for (Node c : children) {
                if (c.key.equals(name)) {
                    return c;
                }
            }
            return null;
        }

        /** What a child of this node should be indented by, taken from the children it already has. */
        private int bodyIndent() {
            if (!children.isEmpty()) {
                return children.get(0).indent;
            }
            return indent < 0 ? 0 : indent + 2;
        }
    }

    private record Insert(int at, List<String> block) {
    }

    /** Every path in the tree, for callers that want to know what a file declares without loading it. */
    static Set<String> paths(String text) {
        Set<String> out = new LinkedHashSet<>();
        collectPaths(parse(lines(text)), out);
        return out;
    }

    private static void collectPaths(Node node, Set<String> out) {
        for (Node child : node.children) {
            out.add(child.path.toLowerCase(Locale.ROOT));
            collectPaths(child, out);
        }
    }
}
