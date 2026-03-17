# Nexus — DI Framework for Hytale & Paper

**Nexus** is a Kotlin-first application framework providing automatic dependency injection with classpath scanning, YAML configuration management, command auto-discovery, and coroutine infrastructure backed by Java 21 virtual threads.

Nexus ships as two modules:
- **`nexus-core`** — DI container, config system, coroutine infrastructure, and Hytale command adapters
- **`nexus-paper`** — Paper Brigadier command system, `BukkitDispatcher`, and Paper-specific extensions

## Features

### Dependency Injection with Classpath Scanning

- **Automatic component discovery** — `@Component`, `@Service`, `@Repository` found at startup via [ClassGraph](https://github.com/classgraph/classgraph)
- **Constructor injection** — dependencies resolved automatically through primary constructors
- **Lifecycle management** — `@PostConstruct` and `@PreDestroy` hooks (supports suspend functions)
- **Scopes** — Singleton (default) and Prototype via `@Scope`
- **Polymorphic resolution** — beans resolved by interface or superclass type
- **Qualifier support** — `@Qualifier("name")` to disambiguate multiple beans of the same type
- **External beans** — pre-built instances registered via `externalBeans` map before scanning
- **Thread-safe** — concurrent access with double-check locking for singletons

### Coroutine Infrastructure

- **Virtual thread dispatchers** — Java 21 virtual threads with automatic classloader propagation
- **Per-plugin scopes** — each plugin gets its own `CoroutineScope` with `SupervisorJob`
- **Injectable** — scope and dispatchers auto-registered as beans
- **Lifecycle-managed** — scopes cancelled automatically on context shutdown
- **Shared utilities** — `withIO` and `withDefault` dispatcher helpers

### Configuration System

- **Auto-discovery** — `@ConfigFile` classes found by classpath scanning, loaded, and registered as injectable beans
- **YAML format** — human-friendly config files with comment preservation
- **Annotation-based** — `@ConfigFile`, `@ConfigName`, `@Comment`, `@Transient`
- **Type-safe loading** — automatic type conversion for primitives, collections, nested objects
- **Hot reload** — reload configs at runtime without restarting
- **Centralized management** — `ConfigManager` for loading, saving, and caching all configs

### Command System — Hytale (nexus-core)

- **Annotation-based** — `@Command` classes with `@Arg` parameters auto-discovered
- **Type-safe arguments** — map Kotlin types to Hytale arguments via `ArgumentResolver`
- **Adapters** — AsyncCommand, PlayerCommand, TargetPlayerCommand, TargetEntityCommand
- **Suspend support** — command execute methods can be suspend functions
- **Context injection** — `@Context` parameters for CommandContext, World, Store, etc.

### Command System — Paper Brigadier (nexus-paper)

- **`@Command` + `@Subcommand`** — single command class with multiple subcommand methods
- **Paper Brigadier integration** — builds Mojang `LiteralCommandNode` trees automatically
- **Multi-arg support** — bottom-up Brigadier node construction (handles `then()` build semantics correctly)
- **`@Arg` type resolution** — built-in resolvers for String, Int, Double, Float, Boolean, Player
- **`@Context` injection** — Player, CommandSender, CommandSourceStack, Server
- **`@Permission`** — per-subcommand permission checks
- **`@PlayerOnly`** — restrict subcommands to player senders
- **`@Async`** — run subcommands on coroutine scope instead of main thread
- **`BukkitDispatcher`** — coroutine dispatcher that runs on Paper's main thread (skips dispatch when already on main thread)

## Quick Start

### 1. Add Nexus to your project

```kotlin
dependencies {
    // Core DI + config + coroutines
    implementation("net.badgersmc:nexus-core:1.5.3")

    // Paper command system + BukkitDispatcher (optional)
    implementation("net.badgersmc:nexus-paper:1.5.3")
}
```

### 2. Annotate your classes

```kotlin
@Repository
class PlayerRepository(private val database: Database) {
    suspend fun findPlayer(id: UUID): PlayerStats? = withIO {
        // database query
    }
}

@Service
class PlayerService(private val repository: PlayerRepository) {
    @PostConstruct
    fun init() {
        println("PlayerService initialized!")
    }

    suspend fun getPlayer(id: UUID): PlayerStats? {
        return repository.findPlayer(id)
    }
}
```

### 3. Create a Nexus context

```kotlin
val nexus = NexusContext.create(
    basePackage = "net.example.myplugin",
    classLoader = this::class.java.classLoader,
    configDirectory = dataDirectory,
    contextName = "MyPlugin",
    externalBeans = mapOf(
        "plugin" to this,
        "database" to database
    )
)

// Retrieve auto-discovered beans
val playerService = nexus.getBean<PlayerService>()
val config = nexus.getBean<MyPluginConfig>()

// Cleanup on disable
nexus.close()
```

### 4. Register Paper commands (optional)

```kotlin
// In onEnable(), after creating the NexusContext:
nexus.registerPaperCommands(
    basePackage = "net.example.myplugin",
    classLoader = this::class.java.classLoader,
    plugin = this
)
```

## Paper Commands

Define a single `@Command` class with `@Subcommand` methods:

```kotlin
@Command(name = "sg", description = "Survival Games", permission = "sg.use")
class SGCommand(
    private val gameManager: GameManager,  // DI works!
    private val config: GameConfig
) {
    @Subcommand("join")
    @PlayerOnly
    fun join(@Context player: Player, @Arg("arena") arena: String) {
        gameManager.joinGame(player, arena)
    }

    @Subcommand("create")
    @Permission("sg.admin")
    fun create(
        @Context player: Player,
        @Arg("name") name: String,
        @Arg("radius") radius: Int  // multi-arg commands work correctly
    ) {
        gameManager.createArena(player, name, radius)
    }

    @Subcommand("stats")
    @Async
    suspend fun stats(@Context player: Player, @Arg("target") target: Player) {
        val stats = statsService.getStats(target)
        player.sendMessage(stats.format())
    }
}
```

### Supported Paper @Context types

| Type | Description |
|---|---|
| `Player` | The player who ran the command |
| `CommandSender` | Generic sender (player or console) |
| `CommandSourceStack` | Paper's raw command source |
| `Server` | The server instance |

### BukkitDispatcher

Run coroutines on the Paper main thread:

```kotlin
@Service
class MyService(private val bukkitDispatcher: BukkitDispatcher) {
    suspend fun doMainThreadWork() {
        withContext(bukkitDispatcher) {
            // Safe to call Bukkit API here
            player.teleport(location)
        }
    }
}
```

`BukkitDispatcher` skips dispatch when already on the main thread — no scheduling overhead.

## Hytale Commands

For Hytale mods, pass `commandRegistry` to `NexusContext.create()`:

```kotlin
val context = NexusContext.create(
    basePackage = "net.example.mymod",
    classLoader = this::class.java.classLoader,
    commandRegistry = this.commandRegistry  // Hytale's CommandRegistry
)
```

### Define a Hytale command

```kotlin
@Command(
    name = "heal",
    description = "Heal a player",
    permission = "admin.heal",
    type = CommandType.PLAYER
)
class HealCommand(private val healthService: HealthService) {
    fun execute(
        @Context context: CommandContext,
        @Context world: World,
        @Context store: Store<EntityStore>,
        @Context ref: Ref<EntityStore>,
        @Arg("amount", "Amount of health", required = false, defaultValue = "20") amount: Int
    ) {
        healthService.heal(store, ref, amount)
        context.sendMessage(Message.raw("Healed for $amount HP"))
    }
}
```

### Hytale command types

| Type | Thread | Context Parameters |
|---|---|---|
| `ASYNC` | Background | `CommandContext` |
| `PLAYER` | World thread | `CommandContext`, `World`, `Store<EntityStore>`, `PlayerRef`, `Ref<EntityStore>` |
| `TARGET_PLAYER` | World thread | Same as PLAYER (target player's ref) |
| `TARGET_ENTITY` | World thread | `CommandContext`, `World`, `Store<EntityStore>`, `ObjectList<Ref<EntityStore>>` |

## Configuration System

### Define a config class

```kotlin
@ConfigFile("mymod")
@Comment("My Mod Configuration")
class MyModConfig {
    @Comment("Enable debug mode")
    var debug: Boolean = false

    @ConfigName("max-players")
    @Comment("Maximum players allowed")
    var maxPlayers: Int = 100

    @Comment("Database settings")
    var database: DatabaseSettings = DatabaseSettings()

    class DatabaseSettings {
        var host: String = "localhost"
        var port: Int = 3306
    }
}
```

### Generated YAML

```yaml
# My Mod Configuration

# Enable debug mode
debug: false

# Maximum players allowed
max-players: 100

# Database settings
database:
  host: "localhost"
  port: 3306
```

### Injecting configs

```kotlin
@Service
class GameService(private val config: MyModConfig) {
    fun getMaxPlayers() = config.maxPlayers
}

@Service
class AdminService(private val configManager: ConfigManager) {
    fun reloadAll() = configManager.reloadAll()
}
```

## Coroutine Infrastructure

Nexus provides centralized coroutine support backed by Java 21 virtual threads. When you pass a `classLoader` to `NexusContext.create()`, Nexus automatically creates a virtual thread executor, coroutine dispatcher, and plugin-scoped `CoroutineScope`.

### Why this matters

Java 21 virtual threads inherit the system classloader, not the plugin's. When a coroutine continuation tries to load a plugin class on a virtual thread, it fails. Nexus wraps every virtual thread task to propagate the correct classloader automatically.

### Injecting the scope

```kotlin
@Service
class MyService(private val scope: CoroutineScope) {
    fun doAsyncWork() {
        scope.launch {
            // runs on virtual threads with correct classloader
        }
    }
}
```

### Suspend lifecycle methods

`@PostConstruct` and `@PreDestroy` methods can be suspend functions:

```kotlin
@Service
class CacheService {
    @PostConstruct
    suspend fun warmUp() { /* async initialization */ }

    @PreDestroy
    suspend fun flush() { /* async cleanup */ }
}
```

### Shutdown lifecycle

When `context.close()` is called:

1. Cancel the coroutine scope (stops all running coroutines)
2. Invoke `@PreDestroy` on all singletons
3. Shutdown the virtual thread executor
4. Clear the bean registry

## Annotations Reference

### Component Discovery

| Annotation | Description |
|---|---|
| `@Component` | Generic managed component |
| `@Service` | Service layer component |
| `@Repository` | Data access layer component |

### Dependency Injection

| Annotation | Description |
|---|---|
| `@Inject` | Mark injection points (optional for constructors) |
| `@Qualifier("name")` | Disambiguate between multiple beans of same type |

### Lifecycle

| Annotation | Description |
|---|---|
| `@PostConstruct` | Called after DI (supports suspend) |
| `@PreDestroy` | Called before shutdown (supports suspend) |
| `@Scope(ScopeType)` | SINGLETON (default) or PROTOTYPE |

### Configuration

| Annotation | Description |
|---|---|
| `@ConfigFile("name")` | Maps class to `name.yaml` |
| `@ConfigName("key")` | Custom YAML key name |
| `@Comment("text")` | YAML comment above the field |
| `@Transient` | Excluded from save/load |

### Commands (Hytale)

| Annotation | Description |
|---|---|
| `@Command(...)` | Command class with metadata (name, description, permission, aliases, type) |
| `@Arg("name", ...)` | User-provided argument |
| `@Context` | Runtime injection (CommandContext, World, Store, etc.) |

### Commands (Paper)

| Annotation | Description |
|---|---|
| `@Command(...)` | Root command with name, description, permission |
| `@Subcommand("path")` | Subcommand method (space-separated path, e.g. `"admin setup"`) |
| `@Arg("name")` | Brigadier argument with auto-resolved type |
| `@Context` | Runtime injection (Player, CommandSender, CommandSourceStack, Server) |
| `@Permission("node")` | Per-subcommand permission |
| `@PlayerOnly` | Restrict to player senders |
| `@Async` | Run on coroutine scope instead of main thread |

## Architecture

```
nexus-core/
├── core/
│   ├── NexusContext          Main container — lifecycle, bean management
│   ├── ComponentRegistry     Bean definitions + singleton cache
│   ├── BeanFactory           Constructor injection, lifecycle hooks
│   └── BeanDefinition        Bean metadata (name, type, scope, factory)
├── scanning/
│   └── ComponentScanner      ClassGraph-based classpath scanning
├── annotations/              @Component, @Service, @Repository, @Inject, etc.
├── coroutines/
│   ├── NexusDispatchers      Virtual thread executor + classloader propagation
│   ├── NexusScope            Per-plugin CoroutineScope with SupervisorJob
│   └── CoroutineExtensions   withIO, withDefault helpers
├── config/
│   ├── ConfigManager         Centralized config loading, saving, caching
│   ├── ConfigLoader          YAML serialization with reflection
│   └── Annotations           @ConfigFile, @ConfigName, @Comment, @Transient
└── commands/                 Hytale command adapters
    ├── CommandScanner         ClassGraph-based command discovery
    ├── CommandRegistry        Bridges to Hytale's CommandRegistry
    └── adapters/              Async, Player, TargetPlayer, TargetEntity

nexus-paper/
├── BukkitDispatcher           Main-thread coroutine dispatcher
├── PaperNexusExtensions       registerPaperCommands() extension
└── commands/
    ├── PaperCommandScanner    Scans @Command + @Subcommand methods
    ├── PaperCommandRegistry   Builds Brigadier trees, registers with Paper
    ├── PaperCommandDefinition Metadata for scanned commands
    ├── annotations/           @Subcommand, @Permission, @PlayerOnly, @Async
    └── arguments/
        ├── PaperArgumentResolver   Interface for type → Brigadier arg mapping
        └── PaperArgumentResolvers  Registry (String, Int, Double, Float, Boolean, Player)
```

## Requirements

- Java 21+ (for virtual threads)
- Kotlin 2.0+
- **Hytale commands**: Hytale Server API
- **Paper commands**: Paper 1.21.1+

## Shadow JAR Relocation

When shading Nexus into your plugin, relocate ClassGraph as well:

```kotlin
tasks.shadowJar {
    relocate("net.badgersmc.nexus", "com.example.mymod.shaded.nexus")
    relocate("io.github.classgraph", "com.example.mymod.shaded.classgraph")
    relocate("nonapi.io.github.classgraph", "com.example.mymod.shaded.nonapi.classgraph")
}
```

## License

MIT License — see LICENSE file for details.
