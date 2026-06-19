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
 * Resolves a token to an {@link OfflinePlayer} that has played on the server before, so commands can target offline
 * players (e.g. data edits). Suggests online player names for convenience.
 */
public final class PlayerArgument implements ArgumentType<OfflinePlayer> {

    @SuppressWarnings("deprecation") // name-based lookup is intentional for offline targeting
    @Override
    public OfflinePlayer parse(CommandContext context, String raw) throws CommandException {
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
