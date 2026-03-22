package com.bhd.autoclear.core;

import com.bhd.autoclear.config.ConfigManager;
import com.bhd.autoclear.config.ResolvedConfig;
import com.bhd.autoclear.display.BossBarHandler;
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
            executeClear(world, config, false);
            tickCounters.put(key, intervalTicks);
            warningActive.put(key, false);
        }
    }

    // ── Server tick handler (TPS emergency check) ─────────────────────────────

    private void onServerTickEnd(MinecraftServer server) {
        if (++serverTickCount % TpsTracker.getSampleIntervalTicks() != 0) return;

        double tps = tpsTracker.getCurrentTps();
        // Iterate all loaded worlds
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
            break; // one emergency clear per 100-tick check; avoids redundant per-world triggers
        }
    }

    // ── Clear execution ───────────────────────────────────────────────────────

    /**
     * Executes a full scan-and-clear pass for a single world.
     *
     * @param world     the world to clear
     * @param config    the resolved config for that world
     * @param emergency true if triggered by TPS threshold, not the scheduler
     */
    public void executeClear(ServerWorld world, ResolvedConfig config, boolean emergency) {
        ScanResult result = ItemScanner.scan(world, config);

        // Threshold check — skip clear if not enough items
        if (config.itemCountThreshold > 0 && result.totalFound() < config.itemCountThreshold) {
            if (config.logClearsToConsole) {
                LOGGER.info("[AutoClear] {} — skipped (found {}, threshold {})",
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
            LOGGER.info("{}[AutoClear] {} — cleared {}/{} items",
                    prefix, world.getRegistryKey().getValue(), removed, total);
        }

        // Broadcast to players
        if (config.broadcastClearResults && world.getServer() != null) {
            String msgKey = emergency ? "autoclear.broadcast.emergency" : "autoclear.broadcast.cleared";
            Text message = Text.translatable(msgKey, removed, total)
                    .formatted(emergency ? Formatting.RED : Formatting.YELLOW);
            world.getServer().getPlayerManager().broadcast(message, false);
        }

        // Dismiss bossbar after the clear
        bossBarHandler.dismiss();
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
            executeClear(world, config, true);

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
            Text msg = Text.translatable("autoclear.actionbar.warning", config.warningStartSeconds)
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
            executeClear(world, config, false);
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
        executeClear(world, config, false);
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
}
