# Using DzusillCore as a Dependency (JitPack)

Instead of cloning the template, you can depend on DzusillCore as a Maven library via [JitPack](https://jitpack.io). This works if you already have your own plugin structure and just want to pull in the framework.

## When to use which approach

| Approach | Best for |
|---|---|
| [Template (clone)](installation.md) | New plugin from scratch — gives you the full example + build setup |
| **JitPack dependency** | Existing project — you manage your own structure, just add the framework |

---

## 1. Add JitPack repository

In your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

## 2. Add the dependency

```xml
<dependency>
    <groupId>me.dzusill</groupId>
    <artifactId>DzusillCore</artifactId>
    <version>1.1.0</version>
    <scope>compile</scope>
</dependency>
```

> Replace `1.1.0` with the [latest release tag](https://github.com/dzusill/DzusillCore/releases).
> Use `compile` scope (recommended) to shade DzusillCore into your JAR — see the scope table below.

### `provided` vs `compile` scope

| Scope | When |
|---|---|
| `provided` | You deploy `DzusillCore-x.x.x.jar` as a standalone plugin on the server — all your consumer plugins share one copy |
| `compile` + shade | You bundle DzusillCore into your plugin's fat JAR — no separate server plugin needed, but each plugin carries its own copy |

If you shade DzusillCore, relocate it to avoid classpath conflicts with other plugins that also shade it:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <relocations>
                    <relocation>
                        <pattern>me.dzusill.core</pattern>
                        <shadedPattern>com.yourname.yourplugin.lib.core</shadedPattern>
                    </relocation>
                </relocations>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## 3. Declare plugin dependency (provided scope only)

If you use `provided` scope, add DzusillCore to your `plugin.yml` so Paper loads it first:

```yaml
depend:
  - DzusillCore
```

---

## 4. Extend CorePlugin

Create your main class:

```java
public class MyPlugin extends CorePlugin {

    @Override
    protected CoreModule[] modules() {
        return new CoreModule[]{
            new FoundationModule(this),
            new CommandModule(this),
        };
    }
}
```

Update `plugin.yml` main class:

```yaml
main: com.yourname.yourplugin.MyPlugin
```

---

## 5. What is NOT included in the artifact

The JitPack artifact intentionally excludes:

- `me.dzusill.core.example.*` — example plugin code (reference only, not part of the API)
- `plugin.yml` — would conflict with your own plugin descriptor

The artifact includes (shaded/relocated internally):

- HikariCP → `me.dzusill.core.lib.hikari`
- Adventure → `me.dzusill.core.lib.kyori`
- MySQL + PostgreSQL JDBC drivers

---

## Next steps

- [Your First Plugin](first-plugin.md) — build your first module
- [Modules](../core-concepts/modules.md) — how the lifecycle works
- [Services](../core-concepts/services.md) — how modules share dependencies
