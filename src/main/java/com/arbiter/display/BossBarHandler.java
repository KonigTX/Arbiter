package com.arbiter.display;

import com.arbiter.config.ResolvedConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the {@link ServerBossBar} that shows the item-clear countdown to all online players.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Single shared bar instance — players are added/removed via
 *       {@link ServerBossBar#addPlayer} and {@link ServerBossBar#removePlayer}.
 *       This avoids the memory leak of creating one bar per player.</li>
 *   <li>Color graduation: GREEN at start → YELLOW at configured threshold → RED at final threshold.</li>
 *   <li>Players who join mid-countdown are added to the active bar via the
 *       {@code ServerPlayConnectionEvents.JOIN} event.</li>
 *   <li>The bar is dismissed (all players removed, reference nulled) after the clear executes
 *       or when {@link #dismiss()} is called explicitly (e.g., on config reload).</li>
 * </ul>
 */
public class BossBarHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("Arbiter/BossBarHandler");

    /** The active bossbar, or {@code null} when no countdown is running. */
    private volatile ServerBossBar activeBar = null;

    /** Total duration in seconds of the current countdown — used to compute fill percent. */
    private int totalSeconds = 0;

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers the player-join event so late joiners see the bossbar mid-countdown.
     * Call once during mod initialization.
     */
    public void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerBossBar bar = activeBar;
            if (bar != null) {
                bar.addPlayer(handler.player);
            }
        });
        LOGGER.debug("BossBarHandler registered player-join event");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Creates the countdown bossbar and adds all currently online players to it.
     * If a bar is already active (e.g., rapid force-clear), it is dismissed first.
     *
     * @param server       the Minecraft server instance (for player list access)
     * @param totalSec     total seconds for the countdown (matches {@code warning_start_seconds})
     * @param config       resolved config for bar style settings
     */
    public void startCountdown(MinecraftServer server, int totalSec, ResolvedConfig config) {
        dismiss(); // clean up any lingering bar

        totalSeconds = totalSec;
        Text title = Text.literal("Items clearing in " + totalSec + "s");

        activeBar = new ServerBossBar(title, BossBar.Color.GREEN, config.bossbarStyle);
        activeBar.setPercent(1.0f);
        activeBar.setVisible(true);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            activeBar.addPlayer(player);
        }

        LOGGER.debug("BossBar countdown started: {}s", totalSec);
    }

    /**
     * Updates the bossbar fill percentage and color based on remaining seconds.
     * Called once per world tick during the warning phase.
     *
     * @param server           server instance (for player list — handles mid-countdown joins
     *                         as a fallback; primary join handling is via the event)
     * @param secondsRemaining how many seconds remain until the clear
     * @param configuredTotal  total countdown seconds from config (for percent calculation)
     * @param config           resolved config for color thresholds
     */
    public void tick(MinecraftServer server, int secondsRemaining, int configuredTotal, ResolvedConfig config) {
        ServerBossBar bar = activeBar;
        if (bar == null) return;

        // Update fill percent — clamp to [0, 1]
        float percent = configuredTotal > 0
                ? Math.max(0.0f, Math.min(1.0f, (float) secondsRemaining / configuredTotal))
                : 0.0f;
        bar.setPercent(percent);

        // Update title to show live countdown
        bar.setName(Text.literal("Items clearing in " + secondsRemaining + "s"));

        // Color graduation: GREEN → YELLOW → RED
        BossBar.Color newColor;
        if (secondsRemaining <= config.bossbarRedThresholdSeconds) {
            newColor = BossBar.Color.RED;
        } else if (secondsRemaining <= config.bossbarYellowThresholdSeconds) {
            newColor = BossBar.Color.YELLOW;
        } else {
            newColor = BossBar.Color.GREEN;
        }

        if (bar.getColor() != newColor) {
            bar.setColor(newColor);
        }
    }

    /**
     * Removes all players from the bossbar and clears the reference.
     * Safe to call when no bar is active (no-op).
     */
    public void dismiss() {
        ServerBossBar bar = activeBar;
        if (bar == null) return;

        bar.clearPlayers();
        bar.setVisible(false);
        activeBar = null;
        totalSeconds = 0;

        LOGGER.debug("BossBar dismissed");
    }

    // ── State query ───────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if a countdown bossbar is currently active.
     */
    public boolean isActive() {
        return activeBar != null;
    }
}
