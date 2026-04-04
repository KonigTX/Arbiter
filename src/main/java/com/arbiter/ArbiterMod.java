package com.arbiter;

import com.arbiter.commands.ArbiterCommands;
import com.arbiter.config.ConfigManager;
import com.arbiter.core.ClearanceManager;
import com.arbiter.core.StatsTracker;
import com.arbiter.core.TpsTracker;
import com.arbiter.display.BossBarHandler;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arbiter mod entry point.
 *
 * <p>Wires all subsystems together in strict dependency order and registers them
 * with the Fabric event bus. No static singletons are used — all state lives in
 * instances created here and passed by constructor injection.
 *
 * <p>Initialization order (required — later systems depend on earlier ones):
 * <ol>
 *   <li>{@link ConfigManager#load()} — parse config file before anything reads settings.</li>
 *   <li>{@link TpsTracker#register()} — start sampling MSPT before ClearanceManager checks TPS.</li>
 *   <li>{@link BossBarHandler#register()} — register join event before ClearanceManager fires warnings.</li>
 *   <li>{@link ClearanceManager#register()} — register tick events with all dependencies ready.</li>
 *   <li>{@link ArbiterCommands#register} — register commands (safe at any point before server start).</li>
 *   <li>{@link ConfigManager#startWatchThread()} — start hot-reload thread last (dependencies must be ready for callback).</li>
 * </ol>
 */
public class ArbiterMod implements ModInitializer {

    public static final String MOD_ID = "arbiter";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Arbiter initializing...");

        // ── Step 1: Config ────────────────────────────────────────────────────
        ConfigManager configManager = new ConfigManager();
        configManager.load();

        // ── Step 2: TPS Tracker ───────────────────────────────────────────────
        TpsTracker tpsTracker = new TpsTracker();
        tpsTracker.register();

        // ── Step 3: Supporting subsystems ─────────────────────────────────────
        BossBarHandler bossBarHandler = new BossBarHandler();
        bossBarHandler.register();

        StatsTracker statsTracker = new StatsTracker();

        // ── Step 4: ClearanceManager (core orchestrator) ──────────────────────
        ClearanceManager clearanceManager = new ClearanceManager(
                configManager, tpsTracker, bossBarHandler, statsTracker);
        clearanceManager.register();

        // Wire config reload → reset tick counters so new intervals take effect immediately
        configManager.setOnReloadCallback(clearanceManager::resetAllCounters);

        // ── Step 5: Commands ──────────────────────────────────────────────────
        ArbiterCommands.register(configManager, clearanceManager, statsTracker, tpsTracker);

        // ── Step 6: Hot-reload watch thread ───────────────────────────────────
        // Start last — the callback is already wired, so a rapid file change at startup
        // will not race against unregistered systems.
        configManager.startWatchThread();

        LOGGER.info("Arbiter initialized successfully. Type /arbiter help for commands.");
    }
}
