# Your First Plugin

This page walks through converting the example package into your own plugin from scratch.

## 1. Rename the package

Rename `me.dzusill.core.example` to your package (e.g. `me.yourname.myplugin`). In IntelliJ IDEA: right-click the package → Refactor → Rename.

## 2. Create your main class

Extend `CorePlugin` and implement `modules()`:

```java
public class MyPlugin extends CorePlugin {

    @Override
    protected CoreModule[] modules() {
        return new CoreModule[]{
                new FoundationModule(this),   // config + messages + scheduler + listeners
                new CommandModule(this)        // your commands
        };
    }

    @Override
    protected String[] banner() {
        return new String[]{
                "",
                "  MyPlugin v" + getPluginMeta().getVersion(),
                ""
        };
    }
}
```

> **Do not override `onEnable()` or `onDisable()`.** These are declared `final` in `CorePlugin` and managed by `ModuleManager`. Use the `onPreEnable()`, `onPostEnable()`, and `onPreDisable()` hooks instead, or put startup logic in a module.

## 3. Update plugin.yml

```yaml
name: MyPlugin
version: '1.0.0'
main: me.yourname.myplugin.MyPlugin
api-version: '1.21'
description: My awesome plugin
authors: [yourname]
softdepend: [Vault, PlaceholderAPI, Essentials]   # remove any you don't need
```

## 4. Create your first module

A module groups a logical subsystem (commands, menus, a data service…). The minimum:

```java
public final class CommandModule extends AbstractModule {

    public CommandModule(CorePlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "Commands";
    }

    @Override
    public void onEnable() {
        CommandRegistry commands = service(CommandRegistry.class);
        commands.register(new MyCommand());
    }
}
```

## 5. Build and run

```bash
mvn package
cp target/MyPlugin-1.0.0.jar /path/to/server/plugins/
```

Start the server. The banner you defined in `banner()` will appear in the console on startup.

---

## Module order matters

Modules are enabled in the order you declare them in `modules()` and disabled in **reverse** order. Always declare foundation modules (config, messages, scheduler) before the modules that depend on them.

```java
return new CoreModule[]{
        new FoundationModule(this),    // provides ConfigManager, MessageService, SchedulerService
        new DatabaseModule(this),      // requires SchedulerService → must come after Foundation
        new MenuModule(this),          // requires MenuManager
        new CommandModule(this)        // requires everything above
};
```
