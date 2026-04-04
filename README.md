<div align="center">

# AutoClear

### Smarter than `/kill @e[type=item]`

**Intelligent ground item clearing for Minecraft servers — protects what matters, clears what doesn't.**

[![Fabric](https://img.shields.io/badge/loader-Fabric-63432C?style=for-the-badge&logo=fabricmc&logoColor=white)](https://fabricmc.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=for-the-badge&logo=minecraft&logoColor=white)](https://minecraft.net)
[![Environment](https://img.shields.io/badge/environment-Server--Side-blue?style=for-the-badge)](.)
[![License](https://img.shields.io/badge/license-MIT-green?style=for-the-badge)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)

---

*Tired of clearlag plugins nuking your Netherite? AutoClear uses a 10-rule priority system to intelligently decide what stays and what goes — with full mod support, TPS-aware emergency clearing, and a bossbar countdown your players will actually appreciate.*

</div>

---

## ✨ Features

### 🛡️ Smart Item Protection
- **Named Items** — Anvil-renamed items are never cleared
- **Enchanted Items** — Any enchanted gear is automatically protected
- **Rarity Tiers** — Protects Rare and Epic items by default (configurable threshold)
- **Player Proximity** — Items near players are safe (configurable radius)

### 🔌 Full Mod Support
- **Namespace Protection** — Protect entire mods with one line (e.g., all `cobblemon` items)
- **Per-Item Overrides** — PROTECT or BLACKLIST specific items, overriding all other rules
- **Namespace Tiers** — Assign clearing priority per mod: `LOW`, `NORMAL`, `HIGH`
- **Works with any mod** — Automatically detects items from Cobblemon, Create, and any other Fabric mod via registry namespace

### ⚡ TPS-Aware Intelligence
- **Emergency Clear** — Automatically triggers when TPS drops below your threshold
- **Cooldown System** — Prevents rapid-fire emergency clears during sustained lag
- **Item Count Threshold** — Skips clearing if only a few items exist (why bother?)
- **No `System.gc()` Calls** — Unlike ClearLagg, AutoClear never causes JVM stutter

### 🌍 Per-Dimension Configuration
- **Independent Timers** — Nether clears every 3 minutes while Overworld runs every 5
- **Dimension Overrides** — Different thresholds, intervals, and protection rules per world
- **Full Inheritance** — Omitted fields fall back to global defaults

### 📊 Beautiful Display
- **Bossbar Countdown** — Clean, animated countdown visible to all players
- **Color Graduation** — Green → Yellow → Red as the timer approaches zero
- **Late-Join Support** — Players who join mid-countdown see the bar immediately
- **Optional Actionbar** — Enable actionbar warnings alongside or instead of the bossbar
- **No Chat Spam** — Your chat stays clean. No more countdown messages every second.

### 🔧 Hot-Reload Config
- **JSON5 with Comments** — Every setting is explained inline, right in the config file
- **Edit While Running** — Changes apply within ~1 second, no restart needed
- **`/autoclear reload`** — Or reload manually with a command
- **Validation** — Invalid values are clamped to safe ranges automatically

---

## 📦 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.1
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Drop `autoclear-x.x.x.jar` into your server's `mods/` folder
4. Start the server — `config/autoclear.json5` is generated automatically
5. Edit the config to taste, or use `/autoclear reload`

> **Note:** AutoClear is server-side only. Players do not need to install anything.

---

## Quick Start Modes

Not sure where to start with configuration? AutoClear ships with three built-in presets that cover the most common server styles. Apply any preset with a single command — the config is rewritten automatically and hot-reloaded within ~1 second.

| Mode | Interval | Item Threshold | Protection Level | Proximity / Emergency TPS | Command |
|---|---|---|---|---|---|
| `aggressive` | 60 s | 0 (always clear) | EPIC rarity only — named, enchanted, namespace lists all disabled | 2 blocks / 18.0 TPS | `/autoclear mode aggressive` |
| `warden` | 180 s | 50 items | Named, enchanted, RARE+ rarity, Cobblemon namespace | 8 blocks / 15.0 TPS | `/autoclear mode warden` |
| `calm` | 600 s | 200 items | Named, enchanted, UNCOMMON+ rarity, Cobblemon + Create namespaces, extra protected items | 20 blocks / 8.0 TPS | `/autoclear mode calm` |
| `custom` | (from config) | (from config) | Hand-tuned — preset is a no-op; all values come from your config file | (from config) | `/autoclear mode custom` |

Applying a preset writes only `{ "mode": "<name>" }` to `config/autoclear.json5`. Preset values are resolved in memory at load time, so upgrading the mod always picks up the latest preset definitions. Switch back to manual tuning at any time by editing the config file and running `/autoclear reload`.

---

## 💻 Commands

```
/autoclear force [dimension]    Force-clear all worlds (or a specific dimension)
/autoclear status               Show TPS, time until next clear, item counts per world
/autoclear reload               Reload config from disk without restarting
/autoclear stats                View session statistics (clears run, items removed)
/autoclear dryrun [dimension]   Preview what would be cleared without removing anything
/autoclear testbar [seconds]    Run a bossbar countdown test (default: warning_start_seconds)
/autoclear mode <preset>        Apply a preset: aggressive | warden | calm | custom
```

*`dryrun` output example: `Would CLEAR: 47 items — Would protect: 12`*

### Permissions

| Permission Node | Default OP Level | Description |
|---|---|---|
| `autoclear.force` | 4 | Force an immediate clear |
| `autoclear.status` | 2 | View clear timers and TPS |
| `autoclear.reload` | 4 | Reload configuration |
| `autoclear.stats` | 2 | View session statistics |
| `autoclear.dryrun` | 2 | Preview a clear without removing items |
| `autoclear.testbar` | 4 | Run a bossbar countdown test |
| `autoclear.mode` | 4 | Apply a built-in preset mode |

> Permissions integrate with [LuckPerms](https://luckperms.net/) automatically. Falls back to OP level when LuckPerms is not installed.

---

## ⚙️ Configuration

AutoClear generates a fully-commented `config/autoclear.json5` on first run. Here are the key options:

| Setting | Default | Description |
|---|---|---|
| `clear_interval_seconds` | `300` | Time between scheduled clears |
| `warning_start_seconds` | `30` | Bossbar appears this many seconds before clear |
| `item_count_threshold` | `100` | Skip clear if fewer items than this |
| `emergency_tps_threshold` | `15.0` | Auto-clear when TPS drops below this (`0` = off) |
| `protect_named_items` | `true` | Protect anvil-renamed items |
| `protect_enchanted_items` | `true` | Protect enchanted items |
| `protect_rarity_minimum` | `"RARE"` | Minimum vanilla rarity to auto-protect |
| `player_proximity_radius` | `10` | Don't clear items within N blocks of players |
| `protected_namespaces` | `["cobblemon"]` | Protect all items from these mods |
| `bossbar_enabled` | `true` | Show the bossbar countdown |
| `emergency_clear_cooldown_seconds` | `60` | Minimum seconds between emergency clears |
| `protected_items` | `["minecraft:nether_star", ...]` | Exact item IDs that are always protected |
| `item_overrides` | `{"minecraft:dirt": "BLACKLIST", ...}` | Per-item PROTECT or BLACKLIST — overrides all other rules |
| `actionbar_warnings_enabled` | `false` | Show countdown in actionbar alongside or instead of bossbar |
| `broadcast_clear_results` | `true` | Announce clear results to all players |

<details>
<summary><b>📄 Full Example Config</b> (click to expand)</summary>

```json5
// AutoClear configuration — changes are hot-reloaded automatically
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
    "cobblemon"
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

---

## 🛡️ Protection Priority System

AutoClear evaluates every ground item through a **10-rule predicate chain**. The first matching rule wins:

| Priority | Rule | Effect |
|---|---|---|
| 1 | `item_overrides` → BLACKLIST | Always clear — overrides everything |
| 2 | `item_overrides` → PROTECT | Always protect — overrides everything |
| 3 | `protected_items` list | Protect (exact item ID match) |
| 4 | `protected_namespaces` | Protect (entire mod namespace) |
| 5 | `namespace_tiers` → PROTECT | Protect (namespace-level tier) |
| 6 | Vanilla Rarity check | Protect if rarity >= configured minimum |
| 7 | Enchanted item | Protect if `protect_enchanted_items: true` |
| 8 | Custom name (anvil) | Protect if `protect_named_items: true` |
| 9 | Player proximity | Protect if within radius of any player |
| 10 | Default | **Clear** |

> **Example:** A `cobblemon:master_ball` dropped on the ground is protected by Rule 4 (namespace). But `minecraft:cobblestone` marked as `BLACKLIST` in `item_overrides` is cleared by Rule 1 — even if it's enchanted.

---

## 🛠️ Compatibility

| | |
|---|---|
| **Minecraft** | 1.21.1 |
| **Mod Loader** | Fabric (Fabric API required) |
| **Environment** | Server-side only — no client install needed |
| **Java** | 21+ |
| **Permissions** | [LuckPerms](https://luckperms.net/) (optional — falls back to OP levels) |
| **Config GUI** | [Mod Menu](https://modrinth.com/mod/modmenu) (optional) |

### Mod Compatibility
AutoClear works with **any Fabric mod** out of the box. Protect any mod's items by adding its namespace to `protected_namespaces`:

```json5
"protected_namespaces": [
  "cobblemon",   // All Cobblemon items (Poke Balls, berries, etc.)
  "create",      // All Create mod items
  "ae2",         // Applied Energistics
  "mythicmetals" // Mythic Metals
]
```

---

## 🔨 Building from Source

```bash
git clone https://github.com/KonigTX/Arbiter.git
cd autoclear
./gradlew build
```

Output: `build/libs/autoclear-1.1.0.jar`

---

## 📊 How It Compares

| Feature | AutoClear | ICL | ClearLagg |
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
| `System.gc()` stutter | ❌ Never | ❌ Never | ⚠️ Yes |
| Server-side only | ✅ | ✅ | ✅ (Bukkit) |

---

## 📜 License

[MIT](LICENSE) — use it, modify it, ship it.

---

<div align="center">

**Built with Fabric API, Cloth Config, and Jankson**

*Made by [KonigTX](https://github.com/KonigTX)*

</div>
