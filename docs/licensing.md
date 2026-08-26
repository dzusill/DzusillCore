# Licensing

`me.dzusill.core.license` is the anti-piracy gate shared by every paid plugin in
the catalogue. The verification server that answers it lives in
[`license-server/`](../../license-server) — PHP and MySQL, deployed on its own
subdomain.

## Wiring a plugin

Three things, and none of them is optional.

**1. The module**, right after the foundation module — it needs the
`ConfigManager` and `SchedulerService` that module publishes:

```java
public class DShop extends CorePlugin {

    private static final String BBB_LICENSE = "%%__DZ_LICENSE__%%";

    @Override
    protected CoreModule[] modules() {
        return new CoreModule[]{
            new FoundationModule(this),
            new LicenseModule(this, "dshop", "https://builtbybit.com/resources/dshop", BBB_LICENSE),
            // …
        };
    }
}
```

`BBB_LICENSE` **must be declared in the plugin's own class.** BuiltByBit rewrites
string constants inside the `.class` files of the jar it served, and DzusillCore
is not that jar. Put it in core and it is never substituted.

**2. `src/main/resources/license.properties`**

```properties
enforce=${license.enforce}
```

**3. `pom.xml`** — the property, the profile, and filtering for that one file:

```xml
<properties>
    <license.enforce>false</license.enforce>
</properties>

<profiles>
    <profile>
        <id>licensed</id>
        <properties><license.enforce>true</license.enforce></properties>
    </profile>
</profiles>
```

```xml
<resources>
    <resource>
        <directory>src/main/resources</directory>
        <filtering>true</filtering>
        <includes>
            <include>plugin.yml</include>
            <include>license.properties</include>
        </includes>
    </resource>
    <resource>
        <directory>src/main/resources</directory>
        <filtering>false</filtering>
        <excludes>
            <exclude>plugin.yml</exclude>
            <exclude>license.properties</exclude>
        </excludes>
    </resource>
</resources>
```

The `<excludes>` half matters as much as the `<includes>` half. A second
`<resource>` that copies the whole directory unfiltered runs *after* the
filtered one and overwrites it, so the jar ships the literal
`${license.enforce}`, `Boolean.parseBoolean` returns false, and the plugin never
checks a licence. Nothing fails and nothing is logged.

## Releasing

```bash
mvn clean package -Plicensed
```

**A jar built without `-Plicensed` is unprotected.** It runs, it logs
`License enforcement disabled (development build)` once, and it works forever
for anybody who has it. That line in the console is the only difference between
a release jar and a free one — check for it before uploading:

```bash
unzip -p target/dShop.jar license.properties     # must say enforce=true
```

## How it behaves

| Situation | Result |
|---|---|
| Valid key | runs; logs `License OK (DZ-4…)` |
| No key, no BBB injection | disabled, banner points at the store |
| Revoked / expired / wrong product | disabled at the next check (~6 h) |
| Licence server unreachable, grace valid | **runs**, logs `running on offline grace` |
| Licence server unreachable, grace expired | disabled |
| Response unsigned or signed by the wrong key | treated as unreachable → grace |

The grace window is the server's outage budget: the licence server currently
signs 14 days, so it can be down for a fortnight before one paying customer
notices.

## Where the key comes from

Two ways, and a buyer normally sees neither.

**BuiltByBit injection** (the normal path). BBB's *External license key*
placeholder calls the licence server at download time and writes the key into
the jar. `BBB_LICENSE` arrives already substituted, `license.yml` stays empty,
and the buyer installs a jar that simply works.

**`license.yml`** (the fallback). Used when the placeholder came through
un-substituted — recognisable because it kept its `%%` markers — which is the
case for every build made here and for any sale outside BuiltByBit.

## Rotating the signing key

Don't, unless the private key leaked.

One Ed25519 pair covers the whole catalogue: the signed payload names the
product, so a key issued for dShop cannot be replayed at dSell even though both
jars trust the same constant. That makes rotation one operation instead of
sixteen — but it is also a **release of all sixteen jars**, because every copy
already downloaded verifies against whatever was compiled into it. Anyone who
does not update keeps failing until they do.

Losing the private key is worse than leaking it. A leak is recoverable: revoke
the issued keys, rotate, ship. A loss means you can no longer sign anything the
jars in the wild will accept.

## Testing

`license-server/tests/contract-test.sh` compiles the four protocol classes from
this package and drives the real `LicenseClient` against a live PHP server —
valid, revoked, expired, wrong product, instance takeover, offline grace, a
tampered cache, a spoofed server, a replayed nonce and a dead database.

A PHP test of PHP's own signing would agree with itself no matter how wrong it
was. The payload is a `|`-joined string built independently in two languages;
only the Java class a customer actually runs can prove the server's signature is
one they will accept.
