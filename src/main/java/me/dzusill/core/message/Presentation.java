package me.dzusill.core.message;

/**
 * How one message is delivered: where it is shown, and what it sounds like.
 *
 * <p>
 * Both fields are nullable so a partial override means what it looks like it means. An override that names only a
 * channel keeps its category's sound rather than silencing it, which is the reading a server owner expects when they
 * write two lines into a config and not four.
 * </p>
 *
 * @param channel
 *            where to show it, or {@code null} to inherit
 * @param sound
 *            what to play, or {@code null} to inherit. {@link SoundSpec#NONE} is a decision, not an absence
 */
public record Presentation(MessageChannel channel, SoundSpec sound) {

    /** Plain chat, no sound — how every message behaved before this existed, and the default for anything unstated. */
    public static final Presentation DEFAULT = new Presentation(MessageChannel.CHAT, SoundSpec.NONE);

    /**
     * This on top of {@code base}: whatever this one states wins, and whatever it leaves out is inherited.
     */
    public Presentation over(Presentation base) {
        if (base == null) {
            return this;
        }
        return new Presentation(channel != null ? channel : base.channel(), sound != null ? sound : base.sound());
    }

    /** @return the same, with anything still unstated filled in from {@link #DEFAULT} */
    public Presentation complete() {
        return over(DEFAULT);
    }
}
