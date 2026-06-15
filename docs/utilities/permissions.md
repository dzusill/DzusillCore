# Permissions

## Built-in permission nodes

`CorePermission` lists the framework's own nodes as constants:

```java
public final class CorePermission {
    public static final String ADMIN  = "core.admin";
    public static final String RELOAD = "core.reload";
}
```

These are declared in `plugin.yml`:

```yaml
permissions:
  core.admin:
    description: Grants access to all DzusillCore administration commands
    default: op
  core.reload:
    description: Allows reloading the plugin configuration
    default: op
  core.heal:
    description: Allows use of the example /heal command
    default: op
  core.shop:
    description: Allows use of the example /shop command
    default: true
```

## Adding permissions for your plugin

1. Create a constants class:

```java
public final class MyPluginPermission {
    public static final String ADMIN    = "myplugin.admin";
    public static final String HEAL     = "myplugin.heal";
    public static final String SHOP     = "myplugin.shop";
    public static final String SET_WARP = "myplugin.setwarp";
    public static final String USE_WARP = "myplugin.warp";

    private MyPluginPermission() {}
}
```

2. Reference the constant in `@CommandMeta`:

```java
@CommandMeta(name = "setwarp", permission = MyPluginPermission.SET_WARP)
```

3. Declare in `plugin.yml`:

```yaml
permissions:
  myplugin.admin:
    description: Full admin access
    default: op
    children:
      myplugin.setwarp: true
      myplugin.warp: true
  myplugin.heal:
    description: Use /heal
    default: op
  myplugin.shop:
    description: Open the shop
    default: true
  myplugin.setwarp:
    description: Create warps
    default: op
  myplugin.warp:
    description: Use warps
    default: true
```

## Permission in commands

The command framework checks permissions automatically. Set it in `@CommandMeta` or via `permission()`:

```java
// Annotation
@CommandMeta(name = "heal", permission = "myplugin.heal")

// Programmatic
super("heal");
permission("myplugin.heal");
```

If the sender lacks the permission, `Messages.NO_PERMISSION` is sent automatically. The command never calls `run()`.

## Permission in menus

Check permissions manually inside click handlers or in `decorate()`:

```java
if (!context.player().hasPermission(MyPluginPermission.ADMIN)) {
    set(22, MenuItem.display(
            new ItemBuilder(Material.BARRIER).name("<red>No access").build()));
    return;
}
// admin-only items ...
```

## Tab-completion filtering

Router commands automatically hide children whose permission the sender lacks. No extra code needed.
