package me.dzusill.core.command.argument.types;

import java.util.Arrays;
import java.util.List;

import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.argument.ArgumentType;

/**
 * Passes a token through unchanged. Optionally constrained to a fixed set of choices, which then double as
 * tab-completion suggestions.
 */
public final class StringArgument implements ArgumentType<String> {

    private final List<String> choices;

    public StringArgument() {
        this.choices = List.of();
    }

    public StringArgument(String... choices) {
        this.choices = Arrays.asList(choices);
    }

    @Override
    public String parse(CommandContext context, String raw) {
        return raw;
    }

    @Override
    public List<String> suggest(CommandContext context, String token) {
        return choices;
    }
}
