# Optional Integrations (Hooks)

The hook system allows DzusillCore plugins to integrate with optional third-party plugins (Vault, PlaceholderAPI, EssentialsX, …) without crashing when those plugins are absent. This is critical for a framework used across both simple and complex server setups.

## How it works

```
HookManager.register("Vault", VaultHook::new)
  └─ Bukkit.getPluginManager().isPluginEnabled("Vault")
       ├─ false → return empty(); factory never called, class never loaded
       └─ true  → VaultHook hook = factory.get()
                   hook.tryEnable() → hook.setup()
                   hook.isActive() = true
```

The factory (constructor reference) is evaluated **only after** the presence check, so the hook class — including the external API types it imports — is never loaded by the JVM when the target plugin is absent. This means no `NoClassDefFoundError` even when the server runs without Vault or any other soft-dependency.

## PluginHook base class

```java
public abstract class PluginHook {
    protected PluginHook(String pluginName) { ... }

    public final boolean isPresent() { ... }  // checks Bukkit plugin manager
    public final boolean isActive()  { ... }  // true if setup() ran successfully
    public final String  pluginName(){ ... }

    protected abstract void setup();           // called only when plugin is present
}
```

## HookManager

`HookManager` (a `Service`) owns and resolves all hooks:

```java
public <T extends PluginHook> Optional<T> register(String pluginName, Supplier<T> factory)
public <T extends PluginHook> Optional<T> get(Class<T> type)
public boolean isActive(Class<? extends PluginHook> type)
```

## plugin.yml and pom.xml

Declare soft dependencies in `plugin.yml`:

```yaml
softdepend: [Vault, PlaceholderAPI, Essentials]
```

Keep their Maven scope `provided` — they must **not** be bundled in your JAR:

```xml
<dependency>
    <groupId>com.github.MilkBowl</groupId>
    <artifactId>VaultAPI</artifactId>
    <scope>provided</scope>
</dependency>
```

## Built-in hooks

### VaultHook

```java
hooks.register("Vault", VaultHook::new)
     .ifPresent(vault -> plugin.getLogger().info("Economy: " + vault.economy().getName()));
```

```java
hooks.get(VaultHook.class).ifPresent(vault -> {
    Economy eco = vault.economy();
    eco.depositPlayer(player, 100.0);
    double balance = eco.getBalance(player);
});
```

### PlaceholderApiHook

```java
hooks.get(PlaceholderApiHook.class).ifPresent(papi -> {
    String parsed = papi.apply(player, "Your balance: %vault_eco_balance%");
    player.sendMessage(ColorUtils.parse(parsed));
});
```

### EssentialsHook

```java
hooks.get(EssentialsHook.class).ifPresent(ess -> {
    IUser user = ess.essentials().getUser(player);
    // access Essentials user data
});
```

## Registering hooks — IntegrationModule

```java
public final class IntegrationModule extends AbstractModule {

    @Override
    public void onEnable() {
        HookManager hooks = new HookManager(plugin);
        hooks.register("Vault",          VaultHook::new);
        hooks.register("PlaceholderAPI", PlaceholderApiHook::new);
        hooks.register("Essentials",     EssentialsHook::new);
        provide(HookManager.class, hooks);
    }
}
```

## Adding a custom hook

1. Create a class extending `PluginHook`:

```java
public final class LuckPermsHook extends PluginHook {

    private LuckPerms api;

    public LuckPermsHook() {
        super("LuckPerms");
    }

    @Override
    protected void setup() {
        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            this.api = provider.getProvider();
        }
    }

    public LuckPerms api() {
        return api;
    }
}
```

2. Add the dependency to `pom.xml` with `<scope>provided</scope>`.
3. Add `LuckPerms` to `softdepend` in `plugin.yml`.
4. Register lazily:

```java
hooks.register("LuckPerms", LuckPermsHook::new);
```

5. Use safely:

```java
hooks.get(LuckPermsHook.class).ifPresent(lp -> {
    User user = lp.api().getUserManager().getUser(player.getUniqueId());
});
```
