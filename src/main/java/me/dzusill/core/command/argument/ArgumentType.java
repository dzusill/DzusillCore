package me.dzusill.core.command.argument;

import java.util.Collections;
import java.util.List;

import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.CommandException;

/**
 * Converts a single raw command token into a typed value and, optionally, contributes tab-completion suggestions for
 * that position. This is the polymorphic core of the command framework: a command declares <em>what</em> each argument
 * is, and the type knows <em>how</em> to both parse and autocomplete it.
 *
 * @param <T>
 *            the parsed value type
 */
public interface ArgumentType<T> {

    /**
     * Parses {@code raw} into a value of type {@code T}.
     *
     * @throws CommandException
     *             if {@code raw} is not valid for this type
     */
    T parse(CommandContext context, String raw) throws CommandException;

    /**
     * Suggests completions for the in-progress token. Defaults to no suggestions.
     *
     * @param token
     *            the partial text typed so far
     */
    default List<String> suggest(CommandContext context, String token) {
        return Collections.emptyList();
    }
}
