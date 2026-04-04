package com.bhd.autoclear.core;

import com.bhd.autoclear.config.ConfigManager;
import com.bhd.autoclear.config.ItemTier;
import com.bhd.autoclear.config.ResolvedConfig;
import com.bhd.autoclear.display.BossBarHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypeFilter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the full clear lifecycle for all loaded server worlds.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Maintains per-world tick counters that count down to the next clear.</li>
 *   <li>Triggers the warning phase (bossbar countdown) at the configured lead time.</li>
 *   <li>Executes the clear pass: scan → threshold check → discard → log → broadcast.</li>
 *   <li>Monitors TPS every 100 ticks and fires an emergency clear when below threshold.</li>
 *   <li>Enforces an emergency clear cooldown to prevent rapid-fire triggers.</li>
 * </ul>
 *
 * <p>Design note: Per-world tick counters are stored in a {@link ConcurrentHashMap} keyed
 * by {@link RegistryKey}{@code <World>}. {@code END_WORLD_TICK} delivers each world on a
 * single server thread, so per-world state only needs atomic access for the global
 * emergency cooldown timestamp.
 */
public class ClearanceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("AutoClear/ClearanceManager");

    private final ConfigManager configManager;
    private final TpsTracker tpsTracker;
    private final BossBarHandler bossBarHandler;
    private final StatsTracker statsTracker;

    /**
     * Remaining ticks until the next scheduled clear for each world.
     * Initialised to the configured interval on first tick for that world.
     */
    private final Map<RegistryKey<World>, Integer> tickCounters = new ConcurrentHashMap<>();

    /**
     * Whether the warning bossbar is currently active for each world.
     * Used to avoid re-triggering beginWarningPhase on every tick during countdown.
     */
    private final Map<RegistryKey<World>, Boolean> warningActive = new ConcurrentHashMap<>();

    /** Server-level tick counter for the TPS emergency check. */
    private int serverTickCount = 0;

    /** Epoch milliseconds of the last emergency clear. 0 means never. */
    private volatile long lastEmergencyClearMillis = 0L;

    /** Ticks remaining in an active testbar countdown. 0 means inactive. */
    private int testBarTicksRemaining = 0;

    /** Total seconds the testbar was started with. */
    private int testBarTotalSeconds   = 0;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ClearanceManager(
            ConfigManager configManager,
            TpsTracker tpsTracker,
            BossBarHandler bossBarHandler,
            StatsTracker statsTracker) {
        this.configManager = configManager;
        this.tpsTracker = tpsTracker;
        this.bossBarHandler = bossBarHandler;
        this.statsTracker = statsTracker;
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers all tick event listeners. Call once during mod initialization.
     */
    public void register() {
        ServerTickEvents.END_WORLD_TICK.register(this::onWorldTickEnd);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTickEnd);
        LOGGER.info("ClearanceManager registered tick events");
    }

    // ── Hot-reload support ────────────────────────────────────────────────────

    /**
     * Resets all per-world tick counters so that newly configured intervals take effect
     * immediately. Called by {@link ConfigManager#setOnReloadCallback}.
     */
    public void resetAllCounters() {
        tickCounters.clear();
        warningActive.clear();
        bossBarHandler.dismiss();
        LOGGER.info("ClearanceManager: tick counters reset after config reload");
    }

    // ── Testbar ───────────────────────────────────────────────────────────────

    /**
     * Starts a bossbar countdown for {@code seconds} seconds without triggering a clear.
     * Used by {@code /autoclear testbar [seconds]}.
     */
    public void startTestBar(MinecraftServer server, int seconds) {
        testBarTicksRemaining = seconds * 20;
        testBarTotalSeconds   = seconds;
        ResolvedConfig config = configManager.getResolvedConfig(World.OVERWORLD);
        bossBarHandler.startCountdown(server, seconds, config);
    }

    // ── World tick handler ────────────────────────────────────────────────────

    private void onWorldTickEnd(ServerWorld world) {
        RegistryKey<World> key = world.getRegistryKey();
        ResolvedConfig config = configManager.getResolvedConfig(key);

        int intervalTicks = config.clearIntervalSeconds * 20;
        int warningTicks = config.warningStartSeconds * 20;

        // Initialise counter for newly-loaded worlds
        tickCounters.computeIfAbsent(key, k -> intervalTicks);
        warningActive.putIfAbsent(key, false);

        int remaining = tickCounters.get(key) - 1;
        tickCounters.put(key, remaining);

        // ── Warning phase: start bossbar countdown ─────────────────────────
        if (!warningActive.get(key) && remaining == warningTicks && config.bossbarEnabled) {
            warningActive.put(key, true);
            beginWarningPhase(world, config);
        }

        // ── Bossbar tick update during warning phase ───────────────────────
        if (warningActive.get(key) && warningTicks > 0) {
            int secondsRemaining = remaining / 20;
            bossBarHandler.tick(world.getServer(), secondsRemaining, config.warningStartSeconds, config);
        }

        // ── Execute clear ──────────────────────────────────────────────────
        if (remaining <= 0) {
            executeClear(world, config, false, false);
            tickCounters.put(key, intervalTicks);
            warningActive.put(key, false);
        }
    }

    // ── Server tick handler (TPS emergency check) ─────────────────────────────

    private void onServerTickEnd(MinecraftServer server) {
        // ── Testbar countdown tick — runs every tick ───────────────────────────
        if (testBarTicksRemaining > 0) {
            testBarTicksRemaining--;
            if (testBarTicksRemaining <= 0) {
                bossBarHandler.dismiss();
                testBarTicksRemaining = 0;
                testBarTotalSeconds   = 0;
            } else {
                int secsRemaining = testBarTicksRemaining / 20;
                ResolvedConfig config = configManager.getResolvedConfig(World.OVERWORLD);
                bossBarHandler.tick(server, secsRemaining, testBarTotalSeconds, config);
            }
        }

        // ── TPS emergency check — runs every 100 ticks ────────────────────────
        if (++serverTickCount % TpsTracker.getSampleIntervalTicks() != 0) return;

        double tps = tpsTracker.getCurrentTps();
        for (ServerWorld world : server.getWorlds()) {
            ResolvedConfig config = configManager.getResolvedConfig(world.getRegistryKey());

            if (config.emergencyTpsThreshold <= 0.0) continue; // disabled
            if (tps >= config.emergencyTpsThreshold) continue;

            long nowMs = System.currentTimeMillis();
            long cooldownMs = (long) config.emergencyClearCooldownSeconds * 1000L;
            if (nowMs - lastEmergencyClearMillis < cooldownMs) continue;

            LOGGER.warn("TPS dropped to {} (threshold: {}). Triggering emergency clear.",
                    String.format("%.2f", tps), config.emergencyTpsThreshold);
            triggerEmergencyClear(server);
            break;
        }
    }

    // ── Clear execution ───────────────────────────────────────────────────────

    /**
     * Executes a full scan-and-clear pass for a single world.
     *
     * @param world         the world to clear
     * @param config        the resolved config for that world
     * @param emergency     true if triggered by TPS threshold, not the scheduler
     * @param skipThreshold true if itemCountThreshold should be ignored (force/emergency clears)
     */
    public void executeClear(ServerWorld world, ResolvedConfig config, boolean emergency, boolean skipThreshold) {
        // Always dismiss the bossbar at the start — whether the clear runs or skips,
        // the countdown is over and the bar should not stay frozen at 0s.
        bossBarHandler.dismiss();

        ScanResult result = ItemScanner.scan(world, config);

        // Threshold check — skip clear if not enough items (scheduled clears only)
        if (!skipThreshold && config.itemCountThreshold > 0 && result.totalFound() < config.itemCountThreshold) {
            if (config.logClearsToConsole) {
                LOGGER.info("[AutoClear/Scheduled] {} - skipped (found {}, threshold {})",
                        world.getRegistryKey().getValue(), result.totalFound(), config.itemCountThreshold);
            }
            return;
        }

        // Perform the removal
        for (ItemEntity entity : result.toRemove()) {
            entity.discard();
        }

        int removed = result.removeCount();
        int total = result.totalFound();

        // Stats
        statsTracker.recordClear(removed, total, world, emergency);

        // Console logging
        if (config.logClearsToConsole) {
            String prefix = emergency ? "[EMERGENCY] " : "";
            LOGGER.info("{}[AutoClear] {} - cleared {}/{} items",
                    prefix, world.getRegistryKey().getValue(), removed, total);
        }

        // Broadcast to players — skip if nothing was found (avoids spam on empty worlds)
        if (config.broadcastClearResults && world.getServer() != null && total > 0) {
            int saved = total - removed;
            String msgText = emergency
                ? "[AutoClear] EMERGENCY CLEAR - Cleared " + removed + " items | " + saved + " protected (low TPS)"
                : "[AutoClear] Cleared " + removed + " items | " + saved + " protected";
            Text message = Text.literal(msgText)
                    .formatted(emergency ? Formatting.RED : Formatting.YELLOW);
            world.getServer().getPlayerManager().broadcast(message, false);
        }

    }

    /**
     * Triggers an emergency clear across all currently loaded server worlds.
     * Skips the warning phase. Records the emergency timestamp to enforce cooldown.
     */
    public void triggerEmergencyClear(MinecraftServer server) {
        lastEmergencyClearMillis = System.currentTimeMillis();

        // Dismiss any active bossbar before emergency clear
        bossBarHandler.dismiss();

        for (ServerWorld world : server.getWorlds()) {
            ResolvedConfig config = configManager.getResolvedConfig(world.getRegistryKey());
            executeClear(world, config, true, true);

            // Reset the scheduled timer for this world
            int intervalTicks = config.clearIntervalSeconds * 20;
            tickCounters.put(world.getRegistryKey(), intervalTicks);
            warningActive.put(world.getRegistryKey(), false);
        }
    }

    // ── Warning phase ─────────────────────────────────────────────────────────

    private void beginWarningPhase(ServerWorld world, ResolvedConfig config) {
        MinecraftServer server = world.getServer();
        if (server == null) return;

        bossBarHandler.startCountdown(server, config.warningStartSeconds, config);

        if (config.actionbarWarningsEnabled) {
            Text msg = Text.literal("Items clearing in " + config.warningStartSeconds + "s!")
                    .formatted(Formatting.GOLD);
            server.getPlayerManager().broadcast(msg, true); // true = actionbar
        }

        LOGGER.debug("Warning phase started: clear in {}s (world: {})",
                config.warningStartSeconds, world.getRegistryKey().getValue());
    }

    // ── Public force-clear entry point (used by commands) ────────────────────

    /**
     * Immediately clears all loaded worlds, bypassing the scheduler.
     * Used by {@code /autoclear force}.
     */
    public void forceAllWorlds(MinecraftServer server) {
        bossBarHandler.dismiss();
        for (ServerWorld world : server.getWorlds()) {
            ResolvedConfig config = configManager.getResolvedConfig(world.getRegistryKey());
            executeClear(world, config, false, true);
            tickCounters.put(world.getRegistryKey(), config.clearIntervalSeconds * 20);
            warningActive.put(world.getRegistryKey(), false);
        }
    }

    /**
     * Immediately clears a single world, bypassing the scheduler.
     * Used by {@code /autoclear force <dimension>}.
     */
    public void forceWorld(ServerWorld world) {
        RegistryKey<World> key = world.getRegistryKey();
        ResolvedConfig config = configManager.getResolvedConfig(key);
        bossBarHandler.dismiss();
        executeClear(world, config, false, true);
        tickCounters.put(key, config.clearIntervalSeconds * 20);
        warningActive.put(key, false);
    }

    // ── Status accessors (used by commands) ──────────────────────────────────

    /**
     * Returns the number of seconds until the next scheduled clear for the given world.
     * Returns -1 if the world has no active counter yet.
     */
    public int getSecondsUntilClear(RegistryKey<World> worldKey) {
        Integer remaining = tickCounters.get(worldKey);
        if (remaining == null) return -1;
        return remaining / 20;
    }

    // ── Dry-run (used by /autoclear dryrun) ──────────────────────────────────

    /**
     * Runs the item scan pipeline without discarding anything and reports what a real
     * clear would do, including threshold skip detection and per-rule protection breakdown.
     *
     * @param world the world to analyse
     * @return a {@link DryRunResult} describing the hypothetical clear outcome
     */
    public DryRunResult dryRun(ServerWorld world) {
        ResolvedConfig config = configManager.getResolvedConfig(world.getRegistryKey());
        ScanResult scanResult = ItemScanner.scan(world, config);

        int totalFound = scanResult.totalFound();
        boolean thresholdSkipped = config.itemCountThreshold > 0
                && totalFound < config.itemCountThreshold;

        // Items in toRemove are those NOT protected; items NOT in toRemove are protected.
        int wouldClear = scanResult.removeCount();
        int wouldProtect = scanResult.protectedCount();

        // Build per-rule breakdown over all entities in the world.
        List<? extends ItemEntity> allEntities = world.getEntitiesByType(
                TypeFilter.instanceOf(ItemEntity.class),
                e -> true);

        Map<String, Integer> breakdown = countByRule(allEntities, world, config);

        return new DryRunResult(totalFound, wouldClear, wouldProtect,
                thresholdSkipped, config.itemCountThreshold, breakdown);
    }

    /**
     * Mirrors the 10-rule priority chain in {@link ItemFilters#shouldProtect} and tallies
     * how many items are caught by each rule. Each item is attributed to the first rule
     * that would protect it (matching the short-circuit behaviour of shouldProtect).
     *
     * <p>Items that would be cleared (no rule protects them) are counted under "cleared".
     *
     * @param entities all item entities in the world
     * @param world    the world (used for proximity check)
     * @param config   the resolved config for this world
     * @return an ordered map of rule label to protected-item count
     */
    private Map<String, Integer> countByRule(
            List<? extends ItemEntity> entities, ServerWorld world, ResolvedConfig config) {

        // Use LinkedHashMap to preserve insertion order for display
        Map<String, Integer> tally = new LinkedHashMap<>();
        tally.put("blacklist",         0);
        tally.put("overrideProtect",   0);
        tally.put("protectedItems",    0);
        tally.put("protectedNamespaces", 0);
        tally.put("namespaceTier",     0);
        tally.put("rarity",            0);
        tally.put("enchanted",         0);
        tally.put("named",             0);
        tally.put("proximity",         0);
        tally.put("cleared",           0);

        for (ItemEntity entity : entities) {
            ItemStack stack = entity.getStack();
            if (stack.isEmpty()) {
                // Ghost entity — would be discarded; counts as cleared
                tally.merge("cleared", 1, Integer::sum);
                continue;
            }

            Identifier id = ItemFilters.getItemId(stack);

            // Rule 1 & 2: item_overrides
            ItemTier explicitTier = config.itemOverrides.get(id.toString());
            if (explicitTier != null) {
                if (explicitTier.isBlacklisted()) {
                    tally.merge("blacklist", 1, Integer::sum);
                    continue;
                }
                if (explicitTier.isProtected()) {
                    tally.merge("overrideProtect", 1, Integer::sum);
                    continue;
                }
                // LOW/NORMAL/HIGH fall through
            }

            // Rule 3: protected_items list
            if (config.protectedItems.contains(id.toString())) {
                tally.merge("protectedItems", 1, Integer::sum);
                continue;
            }

            // Rule 4: protected_namespaces list
            if (config.protectedNamespaces.contains(id.getNamespace())) {
                tally.merge("protectedNamespaces", 1, Integer::sum);
                continue;
            }

            // Rule 5: namespace_tiers PROTECT entry
            ItemTier namespaceTier = config.namespaceTiers.get(id.getNamespace());
            if (namespaceTier != null && namespaceTier.isProtected()) {
                tally.merge("namespaceTier", 1, Integer::sum);
                continue;
            }

            // Rule 6: vanilla Rarity check
            Rarity itemRarity = stack.getOrDefault(DataComponentTypes.RARITY, Rarity.COMMON);
            if (itemRarity.ordinal() >= config.protectRarityMinimum.ordinal()) {
                tally.merge("rarity", 1, Integer::sum);
                continue;
            }

            // Rule 7: enchanted
            if (config.protectEnchantedItems && stack.hasEnchantments()) {
                tally.merge("enchanted", 1, Integer::sum);
                continue;
            }

            // Rule 8: custom name
            if (config.protectNamedItems && stack.get(DataComponentTypes.CUSTOM_NAME) != null) {
                tally.merge("named", 1, Integer::sum);
                continue;
            }

            // Rule 9: player proximity
            if (config.playerProximityRadius > 0) {
                double radiusSq = (double) config.playerProximityRadius * config.playerProximityRadius;
                boolean nearPlayer = false;
                for (PlayerEntity player : world.getPlayers()) {
                    if (player.squaredDistanceTo(entity) <= radiusSq) {
                        nearPlayer = true;
                        break;
                    }
                }
                if (nearPlayer) {
                    tally.merge("proximity", 1, Integer::sum);
                    continue;
                }
            }

            // Rule 10: default — cleared
            tally.merge("cleared", 1, Integer::sum);
        }

        return tally;
    }

    // ── DryRunResult record ───────────────────────────────────────────────────

    /**
     * Immutable result of a {@link #dryRun} pass.
     *
     * @param totalFound         total item entities found in the world
     * @param wouldClear         items that would be discarded if a real clear ran
     * @param wouldProtect       items that would be preserved by any protection rule
     * @param thresholdSkipped   true if totalFound is below itemCountThreshold
     * @param threshold          the itemCountThreshold value from config
     * @param protectionBreakdown per-rule tally of protected items (ordered map)
     */
    public record DryRunResult(
            int totalFound,
            int wouldClear,
            int wouldProtect,
            boolean thresholdSkipped,
            int threshold,
            Map<String, Integer> protectionBreakdown
    ) {}
}
