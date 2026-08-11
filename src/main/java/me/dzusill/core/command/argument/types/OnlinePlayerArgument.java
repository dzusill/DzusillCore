package me.dzusill.core.command.argument.types;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.CommandException;
import me.dzusill.core.command.argument.ArgumentType;
import me.dzusill.core.message.Messages;
import me.dzusill.core.message.Placeholder;

/**
 * Resolves a token to an online {@link Player}, accepting the start of a name as well as the whole thing.
 *
 * <p>
 * See {@link PlayerLookup} for why an ambiguous fragment is refused rather than guessed.
 * </p>
 */
public final class OnlinePlayerArgument implements ArgumentType<Player> {

    @Override
    public Player parse(CommandContext context, String raw) throws CommandException {
        List<Player> ambiguous = PlayerLookup.ambiguous(raw);
        if (!ambiguous.isEmpty()) {
            throw new CommandException(Messages.PLAYER_AMBIGUOUS,
                    Placeholder.of("name", raw).and("players", PlayerLookup.names(ambiguous)));
        }
        return PlayerLookup.online(raw)
                .orElseThrow(() -> new CommandException(Messages.PLAYER_NOT_FOUND, Placeholder.of("name", raw)));
    }

    @Override
    public List<String> suggest(CommandContext context, String token) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
