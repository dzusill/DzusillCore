package me.dzusill.core.command.argument.types;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;

import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.CommandException;
import me.dzusill.core.command.argument.ArgumentType;
import me.dzusill.core.message.Messages;
import me.dzusill.core.message.Placeholder;

/**
 * Parses a token into a {@link Material}, accepting the standard {@code namespaced_key} form, and suggests matching
 * material names for autocomplete.
 */
public final class MaterialArgument implements ArgumentType<Material> {

    @Override
    public Material parse(CommandContext context, String raw) throws CommandException {
        Material material = Material.matchMaterial(raw);
        if (material == null) {
            throw new CommandException(Messages.INVALID_USAGE, Placeholder.of("usage", "<material>"));
        }
        return material;
    }

    @Override
    public List<String> suggest(CommandContext context, String token) {
        return Arrays.stream(Material.values()).filter(material -> !material.isLegacy())
                .map(material -> material.name().toLowerCase(Locale.ROOT)).toList();
    }
}
