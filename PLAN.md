# Plan: Eradicate BHD + Full AutoClear Sweep
**Date:** 2026-04-04
**Status:** READY FOR EXECUTION

## Objective
1. Remove all `bhd`/`BHD` traces — rename package `com.bhd.arbiter` → `com.arbiter`, delete the now-empty `bhd` directory tree.
2. Rename all three `AutoClear*` classes to `Arbiter*` to eliminate remaining `autoclear`/`AutoClear` identifiers.
3. Fix `settings.gradle` project name (`autoclear` → `arbiter`).
4. Verify build passes, commit, push.

## Full Change Inventory

### A. Directory Move (git mv)
| From | To |
|---|---|
| `src/main/java/com/bhd/arbiter/` | `src/main/java/com/arbiter/` |

After move, delete empty `src/main/java/com/bhd/` tree (git rm will handle it automatically since it'll be empty).

### B. Class Renames (file rename + content)
| Old filename / class | New filename / class | Referenced in |
|---|---|---|
| `AutoClearMod.java` / `AutoClearMod` | `ArbiterMod.java` / `ArbiterMod` | itself, `AutoClearCommands.java` (now ArbiterCommands), `fabric.mod.json` |
| `AutoClearCommands.java` / `AutoClearCommands` | `ArbiterCommands.java` / `ArbiterCommands` | `AutoClearMod.java` (now ArbiterMod) |
| `AutoClearConfig.java` / `AutoClearConfig` | `ArbiterConfig.java` / `ArbiterConfig` | `ConfigManager.java`, `DimensionOverride.java`, `ResolvedConfig.java`, `ClearanceManager.java` (indirect), `TpsTracker.java` (javadoc) |

### C. Package String Replacements (all 14 .java files)
Replace `com.bhd.arbiter` → `com.arbiter` in every package declaration and import.

### D. Class Name String Replacements (all .java files)
- `AutoClearMod` → `ArbiterMod`
- `AutoClearCommands` → `ArbiterCommands`
- `AutoClearConfig` → `ArbiterConfig`

### E. Build/Metadata Files
| File | Change |
|---|---|
| `gradle.properties` | `maven_group=com.bhd.arbiter` → `maven_group=com.arbiter` |
| `fabric.mod.json` | `com.bhd.arbiter.AutoClearMod` → `com.arbiter.ArbiterMod` |
| `settings.gradle` | `rootProject.name = "autoclear"` → `rootProject.name = "arbiter"` |

### F. Javadoc Comment Cleanup
- `AutoClearCommands.java` (now ArbiterCommands): Javadoc tree listing still references `autoclear.force`, `autoclear.status`, `autoclear.reload`, `autoclear.stats` → update to `arbiter.*`
- All `{@code AutoClearMod}`, `{@link AutoClearConfig}`, `{@link AutoClearCommands}` refs in javadoc

## Execution Order
1. `git mv` directory
2. `git mv` each of the 3 renamed Java files within the new path
3. Sed-replace all `com.bhd.arbiter` → `com.arbiter` across all 14 .java files
4. Sed-replace `AutoClearMod` / `AutoClearCommands` / `AutoClearConfig` → `ArbiterMod` / `ArbiterCommands` / `ArbiterConfig` across all .java files
5. Update `gradle.properties`, `fabric.mod.json`, `settings.gradle`
6. Run `./gradlew build` — fix any errors
7. Grep verify: no `bhd`, no `autoclear`/`AutoClear` remain (except GitHub URL, which is out of scope)
8. Commit + push

## Out of Scope
- GitHub repo URL in `fabric.mod.json` (`github.com/bhd/autoclear`) — user handles repo rename manually
- `docs/context/` internal artifacts
- `PLAN.md` itself

## Acceptance Criteria
- [ ] `src/main/java/com/bhd/` directory no longer exists
- [ ] All package declarations read `com.arbiter.*`
- [ ] No file named `AutoClear*.java` exists
- [ ] No class named `AutoClearMod`, `AutoClearCommands`, or `AutoClearConfig` exists
- [ ] `gradle.properties` maven_group = `com.arbiter`
- [ ] `fabric.mod.json` entrypoint = `com.arbiter.ArbiterMod`
- [ ] `settings.gradle` rootProject.name = `arbiter`
- [ ] `./gradlew build` passes
- [ ] `grep -r "bhd\|BHD\|autoclear\|AutoClear" src/` returns nothing (or only the GitHub URL)
