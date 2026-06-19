package me.dzusill.core.command.meta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a command node's identity and access rules without boilerplate. A node annotated with this can use the
 * no-arg {@code super()} constructor; the framework reads the values reflectively.
 *
 * <pre>{@code
 * &#64;CommandMeta(name = "heal", permission = "core.heal", playerOnly = true)
 * public final class HealCommand extends CoreCommand { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CommandMeta {

    /** Primary command/subcommand label. */
    String name();

    /** Alternative labels. */
    String[] aliases() default {};

    /** Permission node required to run; empty means no permission check. */
    String permission() default "";

    /** Short human-readable description. */
    String description() default "";

    /** If {@code true}, only players (not the console) may run the command. */
    boolean playerOnly() default false;
}
