package com.arbiter.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Root configuration POJO for Arbiter.
 *
 * <p>All fields carry sensible defaults so that a missing key in the JSON5 file
 * does not break the mod. {@link ConfigManager} handles serialization via Jankson;
 * downstream code always consumes a {@link ResolvedConfig} built from this class
 * merged with a {@link DimensionOverride}.
 *
 * <p>Field names match the JSON5 snake_case keys documented in the config file.
 */
public class ArbiterConfig {

    // ── Preset Mode ───────────────────────────────────────────────────────────

    /**
     * Named preset mode. Valid values: {@code "custom"}, {@code "aggressive"}, {@code "warden"}, {@code "calm"}.
     * {@code "custom"} is the no-op sentinel — all other fields are used as-is from the config file.
     * Any other preset overwrites the fields below at load time.
     */
    public String mode = "custom";

    // ── Timing ────────────────────────────────────────────────────────────────

    /** How often to run a scheduled clear, in seconds. */
    public int clearIntervalSeconds = 300;

    /** How many seconds before the clear to start the bossbar countdown. */
    public int warningStartSeconds = 30;

    // ── Thresholds ────────────────────────────────────────────────────────────

    /** Minimum item count required to trigger a clear. 0 = always clear. */
    public int itemCountThreshold = 100;

    /** Emergency clear when TPS drops below this value. 0.0 = disabled. */
    public double emergencyTpsThreshold = 15.0;

    /** Minimum seconds between successive emergency clears. Prevents rapid-fire triggers. */
    public int emergencyClearCooldownSeconds = 60;

    // ── Item Protection ───────────────────────────────────────────────────────

    /** Protect items that have been renamed at an anvil. */
    public boolean protectNamedItems = true;

    /** Protect items that carry any enchantment. */
    public boolean protectEnchantedItems = true;

    /**
     * Minimum vanilla rarity to automatically protect.
     * Valid values: {@code "COMMON"}, {@code "UNCOMMON"}, {@code "RARE"}, {@code "EPIC"}.
     * {@code "COMMON"} effectively disables rarity-based protection.
     */
    public String protectRarityMinimum = "RARE";

    /** Items within this many blocks of any online player are not removed. 0 = disabled. */
    public int playerProximityRadius = 10;

    // ── Namespace and Item Lists ──────────────────────────────────────────────

    /** Registry namespaces whose items are always protected. */
    public List<String> protectedNamespaces = new ArrayList<>(List.of("cobblemon"));

    /** Specific item IDs that are always protected. */
    public List<String> protectedItems = new ArrayList<>(List.of(
            "minecraft:nether_star",
            "minecraft:elytra",
            "minecraft:dragon_egg",
            "minecraft:totem_of_undying"
    ));

    /**
     * Per-namespace tier assignment.
     * Valid tier values: {@code "PROTECT"}, {@code "LOW"}, {@code "NORMAL"}, {@code "HIGH"}, {@code "BLACKLIST"}.
     */
    public Map<String, String> namespaceTiers = new HashMap<>(Map.of(
            "minecraft", "NORMAL",
            "create", "NORMAL"
    ));

    /**
     * Per-item tier overrides. These win over ALL other rules.
     * Valid tier values: {@code "PROTECT"}, {@code "LOW"}, {@code "NORMAL"}, {@code "HIGH"}, {@code "BLACKLIST"}.
     */
    public Map<String, String> itemOverrides = new HashMap<>(Map.of(
            "minecraft:dirt", "BLACKLIST",
            "minecraft:cobblestone", "BLACKLIST",
            "minecraft:netherite_ingot", "PROTECT"
    ));

    // ── Display ───────────────────────────────────────────────────────────────

    /** Whether to display the bossbar countdown before each clear. */
    public boolean bossbarEnabled = true;

    /**
     * Bossbar segment style.
     * Valid values: {@code "PROGRESS"}, {@code "NOTCHED_6"}, {@code "NOTCHED_10"},
     * {@code "NOTCHED_12"}, {@code "NOTCHED_20"}.
     */
    public String bossbarStyle = "NOTCHED_10";

    /** Seconds remaining at which the bossbar transitions from GREEN to YELLOW. */
    public int bossbarYellowThresholdSeconds = 15;

    /** Seconds remaining at which the bossbar transitions from YELLOW to RED. */
    public int bossbarRedThresholdSeconds = 5;

    /** Send actionbar messages at warning intervals in addition to the bossbar. */
    public boolean actionbarWarningsEnabled = false;

    // ── Per-Dimension Overrides ───────────────────────────────────────────────

    /**
     * Dimension-keyed overrides. Any field omitted in a dimension entry inherits the global value.
     * Keys use the full dimension registry key, e.g., {@code "minecraft:the_nether"}.
     */
    public Map<String, DimensionOverride> dimensions = new HashMap<>();

    // ── Logging and Broadcast ─────────────────────────────────────────────────

    /** Log cleared item counts to server console after each clear. */
    public boolean logClearsToConsole = true;

    /** Broadcast clear results to all players in chat after each clear. */
    public boolean broadcastClearResults = true;

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Clamps all numeric fields to legal ranges.
     * Called by {@link ConfigManager} after loading to prevent nonsensical values.
     */
    public void clampToValidRanges() {
        clearIntervalSeconds = Math.max(10, clearIntervalSeconds);
        warningStartSeconds = Math.max(0, Math.min(warningStartSeconds, clearIntervalSeconds - 1));
        itemCountThreshold = Math.max(0, itemCountThreshold);
        emergencyTpsThreshold = Math.max(0.0, Math.min(emergencyTpsThreshold, 20.0));
        emergencyClearCooldownSeconds = Math.max(0, emergencyClearCooldownSeconds);
        playerProximityRadius = Math.max(0, playerProximityRadius);
        bossbarYellowThresholdSeconds = Math.max(0, bossbarYellowThresholdSeconds);
        bossbarRedThresholdSeconds = Math.max(0, bossbarRedThresholdSeconds);

        // Ensure yellow > red to keep color graduation sensible
        if (bossbarRedThresholdSeconds >= bossbarYellowThresholdSeconds) {
            bossbarRedThresholdSeconds = Math.max(0, bossbarYellowThresholdSeconds - 1);
        }
    }
}
