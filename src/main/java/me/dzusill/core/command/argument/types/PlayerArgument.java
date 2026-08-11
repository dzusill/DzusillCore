package me.dzusill.core.command.argument.types;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.CommandException;
import me.dzusill.core.command.argument.ArgumentType;
import me.dzusill.core.message.Messages;
import me.dzusill.core.message.Placeholder;

/**
 * Resolves a token to an {@link OfflinePlayer} that has played on the server before, so commands can target somebody
 * who is not online - a history lookup, a data edit.
 *
 * <p>
 * An online player is matched first, and by the start of their name as well as the whole of it, because that is how
 * staff type one. Only if nothing online matches is the token treated as a full name to look up offline: a fragment
 * cannot be resolved against players who are not here, since there is no list to compare it against.
 * </p>
 */
public final class PlayerArgument implements ArgumentType<OfflinePlayer> {

    @SuppressWarnings("deprecation") // name-based lookup is intentional for offline targeting
    @Override
    public OfflinePlayer parse(CommandContext context, String raw) throws CommandException {
        List<Player> ambiguous = PlayerLookup.ambiguous(raw);
        if (!ambiguous.isEmpty()) {
            throw new CommandException(Messages.PLAYER_AMBIGUOUS,
                    Placeholder.of("name", raw).and("players", PlayerLookup.names(ambiguous)));
        }
        java.util.Optional<Player> online = PlayerLookup.online(raw);
        if (online.isPresent()) {
            return online.get();
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(raw);
        if (!player.hasPlayedBefore() && !player.isOnline()) {
            throw new CommandException(Messages.PLAYER_NOT_FOUND, Placeholder.of("name", raw));
        }
        return player;
    }

    @Override
    public List<String> suggest(CommandContext context, String token) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
