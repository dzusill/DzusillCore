package me.dzusill.core.command.argument.types;

import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.CommandException;
import me.dzusill.core.command.argument.ArgumentType;
import me.dzusill.core.message.Messages;
import me.dzusill.core.message.Placeholder;
import me.dzusill.core.util.NumberUtils;

/**
 * Parses an integer, optionally clamped to an inclusive {@code [min, max]} range. Invalid input or out-of-range values
 * raise a {@link CommandException} with a user-facing message.
 */
public final class IntArgument implements ArgumentType<Integer> {

    private final int min;
    private final int max;

    public IntArgument() {
        this(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public IntArgument(int min, int max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public Integer parse(CommandContext context, String raw) throws CommandException {
        int value = NumberUtils.parseInt(raw)
                .orElseThrow(() -> new CommandException(Messages.INVALID_NUMBER, Placeholder.of("input", raw)));
        if (value < min || value > max) {
            throw new CommandException(Messages.INVALID_NUMBER, Placeholder.of("input", raw));
        }
        return value;
    }
}
