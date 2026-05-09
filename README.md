<div align="center">

<img src="docs/media/banner.png" alt="Mint Banner" width="800" />

<h1>Mint</h1>

<p>🍃 <strong>A Lovingly Crafted Quality of Life Extension</strong> — Quark-style tweaks for Paper and Folia. 🍃</p>

<p>
  <img src="https://img.shields.io/badge/Minecraft-Paper%20%7C%20Folia-orange" alt="Minecraft" />
  <img src="https://img.shields.io/badge/Java-21-ea9c1b?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/github/actions/workflow/status/BuddySirJava/Mint/ci.yml?branch=main&label=CI" alt="CI" />
  <img src="https://img.shields.io/github/license/BuddySirJava/Mint?label=License&color=blue" alt="License" />
</p>

</div>

---

**Mint** adds **29** lightweight, vanilla-friendly gameplay modules for **Paper** and **Folia**. Modules can be toggled per player, work with common protection plugins, support several storage backends, and require **no client mods**.

## Table of contents

- [Features](#features)
- [Installation](#installation)
- [Commands and permissions](#commands-and-permissions)
- [Technical details](#technical-details)
- [Building from source](#building-from-source)
- [License and credits](#license-and-credits)
- [Support and contributions](#support-and-contributions)

## Features

Modules are documented by category (config keys, permissions, and previews where available):

- [Building](docs/building.md)
- [Farming](docs/farming.md)
- [Inventory](docs/inventory.md)
- [Interaction](docs/interaction.md)
- [Mobility](docs/mobility.md)
- [Transport](docs/transport.md)
- [Entity](docs/entity.md)

## Installation

1. Download the latest JAR from [Releases](https://github.com/BuddySirJava/Mint/releases).
2. Place it in your server's `plugins/` directory and restart.
3. Adjust settings in `plugins/Mint/config.yml` as needed.

**Requirements:** Paper or Folia **1.21.4+**, Java **21+**.

**Soft dependencies (optional):** PlaceholderAPI, WorldGuard, GriefPrevention, Towny, ProtocolLib, BentoBox.

## Commands and permissions

| Command | Description | Permission |
| --- | --- | --- |
| `/mint` | Open personal module GUI | `mint.gui` (default: true) |
| `/mint help` / `about` | Show plugin info | — |
| `/mint admin reload` | Reload configuration | `mint.admin`, `mint.reload` |
| `/mint admin modules` | List all module states | `mint.admin` |
| `/mint admin toggle <mod> [player]` | Toggle a module | `mint.toggle`; `mint.toggle.others` when `[player]` is set |
| `/mint admin global <mod> <on/off>` | Globally toggle a module in config | `mint.admin.global` |
| `/mint admin profile list` | List saved module presets | `mint.admin.profile` |
| `/mint admin profile save <name> [player]` | Snapshot a player's enabled modules | `mint.admin.profile` |
| `/mint admin profile load <name> [player]` | Apply a preset to a player | `mint.admin.profile` |
| `/mint admin profile delete <name>` | Remove a preset from config | `mint.admin.profile` |
| `/mint admin save` | Save config to disk | `mint.reload` |

*Bypass protection checks in supported plugins with permission `mint.bypass.protection`.*

## Technical details

- **Storage:** YAML (default), H2, MySQL, MariaDB, MongoDB.
- **Performance:** Folia-aware scheduling; async access guarded for region-thread safety.
- **Protection:** Uses WorldGuard, GriefPrevention, Towny, and BentoBox rules when present.
- **Customization:** GUI and messages via `gui.yml` and `lang.yml` (MiniMessage).
- **PlaceholderAPI:** `%mint_modules_total%` (personal module count), `%mint_modules_server_total%`, `%mint_modules_enabled_count%` (enabled for the player). `%mint_module_<key>%` reflects per-player state for personal modules and config for server-wide modules; `%mint_global_<key>%` always reflects config.
- **Tests:** `mvn test` runs the suite (includes Folia compatibility checks).

**Config migration:** Reacharound Placement still reads the legacy `modules.bedrock-bridging.enabled` key when `modules.reacharound-placement.enabled` is omitted, so older configs keep working.

## Building from source

```bash
git clone https://github.com/BuddySirJava/Mint.git
cd Mint
mvn clean package
```

Build output is under `target/`.

## License and credits

Licensed under [AGPL-3.0](LICENSE). Inspired by the [Quark](https://quarkmod.net/) mod by Vazkii and [V-Tweaks](https://mods.oitsjustjose.com/V-Tweaks/) by oitsjustjose.

## Support and contributions

Mint is free and open source. Feedback and real-world server use help the most.

- **Issues and feature ideas:** [GitHub Issues](https://github.com/BuddySirJava/Mint/issues)
- **Contribute:** [CONTRIBUTING.md](CONTRIBUTING.md)

If you'd like to support development:

- **TON:** `UQCW9EFr3jexVjVvQm-njUV-oY6bVKq6e4rZbe1D4Hcmw0sX`

