package me.dzusill.core.command.argument.types;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.CommandException;
import me.dzusill.core.command.argument.ArgumentType;
import me.dzusill.core.message.Messages;
import me.dzusill.core.message.Placeholder;

/**
 * Parses a token into a constant of the given enum, case-insensitively, and suggests all of the enum's constants
 * (lower-cased) for autocomplete.
 *
 * @param <E>
 *            the enum type
 */
public final class EnumArgument<E extends Enum<E>> implements ArgumentType<E> {

    private final Class<E> enumType;

    public EnumArgument(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public E parse(CommandContext context, String raw) throws CommandException {
        try {
            return Enum.valueOf(enumType, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new CommandException(Messages.INVALID_USAGE, Placeholder.of("usage", "<" + names() + ">"));
        }
    }

    @Override
    public List<String> suggest(CommandContext context, String token) {
        return Arrays.stream(enumType.getEnumConstants()).map(constant -> constant.name().toLowerCase(Locale.ROOT))
                .toList();
    }

    private String names() {
        return String.join("|", suggest(null, ""));
    }
}
