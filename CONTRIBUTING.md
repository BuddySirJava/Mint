# Contributing to Mint

## Module lifecycle

A module is **registered** at startup in `ModuleManager.registerModules()`. It becomes **active** only when:

1. **`modules.<key>.enabled`** is `true` in `config.yml` (see `Module.isEnabledByConfig`), and  
2. **At least one online player** has that module enabled in their personal toggles.

When no players want the module, `disable()` runs and it leaves the active set. Design `enable()` / `disable()` so they can run repeatedly and leave no leaked listeners or tasks.

## The `Module` interface

Implement `ir.buddy.mint.module.Module`:

| Method | Purpose |
|--------|---------|
| `getName()` | Human-readable title (shown in GUI and `/mint admin modules`). |
| `getConfigPath()` | Must be **`modules.<kebab-key>`** (e.g. `modules.door-knock`). Used for config and for the internal module key. |
| `getDescription()` | Short line for GUI and command output. |
| `enable()` | Register listeners, start region-safe tasks, etc. |
| `disable()` | Unregister listeners (`HandlerList.unregisterAll(this)` for `Listener` impls), cancel repeating tasks, clear state. |

Optional overrides:

- **`isEnabledByConfig`** — only if the default `getBoolean(getConfigPath() + ".enabled", true)` is wrong.
- **`supportsAsyncPreparation`** / **`prepareEnable`** — use when heavy IO or CPU work must finish before `enable()` runs on the main thread (see `ModuleManager.requestModuleEnable`).

## Naming and keys

- **Config / YAML key:** `getModuleKey(module)` strips the `modules.` prefix. That key must match:
  - The block under **`modules:`** in [`src/main/resources/config.yml`](src/main/resources/config.yml) (e.g. `door-knock:`).
  - An entry under **`module-icons:`** in [`src/main/resources/gui.yml`](src/main/resources/gui.yml) if you want a custom icon (otherwise the GUI uses a default).
- **Normalization:** CLI and PlaceholderAPI matching use `ModuleManager.normalizeModuleInput` (lowercase, alphanumeric only). Display names like `"Door Knock"` still match `doorknock` and `door-knock`.
- **Class name:** `SomethingModule` in package **`ir.buddy.mint.module.impl`**.

Keep **one module class per feature** unless sub-components are clearly private helpers in the same file.

## Registration checklist

1. **Class** — `src/main/java/ir/buddy/mint/module/impl/<Name>Module.java` implementing `Module` (and `Listener` when using events).
2. **`ModuleManager.registerModules()`** — `modules.add(new YourModule(plugin));`  
   Use `MintPlugin` in the constructor only when you need APIs beyond `JavaPlugin` (e.g. `getModuleManager()`, `getGuiConfig()`). Otherwise `JavaPlugin` keeps modules easy to test.
3. **`config.yml`** — add `modules.<key>:` with at least `enabled: true/false` and any module-specific options. Defaults should match what `PluginConfigValidator` and your code expect.
4. **`gui.yml`** — add `module-icons.<key>: <MATERIAL>` for a sensible inventory icon (comment in `gui.yml` lists the pattern).
5. **Config validation** — `PluginConfigValidator` logs unknown `modules.*` keys and missing registered keys; after adding a module, keys should line up so the log stays clean on startup.

## Per-player toggles and protection

- **Per-player:** At the start of any player-visible behavior (events, schedulers tied to a player), guard with:

  `ModuleAccess.isEnabledForPlayer(plugin, this, player)`

  Use the same `plugin` reference you registered the listener with (the Mint plugin instance).

- **Building / breaking blocks:** Before changing the world on behalf of a player, call:

  `ModuleAccess.canBuild(plugin, player, location)`

  so WorldGuard / GriefPrevention rules and **`mint.bypass.protection`** are respected.

## Folia and threading (Paper / Folia)

Mint targets **Folia-compatible** scheduling. Do **not** use `Bukkit.getScheduler()` for game-state or entity work.

Use **`ir.buddy.mint.util.FoliaScheduler`**:

| Situation | API |
|-----------|-----|
| Work tied to a **block/chunk region** | `runRegion`, `runRegionLater`, `runRegionAtFixedRate` |
| Work tied to an **entity** | `runEntity`, `runEntityLater`, `runEntityAtFixedRate` |
| **Global** server tick work | `runGlobal`, `runGlobalLater`, `runGlobalAtFixedRate` |
| **Off-thread** prep (no world/entity access) | `runAsync` + then hop back with region/entity/global as appropriate |

If you repeat work every tick, store a `ScheduledTaskHandle` (or equivalent) and **cancel in `disable()`**.

## Events and listeners

- Implement `Listener`, register in `enable()` with `plugin.getServer().getPluginManager().registerEvents(this, plugin)`.
- In `disable()`, call `HandlerList.unregisterAll(this)`.
- Prefer **`@EventHandler(priority = ..., ignoreCancelled = true)`** when you should not override vanilla after another plugin cancels.
- Respect **`EquipmentSlot.HAND`** (and off-hand rules) for interact events when relevant, to avoid double firing.

## Configuration access

- Prefer **`plugin.getConfig()`** and paths under your **`getConfigPath()`** (e.g. `getConfigPath() + ".some-option"`).
- After `/mint reload`, changed keys under your module are handled by `ModuleManager.reloadChangedModules`; avoid caching config values in static fields without reload hooks.

## Recipes (special case)

If the module registers **Bukkit recipes**, follow the **`CarpetGeometryModule`** pattern: expose a method called from **`ModuleManager.registerRecipes()`** during `onEnable` **before** modules are enabled, so recipe registration order stays consistent.

## Style and quality

- Match existing code: imports, brace style, minimal comments (explain non-obvious invariants only).
- No new mandatory dependencies in `pom.xml` without discussion; prefer Paper / Bukkit APIs.
- Avoid long-lived static mutable state tied to a world; use instance fields on the module or per-player maps keyed by `UUID` with cleanup on quit if needed.

## PR checklist (modules)

- [ ] New class under `module.impl`, implements `Module` (+ `Listener` if needed).
- [ ] `ModuleManager.registerModules()` updated.
- [ ] `config.yml` section under `modules.<key>` with `enabled` and documented options.
- [ ] `gui.yml` `module-icons` entry for `<key>` (unless intentionally defaulting).
- [ ] `ModuleAccess.isEnabledForPlayer` (and `canBuild` if mutating the world) on all relevant paths.
- [ ] No raw `Bukkit.getScheduler()` for gameplay; use `FoliaScheduler`.
- [ ] `disable()` cleans listeners and repeating tasks.
- [ ] `mvn -q package` succeeds.
