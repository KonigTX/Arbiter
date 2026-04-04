## What is Arbiter?

Arbiter is a server-side Fabric mod that automatically clears ground item entities on a configurable schedule, keeping your server performant without deleting items that matter. No client install is required — players join and play normally while Arbiter handles cleanup in the background.

## Compatibility

- Server-side: Required
- Client-side: Not required — players join without installing Arbiter
- Java 21+, Fabric Loader, and Fabric API required

## Features

- **Smart Item Protection** — Named (anvil-renamed) and enchanted items are never cleared; rarity-based protection defaults to RARE and above (`protect_rarity_minimum: "RARE"`).
- **TPS-Aware Emergency Clear** — Monitors server TPS and triggers an accelerated sweep when TPS drops below `emergency_tps_threshold` (default: `15.0`), with a `emergency_clear_cooldown_seconds` (default: `60`) to prevent rapid-fire clearing.
- **Bossbar Countdown** — Color-graduated bossbar (green → yellow → red) broadcasts a `warning_start_seconds` (default: `30`) warning to all players, including late joiners, before each scheduled clear.
- **Per-Dimension Configuration** — Each dimension carries independent `clear_interval_seconds`, `item_count_threshold`, and protection overrides; unset fields inherit from global config.
- **Clear, Ordered Protection Priority** — Item fate is resolved by a deterministic 10-rule priority system: `item_overrides` BLACKLIST beats PROTECT beats `protected_items` list beats `protected_namespaces` beats rarity beats proximity. No item is cleared unexpectedly.
- **Mod Namespace Protection** — Add any mod's registry ID to `protected_namespaces` to protect all of its items with one line; assign priority tiers (`LOW` / `NORMAL` / `HIGH`) via `namespace_tiers`.
- **Hot-Reload JSON5 Config** — File changes apply within ~1 second via file-watch; `/arbiter reload` forces an immediate reload. Invalid values are clamped to safe ranges on load.

## Quick Start Modes

Arbiter ships with three built-in presets. Apply any preset with a single command — the config is rewritten and hot-reloaded automatically.

| Mode | Interval | Item Threshold | Protection Level | Command |
|---|---|---|---|---|
| `aggressive` | 60 s | 0 (always clear) | EPIC rarity only; named/enchanted/namespace disabled | `/arbiter mode aggressive` |
| `warden` | 180 s | 50 items | Named, enchanted, RARE+, namespace list | `/arbiter mode warden` |
| `calm` | 600 s | 200 items | Named, enchanted, UNCOMMON+, namespace list, extended protected items | `/arbiter mode calm` |
| `custom` | from config | from config | Hand-tuned; all values from config file | `/arbiter mode custom` |

## Commands

| Command | Effect | Permission Node |
|---|---|---|
| `/arbiter force [dimension]` | Force-clear all worlds or a specific dimension | `arbiter.force` |
| `/arbiter status` | Show TPS, time until next clear, item counts per world | `arbiter.status` |
| `/arbiter reload` | Reload config from disk without restarting | `arbiter.reload` |
| `/arbiter stats` | View session statistics (clears run, items removed) | `arbiter.stats` |
| `/arbiter dryrun [dimension]` | Preview what would be cleared without removing anything | `arbiter.dryrun` |
| `/arbiter testbar [seconds]` | Run a bossbar countdown test | `arbiter.testbar` |
| `/arbiter mode <preset>` | Apply a preset: aggressive / warden / calm / custom | `arbiter.mode` |

LuckPerms integration is automatic — falls back to OP level if LuckPerms is not installed.

`dryrun` output example: `Would CLEAR: 47 items — Would protect: 12`

## Configuration

Arbiter works out of the box. `config/arbiter.json5` is generated on first run and is fully annotated — every setting is documented inline.

| Setting | Default | Description |
|---|---|---|
| `clear_interval_seconds` | `300` | Time between scheduled clears |
| `warning_start_seconds` | `30` | Bossbar countdown starts this many seconds before clear |
| `item_count_threshold` | `100` | Skip clear if fewer item entities than this exist |
| `emergency_tps_threshold` | `15.0` | Trigger emergency clear when TPS drops below this; `0` disables |
| `emergency_clear_cooldown_seconds` | `60` | Minimum seconds between emergency clears |
| `protect_named_items` | `true` | Protect anvil-renamed items |
| `protect_enchanted_items` | `true` | Protect enchanted items |
| `protect_rarity_minimum` | `"RARE"` | Minimum vanilla rarity to auto-protect (EPIC / RARE / UNCOMMON / COMMON) |
| `player_proximity_radius` | `10` | Protect items within N blocks of any player |
| `protected_namespaces` | `["mod_namespace"]` | Protect all items from listed mod namespaces |
| `protected_items` | `["minecraft:nether_star", ...]` | Exact item IDs always protected |
| `item_overrides` | `{"minecraft:dirt": "BLACKLIST"}` | Per-item PROTECT or BLACKLIST — overrides all other rules |
| `bossbar_enabled` | `true` | Show bossbar countdown |
| `actionbar_warnings_enabled` | `false` | Show countdown in actionbar alongside bossbar |
| `broadcast_clear_results` | `true` | Announce clear results to all players |

> **Note:** `protect_rarity_minimum: "EPIC"` protects almost nothing in vanilla — enchanted golden apple is the only EPIC item. Use `"RARE"` or `"UNCOMMON"` for meaningful protection.

See the full example config on the [GitHub README](https://github.com/KonigTX/Arbiter#readme).

## How It Compares

| Feature | Arbiter | ICL | ClearLagg |
|---|---|---|---|
| Bossbar countdown | ✅ Color-graduated | ❌ Chat only | ❌ Chat only |
| Named item protection | ✅ | ❌ | ⚠️ Partial |
| Enchanted item protection | ✅ | ❌ | ⚠️ Partial |
| Rarity-based protection | ✅ | ❌ | ❌ |
| Mod namespace protection | ✅ | ❌ | ❌ |
| Per-item PROTECT/BLACKLIST | ✅ | ❌ | ⚠️ Whitelist only |
| TPS emergency clear | ✅ With cooldown | ❌ | ⚠️ No cooldown |
| Item count threshold | ✅ | ❌ | ❌ |
| Per-dimension config | ✅ | ❌ | ❌ |
| Hot-reload config | ✅ File watch + command | ❌ | ❌ |
| Player proximity protection | ✅ | ❌ | ❌ |
| No `System.gc()` stutter | ✅ | ✅ | ❌ |
| Silent clears (no sounds) | ✅ | ❌ Noteblock beep | ✅ |
| Server-side only | ✅ | ✅ | ✅ (Bukkit) |

## Requirements

- Minecraft 1.21.1
- Fabric Loader (latest)
- Fabric API
- Java 21+
- LuckPerms (optional — falls back to OP levels)

## FAQ

**Is Arbiter server-side only?**
Yes. Players connect to a server running Arbiter without installing anything client-side.

**Will Arbiter be backported to older Minecraft versions?**
No backport is currently planned. Watch the GitHub repository for future version announcements.

**Does Arbiter conflict with ClearLagg, ICL, or other item-clearing mods?**
Running two item-clearing mods simultaneously is redundant and not recommended. Arbiter is otherwise compatible with all Fabric mods.

**Can I protect items from modded dimensions or custom registries?**
Yes — add any mod's registry namespace to `protected_namespaces`. Per-item overrides via `item_overrides` work for any item ID regardless of mod.

---

[GitHub Issues](https://github.com/KonigTX/Arbiter/issues) | [MIT License](LICENSE)
