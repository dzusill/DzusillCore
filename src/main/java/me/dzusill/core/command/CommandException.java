package me.dzusill.core.command;

import me.dzusill.core.message.Placeholder;

/**
 * A controlled, user-facing command failure. Instead of sending messages from deep inside parse or run logic, code
 * throws this with a {@code messages.yml} key; the command dispatcher catches it and renders the message uniformly.
 */
public class CommandException extends Exception {

    private final String messageKey;
    private final transient Placeholder placeholder;

    public CommandException(String messageKey) {
        this(messageKey, Placeholder.empty());
    }

    public CommandException(String messageKey, Placeholder placeholder) {
        super(messageKey);
        this.messageKey = messageKey;
        this.placeholder = placeholder;
    }

    public String messageKey() {
        return messageKey;
    }

    public Placeholder placeholder() {
        return placeholder;
    }
}
