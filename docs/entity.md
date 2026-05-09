# Entity Modules

These modules modify mob and creature-specific behavior.

## Chicken Glide
- **Config key:** `modules.chicken-glide`
- **Description:** Lets players carry chickens and glide with slow falling.
- **Permission node:** `mint.module.chicken-glide` (default; override with `modules.chicken-glide.permission`)

![Chicken Glide Preview](media/modules/chicken-pickup.gif)

## Feather Pluck
- **Config key:** `modules.feather-pluck`
- **Description:** Plucks feathers from chickens with shears using cooldown and feedback cues.
- **Permission node:** `mint.module.feather-pluck` (default; override with `modules.feather-pluck.permission`)

## Vexes Die With Their Masters
- **Config key:** `modules.vexes-die-with-their-masters`
- **Description:** Removes vexes when their summoner dies.
- **Permission node:** `N/A` (server-scoped module; no per-player permission gate)
