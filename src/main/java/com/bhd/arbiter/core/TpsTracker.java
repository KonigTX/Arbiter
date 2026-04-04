package com.bhd.arbiter.core;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks server TPS by sampling {@link MinecraftServer#getAverageTickTime()} every 100 ticks.
 *
 * <p>TPS is derived as {@code Math.min(20.0, 1000.0 / mspt)} — the cap at 20.0 prevents
 * artificially reporting "super-speed" when the server is very lightly loaded and ticks
 * complete faster than their nominal 50ms window.
 *
 * <p>Guard against division by zero: if {@code getAverageTickTime()} returns 0 (server just
 * started, rolling average not yet meaningful), the last known good TPS is preserved.
 *
 * <p>Designed as a singleton-style class instantiated once in {@code AutoClearMod}.
 */
public class TpsTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("Arbiter/TpsTracker");

    /** How many ticks between each MSPT sample. */
    private static final int SAMPLE_INTERVAL_TICKS = 100;

    /** Nominal TPS assumed before the first valid sample arrives. */
    private static final double INITIAL_TPS = 20.0;

    private int tickCount = 0;
    private volatile double currentTps = INITIAL_TPS;

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers the tick listener that samples MSPT every {@link #SAMPLE_INTERVAL_TICKS} ticks.
     * Call once during mod initialization.
     */
    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTickEnd);
        LOGGER.debug("TpsTracker registered");
    }

    // ── Tick handler ──────────────────────────────────────────────────────────

    private void onServerTickEnd(MinecraftServer server) {
        if (++tickCount < SAMPLE_INTERVAL_TICKS) return;
        tickCount = 0;

        double mspt = server.getAverageTickTime();

        // Guard: skip update if MSPT is not yet meaningful (server startup)
        if (mspt <= 0.0) return;

        currentTps = Math.min(20.0, 1000.0 / mspt);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the most recently sampled TPS value, capped at 20.0.
     * Returns {@link #INITIAL_TPS} (20.0) if no sample has been taken yet.
     */
    public double getCurrentTps() {
        return currentTps;
    }

    /**
     * Returns the tick interval at which MSPT is sampled.
     * Used by {@link ClearanceManager} when scheduling its own per-100-tick checks.
     */
    public static int getSampleIntervalTicks() {
        return SAMPLE_INTERVAL_TICKS;
    }
}
