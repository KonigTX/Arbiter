package com.bhd.autoclear.config;

import java.util.List;
import java.util.Map;

/**
 * Per-dimension configuration subset.
 *
 * <p>Every field is nullable. A {@code null} value signals "inherit from global config".
 * {@link ResolvedConfig} merges a {@link DimensionOverride} onto an {@link AutoClearConfig}
 * at read time, so downstream code never needs to handle nulls.
 *
 * <p>Field names match the JSON5 snake_case keys inside the {@code "dimensions"} object.
 */
public class DimensionOverride {

    // ── Timing ────────────────────────────────────────────────────────────────

    /** Override for {@link AutoClearConfig#clearIntervalSeconds}. Null = inherit. */
    public Integer clearIntervalSeconds = null;

    /** Override for {@link AutoClearConfig#warningStartSeconds}. Null = inherit. */
    public Integer warningStartSeconds = null;

    // ── Thresholds ────────────────────────────────────────────────────────────

    /** Override for {@link AutoClearConfig#itemCountThreshold}. Null = inherit. */
    public Integer itemCountThreshold = null;

    /** Override for {@link AutoClearConfig#emergencyTpsThreshold}. Null = inherit. */
    public Double emergencyTpsThreshold = null;

    /** Override for {@link AutoClearConfig#emergencyClearCooldownSeconds}. Null = inherit. */
    public Integer emergencyClearCooldownSeconds = null;

    // ── Item Protection ───────────────────────────────────────────────────────

    /** Override for {@link AutoClearConfig#protectNamedItems}. Null = inherit. */
    public Boolean protectNamedItems = null;

    /** Override for {@link AutoClearConfig#protectEnchantedItems}. Null = inherit. */
    public Boolean protectEnchantedItems = null;

    /** Override for {@link AutoClearConfig#protectRarityMinimum}. Null = inherit. */
    public String protectRarityMinimum = null;

    /** Override for {@link AutoClearConfig#playerProximityRadius}. Null = inherit. */
    public Integer playerProximityRadius = null;

    // ── Namespace and Item Lists ──────────────────────────────────────────────

    /** Override for {@link AutoClearConfig#protectedNamespaces}. Null = inherit. */
    public List<String> protectedNamespaces = null;

    /** Override for {@link AutoClearConfig#protectedItems}. Null = inherit. */
    public List<String> protectedItems = null;

    /** Override for {@link AutoClearConfig#namespaceTiers}. Null = inherit. */
    public Map<String, String> namespaceTiers = null;

    /** Override for {@link AutoClearConfig#itemOverrides}. Null = inherit. */
    public Map<String, String> itemOverrides = null;

    // ── Display ───────────────────────────────────────────────────────────────

    /** Override for {@link AutoClearConfig#bossbarEnabled}. Null = inherit. */
    public Boolean bossbarEnabled = null;

    /** Override for {@link AutoClearConfig#bossbarStyle}. Null = inherit. */
    public String bossbarStyle = null;

    /** Override for {@link AutoClearConfig#bossbarYellowThresholdSeconds}. Null = inherit. */
    public Integer bossbarYellowThresholdSeconds = null;

    /** Override for {@link AutoClearConfig#bossbarRedThresholdSeconds}. Null = inherit. */
    public Integer bossbarRedThresholdSeconds = null;

    /** Override for {@link AutoClearConfig#actionbarWarningsEnabled}. Null = inherit. */
    public Boolean actionbarWarningsEnabled = null;

    // ── Logging and Broadcast ─────────────────────────────────────────────────

    /** Override for {@link AutoClearConfig#logClearsToConsole}. Null = inherit. */
    public Boolean logClearsToConsole = null;

    /** Override for {@link AutoClearConfig#broadcastClearResults}. Null = inherit. */
    public Boolean broadcastClearResults = null;
}
