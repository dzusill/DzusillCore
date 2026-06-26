# Installation

> **Two approaches available:**
> - **Template (this page)** — clone the repo, rename, delete the example package. Best for new plugins.
> - **[JitPack dependency](as-dependency.md)** — add DzusillCore as a Maven dependency. Best if you already have your own project structure.

---

## 1. Create your project from the template

On GitHub, click **Use this template → Create a new repository** to generate a fresh repo with all the framework files. Then clone your new repo locally:

```bash
git clone https://github.com/YOUR_USERNAME/YOUR_PLUGIN.git
cd YOUR_PLUGIN
```

Alternatively, clone DzusillCore directly if you want to inspect or contribute to the framework itself:

```bash
git clone https://github.com/dzusill/DzusillCore.git
cd DzusillCore
```

## 2. Rename the project

Open `pom.xml` and update these three values to match your plugin:

```xml
<groupId>com.yourname</groupId>
<artifactId>YourPlugin</artifactId>
<name>YourPlugin</name>
```

Then update the `main` class path in `src/main/resources/plugin.yml`:

```yaml
main: com.yourname.yourplugin.YourPlugin
```

## 3. Replace the example package

The `me.dzusill.core.example` package is a fully working reference plugin. Once you understand the structure:

1. Create your own package (e.g. `com.yourname.yourplugin`).
2. Extend `CorePlugin` and implement `modules()`.
3. Delete the `example` package.

See [Your First Plugin](first-plugin.md) for a step-by-step walkthrough.

## 4. Build the JAR

```bash
mvn package
```

Maven downloads all dependencies on first run. Output JAR:

```
target/YourPlugin-1.0.0.jar
```

This is a **fat/shaded JAR** — HikariCP is relocated to `me.dzusill.core.lib.hikari` and both MySQL and PostgreSQL JDBC drivers are bundled inside.

## 5. Drop into your server

```bash
cp target/YourPlugin-1.0.0.jar /path/to/server/plugins/
```

Restart the Paper server. The plugin loads and prints a startup banner.

## 6. Run the tests (optional)

```bash
mvn test
```

All tests should pass. See [Testing](../testing.md) for details.

---

## GitBook.com sync note

Connect the `dzusill/DzusillCore` GitHub repo to GitBook. GitBook reads `.gitbook.yaml` from the
repo root — no monorepo path override needed.
