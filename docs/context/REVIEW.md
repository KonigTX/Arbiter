# Code Review — Writing & Tone
**Date:** 2026-04-03
**Scope:** README.md — tone, voice, feature section quality, and rarity definition gap

## Verdict: NEEDS WORK

---

## Critical (P0)

- **README.md:28, 132** Rarity tiers (`EPIC`, `RARE`, `UNCOMMON`, `COMMON`) appear as configuration values in the Features section, the Quick Start table, and the config reference — but the README never defines what vanilla items fall into each tier. A server admin reading `"protect_rarity_minimum": "RARE"` cannot know whether their elytra, trident, or music disc is actually protected without leaving the page. This is a usability gap, not a cosmetic one.

  Fix: add a small reference table near line 28, after the first mention of rarity tiers. Minimum viable content:

  | Rarity | Name color | Vanilla examples |
  |---|---|---|
  | EPIC | Purple | Enchanted golden apple (the only vanilla EPIC item) |
  | RARE | Aqua | Elytra, trident, music discs, totem of undying, ender pearls, beacons |
  | UNCOMMON | Yellow | Saddle, name tag, golden apple, most mob-loot rare drops |
  | COMMON | White | Tools, armor, food, most crafted items — everything else |

  One important implication worth surfacing: enchanted golden apple is the only truly EPIC-rarity item in vanilla 1.21. Admins setting `"protect_rarity_minimum": "EPIC"` are effectively relying on the enchanted item and named item rules for all practical protection — the rarity rule alone will protect almost nothing at that threshold.

---

## Important (P1)

- **README.md:17** The opening sentence is the weakest line in the document: _"Tired of clearlag plugins nuking your Netherite?"_ — This is infomercial cold-open structure applied to a mod README. It assumes a pain point instead of stating a capability. The subtitle on line 6 already does this job adequately. Cut the whole sentence. Nothing needs to replace it — the subtitle and the feature table carry the opening on their own.

- **README.md:6, 17, 37** "Intelligent" / "intelligently" / "Intelligence" appear three times in the first screenful. The word signals nothing; every feature description already shows the mechanism. The section header "TPS-Aware Intelligence" (line 37) could simply be "TPS-Aware Clearing". Cut or replace all three instances.

- **README.md:27, 35, 38, 59** "Automatically" appears repeatedly as a qualifier that adds no information. Examples: "automatically protected" (line 27), "Automatically detects" (line 35), "Automatically triggers" (line 38), "applied within ~1 second, no restart needed" (line 59 already avoids it — that version is better). Each instance can either be cut or replaced with the mechanism. "Protect any mod with one line" is more useful than "Automatically detects items from Cobblemon".

- **README.md:40** _"Item Count Threshold — Skips clearing if only a few items exist (why bother?)"_ — The parenthetical is attempting to sound casual. It reads as dismissive of a feature that is genuinely useful for preventing thrashing on lightly-populated worlds. Drop the aside. The setting name and behavior are self-explanatory.

- **README.md:41** _"No `System.gc()` Calls — Unlike ClearLagg, AutoClear never causes JVM stutter"_ — The competitor callout belongs in the comparison table at the bottom where it has context. In a feature bullet it reads as defensive. A feature bullet should describe what the mod does. Rewrite as: "`System.gc()` is never called — no JVM stutter during clears." and remove "Unlike ClearLagg" from this location.

- **README.md:35** _"Works with any mod — Automatically detects items from Cobblemon, Create, and any other Fabric mod via registry namespace"_ — This bullet restates the section header ("Full Mod Support") and the three bullets above it. It exists to land on an encouraging note, which is a marketing instinct. Remove it.

- **README.md:53** _"No Chat Spam — Your chat stays clean. No more countdown messages every second."_ — Two sentences making the same point. The second adds nothing. Cut it.

---

## Suggestions (P2)

- **README.md:23-60** The Features section has 6 subsections and 23 bullet points. The Protection Priority table (line 216) does in 10 rows what 23 bullets struggle to do. Consider whether some subsections can be collapsed or cut now that the priority table exists — it is a better reference artifact than the feature list.

- **README.md:48** "Beautiful Display" as a section title is the most marketing-forward header in the document. "Bossbar & Display" or just "Display" is more direct.

- **README.md:77** The Quick Start Modes section has no header emoji while every other section does. Either add one for consistency or strip emojis from all section headers — the inconsistency is visible on a quick scan.

- **README.md:287** The comparison table row for `System.gc()` stutter uses "❌ Never" for both AutoClear and ICL, which breaks the ✅/❌ convention — ❌ in that row means "good." Reframe the row header as something positive ("No GC stutter") so ✅ is consistently the favorable state.

---

## What's Good

- The Protection Priority table (lines 219-230) is the strongest section in the document. A numbered predicate chain with first-match semantics is exactly the right format for this, and the worked example below it is concrete and specific.
- The Quick Start Modes table is well-structured. Four modes, all parameters in one row — a server admin can make a decision without reading the rest.
- The config reference is complete. The inline-comment example config is genuinely helpful, and the `<details>` collapse is the right call.
- Commands section with explicit permission nodes and OP-level fallback is exactly what a server admin needs.
- Building from Source is three lines. Good.
- The comparison table against ICL and ClearLagg avoids overclaiming. The "Partial" qualifiers on ClearLagg are more honest than a flat ❌.

---

## Action Items

1. Add a vanilla rarity reference table near line 28 — the first mention of rarity tiers. This is the only gap that creates a real misconfiguration risk. (P0)
2. Note in that table or nearby that EPIC covers only enchanted golden apple in vanilla 1.21 — admins setting `protect_rarity_minimum: EPIC` should understand what they're getting. (P0)
3. Cut the "Tired of clearlag plugins..." opening sentence (line 17) entirely. (P1)
4. Replace all three uses of "intelligent" / "intelligently" / "Intelligence" with either the mechanism or a plain noun. (P1)
5. Audit "automatically" across the Features section — cut or replace each instance with what the mechanism actually is. (P1)
6. Remove the parenthetical "(why bother?)" from line 40. (P1)
7. Remove "Unlike ClearLagg" from the `System.gc()` feature bullet (line 41) — keep the claim, drop the name. (P1)
8. Remove the "Works with any mod" bullet (line 35). (P1)
9. Cut the second sentence from "No Chat Spam" (line 53). (P1)
10. Rename "Beautiful Display" to "Display" or "Bossbar & Display". (P2)
11. Fix the comparison table `System.gc()` row so ✅ is consistently the favorable state. (P2)
