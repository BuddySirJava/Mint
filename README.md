<div align="center">

<img src="media/banner.png" alt="Mint Banner" width="800" />

# Mint

🍃 **A Lovingly Crafted Quality of Life Extension** — Quark-style tweaks for Paper and Folia. 🍃

![Minecraft](https://img.shields.io/badge/Minecraft-Paper%20%7C%20Folia-orange)
![License](https://img.shields.io/github/license/BuddySirJava/Mint?label=License&color=blue)

</div>

Mint ships **29** lightweight, vanilla-friendly gameplay modules. Each one can be toggled per-player, integrates with common protection plugins, supports multiple storage backends, and requires no client mods.

## Features

Mint ships 29 lightweight modules grouped by category:

- [Building](docs/building.md)
- [Farming](docs/farming.md)
- [Inventory](docs/inventory.md)
- [Interaction](docs/interaction.md)
- [Mobility](docs/mobility.md)
- [Transport](docs/transport.md)
- [Entity](docs/entity.md)


## Installation

1. Download the latest releases from [Releases](https://github.com/BuddySirJava/Mint/releases).
2. Drop it into your `plugins/` folder and restart.
3. Configure settings via `plugins/Mint/config.yml`.

**Requirements:** Paper 1.21.4+ Java 21+. (Folia support is currently **Experimental**)

**Optional integrations:** PlaceholderAPI, WorldGuard, GriefPrevention, Towny, and BentoBox (soft-dependencies).

## Commands & Permissions

| Command | Description | Permission |
| ----------------------------------- | ------------------------ | --------------------------- |
| `/mint` | Open personal module GUI | `mint.gui` (Default: true) |
| `/mint help` / `about` | Standard info commands | — |
| `/mint admin reload` | Reload configuration | `mint.admin`, `mint.reload` |
| `/mint admin modules` | List all module states | `mint.admin` |
| `/mint admin toggle <mod> [player]` | Toggle a module | `mint.toggle`; `mint.toggle.others` when `[player]` is set |
| `/mint admin global <mod> <on/off>` | Globally toggle a module in config | `mint.admin.global` |
| `/mint admin profile list` | List saved module presets | `mint.admin.profile` |
| `/mint admin profile save <name> [player]` | Snapshot a player's enabled modules | `mint.admin.profile` |
| `/mint admin profile load <name> [player]` | Apply a preset to a player | `mint.admin.profile` |
| `/mint admin profile delete <name>` | Remove a preset from config | `mint.admin.profile` |
| `/mint admin save` | Save config to disk | `mint.reload` |

*Bypass region protections using `mint.bypass.protection`.*

## Technical Details

- **Storage:** Supports YAML (default), H2, MySQL, MariaDB, and MongoDB.
- **Performance:** Folia-aware scheduling; async access is guarded for region-thread safety.
- **Protection:** Respects WorldGuard, GriefPrevention, Towny, and BentoBox build rules when those plugins are present.
- **Customization:** Full GUI and message customization via `gui.yml` and `lang.yml` (MiniMessage supported).
- **PlaceholderAPI:** `%mint_modules_total%` (personal modules count), `%mint_modules_server_total%`, `%mint_modules_enabled_count%` (personal enabled for the player). `%mint_module_<key>%` uses per-player state for personal modules and config-only for server-wide modules (`%mint_global_<key>%` always reflects config).
- **Tests:** `mvn test` runs unit tests (including Folia compatibility guards).

**Config note:** Reacharound Placement still honors the legacy `modules.bedrock-bridging.enabled` flag if `modules.reacharound-placement.enabled` is absent, so older configs keep working.

## Building from Source
```bash
git clone https://github.com/BuddySirJava/Mint.git
cd Mint
mvn clean package
```
## License & Credits

Licensed under [AGPL-3.0](LICENSE). Inspired by the [Quark mod](https://quarkmod.net/) by Vazkii.

## Support & Contributions

Mint is, and always will be, free and open-source. My goal is to solve long-standing server limitations that the community has dealt with for years. Simply using the plugin and providing feedback is the best support I could ask for! ❤️

If you'd like to support development further:

* **TON:** `UQCW9EFr3jexVjVvQm-njUV-oY6bVKq6e4rZbe1D4Hcmw0sX`
* **Contribute:** See [CONTRIBUTING.md](CONTRIBUTING.md)
