# Nexus — Application Framework for Paper

**Nexus** is a Kotlin-first application framework for Paper plugins. It bundles dependency injection with classpath scanning, YAML configuration, command auto-discovery, coroutine infrastructure, and a growing set of opt-in modules that handle the boilerplate every plugin reinvents: i18n, persistence, schedulers, GUIs, Bedrock forms, Vault, PlaceholderAPI, and more.

> **2.0.0 — Hytale support removed.** Earlier Nexus releases shipped Hytale command adapters in the root project. The Hytale module is no longer maintained and was dropped in 2.0.0. If you still need it, pin to `1.11.0`.

## Modules

| Module | Version | Purpose |
|---|---|---|
| **`nexus-core`** | 2.0.0 | DI container, config system, coroutine infrastructure, command annotations |
| **`nexus-paper`** | 2.0.0 | Paper Brigadier command system, `BukkitDispatcher`, Paper extensions |
| **`nexus-resources`** | 2.0.0 | Bundled-resource extraction (`ResourceExtractor.extractIfMissing` / `extractDirectory` / `overwriteIfNewerVersion`) — foundation for i18n + migrations |
| **`nexus-i18n`** | 2.0.0 | MiniMessage-backed YAML translator (`LangService`), `@LangFile` annotation, per-locale file with bundled-default overlay |
| **`nexus-persistence`** | 2.0.0 | `DatabaseFactory.open` (HikariCP, SQLite/MariaDB/Postgres) + idempotent versioned `MigrationRunner` |
| **`nexus-scheduler`** | 2.0.0 | `NexusScheduler` with `AutoCloseable` handles, `cancelAll()` on shutdown, thread guards |
| **`nexus-paper-loader`** | 2.0.0 | Java-only abstract `NexusPaperPluginLoader` declaring the standard runtime library set |
| **`nexus-paper-gui`** | 2.0.0 | `ItemBuilder` DSL, `LivePollingMenu`, `PaginatedListMenu` (IFramework + Adventure) |
| **`nexus-paper-bedrock`** | 2.0.0 | `PlatformDetectionService` (reflective Floodgate probe) + `CumulusFormBase` |
| **`nexus-paper-listeners`** | 2.0.0 | `@Listener` marker + `registerNexusListeners` scanner (auto-registers Bukkit listeners) |
| **`nexus-vault`** | 2.0.0 | `EconomyProvider` port + `VaultEconomyAdapter` + `VaultHealth` + `VaultDegradedEvent` |
| **`nexus-papi`** | 2.0.0 | `@PapiExpansion` + `PlaceholderResolver` + auto-registration with PlaceholderAPI |
| **`nexus-permissions`** | 2.2.0 | Kotlin DSL for permission trees + YAML serializer + `paper-plugin.yml` merger (library) |
| **`nexus-permissions-gradle`** | 2.2.0 | Gradle plugin: runs the DSL during `processResources`, splices the result into `paper-plugin.yml` before `shadowJar` |

Roadmap and acceptance REQs: see [`docs/roadmap.md`](docs/roadmap.md) and [`docs/requirements.md`](docs/requirements.md).

## Quick Start

### 1. Add the modules you need

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") // Nexus releases — no token required

    // Opt-in to local-published snapshots for dev:
    // -PuseMavenLocal=true on the command line
}

dependencies {
    // Core DI + config + coroutines — always needed
    implementation("com.github.BadgersMC.Nexus:nexus-core:v2.2.0")

    // Pick whichever extras you want:
    implementation("com.github.BadgersMC.Nexus:nexus-paper:v2.2.0")            // Paper commands
    implementation("com.github.BadgersMC.Nexus:nexus-resources:v2.2.0")        // Bundled resource extraction
    implementation("com.github.BadgersMC.Nexus:nexus-i18n:v2.2.0")             // MiniMessage i18n
    implementation("com.github.BadgersMC.Nexus:nexus-persistence:v2.2.0")      // DB + migrations
    implementation("com.github.BadgersMC.Nexus:nexus-scheduler:v2.2.0")        // Bukkit scheduler facade
    implementation("com.github.BadgersMC.Nexus:nexus-paper-gui:v2.2.0")        // IFramework GUIs
    implementation("com.github.BadgersMC.Nexus:nexus-paper-bedrock:v2.2.0")    // Cumulus / Floodgate
    implementation("com.github.BadgersMC.Nexus:nexus-paper-listeners:v2.2.0")  // @Listener auto-register
    implementation("com.github.BadgersMC.Nexus:nexus-vault:v2.2.0")            // Vault economy
    implementation("com.github.BadgersMC.Nexus:nexus-papi:v2.2.0")             // PlaceholderAPI
    implementation("com.github.BadgersMC.Nexus:nexus-paper-loader:v2.2.0")     // Shared PluginLoader
}
```

> JitPack serves the artifacts from this public repo on demand. The runtime package names inside the JARs remain `net.badgersmc.nexus.*` — only the Maven coordinates use the JitPack groupId. If you shadow + relocate, target `net.badgersmc.nexus`.

### 2. Annotate your classes

```kotlin
@Repository
class PlayerRepository(private val database: DataSource) {
    fun findPlayer(id: UUID): PlayerStats? { /* ... */ }
}

@Service
class PlayerService(private val repository: PlayerRepository) {
    @PostConstruct
    fun init() { /* ... */ }
}
```

### 3. Build a NexusContext + register the extras you wired

```kotlin
override fun onEnable() {
    val nexus = NexusContext.create(
        basePackage = "net.example.myplugin",
        classLoader = this::class.java.classLoader,
        configDirectory = dataFolder.toPath(),
        contextName = "MyPlugin",
        externalBeans = mapOf("plugin" to this)
    )

    // Wire i18n
    val lang = LangService(this, Locale("en_US"), MyPluginLang::class.java)
    nexus.registerBean("langService", LangService::class, lang)

    // Wire persistence
    val ds = DatabaseFactory.open(DatabaseSpec.Sqlite(File(dataFolder, "plugin.db")))
    MigrationRunner(ds, "migrations", this::class.java.classLoader).runAll()
    nexus.registerBean("dataSource", DataSource::class, ds as DataSource)

    // Wire scheduler
    val scheduler = NexusScheduler(this)
    nexus.registerBean("nexusScheduler", NexusScheduler::class, scheduler)

    // Register Paper commands + @Listener beans
    nexus.registerPaperCommands(basePackage = "net.example.myplugin", classLoader = ..., plugin = this)
    registerNexusListeners(basePackage = "net.example.myplugin", classLoader = ..., plugin = this, nexus = nexus)

    // Cleanup on disable
    saveState(scheduler)
}

override fun onDisable() {
    nexus?.close()           // cancels scope, fires @PreDestroy
    scheduler?.cancelAll()   // cancels every outstanding Bukkit task
}
```

## Module guides

### `nexus-core` — Dependency injection + config

- **Component discovery** — `@Component`, `@Service`, `@Repository` auto-discovered via [ClassGraph](https://github.com/classgraph/classgraph)
- **Constructor injection** — dependencies resolved through primary constructors
- **Lifecycle** — `@PostConstruct` / `@PreDestroy` (supports suspend)
- **Scopes** — Singleton (default) or `@Scope(ScopeType.PROTOTYPE)`
- **Polymorphic** — beans resolved by interface or superclass type
- **Qualifiers** — `@Qualifier("name")` to disambiguate
- **External beans** — pre-built instances registered via `externalBeans` before scanning
- **Coroutines** — Java 21 virtual thread dispatchers with classloader propagation, per-plugin `CoroutineScope` with `SupervisorJob`, `BukkitDispatcher` for Paper main-thread work
- **Config** — `@ConfigFile`, `@ConfigName`, `@Comment`, `@Transient`; YAML with comment preservation; auto-discovered and registered as beans; `ConfigManager` for centralised reload/save

### `nexus-paper` — Paper Brigadier commands

```kotlin
@Command(name = "sg", description = "Survival Games", permission = "sg.use")
class SGCommand(private val gameManager: GameManager) {
    @Subcommand("join")
    @PlayerOnly
    fun join(@Context player: Player, @Arg("arena") arena: String) {
        gameManager.joinGame(player, arena)
    }

    @Subcommand("admin create")               // multi-segment paths work
    @Permission("sg.admin")
    fun create(@Context p: Player, @Arg("name") n: String, @Arg("radius") r: Int) { /* ... */ }

    @Subcommand("stats")
    @Async                                    // coroutine scope, not main thread
    suspend fun stats(@Context p: Player, @Arg("target") t: Player) { /* ... */ }
}
```

Built-in resolvers: `String`, `Int`, `Long`, `Double`, `Float`, `Boolean`, `Player`. `@Context` types: `Player`, `CommandSender`, `CommandSourceStack`, `Server`.

### `nexus-resources` — Bundled defaults

```kotlin
// Extract a single resource on first run; never overwrites an existing user file.
ResourceExtractor.extractIfMissing(plugin, "lang/en_US.yml")

// Extract every file under a JAR prefix (preserves directory structure).
ResourceExtractor.extractDirectory(plugin, "migrations", File(plugin.dataFolder, "migrations"))

// Opt-in overwrite when a bundled version is newer.
ResourceExtractor.overwriteIfNewerVersion(plugin, "lang/en_US.yml", target,
    currentVersion = 1, bundledVersion = 2)
```

Defends against zip-slip path traversal in `extractDirectory`.

### `nexus-i18n` — MiniMessage translator

```kotlin
@LangFile(resourcePrefix = "lang", defaultLocale = "en_US")
class MyPluginLang

// In onEnable:
val lang = LangService(plugin, Locale(config.lang.locale), MyPluginLang::class.java)
nexus.registerBean("langService", LangService::class, lang)

// Anywhere LangService is injected:
player.sendMessage(lang.msg("admin.import.result",
    "created" to r.created,
    "skipped" to r.skipped
))
```

Full MiniMessage syntax in the lang file: `<red>`, `<gradient:#A:#B>`, `<rainbow>`, `<hover:show_text:'…'>`, `<click:run_command:/foo>`. Bundled defaults are overlaid onto a partial user file so missing keys still resolve. `LangService.legacy(...)` returns a §-serialised string for Cumulus/legacy paths; `LangService.raw(...)` returns the unrendered template for nested placeholders.

### `nexus-persistence` — JDBC + migrations

```kotlin
// Declarative spec — sealed type with Sqlite, MariaDB, Postgres, JdbcUrl variants
val ds = DatabaseFactory.open(
    DatabaseSpec.MariaDB(
        host = "db.example", port = 3306, database = "mydb",
        username = "user", password = "secret",
        params = mapOf("useSSL" to "true", "serverTimezone" to "UTC")
    )
)

// Versioned migrations from src/main/resources/migrations/V001__init.sql etc.
MigrationRunner(ds, "migrations", plugin::class.java.classLoader).runAll()
```

Pool sizing baked in: SQLite max=1 (single-writer), networked DBs default to 10. URL params are URL-encoded. Migration discovery walks every jar on the classloader (not just the runner's own jar) — picks up migrations shipped from dependency jars. Duplicate version numbers throw at discovery time. Statement splitter handles quoted literals and `--` line comments.

### `nexus-scheduler` — Bukkit scheduler facade

```kotlin
@Service
class MyService(private val scheduler: NexusScheduler) {
    fun start() {
        // Returns AutoCloseable — close() cancels the underlying Bukkit task.
        val task = scheduler.runRepeating(0L, 20L) {
            // every second, on main thread
        }
        // Async variant runs off-thread:
        scheduler.runRepeatingAsync(0L, 100L) {
            // every 5 seconds, off main thread
        }
    }
}

// In onDisable: scheduler.cancelAll() — closes every outstanding handle.
```

Thread guards: `scheduler.requireMainThread()` / `scheduler.requireAsyncThread()` throw `IllegalStateException` when called on the wrong thread.

### `nexus-paper-gui` — GUI helpers

```kotlin
// Build Adventure-aware ItemStacks with one DSL call:
val icon = itemStack(Material.EMERALD) {
    name(lang.msg("entry.name"))
    lore(lang.msg("entry.line.1"), lang.msg("entry.line.2"))
    glow()
}

// Subclass LivePollingMenu for any GUI that needs periodic redraws —
// auto-cancels its scheduler task when the player closes the inventory.
class AuctionBrowser(scheduler: NexusScheduler, ...) : LivePollingMenu(scheduler) {
    override fun title() = lang.msg("gui.auctions.title")
    override fun render(gui: ChestGui) { /* repaint from current state */ }
}

// Or PaginatedListMenu for typed list browsers — handles pages + sort + close button.
class MemberList(scheduler: NexusScheduler, ...) : PaginatedListMenu<UUID>(scheduler, rows = 6) {
    override fun items(): List<UUID> = shop.trusted.toList()
    override fun renderEntry(item: UUID): ItemStack = itemStack(...)
    // override prevIcon / nextIcon / pageIndicatorIcon / closeIcon
}
```

### `nexus-paper-bedrock` — Bedrock / Floodgate / Cumulus

```kotlin
@Service
class MyMenuFactory(private val platform: PlatformDetectionService) {
    fun open(player: Player) {
        if (platform.isBedrockPlayer(player) && platform.isCumulusAvailable()) {
            BedrockForm(logger, lang).open(player)
        } else {
            JavaMenu().open(player)
        }
    }
}

class BedrockForm(logger: Logger, lang: LangService) : CumulusFormBase(logger, lang) {
    override fun buildForm(): Form = CustomForm.builder().title("…").build()
}
```

Cumulus and Floodgate are declared `compileOnly`. Always guard via `PlatformDetectionService` before instantiating a `CumulusFormBase` — loading the class on a server without Cumulus throws `NoClassDefFoundError` by design.

### `nexus-paper-listeners` — `@Listener` auto-register

```kotlin
@Component
@Listener
class MyShopListener(private val shops: ShopRepository) : org.bukkit.event.Listener {
    @EventHandler
    fun onClick(event: PlayerInteractEvent) { /* ... */ }
}

// In onEnable, after creating the NexusContext:
registerNexusListeners(
    basePackage = "net.example.myplugin",
    classLoader = this::class.java.classLoader,
    plugin = this,
    nexus = nexus
)
```

No more `@PostConstruct fun register() { Bukkit.getPluginManager().registerEvents(this, plugin) }` boilerplate in every listener.

### `nexus-vault` — Economy port

```kotlin
@Service
class ShopTradeService(private val economy: EconomyProvider, private val health: VaultHealth) {
    fun execute(buyer: UUID, price: Double): Boolean {
        if (!health.isAvailable) return false
        return economy.withdraw(buyer, price)
    }
}

// VaultDegradedEvent fires when the Vault provider disappears at runtime —
// listen to gate auctions / rent collection / shop trades.
```

`VaultEconomyAdapter` re-resolves the Vault registration on every call so plugins that re-register the provider at runtime work cleanly. Rejects negative + non-finite amounts on `has` / `withdraw` / `deposit`.

### `nexus-papi` — PlaceholderAPI

```kotlin
@Component
@PapiExpansion(identifier = "myplugin", author = "BadgersMC", version = "1.0.0")
class MyExpansion(private val stats: StatsService) : PlaceholderResolver {
    override fun resolve(player: OfflinePlayer?, params: String): String? = when (params) {
        "kills" -> stats.kills(player?.uniqueId).toString()
        else -> null
    }
}

// In onEnable, after creating the NexusContext:
registerNexusExpansions(basePackage = "net.example.myplugin", classLoader = ..., nexus = nexus)
```

Silently no-ops when PlaceholderAPI is not installed.

### `nexus-permissions` + `nexus-permissions-gradle` — Permission tree DSL

Stop hand-maintaining the `permissions:` block of `paper-plugin.yml`. Declare the tree once in `build.gradle.kts`; the Gradle plugin splices it into the staged resource before the jar is packed.

`nexus-permissions-gradle` is the only Nexus module distributed as a **Gradle plugin**, not a library. Plugin Portal submission is deferred for the 2.2 line — wire it via the classic `buildscript { classpath(...) }` form so JitPack resolves it.

```kotlin
// build.gradle.kts (consumer)
import net.badgersmc.nexus.permissions.Default

buildscript {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.github.BadgersMC.Nexus:nexus-permissions-gradle:v2.2.0")
    }
}

plugins {
    java
    kotlin("jvm")
}

apply(plugin = "net.badgersmc.nexus.permissions")

configure<net.badgersmc.nexus.permissions.gradle.NexusPermissionsExtension> {
    tree {
        node("myplugin.admin", default = Default.OP, description = "All admin commands") {
            child("reload")
            child("import", default = Default.NOT_OP)
        }
        node("myplugin.use", default = Default.TRUE)
    }
}
```

Drop a `src/main/resources/paper-plugin.yml` that contains everything except `permissions:` — the merger fills the rest:

```yaml
name: MyPlugin
main: net.example.myplugin.MyPlugin
api-version: '1.21'
authors: [BadgersMC]
```

After `./gradlew classes`, the staged `build/resources/main/paper-plugin.yml` gains:

```yaml
permissions:
  myplugin.admin:
    default: op
    description: All admin commands
    children:
    - myplugin.admin.import
    - myplugin.admin.reload
  myplugin.admin.import:
    default: not op
  myplugin.admin.reload:
    default: op
  myplugin.use:
    default: true
```

The generation task auto-fences `shadowJar` when the shadow plugin (`com.gradleup.shadow` or the legacy `com.github.johnrengelman.shadow` id) is applied — no extra wiring needed. Re-running produces a byte-identical file (REQ-205), so the merge plays nicely with reproducible-build setups.

### `nexus-paper-loader` — Shared `PluginLoader`

```java
// src/main/java/.../MyPluginLoader.java — must stay Java; Paper loads this
// before any Kotlin classes are on the classpath.
public final class MyPluginLoader extends NexusPaperPluginLoader {
    @Override
    @NotNull
    protected List<String> additionalLibraries() {
        return List.of(
            "com.zaxxer:HikariCP:5.1.0",
            "org.xerial:sqlite-jdbc:3.45.1.0"
        );
    }
}
```

```yaml
# paper-plugin.yml
loader: net.example.myplugin.MyPluginLoader
```

The Nexus base provides the standard runtime library set: kotlin-stdlib, kotlin-reflect, kotlinx-coroutines-core-jvm, kaml-jvm, classgraph, slf4j-api. Uses `repo1.maven.org` directly to bypass server-side Maven mirror outages.


## Annotations cheat-sheet

| Annotation | Module | Description |
|---|---|---|
| `@Component`, `@Service`, `@Repository` | core | Auto-discovered DI bean |
| `@Inject`, `@Qualifier("name")` | core | Mark injection point / disambiguate |
| `@PostConstruct`, `@PreDestroy`, `@Scope(...)` | core | Lifecycle and scope |
| `@ConfigFile("name")`, `@ConfigName`, `@Comment`, `@Transient` | core | Config mapping |
| `@Command`, `@Arg`, `@Context` | core / paper | Command class + parameters |
| `@Subcommand("path")`, `@Permission`, `@PlayerOnly`, `@Async`, `@Suggests` | paper | Paper Brigadier extras |
| `@LangFile(resourcePrefix, defaultLocale)` | i18n | Marker class for lang resource location |
| `@Listener` | paper-listeners | Auto-register a Bukkit `Listener` |
| `@PapiExpansion(identifier, author, version)` | papi | Auto-register a `PlaceholderResolver` |

## Architecture

```
nexus-core/                       Phase 0
├── core/             NexusContext, ComponentRegistry, BeanFactory, BeanDefinition
├── scanning/         ComponentScanner (ClassGraph)
├── annotations/      @Component, @Service, @Repository, @Inject, …
├── coroutines/       NexusDispatchers, NexusScope, CoroutineExtensions
├── config/           ConfigManager, ConfigLoader, @ConfigFile / @ConfigName / @Comment / @Transient
└── commands/         Shared @Command / @Arg / @Context annotations + CommandDefinition

nexus-paper/                      Phase 0
├── BukkitDispatcher              Main-thread coroutine dispatcher
├── PaperNexusExtensions          registerPaperCommands()
└── commands/                     Paper Brigadier scanner + registry + resolvers

nexus-resources/                  Phase 1
└── ResourceExtractor             extractIfMissing / extractDirectory / overwriteIfNewerVersion

nexus-i18n/                       Phase 1
├── LangFile, Locale              Marker annotation + locale id wrapper
└── LangService                   YAML + MiniMessage + bundled-default overlay

nexus-persistence/                Phase 2
├── DatabaseSpec                  Sealed (Sqlite, MariaDB, Postgres, JdbcUrl)
├── DatabaseFactory               HikariCP wrapper with pool-sizing rules
├── MigrationRunner               Versioned idempotent migration runner
└── DataSourceProvider            Optional multi-DS port

nexus-scheduler/                  Phase 2
├── SchedulerBackend              Bukkit / Test backend abstraction
└── NexusScheduler                run* → AutoCloseable, cancelAll, thread guards

nexus-paper-loader/               Phase 2 (Java only)
└── NexusPaperPluginLoader        Standard runtime library set + additionalLibraries() hook

nexus-paper-gui/                  Phase 3
├── MenuBase                      Marker interface
├── ItemBuilder                   itemStack { … } DSL
├── LivePollingMenu               Refresh-on-tick GUI base
└── PaginatedListMenu             Typed list browser with controls

nexus-paper-bedrock/              Phase 3
├── PlatformDetectionService      Reflective Floodgate/Cumulus probes
└── CumulusFormBase               Cumulus form base with LangService integration

nexus-paper-listeners/            Phase 3
├── @Listener
└── ListenerRegistry              ClassGraph scanner + Bukkit register

nexus-vault/                      Phase 4
├── EconomyProvider               Plugin-side port
├── VaultEconomyAdapter           Vault adapter (re-resolves every call)
├── VaultHealth                   Mutable availability flag bean
└── VaultDegradedEvent            Fired when provider disappears

nexus-papi/                       Phase 4
├── @PapiExpansion
├── PlaceholderResolver
├── NexusExpansionAdapter         Bridges Nexus → PAPI's PlaceholderExpansion
└── ExpansionRegistry             ClassGraph scanner + PAPI register

nexus-permissions/                Phase 4 (pure library)
├── Default                       OP / NOT_OP / TRUE / FALSE → Bukkit permission defaults
├── PermissionTree                In-memory tree built by the DSL
├── permissionTree { … }          Type-safe builder (node / child)
├── PermissionTreeSerializer      Flat-keyed Paper permissions: YAML
└── PaperPluginYmlMerger          Range-based splice into existing paper-plugin.yml

nexus-permissions-gradle/         Phase 4 (Gradle plugin)
├── NexusPermissionsPlugin        Apply-time wiring + lifecycle hooks
├── NexusPermissionsExtension     nexusPermissions { tree { … } } DSL block
└── GenerateNexusPermissionsTask  Runs after processResources, before jar / shadowJar
```

## Requirements

- Java 21+ (for virtual threads)
- Kotlin 2.0+
- **Paper modules**: Paper 1.21.11-R0.1-SNAPSHOT or newer
- Optional runtime deps: PlaceholderAPI 2.11+, Vault 1.7+, Floodgate 2.2+, Cumulus 2.0+

## Shadow JAR relocation

When shading Nexus into your plugin, relocate `net.badgersmc.nexus` and `io.github.classgraph`:

```kotlin
tasks.shadowJar {
    relocate("net.badgersmc.nexus", "com.example.mymod.shaded.nexus")
    relocate("io.github.classgraph", "com.example.mymod.shaded.classgraph")
    relocate("nonapi.io.github.classgraph", "com.example.mymod.shaded.nonapi.classgraph")
}
```

If you're using Paper's runtime library loader (recommended — see `nexus-paper-loader`), exclude the heavy transitives from the shadow jar so they're downloaded by Paper at runtime instead of bundled:

```kotlin
tasks.shadowJar {
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib:.*"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-reflect:.*"))
        exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:.*"))
        exclude(dependency("com.charleskorn.kaml:kaml-jvm:.*"))
        // …keep this list in sync with NexusPaperPluginLoader.STANDARD_NEXUS_LIBRARIES
    }
}
```

## Consuming Nexus

Nexus is published via [JitPack](https://jitpack.io). JitPack builds the artifacts from this public repo on demand the first time someone requests them, then caches them on its CDN.

### Coordinates

```
com.github.BadgersMC.Nexus:<module>:<tag>
```

- `<module>` matches a sub-project name (`nexus-core`, `nexus-paper-gui`, …)
- `<tag>` is a release tag, **with the `v` prefix** (e.g. `v2.2.0`) — JitPack uses the tag verbatim
- For a SNAPSHOT off a branch: `com.github.BadgersMC.Nexus:nexus-core:main-SNAPSHOT`

No token required. The first request after a new tag triggers a build (~1–2 minutes); after that it is served from the CDN.

### Local development

Plain `./gradlew build` works once `maven("https://jitpack.io")` is in your repositories. Nothing else to configure.

If you are hacking on Nexus and want to pick up in-progress changes that are not tagged yet:

```bash
git clone https://github.com/BadgersMC/Nexus.git
cd Nexus
./gradlew -PuseMavenLocal=true publishToMavenLocal
```

Then in your consumer: `./gradlew -PuseMavenLocal=true build` — `mavenLocal()` is gated behind that flag on every Nexus consumer in the org so CI never picks up stale local jars.

### GitHub Actions on a consumer plugin

Nothing to configure. JitPack is public.

```yaml
- name: Build
  run: ./gradlew build
```

### Internal mirror (GitHub Packages)

Nexus is *also* published to `maven.pkg.github.com/BadgersMC/Nexus` on every tag — that workflow stays in place for internal CI mirrors. Consumer plugins should not need it. See `.github/workflows/publish.yml` to inspect or re-trigger.

### Publishing a new Nexus release

1. Bump `version` in `build.gradle.kts` (sub-modules pull from `rootProject.version`).
2. Merge to `main`.
3. Tag: `git tag v2.2.0 && git push origin v2.2.0`.
4. The `Publish to GitHub Packages` workflow fires (runs the test suite + pushes to GHP). JitPack picks up the new tag on the first consumer request.

The workflow checks that the tag matches the project version and fails fast otherwise.

## License

MIT License — see LICENSE file for details.
