# Vault — Arbiter (Fabric Mod)

## Directory Structure
```
autoclear/
├── build.gradle / gradle.properties / settings.gradle
├── src/main/java/com/bhd/autoclear/
│   ├── AutoClearMod.java              — entry point, wires all subsystems
│   ├── commands/AutoClearCommands.java — /autoclear force|status|reload|stats
│   ├── config/
│   │   ├── AutoClearConfig.java       — POJO with all config fields + defaults
│   │   ├── ConfigManager.java         — Jankson JSON5 load/save, hot-reload watcher
│   │   ├── DimensionOverride.java     — per-dimension config partial
│   │   ├── ItemTier.java              — enum: PROTECT/LOW/NORMAL/HIGH/BLACKLIST
│   │   └── ResolvedConfig.java        — merged global+dimension config snapshot
│   ├── core/
│   │   ├── ClearanceManager.java      — tick orchestrator, schedules/executes clears
│   │   ├── ItemFilters.java           — 10-rule predicate chain (shouldProtect)
│   │   ├── ItemScanner.java           — world scan → ScanResult
│   │   ├── ScanResult.java            — holds toRemove + totalFound
│   │   ├── StatsTracker.java          — session statistics
│   │   └── TpsTracker.java            — MSPT sampling → TPS float
│   └── display/BossBarHandler.java    — ServerBossBar countdown + color graduation
└── src/main/resources/
    ├── fabric.mod.json
    └── assets/autoclear/lang/en_us.json
```

## Project Summary
Fabric 1.21.1 server-side mod that clears ground item entities on a configurable timer. Uses a 10-rule priority chain to protect named/enchanted/rare/nearby items. Shows a color-graduated bossbar countdown. Supports TPS-aware emergency clears, per-dimension overrides, hot-reload JSON5 config, and session stats.

## Tech Stack
- Minecraft 1.21.1, Fabric Loader 0.16.2, Fabric API 0.102.1
- Yarn mappings 1.21.1+build.3
- Jankson (JSON5), Cloth Config (GUI), fabric-permissions-api (LuckPerms bridge)
- Java 21 (compiled), Java 22 (runtime on this machine)

## Key Config Defaults
- `clearIntervalSeconds`: 300 (5 min)
- `warningStartSeconds`: 30
- `itemCountThreshold`: 100  ← WHY user's 41 items didn't trigger a clear
- `playerProximityRadius`: 10
- `protectedNamespaces`: ["cobblemon"]
- `protectedItems`: [nether_star, elytra, dragon_egg, totem]
- `itemOverrides`: dirt/cobblestone=BLACKLIST, netherite_ingot=PROTECT

## Known Issue (User's Problem)
User dropped 41 items; threshold is 100. Clear was skipped with log: "skipped (found 41, threshold 100)". Not a bug — working as designed. Testing requires either ≥100 items or lowering threshold.
