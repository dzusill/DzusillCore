package me.dzusill.core.command.argument;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.CommandException;
import me.dzusill.core.message.Messages;
import me.dzusill.core.message.Placeholder;
import me.dzusill.core.util.TextUtils;

/**
 * Binds an ordered list of named argument specifications to the raw tokens of a command, both for parsing (producing
 * {@link Arguments}) and for tab-completion (suggesting values for the token currently being typed). This is where a
 * command's declared shape drives autofill, so individual commands never implement {@code onTabComplete} by hand.
 */
public final class ArgumentParser {

    /**
     * A single named, typed, possibly-optional argument slot.
     */
    public record Spec(String name, ArgumentType<?> type, boolean required) {
    }

    private final List<Spec> specs;

    public ArgumentParser(List<Spec> specs) {
        this.specs = specs;
    }

    /**
     * Parses the tokens at and after {@code offset} into typed values.
     *
     * @param offset
     *            index of the first token belonging to these arguments (after any subcommand routing tokens)
     * @throws CommandException
     *             if a required argument is missing or a token fails to parse
     */
    public Arguments parse(CommandContext context, String[] args, int offset) throws CommandException {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < specs.size(); i++) {
            Spec spec = specs.get(i);
            int index = offset + i;
            boolean present = index < args.length && !args[index].isEmpty();
            if (!present) {
                if (spec.required()) {
                    // The command is part of the usage, not decoration around it: "Usage: <player>" leaves the reader
                    // to work out which command they were using, which is the one thing they demonstrably got wrong.
                    String command = commandOf(context, args, offset);
                    throw new CommandException(Messages.INVALID_USAGE,
                            Placeholder.of("usage", (command + " " + usage()).trim()).and("cmd", command));
                }
                continue;
            }
            values.put(spec.name(), spec.type().parse(context, args[index]));
        }
        return new Arguments(values);
    }

    /**
     * Suggests completions for the in-progress token, delegating to the {@link ArgumentType} that owns that position.
     */
    public List<String> suggest(CommandContext context, String[] args, int offset) {
        int relative = args.length - 1 - offset;
        if (relative < 0 || relative >= specs.size()) {
            return List.of();
        }
        Spec spec = specs.get(relative);
        String token = args[args.length - 1];
        return TextUtils.partialMatches(token, spec.type().suggest(context, token));
    }

    /**
     * The command as the sender typed it, routing tokens included.
     *
     * <p>
     * {@code offset} is how many tokens were consumed getting here, which is exactly the subcommand path — so a leaf
     * comes out {@code /tp} and a routed node {@code /oberonstaff status}, without either having to be told its own
     * name.
     * </p>
     */
    private static String commandOf(CommandContext context, String[] args, int offset) {
        StringBuilder line = new StringBuilder("/").append(context.label());
        for (int i = 0; i < offset && i < args.length; i++) {
            line.append(' ').append(args[i]);
        }
        return line.toString();
    }

    /**
     * @return a usage fragment such as {@code <name> [target]} built from the specs
     */
    public String usage() {
        StringBuilder builder = new StringBuilder();
        for (Spec spec : specs) {
            builder.append(spec.required() ? " <" + spec.name() + ">" : " [" + spec.name() + "]");
        }
        return builder.toString().trim();
    }
}
