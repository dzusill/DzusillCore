package me.dzusill.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.message.MessageService;

/**
 * What a usage message calls the command.
 *
 * <p>
 * A name the server owns is reached by rewriting {@code /msg hi} into {@code /oberonmsg:msg hi} before dispatch, so the
 * label Bukkit hands back carries the framework's own namespace. Echoed into a message, that told a player their
 * mistake was {@code Usage: /oberonmsg:msg <player>} — a command they never typed and could not have discovered.
 * </p>
 */
class UsageNamesTheCommandTest {

    private static CommandContext contextFor(String label) {
        return new CommandContext(Mockito.mock(CorePlugin.class), Mockito.mock(org.bukkit.command.CommandSender.class),
                Mockito.mock(MessageService.class), label, new String[0]);
    }

    @Test
    void theNamespaceTheFrameworkAddedIsNotShownBack() {
        assertEquals("msg", contextFor("oberonmsg:msg").label());
        assertEquals("tp", contextFor("oberonstaff:tp").label());
    }

    @Test
    void aPlainLabelIsUntouched() {
        assertEquals("msg", contextFor("msg").label());
        assertEquals("oberonstaff", contextFor("oberonstaff").label());
    }

    /** Whatever a caller hands over, the label is a string — a command with none is not a crash. */
    @Test
    void aMissingLabelIsEmptyRatherThanNull() {
        assertEquals("", contextFor(null).label());
    }
}
