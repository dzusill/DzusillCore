# Requirements

Before building or using DzusillCore, make sure your environment meets the following requirements.

## Java Development Kit

DzusillCore targets **Java 21**. Download it from [Adoptium](https://adoptium.net/) (Eclipse Temurin) or any other JDK 21 distribution.

```bash
java -version
# Expected: openjdk 21 or later
```

## Maven

The project uses **Apache Maven 3.8+** as its build tool.

```bash
mvn -version
# Expected: Apache Maven 3.8.x or later
```

## Paper server

The framework targets the **Paper API 1.21.1**. The compiled JAR must be loaded on a Paper (or compatible fork) server. It will **not** work on vanilla Bukkit/Spigot without adaptation.

Download Paper from [papermc.io](https://papermc.io/).

## Optional soft dependencies

These plugins are fully optional. The framework works without them; they are only activated if present on the server.

| Plugin | What it unlocks |
|---|---|
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Economy API (`VaultHook`) |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Placeholder resolution (`PlaceholderApiHook`) |
| [EssentialsX](https://essentialsx.net/) | User data access (`EssentialsHook`) |
