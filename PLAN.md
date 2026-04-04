# Plan: Rewrite AutoClear README — Tone and Usability Pass
**Date:** 2026-04-03
**Status:** READY FOR IMPLEMENTATION

## Objective
Revise `README.md` to remove infomercial language, cut filler qualifiers, and add a vanilla rarity reference table that closes a real misconfiguration risk for server admins. Only `README.md` is touched.

## Context Summary
- **From Review:** 1 P0 (rarity tiers appear as config values but are never defined — admin cannot know whether elytra or tridents are protected without leaving the page), 8 P1 issues (infomercial cold-open, "intelligent" family x3, "automatically" as filler qualifier x4, "(why bother?)" parenthetical, "Unlike ClearLagg" in a feature bullet, redundant "Works with any mod" bullet, double-sentence "No Chat Spam"), 2 P2 issues addressed per task brief (rename "Beautiful Display", fix ✅/❌ framing in comparison table).
- **From Research:** N/A
- **Constraints:** Only `README.md` may change. No code files. Preserve all existing markdown structure (emoji headers, table formats, `<details>` block, badges).

## Implementation Steps

1. **README.md L6** — Remove "Intelligent" from the subtitle. New text: `**Ground item clearing for Minecraft servers — protects what matters, clears what doesn't.**`

2. **README.md L17** — Delete the entire italicized cold-open paragraph: `*Tired of clearlag plugins nuking your Netherite? AutoClear uses a 10-rule priority system to intelligently decide what stays and what goes — with full mod support, TPS-aware emergency clearing, and a bossbar countdown your players will actually appreciate.*`. Delete the line entirely — nothing replaces it. The subtitle and feature table carry the opening.

3. **README.md L27** — Remove "automatically" from the enchanted items bullet. New text: `**Enchanted Items** — Any enchanted gear is protected`

4. **README.md L28 — insert rarity table** — After the `**Player Proximity**` bullet (the last bullet in the Smart Item Protection list), insert the following block before the next `###` subsection:

   ```markdown
   **Vanilla rarity reference (Minecraft 1.21.1):**

   | Rarity | Name color | Vanilla examples |
   |---|---|---|
   | EPIC | Gold | Enchanted golden apple — the only vanilla EPIC item |
   | RARE | Aqua | Elytra, trident, music discs, totem of undying, ender pearls, beacon |
   | UNCOMMON | Yellow | Saddle, name tag, golden apple, most mob-loot rare drops |
   | COMMON | White | Tools, armor, food, most crafted items — everything else |

   > **Note:** `"protect_rarity_minimum": "EPIC"` protects almost nothing by rarity alone — enchanted golden apple is the only vanilla EPIC item. Use `RARE` or `UNCOMMON` for meaningful rarity-based protection.
   ```

5. **README.md L35** — Delete the entire bullet: `**Works with any mod** — Automatically detects items from Cobblemon, Create, and any other Fabric mod via registry namespace`. It restates the section header and the three bullets above it.

6. **README.md L37** — Rename section header from `### ⚡ TPS-Aware Intelligence` to `### ⚡ TPS-Aware Clearing`

7. **README.md L38** — Remove "Automatically" from the emergency clear bullet. New text: `**Emergency Clear** — Triggers when TPS drops below your configured threshold`

8. **README.md L40** — Remove the `(why bother?)` parenthetical. New text: `**Item Count Threshold** — Skips clearing if fewer items exist than the configured minimum`

9. **README.md L41** — Remove "Unlike ClearLagg" from the `System.gc()` feature bullet. New text: `` **No `System.gc()` Calls** — No JVM stutter during clears ``

10. **README.md L48** — Rename section header from `### 📊 Beautiful Display` to `### 📊 Bossbar & Display`

11. **README.md L53** — Cut the second sentence from "No Chat Spam". New text: `**No Chat Spam** — Clear results are broadcast once per clear.`

12. **README.md L59** — Remove "automatically" as a trailing filler from the Validation bullet. New text: `**Validation** — Invalid values are clamped to safe ranges on load`

13. **README.md L287** — Fix the comparison table row so ✅ is consistently the favorable state. Change row header from `` `System.gc()` stutter `` to `` No `System.gc()` stutter ``. Change AutoClear cell from `❌ Never` to `✅`, ICL cell from `❌ Never` to `✅`, ClearLagg cell from `⚠️ Yes` to `❌`.

## File Inventory

| File | Action | Lines/Functions Affected |
|------|--------|------------------------|
| `README.md` | MODIFY | L6 (subtitle), L17 (delete cold-open), L27 (enchanted bullet), L28+ (insert rarity table after Smart Item Protection bullets), L35 (delete "Works with any mod" bullet), L37 (rename section header), L38 (emergency clear bullet), L40 (item count threshold bullet), L41 (System.gc() bullet), L48 (rename section header), L53 (No Chat Spam bullet), L59 (Validation bullet), L287 (comparison table row) |

## Architecture Decisions
- **Rarity table placement — after bullet list, before next subsection:** Inserting it inline within the bullet list would break the list structure. Placing it after the last bullet in "Smart Item Protection" and before "Full Mod Support" is the natural reading position — an admin reading about rarity protection will land there immediately.
- **EPIC note as blockquote:** The implication that EPIC protects almost nothing is operationally important and easy to miss in a table cell. A `>` blockquote after the table gives it appropriate visual weight without adding a new section.
- **"No Chat Spam" replacement sentence:** The original second sentence ("No more countdown messages every second.") adds no information. The replacement ("Clear results are broadcast once per clear.") states the actual behavior and is more useful than silence.
- **P2 items included:** The task brief explicitly requests "Beautiful Display" rename and comparison table fix, so both are treated as required.

## Testing Strategy
- Scan the finished README for: `Tired of`, `Intelligent`, `intelligently`, `Intelligence`, `why bother`, `Unlike ClearLagg` (in the feature bullet), `Works with any mod`, `Beautiful Display`, `automatically` in feature bullets (verify each remaining instance is mechanistic, not filler).
- Confirm rarity table appears between the Smart Item Protection bullet list and the Full Mod Support subsection.
- Confirm comparison table row reads `No \`System.gc()\` stutter` with `✅` for AutoClear and ICL, `❌` for ClearLagg.
- Confirm all markdown tables have consistent column counts and render without breaks.

## Out of Scope
- No changes to any source code files, gradle files, or config files.
- No collapsing or removing feature subsections (P2 suggestion — not requested).
- No emoji audit or strip across section headers (P2 inconsistency — not requested).
- Do not modify the Protection Priority table (lines 218-230) — it is the strongest section in the document.
- Do not add new sections or files.

## Acceptance Criteria
- [ ] Cold-open sentence (`Tired of clearlag plugins...`) is deleted
- [ ] "Intelligent" / "intelligently" / "Intelligence" do not appear in the document
- [ ] "automatically" is removed from every feature bullet where it is a filler qualifier (enchanted items, emergency clear, validation)
- [ ] `(why bother?)` parenthetical is removed from the Item Count Threshold bullet
- [ ] "Unlike ClearLagg" is removed from the `System.gc()` feature bullet
- [ ] "Works with any mod" bullet is deleted
- [ ] "No Chat Spam" bullet has one sentence
- [ ] Section header "Beautiful Display" is renamed to "Bossbar & Display"
- [ ] Vanilla rarity reference table is present after the Smart Item Protection bullet list with all four tiers and example items matching the 1.21.1 values specified in the task brief
- [ ] Rarity table is followed by a note that EPIC covers only enchanted golden apple and advises using RARE or UNCOMMON for meaningful protection
- [ ] Comparison table row is reframed as "No `System.gc()` stutter" with ✅ for AutoClear and ICL, ❌ for ClearLagg
