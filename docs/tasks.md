# Tasks — Nexus

**Date:** 2026-05-19
**Status:** Active — v1.5.3 → v1.6.0 (Paper 1.21.11 support)

Tags: `TDD` (failing test before code), `DOC` (markdown / template authoring), `INFRA` (manifests, CI, repo plumbing).
State legend: `[ ]` not started, `[~]` in progress, `[x]` done, `[!]` blocked.

Each task carries `References:` (REQ-IDs + spec sections consulted) and `Evidence:` (sources consulted as work proceeds).

---

## Milestone 1.6.0 — Paper 1.21.11 Support

### INFRA tasks

- [ ] **INFRA-01** — Update root build.gradle.kts Paper dependency
  References: REQ-001 (Paper Platform Support), REQ-001 (Dependency Management)
  Tag: INFRA
  Description: Update the root `build.gradle.kts` to reference Paper API `1.21.11-R0.1-SNAPSHOT`. Ensure Kotlin 2.0.21 and JVM toolchain 21 are preserved.
  Evidence: ` `

- [ ] **INFRA-02** — Update nexus-paper build.gradle.kts for Paper 1.21.11
  References: REQ-001 (Paper Platform Support), REQ-001 (Dependency Management)
  Tag: INFRA
  Description: Update `nexus-paper/build.gradle.kts` to change `io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT` to `1.21.11-R0.1-SNAPSHOT` in both `compileOnly` and `testImplementation` blocks.
  Evidence: ` `

### TDD tasks

- [ ] **TDD-01** — Verify Paper 1.21.11 API compatibility in PaperCommandRegistry
  References: REQ-002, REQ-003, REQ-004
  Tag: TDD
  Description: Update `PaperCommandRegistry.kt` if any Brigadier API signatures changed in 1.21.11. Key areas: `CommandSourceStack`, `ArgumentTypes.player()`, `LifecycleEvents.COMMANDS`, `Commands.literal()`. Write a test that verifies command registration and execution works with the new API. Run existing `PaperCommandScannerTest` to confirm green.
  Evidence: ` `

- [ ] **TDD-02** — Verify BukkitDispatcher works with Paper 1.21.11
  References: REQ-005
  Tag: TDD
  Description: Verify `BukkitDispatcher.kt` compiles and functions correctly with Paper 1.21.11. The `Bukkit.isPrimaryThread()` and `plugin.server.scheduler.runTask()` APIs should be verified against 1.21.11. Write a test if possible.
  Evidence: ` `

- [ ] **TDD-03** — Verify PaperArgumentResolvers work with Paper 1.21.11
  References: REQ-004
  Tag: TDD
  Description: Verify `PaperArgumentResolvers.kt` compiles and functions with Paper 1.21.11. Key areas: `ArgumentTypes.player()` return type, `PlayerSelectorArgumentResolver`, `StringArgumentType.word()`. Write tests for each resolver.
  Evidence: ` `

- [ ] **TDD-04** — Full test suite green after 1.21.11 update
  References: REQ-001 (Testing), REQ-002 (Testing)
  Tag: TDD
  Description: Run the full Gradle test suite (`./gradlew test`) after all code changes. All tests that passed before must still pass. Fix any failures introduced by the API update.
  Evidence: ` `

### DOC tasks

- [ ] **DOC-01** — Update README.md for Paper 1.21.11
  References: REQ-001 (Paper Platform Support)
  Tag: DOC
  Description: Update the README to reflect Paper 1.21.11 as the supported version. Update the version badge and any API references.
  Evidence: ` `

- [ ] **DOC-02** — Bump version to 1.6.0
  References: REQ-001 (Paper Platform Support)
  Tag: DOC
  Description: Update `build.gradle.kts` root `version` from `1.5.3` to `1.6.0`.
  Evidence: ` `

---

## Milestone 2.2.0 — Permissions Phase 4 (`nexus-permissions` + Gradle plugin)

Distribution decision: consumers add `nexus-permissions-gradle` via `buildscript { classpath(...) }` from JitPack; no Gradle Plugin Portal submission in this rollout. Apply with `apply(plugin = "net.badgersmc.nexus.permissions")`.

### INFRA tasks

- [x] **INFRA-20** — Add `nexus-permissions` Gradle subproject
  References: REQ-200, REQ-201, REQ-202
  Tag: INFRA
  Description: Create `nexus-permissions/` subproject mirroring the layout of `nexus-vault/` (plugins: `kotlin("jvm")`, `maven-publish`; toolchain 21; JitPack-friendly publishing). Add `include("nexus-permissions")` to `settings.gradle.kts`. Module is pure-Kotlin — no Paper/Gradle dependencies, so the DSL is testable standalone. Add `kaml-jvm` and SnakeYAML for serialization. Root project version bumps to `2.2.0` (handled in INFRA-21 follow-up where shadow wiring lands).
  Evidence: nexus-vault/build.gradle.kts (template module layout — plugins, repositories, publishing block); settings.gradle.kts (existing include pattern); build.gradle.kts root (allprojects repositories + subprojects maven-publish wiring at lines 17-50); docs/roadmap.md "Phase 4 — nexus-permissions" §150-167.

- [x] **INFRA-21** — Add `nexus-permissions-gradle` Gradle subproject
  References: REQ-203, REQ-204, REQ-205
  Tag: INFRA
  Description: Create `nexus-permissions-gradle/` subproject. Apply `kotlin("jvm")`, `java-gradle-plugin`, `maven-publish`. Declare `gradlePlugin { plugins { create("nexusPermissions") { id = "net.badgersmc.nexus.permissions"; implementationClass = "net.badgersmc.nexus.permissions.gradle.NexusPermissionsPlugin" } } }`. Depend on `project(":nexus-permissions")`. Extend root `subprojects { plugins.withId("maven-publish") }` block — or add per-module wiring — so the plugin marker artifact (`net.badgersmc.nexus.permissions.gradle.plugin`) publishes to GitHub Packages alongside the library jar (JitPack picks both up automatically). Add `include("nexus-permissions-gradle")` to `settings.gradle.kts`. Bump root `version` from `2.1.1` to `2.2.0` in this task.
  Evidence: docs/requirements.md REQ-203, REQ-204, REQ-205; Gradle `java-gradle-plugin` plugin docs https://docs.gradle.org/8.10.2/userguide/java_gradle_plugin.html (auto-publishes plugin marker as <id>.gradle.plugin when maven-publish applied); Gradle Plugin extension https://docs.gradle.org/8.10.2/javadoc/org/gradle/plugin/devel/GradlePluginDevelopmentExtension.html; existing root build.gradle.kts subprojects/maven-publish wiring lines 28-50 (GitHub Packages repository auto-injected); existing nexus-vault/build.gradle.kts as module template.

- [x] **INFRA-22** — Document consumer wiring in README
  References: REQ-203, REQ-204
  Tag: INFRA
  Description: Add a "Permissions DSL" section to the root `README.md` showing the JitPack buildscript classpath pattern (`buildscript { dependencies { classpath("com.github.BadgersMC.Nexus:nexus-permissions-gradle:v2.2.0") } }`), the `apply(plugin = ...)` form, and a minimal DSL snippet. Note that Plugin Portal submission is deferred.
  Evidence: docs/roadmap.md Phase 4 §150-167; README.md existing module table format; JitPack classpath docs https://docs.jitpack.io/building-gradle-plugins/ (Gradle plugin coords resolved via classpath from JitPack).

### TDD tasks

- [x] **TDD-200** — DSL builder produces in-memory tree
  References: REQ-200, REQ-201
  Tag: TDD
  Description: Failing JUnit5 test in `nexus-permissions/src/test/kotlin` that calls `permissionTree { node("foo.admin", default = Default.OP, description = "...") { child("reload") ; child("import", default = Default.NOT_OP) } }` and asserts the returned `PermissionTree` exposes exactly two children under `foo.admin`, both with dotted-name prefixes inherited (`foo.admin.reload`, `foo.admin.import`), and the OP/NOT_OP defaults map through. Then write the DSL: `Default` enum (OP/NOT_OP/TRUE/FALSE), `PermissionNode` data class, `PermissionTreeBuilder` + `node`/`child` DSL functions. No Paper or Gradle deps. Implementation lives in `nexus-permissions/src/main/kotlin/net/badgersmc/nexus/permissions/`.
  Evidence: docs/requirements.md REQ-200, REQ-201; docs/roadmap.md "Phase 4 — nexus-permissions" §150-167 (DSL grammar shape); Bukkit PermissionDefault doc https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/permissions/PermissionDefault.html (OP/NOT_OP/TRUE/FALSE values); Kotlin DSL annotation @DslMarker https://kotlinlang.org/docs/type-safe-builders.html (prevents nested receiver leak); org.junit.jupiter.api.Test (JUnit5 — already on nexus-permissions testImplementation); kotlin.test.assertEquals / kotlin.test.assertNotNull (kotlin stdlib test module — already on testImplementation).

- [x] **TDD-201** — YAML serializer matches paper-plugin.yml schema
  References: REQ-202
  Tag: TDD
  Description: Failing test asserting that `PermissionTreeSerializer.toYaml(tree)` returns a string matching a fixture YAML block whose top-level keys are fully-qualified node names with `default` (lowercase `op`/`not op`/`true`/`false`), optional `description`, and optional `children` keys (list of qualified names). Fixture lives in `nexus-permissions/src/test/resources/fixtures/permissions.yml`. Implement using SnakeYAML's `Yaml` with deterministic key ordering (alphabetical) so output is reproducible. Verify against the Paper docs: https://docs.papermc.io/paper/dev/plugin-yml/#permissions.
  Evidence: docs/requirements.md REQ-202; Paper plugin-yml permissions schema https://docs.papermc.io/paper/dev/plugin-yml/#permissions (top-level keyed by fully-qualified name, `default`/`description`/`children` keys, lowercase default values); SnakeYAML 2.x https://bitbucket.org/snakeyaml/snakeyaml/wiki/Documentation (org.yaml.snakeyaml.Yaml, DumperOptions for block style + key ordering); org.junit.jupiter.api.Test; kotlin.test.assertEquals.

- [x] **TDD-202** — paper-plugin.yml merger preserves other top-level keys
  References: REQ-204, REQ-205
  Tag: TDD
  Description: Failing test in `nexus-permissions` that takes a sample `paper-plugin.yml` (with `name`, `main`, `api-version`, `authors`, `dependencies`, and a stale `permissions:` block), runs `PaperPluginYmlMerger.merge(source, permissionsBlock)`, and asserts the result has the new permissions block while every other top-level key + value is byte-identical (use SnakeYAML to parse + re-emit). Add a second test running the merge twice and asserting byte-equality of the two outputs (REQ-205 idempotency). Implementation must preserve top-level key ORDER from the source (use `LinkedHashMap`).
  Evidence: docs/requirements.md REQ-204 (preserve other top-level keys byte-identical), REQ-205 (idempotent re-runs); Paper plugin-yml schema https://docs.papermc.io/paper/dev/plugin-yml/#permissions; approach — surgical string-range replacement of the `permissions:` block by indent scanning, rather than full YAML round-trip, since round-trip drops comments and whitespace that REQ-204's "byte-identical" requires. Append `permissions:` block at EOF when absent. org.junit.jupiter.api.Test, kotlin.test.assertEquals.

- [x] **TDD-203** — Gradle plugin task wires into build lifecycle
  References: REQ-203
  Tag: TDD
  Description: Failing Gradle TestKit test in `nexus-permissions-gradle/src/test/kotlin` that builds a synthetic consumer project applying the plugin + declaring a DSL in `build.gradle.kts`, runs `./gradlew processResources`, and asserts the `paper-plugin.yml` under `build/resources/main/` contains the expected `permissions:` block. Test the `generateNexusPermissions` task is registered, depends on `processResources`, and that `shadowJar` (when present) `dependsOn` it. Implementation: `NexusPermissionsPlugin : Plugin<Project>` registering a `NexusPermissionsExtension` (holds the DSL block) + a `GenerateNexusPermissionsTask` reading the extension, calling into `nexus-permissions` for serialization + merge, writing back to `build/resources/main/paper-plugin.yml`. Hook `shadowJar.dependsOn(generateNexusPermissions)` if the shadow plugin is applied.
  Evidence: docs/requirements.md REQ-203; Gradle TestKit https://docs.gradle.org/8.10.2/userguide/test_kit.html (GradleRunner, withPluginClasspath, withProjectDir, withArguments, build); org.gradle.testkit.runner.GradleRunner; org.gradle.api.Plugin, org.gradle.api.Project; org.gradle.api.DefaultTask; org.gradle.api.tasks.TaskAction; org.gradle.api.provider.Property; org.gradle.api.file.RegularFileProperty; org.junit.jupiter.api.Test, org.junit.jupiter.api.io.TempDir; kotlin.test.assertEquals, kotlin.test.assertTrue. Plugin attaches generateNexusPermissions task to consumer's `classes` lifecycle (requires java plugin); optional shadow hook via plugins.withId for com.gradleup.shadow + legacy com.github.johnrengelman.shadow.

- [x] **TDD-204** — DSL handles nested children
  References: REQ-200
  Tag: TDD
  Description: Failing test for deep nesting: `node("foo") { child("admin") { child("reload") } }` produces three permissions `foo`, `foo.admin`, `foo.admin.reload` in the serialized YAML with `foo.admin` listing `foo.admin.reload` in its `children:` block. Verifies the DSL recursion + serializer handle arbitrary depth.
  Evidence: docs/requirements.md REQ-200; PermissionNodeBuilder.child block param already supports recursion (TDD-200 impl); PermissionTreeSerializer.flatten walks node.children recursively (TDD-201 impl); org.junit.jupiter.api.Test; kotlin.test.assertEquals; kotlin.test.assertTrue. Note: this is a regression-coverage test — the behavior was correctly generalized during TDD-200/201, so the new test went green on first run. Kept as a guard against future serializer/DSL refactors silently breaking deep nesting.

---

## Task authoring rules

1. Every task has exactly ONE tag (`TDD`, `DOC`, or `INFRA`).
2. `References:` cites at least one REQ-ID from `requirements.md`.
3. `Evidence:` starts empty. It must be filled before any skill past `spec-done` will run.
4. Task size ceiling: ~1500 tokens of full briefing. If larger, split.
5. Mark state as work proceeds: `[~]` when entering `spec`; `[x]` only when `spear:refine` has cleared state to `idle`.
