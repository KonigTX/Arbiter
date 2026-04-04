<div align="center">

[![Fabric](https://img.shields.io/badge/loader-Fabric-63432C?style=for-the-badge&logo=fabricmc&logoColor=white)](https://fabricmc.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://minecraft.net)
[![Environment](https://img.shields.io/badge/environment-Server--Side-blue?style=for-the-badge)](.)
[![License](https://img.shields.io/badge/license-MIT-green?style=for-the-badge)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)

</div>

## What is Arbiter?

Arbiter is an open-source, server-side Fabric mod that automatically clears ground item entities on a configurable schedule — no client install required and vanilla game mechanics unchanged. It replaces blunt `/kill @e[type=item]` scripts with intelligent item clear logic: scheduled clears, TPS-aware emergency sweeps, per-dimension auto clear rules, and a protection system that ensures item despawn never takes something that matters.

## Compatibility

| Side | Status | Notes |
|---|---|---|
| Server | Required | All features run server-side only |
| Client | Not needed | Players do not need to install Arbiter to join |
| Java | 21+ required | Fabric Loader + Fabric API also required |

## Features

- **Smart Item Protection** — Named (anvil-renamed) and enchanted items are never cleared; rarity-based protection defaults to RARE and above (`protect_rarity_minimum: "RARE"`).
- **TPS-Aware Emergency Clear** — Monitors server TPS and triggers an accelerated sweep when TPS drops below `emergency_tps_threshold` (default: `15.0`), with a `emergency_clear_cooldown_seconds` (default: `60`) to prevent rapid-fire clearing.
- **Bossbar Countdown** — Color-graduated bossbar (green → yellow → red) broadcasts a `warning_start_seconds` (default: `30`) warning to all players, including late joiners, before each scheduled clear.
- **Per-Dimension Configuration** — Each dimension carries independent `clear_interval_seconds`, `item_count_threshold`, and protection overrides; unset fields inherit from global config.
- **10-Rule Protection Priority Chain** — Item fate is resolved by a deterministic chain: `item_overrides` BLACKLIST beats PROTECT beats `protected_items` list beats `protected_namespaces` beats rarity beats proximity. No item is cleared unexpectedly.
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

| Command | Effect | Permission Node | Default OP Level |
|---|---|---|---|
| `/arbiter force [dimension]` | Force-clear all worlds or a specific dimension | `arbiter.force` | 4 |
| `/arbiter status` | Show TPS, time until next clear, item counts per world | `arbiter.status` | 2 |
| `/arbiter reload` | Reload config from disk without restarting | `arbiter.reload` | 4 |
| `/arbiter stats` | View session statistics (clears run, items removed) | `arbiter.stats` | 2 |
| `/arbiter dryrun [dimension]` | Preview what would be cleared without removing anything | `arbiter.dryrun` | 2 |
| `/arbiter testbar [seconds]` | Run a bossbar countdown test | `arbiter.testbar` | 4 |
| `/arbiter mode <preset>` | Apply a preset: aggressive / warden / calm / custom | `arbiter.mode` | 4 |

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

<details>
<summary><b>Full Example Config</b> (click to expand)</summary>

```json5
// Arbiter configuration — changes are hot-reloaded automatically
{
  // ── Timing ──────────────────────────────────────────────────
  "clear_interval_seconds": 300,
  "warning_start_seconds": 30,

  // ── Thresholds ──────────────────────────────────────────────
  "item_count_threshold": 100,
  "emergency_tps_threshold": 15.0,
  "emergency_clear_cooldown_seconds": 60,

  // ── Item Protection ─────────────────────────────────────────
  "protect_named_items": true,
  "protect_enchanted_items": true,
  "protect_rarity_minimum": "RARE",
  "player_proximity_radius": 10,

  // ── Mod & Item Lists ────────────────────────────────────────
  "protected_namespaces": [
    "your_mod"
  ],
  "protected_items": [
    "minecraft:nether_star",
    "minecraft:elytra",
    "minecraft:dragon_egg",
    "minecraft:totem_of_undying"
  ],
  "namespace_tiers": {
    "minecraft": "NORMAL",
    "create": "NORMAL"
  },

  // Per-item overrides — PROTECT or BLACKLIST specific items
  // These override ALL other rules
  "item_overrides": {
    "minecraft:dirt": "BLACKLIST",
    "minecraft:cobblestone": "BLACKLIST",
    "minecraft:netherite_ingot": "PROTECT"
  },

  // ── Display ─────────────────────────────────────────────────
  "bossbar_enabled": true,
  "bossbar_style": "NOTCHED_10",
  "bossbar_yellow_threshold_seconds": 15,
  "bossbar_red_threshold_seconds": 5,
  "actionbar_warnings_enabled": false,

  // ── Per-Dimension Overrides ─────────────────────────────────
  "dimensions": {
    "minecraft:the_nether": {
      "clear_interval_seconds": 180,
      "item_count_threshold": 50
    },
    "minecraft:the_end": {
      "clear_interval_seconds": 120
    }
  },

  // ── Logging ─────────────────────────────────────────────────
  "log_clears_to_console": true,
  "broadcast_clear_results": true
}
```

</details>

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
