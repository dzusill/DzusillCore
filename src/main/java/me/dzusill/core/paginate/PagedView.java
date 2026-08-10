package me.dzusill.core.paginate;

import java.util.function.Function;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import me.dzusill.core.message.MessageService;
import me.dzusill.core.message.Placeholder;

/**
 * Sends a {@link Page} to somebody: a header, the rows, and a footer they can click to turn.
 *
 * <p>
 * Everything a player reads comes from the calling plugin's own {@code messages.yml} - the header, the row, the
 * Prev/Next wording - so each plugin words its log its own way and every string stays translatable. This class owns
 * only the arithmetic and the click wiring.
 * </p>
 *
 * <p>
 * Turning a page re-runs the command with a different page number rather than holding a session in memory. That means a
 * footer still works after a relog, after a reload, and in a log a moderator scrolled back to twenty minutes later -
 * and it costs nothing to keep.
 * </p>
 */
public final class PagedView {

    /** The token replaced with the target page number in the command a footer button runs. */
    public static final String PAGE_TOKEN = "%page%";

    private final MessageService messages;
    private final Keys keys;

    public PagedView(MessageService messages, Keys keys) {
        this.messages = messages;
        this.keys = keys;
    }

    /**
     * The message keys a paged list needs. All are looked up in the calling plugin's {@code messages.yml}.
     *
     * @param header
     *            shown above the rows; gets {@code %page% %pages% %total% %first% %last%} plus the caller's own
     * @param row
     *            one per entry; gets whatever the caller's row mapper provides
     * @param empty
     *            shown instead of everything when nothing matched
     * @param footer
     *            shown below the rows; gets {@code %prev% %next% %page% %pages% %total%}
     * @param prevLabel
     *            the clickable "previous" wording, e.g. {@code « Prev}
     * @param nextLabel
     *            the clickable "next" wording
     * @param disabledPrevLabel
     *            the same wording greyed out, shown on the first page so the footer does not change width
     * @param disabledNextLabel
     *            the same, on the last page
     * @param pageHint
     *            hover text on a live button; gets {@code %page%}
     */
    public record Keys(String header, String row, String empty, String footer, String prevLabel, String nextLabel,
            String disabledPrevLabel, String disabledNextLabel, String pageHint) {
    }

    /**
     * Renders a page.
     *
     * @param commandTemplate
     *            the command a footer button runs, containing {@link #PAGE_TOKEN} where the page number goes
     * @param rowPlaceholders
     *            builds the placeholders for one row
     * @param headerExtras
     *            placeholders merged into the header, for things only the caller knows (a player name, a total)
     */
    public <T> void send(CommandSender recipient, Page<T> page, String commandTemplate,
            Function<T, Placeholder> rowPlaceholders, Placeholder headerExtras) {
        if (page.isEmpty()) {
            messages.send(recipient, keys.empty(), headerExtras);
            return;
        }
        messages.send(recipient, keys.header(), counts(page).and(headerExtras));
        for (T entry : page.rows()) {
            messages.send(recipient, keys.row(), rowPlaceholders.apply(entry));
        }
        if (page.pageCount() > 1) {
            messages.send(recipient, keys.footer(),
                    counts(page).and("prev", button(page, recipient, commandTemplate, true)).and("next",
                            button(page, recipient, commandTemplate, false)));
        }
    }

    private Placeholder counts(Page<?> page) {
        return Placeholder.of("page", page.page()).and("pages", page.pageCount()).and("total", page.total())
                .and("first", page.firstRowNumber()).and("last", page.lastRowNumber());
    }

    /**
     * One footer button, as a MiniMessage fragment.
     *
     * <p>
     * A console gets the bare label: it cannot click, and wrapping it in tags only makes the log noisier. An edge page
     * gets the disabled wording, which keeps the footer the same shape whichever page you are on instead of making the
     * buttons jump sideways as you page.
     * </p>
     */
    private String button(Page<?> page, CommandSender recipient, String commandTemplate, boolean previous) {
        boolean live = previous ? page.hasPrevious() : page.hasNext();
        String label = messages.raw(previous
                ? (live ? keys.prevLabel() : keys.disabledPrevLabel())
                : (live ? keys.nextLabel() : keys.disabledNextLabel()));
        if (!live || recipient instanceof ConsoleCommandSender) {
            return label;
        }
        int target = previous ? page.page() - 1 : page.page() + 1;
        String command = commandTemplate.replace(PAGE_TOKEN, Integer.toString(target));
        String hint = Placeholder.of("page", target).apply(messages.raw(keys.pageHint()));
        return "<click:run_command:'" + command + "'><hover:show_text:'" + hint + "'>" + label + "</hover></click>";
    }
}
