package com.bhd.arbiter.core;

import net.minecraft.entity.ItemEntity;

import java.util.List;

/**
 * Immutable result of a single item scan pass.
 *
 * <p>Separates the decision of what to remove ({@link ItemScanner}) from the act
 * of removing ({@link ClearanceManager}). This allows threshold checks and logging
 * to inspect the lists before any destructive action occurs.
 *
 * @param toRemove   items that passed the filter and should be discarded
 * @param totalFound total item entities found in the world before filtering
 */
public record ScanResult(List<ItemEntity> toRemove, int totalFound) {

    /** Returns the number of items that will be removed if the clear proceeds. */
    public int removeCount() {
        return toRemove.size();
    }

    /** Returns the number of items that will be preserved (protected by any rule). */
    public int protectedCount() {
        return totalFound - toRemove.size();
    }
}
