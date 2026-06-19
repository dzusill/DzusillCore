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
 * Resolves a token to a currently-online {@link Player}, suggesting online player names for autocomplete. Fails with
 * {@link Messages#PLAYER_NOT_FOUND} when the player is offline/unknown.
 */
public final class OnlinePlayerArgument implements ArgumentType<Player> {

    @Override
    public Player parse(CommandContext context, String raw) throws CommandException {
        Player player = Bukkit.getPlayerExact(raw);
        if (player == null) {
            throw new CommandException(Messages.PLAYER_NOT_FOUND, Placeholder.of("name", raw));
        }
        return player;
    }

    @Override
    public List<String> suggest(CommandContext context, String token) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
