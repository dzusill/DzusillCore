package me.dzusill.core.command.argument.types;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Resolves a typed fragment to an online player, the way staff actually type one.
 *
 * <p>
 * Nobody types {@code elz1one} in a hurry; they type {@code elz} and expect it to work, because that is what every
 * other teleport plugin does. An exact name always wins - a player called {@code el} is reachable even while
 * {@code elz1one} is online - and only then is the fragment treated as a prefix.
 * </p>
 *
 * <p>
 * An ambiguous fragment resolves to nothing rather than to the first match. These lookups sit behind {@code /tp} and
 * {@code /tphere}: silently picking one of two candidates means moving the wrong player, or yanking them across the
 * world, and the staff member has no reason to suspect it happened. Better to be asked for one more letter.
 * </p>
 */
public final class PlayerLookup {

    private PlayerLookup() {
    }

    /**
     * @return the player named exactly {@code token}, or the only one whose name starts with it; empty when nothing
     *         matches or more than one does
     */
    public static Optional<Player> online(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Player exact = Bukkit.getPlayerExact(token);
        if (exact != null) {
            return Optional.of(exact);
        }
        List<Player> matches = prefixMatches(token);
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    /**
     * @return every online player whose name starts with {@code token}, case-insensitively; empty when {@code token}
     *         names somebody exactly, since that is not an ambiguity to report
     */
    public static List<Player> ambiguous(String token) {
        if (token == null || token.isBlank() || Bukkit.getPlayerExact(token) != null) {
            return List.of();
        }
        List<Player> matches = prefixMatches(token);
        return matches.size() > 1 ? matches : List.of();
    }

    /** The names of every ambiguous match, for telling the player which letter to add. */
    public static String names(List<Player> players) {
        return players.stream().map(Player::getName).sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static List<Player> prefixMatches(String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(player -> (Player) player)
                .filter(player -> player.getName().toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
