package me.dzusill.core.message;

/**
 * Rewrites legacy {@code &}/{@code §} colour codes and bare hex prefixes as MiniMessage tags, leaving everything else
 * alone.
 *
 * <p>
 * Needed wherever a string can arrive from outside: a LuckPerms prefix through PlaceholderAPI comes back as
 * {@code &c[Admin] }, and MiniMessage does not know what that is - it would print the {@code &c} to the whole server.
 * The same is true of a config file written in any of the dialects server owners actually use: {@code &c},
 * {@code &#00F986}, Spigot's {@code &x&0&0&F&9&8&6} and a bare {@code #00F986} are all colours to the person who typed
 * them, and all literal text to MiniMessage.
 * </p>
 *
 * <p>
 * The obvious alternative, deserializing with Adventure's legacy serializer and re-serializing to MiniMessage, is wrong
 * here: it treats a MiniMessage tag already in the string as literal text and escapes it, so a hand-written
 * {@code <gradient:...>} rank would come out visible instead of applied. Replacing only the codes leaves both forms
 * working, which matters because one config line can hold a placeholder and a hand-written tag at once.
 * </p>
 *
 * <p>
 * Scanned character by character rather than by pattern, because supporting a bare {@code #RRGGBB} makes the two
 * inseparable: a gradient is written {@code <gradient:#ff0000:#00ff00>}, and a pattern that rewrote the hex inside it
 * would turn one working tag into three broken ones. Anything between a {@code <} and its {@code >} is therefore copied
 * through untouched, and only what sits outside a tag can be rewritten.
 * </p>
 */
public final class LegacyCodes {

    private static final int HEX_DIGITS = 6;

    private LegacyCodes() {
    }

    /**
     * @param text
     *            any string, with or without legacy codes
     * @return the same string with every legacy code and bare hex prefix rewritten as the equivalent MiniMessage tag
     */
    public static String toMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            if (character == '<') {
                int close = closingBracket(text, index);
                if (close > index) {
                    // An existing MiniMessage tag: copy it verbatim, contents and all.
                    out.append(text, index, close + 1);
                    index = close + 1;
                    continue;
                }
            }
            int consumed = appendCode(text, index, out);
            if (consumed > 0) {
                index += consumed;
                continue;
            }
            out.append(character);
            index++;
        }
        return out.toString();
    }

    /**
     * @return {@code true} if {@code text} contains anything this class would rewrite
     *
     *         <p>
     *         Answered by doing the rewrite and comparing, so detection and rewriting can never disagree about what
     *         counts as a code.
     *         </p>
     */
    public static boolean hasLegacyCodes(String text) {
        return text != null && !text.equals(toMiniMessage(text));
    }

    /**
     * Converts whatever colour code starts at {@code index}, if any.
     *
     * @return how many characters were consumed, or {@code 0} when nothing there was a colour code
     */
    private static int appendCode(String text, int index, StringBuilder out) {
        char character = text.charAt(index);
        if (character == '#') {
            String hex = hexAt(text, index + 1);
            if (hex != null) {
                out.append('<').append('#').append(hex).append('>');
                return 1 + HEX_DIGITS;
            }
            return 0;
        }
        // Both prefixes: '&' is what owners type, '§' is what Bukkit's own getters hand back.
        if ((character != '&' && character != '§') || index + 1 >= text.length()) {
            return 0;
        }
        char next = text.charAt(index + 1);
        if (next == '#') {
            String hex = hexAt(text, index + 2);
            if (hex != null) {
                out.append('<').append('#').append(hex).append('>');
                return 2 + HEX_DIGITS;
            }
            return 0;
        }
        // Spigot's expanded form, tried before the plain codes: its pieces would otherwise read as six colours.
        if (next == 'x' || next == 'X') {
            String hex = spigotHexAt(text, index + 2);
            if (hex != null) {
                out.append('<').append('#').append(hex).append('>');
                return 2 + HEX_DIGITS * 2;
            }
            return 0;
        }
        String tag = tagFor(Character.toLowerCase(next));
        if (tag == null) {
            return 0;
        }
        out.append(tag);
        return 2;
    }

    /** @return the six lower-cased hex digits starting at {@code from}, or {@code null} when they are not all hex */
    private static String hexAt(String text, int from) {
        if (from + HEX_DIGITS > text.length()) {
            return null;
        }
        StringBuilder hex = new StringBuilder(HEX_DIGITS);
        for (int i = from; i < from + HEX_DIGITS; i++) {
            if (!isHexDigit(text.charAt(i))) {
                return null;
            }
            hex.append(Character.toLowerCase(text.charAt(i)));
        }
        return hex.toString();
    }

    /** @return the hex from Spigot's expanded {@code &x&0&0&F&9&8&6} form starting at {@code from} */
    private static String spigotHexAt(String text, int from) {
        if (from + HEX_DIGITS * 2 > text.length()) {
            return null;
        }
        StringBuilder hex = new StringBuilder(HEX_DIGITS);
        for (int i = from; i < from + HEX_DIGITS * 2; i += 2) {
            char prefix = text.charAt(i);
            if ((prefix != '&' && prefix != '§') || !isHexDigit(text.charAt(i + 1))) {
                return null;
            }
            hex.append(Character.toLowerCase(text.charAt(i + 1)));
        }
        return hex.toString();
    }

    /**
     * @return the index of the {@code >} closing a tag opened at {@code open}, or {@code -1} when this is a stray
     *         {@code <} rather than a tag
     */
    private static int closingBracket(String text, int open) {
        for (int i = open + 1; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '>') {
                return i;
            }
            if (character == '<') {
                return -1; // A nested '<' means the first one was never a tag.
            }
        }
        return -1;
    }

    private static boolean isHexDigit(char character) {
        return (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f')
                || (character >= 'A' && character <= 'F');
    }

    private static String tagFor(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }
}
