# Cooldowns

`CooldownManager<K>` is a generic, thread-safe cooldown tracker. Each feature owns its own instance with a fixed duration, replacing scattered `Map<UUID, Long>` timestamp patterns.

## Creating a cooldown

```java
// Per-feature cooldown, keyed by UUID
CooldownManager<UUID> healCooldown = new CooldownManager<>(30, TimeUnit.SECONDS);

// Can use any key type
CooldownManager<String> worldCooldown = new CooldownManager<>(5, TimeUnit.MINUTES);
```

## API

| Method | Description |
|---|---|
| `start(K key)` | Starts (or restarts) the cooldown for `key` |
| `isActive(K key)` | `true` if still cooling down |
| `remaining(K key)` | Milliseconds remaining, or `0` if not active |
| `reset(K key)` | Clears the cooldown for `key` |
| `clear()` | Clears all tracked cooldowns |

## Usage pattern in a command

```java
@CommandMeta(name = "heal", playerOnly = true)
public final class HealCommand extends CoreCommand {

    private final CooldownManager<UUID> cooldown = new CooldownManager<>(30, TimeUnit.SECONDS);

    @Override
    public void run(CommandContext context, Arguments args) throws CommandException {
        UUID uuid = context.player().getUniqueId();

        if (cooldown.isActive(uuid)) {
            long remaining = cooldown.remaining(uuid);
            throw new CommandException("cooldown",
                    Placeholder.of("time", TimeUtils.format(remaining)));
        }

        // Do the heal
        Player player = context.player();
        player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        cooldown.start(uuid);   // start cooldown after use
    }
}
```

`messages.yml`:

```yaml
cooldown: "<prefix><red>You must wait <yellow>%time% <red>before using this again."
```

## Thread safety

`CooldownManager` uses a `ConcurrentHashMap` internally, so it is safe to call from both the main thread and async tasks.

## Expired entries

Entries are lazily removed when `isActive()` or `remaining()` is called and the cooldown has expired. There is no background cleanup thread. For very long-lived servers with many unique keys, call `clear()` periodically if necessary.
