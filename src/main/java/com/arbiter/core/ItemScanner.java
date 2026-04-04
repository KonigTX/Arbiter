package com.arbiter.core;

import com.arbiter.config.ResolvedConfig;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.TypeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Scans a {@link ServerWorld} for ground item entities and classifies them using
 * the {@link ItemFilters} predicate chain.
 *
 * <p>This class is intentionally side-effect free: it collects and classifies entities
 * but does NOT call {@link ItemEntity#discard()}. The caller ({@link ClearanceManager})
 * retains full control over when and whether removal actually occurs.
 *
 * <p>Uses {@code world.getEntitiesByType(TypeFilter.instanceOf(ItemEntity.class), predicate)}
 * which leverages the engine's internal entity type index and is significantly more
 * efficient than iterating all entities and filtering with instanceof.
 *
 * <p>The predicate passed to {@code getEntitiesByType} is {@code entity -> true} to collect
 * all item entities, then {@link ItemFilters} is applied to determine which to remove.
 * This two-step approach keeps the engine-level scan clean and side-effect free.
 */
public final class ItemScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger("Arbiter/ItemScanner");

    private ItemScanner() {
        // Utility class — not instantiable
    }

    // ── Primary entry point ───────────────────────────────────────────────────

    /**
     * Scans all item entities in {@code world} and returns a {@link ScanResult} describing
     * which items should be removed and how many were found in total.
     *
     * <p>The returned {@code toRemove} list is a new mutable list safe for the caller to
     * iterate and call {@code discard()} on.
     *
     * @param world  the server world to scan
     * @param config the fully-resolved config for that world
     * @return scan result with classified item lists
     */
    public static ScanResult scan(ServerWorld world, ResolvedConfig config) {
        // Collect all item entities using the engine's type bucket — far faster than iterating all entities
        List<? extends ItemEntity> allItems = world.getEntitiesByType(
                TypeFilter.instanceOf(ItemEntity.class),
                entity -> true  // accept all; filtering happens below
        );

        List<ItemEntity> toRemove = new ArrayList<>(allItems.size());

        for (ItemEntity entity : allItems) {
            if (!ItemFilters.shouldProtect(entity, world, config)) {
                toRemove.add(entity);
            }
        }

        LOGGER.debug("Scan in {}: {} total, {} to remove, {} protected",
                world.getRegistryKey().getValue(),
                allItems.size(),
                toRemove.size(),
                allItems.size() - toRemove.size());

        return new ScanResult(toRemove, allItems.size());
    }

    /**
     * Returns a quick count of all item entities in the world without applying filters.
     * Used by the {@code /arbiter status} command for a fast summary.
     */
    public static int countAll(ServerWorld world) {
        return world.getEntitiesByType(
                TypeFilter.instanceOf(ItemEntity.class),
                entity -> true
        ).size();
    }
}
