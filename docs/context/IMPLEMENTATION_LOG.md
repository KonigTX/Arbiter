# Implementation Log
**Date:** 2026-04-03
**Plan Reference:** PLAN.md — "Rewrite AutoClear README — Tone and Usability Pass" (all 13 steps)

## Changes Made

- `README.md:7` — Removed "Intelligent" from subtitle. Text now reads: `Ground item clearing for Minecraft servers — protects what matters, clears what doesn't.`
- `README.md:17` — Deleted cold-open italic paragraph (`Tired of clearlag plugins...`). Extra blank line also removed for clean spacing.
- `README.md:27` — Removed "automatically" from Enchanted Items bullet. Text now reads: `Any enchanted gear is protected`
- `README.md:29-30` — Inserted vanilla rarity reference table and EPIC blockquote note after the Player Proximity bullet, before the Full Mod Support subsection. Four rows: EPIC, RARE, UNCOMMON, COMMON — each with name color and specific vanilla examples. Blockquote warns that EPIC protects almost nothing and recommends RARE or UNCOMMON.
- `README.md:35` — Deleted "Works with any mod" bullet from Full Mod Support section.
- `README.md:37` — Renamed section header from `TPS-Aware Intelligence` to `TPS-Aware Clearing`.
- `README.md:38` — Removed "Automatically" from Emergency Clear bullet. Text now reads: `Triggers when TPS drops below your configured threshold`
- `README.md:40` — Removed `(why bother?)` parenthetical from Item Count Threshold bullet. Text now reads: `Skips clearing if fewer items exist than the configured minimum`
- `README.md:41` — Removed "Unlike ClearLagg" qualifier from System.gc() bullet. Text now reads: `No JVM stutter during clears`
- `README.md:48` — Renamed section header from `Beautiful Display` to `Bossbar & Display`.
- `README.md:53` — Cut second sentence from No Chat Spam bullet. Text now reads: `Clear results are broadcast once per clear.`
- `README.md:59` — Removed trailing "automatically" from Validation bullet. Text now reads: `Invalid values are clamped to safe ranges on load`
- `README.md:287` — Fixed comparison table row: header changed from `` `System.gc()` stutter `` to `` No `System.gc()` stutter ``; AutoClear cell changed from `❌ Never` to `✅`; ICL cell changed from `❌ Never` to `✅`; ClearLagg cell changed from `⚠️ Yes` to `❌`.

## Deviations from Plan

None. All 13 steps implemented exactly as specified. No code files were touched.

## Testing Notes

- Scanned finished README for banned strings: `Tired of`, `Intelligent`, `intelligently`, `Intelligence`, `why bother`, `Unlike ClearLagg` (feature bullet), `Works with any mod`, `Beautiful Display`, filler `automatically` in feature bullets — none found.
- Rarity table confirmed present between Smart Item Protection bullets and Full Mod Support subsection, with all four tiers and the EPIC blockquote note.
- Comparison table row reads `No \`System.gc()\` stutter` with `✅` for AutoClear and ICL, `❌` for ClearLagg.
- All markdown tables have consistent column counts.
- No build was run — documentation-only change.

## Open Items

None.
