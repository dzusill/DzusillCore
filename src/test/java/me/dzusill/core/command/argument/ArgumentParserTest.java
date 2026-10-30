package me.dzusill.core.command.argument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.CommandException;
import me.dzusill.core.command.argument.types.IntArgument;
import me.dzusill.core.command.argument.types.StringArgument;

class ArgumentParserTest {

    private static final List<ArgumentParser.Spec> SPECS = List.of(
            new ArgumentParser.Spec("name", new StringArgument(), true),
            new ArgumentParser.Spec("amount", new IntArgument(1, 64), false));

    private CommandContext context(String... args) {
        return new CommandContext(null, null, null, "test", args);
    }

    @Test
    void parsesRequiredAndOptionalArguments() throws CommandException {
        ArgumentParser parser = new ArgumentParser(SPECS);
        Arguments args = parser.parse(context("sword", "5"), new String[]{"sword", "5"}, 0);

        assertEquals("sword", args.getString("name"));
        assertEquals(5, args.getInt("amount"));
    }

    @Test
    void optionalArgumentMayBeAbsent() throws CommandException {
        ArgumentParser parser = new ArgumentParser(SPECS);
        Arguments args = parser.parse(context("sword"), new String[]{"sword"}, 0);

        assertTrue(args.has("name"));
        assertFalse(args.has("amount"));
        assertEquals(7, args.getOr("amount", 7));
    }

    @Test
    void missingRequiredArgumentThrows() {
        ArgumentParser parser = new ArgumentParser(SPECS);
        assertThrows(CommandException.class, () -> parser.parse(context(), new String[]{}, 0));
    }

    @Test
    void invalidIntegerThrows() {
        ArgumentParser parser = new ArgumentParser(SPECS);
        assertThrows(CommandException.class,
                () -> parser.parse(context("sword", "notanumber"), new String[]{"sword", "notanumber"}, 0));
    }

    @Test
    void outOfRangeIntegerThrows() {
        ArgumentParser parser = new ArgumentParser(SPECS);
        assertThrows(CommandException.class,
                () -> parser.parse(context("sword", "999"), new String[]{"sword", "999"}, 0));
    }

    @Test
    void usageReflectsRequiredAndOptional() {
        ArgumentParser parser = new ArgumentParser(SPECS);
        assertEquals("<name> [amount]", parser.usage());
    }

    // -------------------------------------------------------------------------
    // What the player is actually shown
    //
    // An ArgumentType sees one token and nothing else, so the most it can say on its own is what it
    // wanted -- "<material>". That reached the player as the whole message: "Usage: <material>",
    // naming neither the command they got wrong nor the arguments around it.
    // -------------------------------------------------------------------------

    /** @return the {@code usage} placeholder of the failure {@code call} throws */
    private static String usageShownBy(org.junit.jupiter.api.function.Executable call) {
        CommandException failure = assertThrows(CommandException.class, call);
        assertEquals("invalid-usage", failure.messageKey());
        return failure.placeholder().get("usage");
    }

    @Test
    void aMissingArgumentIsReportedWithTheWholeCommand() {
        ArgumentParser parser = new ArgumentParser(SPECS);

        assertEquals("/test <name> [amount]", usageShownBy(() -> parser.parse(context(), new String[]{}, 0)));
    }

    /**
     * The reported bug, against the real {@link me.dzusill.core.command.argument.types.EnumArgument} that caused it: a
     * token that failed to parse was reported as {@code Usage: <prices|playerdata>}, with the command it belonged to
     * nowhere in the message.
     */
    @Test
    void anUnparseableArgumentIsAlsoReportedWithTheWholeCommand() {
        ArgumentParser parser = new ArgumentParser(List.of(new ArgumentParser.Spec("target",
                new me.dzusill.core.command.argument.types.EnumArgument<>(Target.class), true)));

        assertEquals("/test <prices|playerdata>",
                usageShownBy(() -> parser.parse(context("nope"), new String[]{"nope"}, 0)));
    }

    /** A type that fails on its own terms, rather than on shape, keeps its own message. */
    @Test
    void anIntegerFailureKeepsItsOwnMessage() {
        ArgumentParser parser = new ArgumentParser(SPECS);

        CommandException failure = assertThrows(CommandException.class,
                () -> parser.parse(context("sword", "nope"), new String[]{"sword", "nope"}, 0));
        assertEquals("invalid-number", failure.messageKey());
    }

    private enum Target {
        PRICES, PLAYERDATA
    }

    /**
     * A type that names what it accepts keeps saying so — the fragment replaces that one argument rather than the whole
     * line, so the reader gets both the command and the valid values.
     */
    @Test
    void aTypesOwnHintReplacesOnlyItsOwnArgument() {
        ArgumentParser parser = new ArgumentParser(List.of(new ArgumentParser.Spec("name", new StringArgument(), true),
                new ArgumentParser.Spec("target", new PickyArgument(), true),
                new ArgumentParser.Spec("page", new IntArgument(1, 9), false)));

        assertEquals("/test <name> <prices|playerdata> [page]",
                usageShownBy(() -> parser.parse(context("sword", "nope"), new String[]{"sword", "nope"}, 0)));
    }

    /** A routed subcommand names its own path, not just the root label. */
    @Test
    void aSubcommandPathIsPartOfTheCommand() {
        ArgumentParser parser = new ArgumentParser(
                List.of(new ArgumentParser.Spec("target", new PickyArgument(), true)));
        String[] args = {"migrate", "nope"};

        assertEquals("/test migrate <prices|playerdata>", usageShownBy(() -> parser.parse(context(args), args, 1)));
    }

    /** A type with something more specific to say than "wrong shape" is not overwritten. */
    @Test
    void aFailureWithItsOwnMessageKeyIsPassedThrough() {
        ArgumentParser parser = new ArgumentParser(
                List.of(new ArgumentParser.Spec("target", new OpinionatedArgument(), true)));

        CommandException failure = assertThrows(CommandException.class,
                () -> parser.parse(context("nope"), new String[]{"nope"}, 0));
        assertEquals("player-not-found", failure.messageKey());
    }

    /** Stands in for EnumArgument: refuses everything, naming the values it would have taken. */
    private static final class PickyArgument implements ArgumentType<String> {
        @Override
        public String parse(CommandContext context, String raw) throws CommandException {
            throw new CommandException("invalid-usage",
                    me.dzusill.core.message.Placeholder.of("usage", "<prices|playerdata>"));
        }
    }

    /** Stands in for a type that fails for a reason of its own rather than on shape. */
    private static final class OpinionatedArgument implements ArgumentType<String> {
        @Override
        public String parse(CommandContext context, String raw) throws CommandException {
            throw new CommandException("player-not-found");
        }
    }
}
