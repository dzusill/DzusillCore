package me.dzusill.core.message;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites legacy {@code &}/{@code §} colour codes as MiniMessage tags, leaving everything else alone.
 *
 * <p>
 * Needed wherever a string can arrive from outside: a LuckPerms prefix through PlaceholderAPI comes back as
 * {@code &c[Admin] }, and MiniMessage does not know what that is - it would print the {@code &c} to the whole server.
 * </p>
 *
 * <p>
 * The obvious alternative, deserializing with Adventure's legacy serializer and re-serializing to MiniMessage, is wrong
 * here: it treats a MiniMessage tag already in the string as literal text and escapes it, so a hand-written
 * {@code <gradient:...>} rank would come out visible instead of applied. Replacing only the codes leaves both forms
 * working, which matters because one config line can hold a placeholder and a hand-written tag at once.
 * </p>
 */
public final class LegacyCodes {

    /** {@code &x&R&R&G&G&B&B} — Spigot's hex form. Matched first; its pieces would otherwise read as six colours. */
    private static final Pattern HEX = Pattern.compile("(?i)[&§]x((?:[&§][0-9a-f]){6})");

    private static final Pattern CODE = Pattern.compile("(?i)[&§]([0-9a-fk-or])");

    private static final Map<Character, String> TAGS = Map.ofEntries(Map.entry('0', "black"),
            Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"), Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"), Map.entry('6', "gold"), Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"), Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"), Map.entry('f', "white"),
            Map.entry('k', "obfuscated"), Map.entry('l', "bold"), Map.entry('m', "strikethrough"),
            Map.entry('n', "underlined"), Map.entry('o', "italic"), Map.entry('r', "reset"));

    private LegacyCodes() {
    }

    /**
     * @param text
     *            any string, with or without legacy codes
     * @return the same string with every legacy code rewritten as the equivalent MiniMessage tag
     */
    public static String toMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = replaceHex(text);
        Matcher matcher = CODE.matcher(result);
        StringBuilder out = new StringBuilder(result.length() + 16);
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            matcher.appendReplacement(out, Matcher.quoteReplacement("<" + TAGS.get(code) + ">"));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** @return {@code true} if {@code text} contains anything this class would rewrite */
    public static boolean hasLegacyCodes(String text) {
        return text != null && (HEX.matcher(text).find() || CODE.matcher(text).find());
    }

    private static String replaceHex(String text) {
        Matcher matcher = HEX.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (matcher.find()) {
            // "&1&2&3&4&5&6" -> "123456"
            String digits = matcher.group(1).replaceAll("[&§]", "");
            matcher.appendReplacement(out,
                    Matcher.quoteReplacement("<#" + digits.toLowerCase(java.util.Locale.ROOT) + ">"));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
