package com.bhd.arbiter.core;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Accumulates session-scoped statistics for cleared items.
 *
 * <p>All mutations are thread-safe via atomic fields; reads produce a consistent
 * snapshot for command output without requiring synchronization.
 *
 * <p>Stats reset when the server restarts (session-scoped, not persisted to disk).
 */
public class StatsTracker {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AtomicInteger totalClears = new AtomicInteger(0);
    private final AtomicLong totalItemsRemoved = new AtomicLong(0);
    private volatile int lastClearCount = 0;
    private volatile int lastClearTotal = 0;
    private volatile String lastClearWorld = "none";
    private volatile Instant lastClearTime = null;
    private volatile boolean lastWasEmergency = false;

    // ── Recording ─────────────────────────────────────────────────────────────

    /**
     * Records the result of a completed clear pass.
     *
     * @param removed   number of items that were discarded
     * @param total     total items found before filtering
     * @param world     the world the clear ran in
     * @param emergency true if this was triggered by a TPS emergency
     */
    public void recordClear(int removed, int total, ServerWorld world, boolean emergency) {
        totalClears.incrementAndGet();
        totalItemsRemoved.addAndGet(removed);
        lastClearCount = removed;
        lastClearTotal = total;
        lastClearTime = Instant.now();
        lastWasEmergency = emergency;

        Identifier worldId = world.getRegistryKey().getValue();
        lastClearWorld = worldId.toString();
    }

    // ── Formatted output ──────────────────────────────────────────────────────

    /**
     * Returns a multi-line formatted stats string suitable for command output.
     * Newlines use {@code \n}; callers should split on newline if formatting individually.
     */
    public String getFormattedStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Arbiter Session Stats ===\n");
        sb.append("  Total clears run   : ").append(totalClears.get()).append("\n");
        sb.append("  Total items removed: ").append(totalItemsRemoved.get()).append("\n");

        if (lastClearTime != null) {
            sb.append("  Last clear at      : ").append(TIME_FORMATTER.format(lastClearTime)).append("\n");
            sb.append("  Last clear world   : ").append(lastClearWorld).append("\n");
            sb.append("  Last clear removed : ").append(lastClearCount)
              .append(" / ").append(lastClearTotal).append(" found\n");
            if (lastWasEmergency) {
                sb.append("  Last clear type    : EMERGENCY (TPS triggered)\n");
            } else {
                sb.append("  Last clear type    : Scheduled\n");
            }
        } else {
            sb.append("  No clears have run this session.\n");
        }

        sb.append("================================");
        return sb.toString();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public int getTotalClears() {
        return totalClears.get();
    }

    public long getTotalItemsRemoved() {
        return totalItemsRemoved.get();
    }

    public int getLastClearCount() {
        return lastClearCount;
    }

    public Instant getLastClearTime() {
        return lastClearTime;
    }
}
