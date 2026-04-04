package com.arbiter.config;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, fully-resolved configuration for a single world/dimension.
 *
 * <p>Built by {@link ConfigManager#getResolvedConfig} by merging a {@link DimensionOverride}
 * onto the global {@link ArbiterConfig}. All fields are non-null; downstream code
 * (ClearanceManager, ItemFilters, BossBarHandler) reads this record directly without
 * needing to know whether a value came from the global config or a dimension override.
 *
 * <p>This is a plain class rather than a Java {@code record} so that it can be instantiated
 * incrementally in the merge factory method without requiring a telescoping constructor.
 */
public final class ResolvedConfig {

    // ── Timing ────────────────────────────────────────────────────────────────
    public final int clearIntervalSeconds;
    public final int warningStartSeconds;

    // ── Thresholds ────────────────────────────────────────────────────────────
    public final int itemCountThreshold;
    public final double emergencyTpsThreshold;
    public final int emergencyClearCooldownSeconds;

    // ── Item Protection ───────────────────────────────────────────────────────
    public final boolean protectNamedItems;
    public final boolean protectEnchantedItems;
    public final Rarity protectRarityMinimum;
    public final int playerProximityRadius;

    // ── Namespace and Item Lists ──────────────────────────────────────────────
    public final List<String> protectedNamespaces;
    public final List<String> protectedItems;
    public final Map<String, ItemTier> namespaceTiers;
    public final Map<String, ItemTier> itemOverrides;

    // ── Display ───────────────────────────────────────────────────────────────
    public final boolean bossbarEnabled;
    public final BossBar.Style bossbarStyle;
    public final int bossbarYellowThresholdSeconds;
    public final int bossbarRedThresholdSeconds;
    public final boolean actionbarWarningsEnabled;

    // ── Logging and Broadcast ─────────────────────────────────────────────────
    public final boolean logClearsToConsole;
    public final boolean broadcastClearResults;

    private ResolvedConfig(Builder b) {
        this.clearIntervalSeconds = b.clearIntervalSeconds;
        this.warningStartSeconds = b.warningStartSeconds;
        this.itemCountThreshold = b.itemCountThreshold;
        this.emergencyTpsThreshold = b.emergencyTpsThreshold;
        this.emergencyClearCooldownSeconds = b.emergencyClearCooldownSeconds;
        this.protectNamedItems = b.protectNamedItems;
        this.protectEnchantedItems = b.protectEnchantedItems;
        this.protectRarityMinimum = b.protectRarityMinimum;
        this.playerProximityRadius = b.playerProximityRadius;
        this.protectedNamespaces = List.copyOf(b.protectedNamespaces);
        this.protectedItems = List.copyOf(b.protectedItems);
        this.namespaceTiers = Map.copyOf(b.namespaceTiers);
        this.itemOverrides = Map.copyOf(b.itemOverrides);
        this.bossbarEnabled = b.bossbarEnabled;
        this.bossbarStyle = b.bossbarStyle;
        this.bossbarYellowThresholdSeconds = b.bossbarYellowThresholdSeconds;
        this.bossbarRedThresholdSeconds = b.bossbarRedThresholdSeconds;
        this.actionbarWarningsEnabled = b.actionbarWarningsEnabled;
        this.logClearsToConsole = b.logClearsToConsole;
        this.broadcastClearResults = b.broadcastClearResults;
    }

    // ── Tier resolution ───────────────────────────────────────────────────────

    /**
     * Returns the resolved {@link ItemTier} for the given item identifier.
     *
     * <p>Resolution order (first match wins):
     * <ol>
     *   <li>{@code item_overrides} exact match</li>
     *   <li>{@code namespace_tiers} namespace match</li>
     *   <li>{@link ItemTier#NORMAL} as the default</li>
     * </ol>
     */
    public ItemTier getTierForItem(Identifier id) {
        String fullId = id.toString(); // "minecraft:dirt"
        ItemTier override = itemOverrides.get(fullId);
        if (override != null) {
            return override;
        }

        ItemTier namespaceTier = namespaceTiers.get(id.getNamespace());
        if (namespaceTier != null) {
            return namespaceTier;
        }

        return ItemTier.NORMAL;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /**
     * Constructs a {@link ResolvedConfig} by merging the global config with an optional
     * dimension override. Any non-null field in {@code override} replaces the global value.
     */
    public static ResolvedConfig merge(ArbiterConfig global, DimensionOverride override) {
        Builder b = new Builder();

        // Timing
        b.clearIntervalSeconds = override != null && override.clearIntervalSeconds != null
                ? override.clearIntervalSeconds : global.clearIntervalSeconds;
        b.warningStartSeconds = override != null && override.warningStartSeconds != null
                ? override.warningStartSeconds : global.warningStartSeconds;

        // Thresholds
        b.itemCountThreshold = override != null && override.itemCountThreshold != null
                ? override.itemCountThreshold : global.itemCountThreshold;
        b.emergencyTpsThreshold = override != null && override.emergencyTpsThreshold != null
                ? override.emergencyTpsThreshold : global.emergencyTpsThreshold;
        b.emergencyClearCooldownSeconds = override != null && override.emergencyClearCooldownSeconds != null
                ? override.emergencyClearCooldownSeconds : global.emergencyClearCooldownSeconds;

        // Item Protection
        b.protectNamedItems = override != null && override.protectNamedItems != null
                ? override.protectNamedItems : global.protectNamedItems;
        b.protectEnchantedItems = override != null && override.protectEnchantedItems != null
                ? override.protectEnchantedItems : global.protectEnchantedItems;

        String rarityStr = (override != null && override.protectRarityMinimum != null)
                ? override.protectRarityMinimum : global.protectRarityMinimum;
        b.protectRarityMinimum = parseRarity(rarityStr);

        b.playerProximityRadius = override != null && override.playerProximityRadius != null
                ? override.playerProximityRadius : global.playerProximityRadius;

        // Namespace and Item Lists
        b.protectedNamespaces = (override != null && override.protectedNamespaces != null)
                ? override.protectedNamespaces : global.protectedNamespaces;
        b.protectedItems = (override != null && override.protectedItems != null)
                ? override.protectedItems : global.protectedItems;

        Map<String, String> rawNamespaceTiers = (override != null && override.namespaceTiers != null)
                ? override.namespaceTiers : global.namespaceTiers;
        b.namespaceTiers = parseTierMap(rawNamespaceTiers);

        Map<String, String> rawItemOverrides = (override != null && override.itemOverrides != null)
                ? override.itemOverrides : global.itemOverrides;
        b.itemOverrides = parseTierMap(rawItemOverrides);

        // Display
        b.bossbarEnabled = override != null && override.bossbarEnabled != null
                ? override.bossbarEnabled : global.bossbarEnabled;

        String styleStr = (override != null && override.bossbarStyle != null)
                ? override.bossbarStyle : global.bossbarStyle;
        b.bossbarStyle = parseBossBarStyle(styleStr);

        b.bossbarYellowThresholdSeconds = override != null && override.bossbarYellowThresholdSeconds != null
                ? override.bossbarYellowThresholdSeconds : global.bossbarYellowThresholdSeconds;
        b.bossbarRedThresholdSeconds = override != null && override.bossbarRedThresholdSeconds != null
                ? override.bossbarRedThresholdSeconds : global.bossbarRedThresholdSeconds;
        b.actionbarWarningsEnabled = override != null && override.actionbarWarningsEnabled != null
                ? override.actionbarWarningsEnabled : global.actionbarWarningsEnabled;

        // Logging and Broadcast
        b.logClearsToConsole = override != null && override.logClearsToConsole != null
                ? override.logClearsToConsole : global.logClearsToConsole;
        b.broadcastClearResults = override != null && override.broadcastClearResults != null
                ? override.broadcastClearResults : global.broadcastClearResults;

        return new ResolvedConfig(b);
    }

    // ── Parse helpers ─────────────────────────────────────────────────────────

    private static Rarity parseRarity(String raw) {
        if (raw == null) return Rarity.RARE;
        return switch (raw.toUpperCase()) {
            case "COMMON" -> Rarity.COMMON;
            case "UNCOMMON" -> Rarity.UNCOMMON;
            case "RARE" -> Rarity.RARE;
            case "EPIC" -> Rarity.EPIC;
            default -> Rarity.RARE;
        };
    }

    private static Map<String, ItemTier> parseTierMap(Map<String, String> raw) {
        HashMap<String, ItemTier> result = new HashMap<>();
        if (raw == null) return result;
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            try {
                result.put(entry.getKey(), ItemTier.valueOf(entry.getValue().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // Unknown tier string in config — treat as NORMAL (safest default)
                result.put(entry.getKey(), ItemTier.NORMAL);
            }
        }
        return result;
    }

    private static BossBar.Style parseBossBarStyle(String raw) {
        if (raw == null) return BossBar.Style.NOTCHED_10;
        return switch (raw.toUpperCase()) {
            case "PROGRESS" -> BossBar.Style.PROGRESS;
            case "NOTCHED_6" -> BossBar.Style.NOTCHED_6;
            case "NOTCHED_10" -> BossBar.Style.NOTCHED_10;
            case "NOTCHED_12" -> BossBar.Style.NOTCHED_12;
            case "NOTCHED_20" -> BossBar.Style.NOTCHED_20;
            default -> BossBar.Style.NOTCHED_10;
        };
    }

    // ── Internal Builder ──────────────────────────────────────────────────────

    private static final class Builder {
        int clearIntervalSeconds;
        int warningStartSeconds;
        int itemCountThreshold;
        double emergencyTpsThreshold;
        int emergencyClearCooldownSeconds;
        boolean protectNamedItems;
        boolean protectEnchantedItems;
        Rarity protectRarityMinimum;
        int playerProximityRadius;
        List<String> protectedNamespaces;
        List<String> protectedItems;
        Map<String, ItemTier> namespaceTiers;
        Map<String, ItemTier> itemOverrides;
        boolean bossbarEnabled;
        BossBar.Style bossbarStyle;
        int bossbarYellowThresholdSeconds;
        int bossbarRedThresholdSeconds;
        boolean actionbarWarningsEnabled;
        boolean logClearsToConsole;
        boolean broadcastClearResults;
    }
}
