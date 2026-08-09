package me.dzusill.core.prompt;

/**
 * How a single chat prompt behaves.
 *
 * @param question
 *            MiniMessage text sent before capture begins
 * @param timeoutTicks
 *            how long to wait before resolving as cancelled
 * @param maxLength
 *            longer input is truncated rather than rejected, so a player never loses a long answer entirely
 * @param cancelKeyword
 *            typing this resolves the prompt as cancelled; case-insensitive
 */
public record PromptOptions(String question, long timeoutTicks, int maxLength, String cancelKeyword) {

    /** 60 seconds, matching the longest-lived of the six downstream implementations this replaces. */
    public static final long DEFAULT_TIMEOUT_TICKS = 1_200L;

    public static final int DEFAULT_MAX_LENGTH = 200;

    public static final String DEFAULT_CANCEL_KEYWORD = "cancel";

    public PromptOptions {
        if (question == null || question.isEmpty())
            throw new IllegalArgumentException("question must not be null or empty");
        if (timeoutTicks <= 0)
            throw new IllegalArgumentException("timeoutTicks must be positive, got " + timeoutTicks);
        if (maxLength < 1)
            throw new IllegalArgumentException("maxLength must be positive, got " + maxLength);
        cancelKeyword = cancelKeyword == null ? DEFAULT_CANCEL_KEYWORD : cancelKeyword;
    }

    public static PromptOptions of(String question) {
        return new PromptOptions(question, DEFAULT_TIMEOUT_TICKS, DEFAULT_MAX_LENGTH, DEFAULT_CANCEL_KEYWORD);
    }

    public PromptOptions withTimeout(long ticks) {
        return new PromptOptions(question, ticks, maxLength, cancelKeyword);
    }

    public PromptOptions withMaxLength(int length) {
        return new PromptOptions(question, timeoutTicks, length, cancelKeyword);
    }
}
