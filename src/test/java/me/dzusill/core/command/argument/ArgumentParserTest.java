package me.dzusill.core.command.argument;

import me.dzusill.core.command.CommandContext;
import me.dzusill.core.command.CommandException;
import me.dzusill.core.command.argument.types.IntArgument;
import me.dzusill.core.command.argument.types.StringArgument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertThrows(CommandException.class,
                () -> parser.parse(context(), new String[]{}, 0));
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
}
