package com.arbiter.config;

/**
 * Clearing priority tier for items.
 *
 * <p>Priority ordering (highest priority wins in the predicate chain):
 * <ul>
 *   <li>{@link #BLACKLIST} — always clear, overrides every protection rule</li>
 *   <li>{@link #PROTECT} — always protect, overrides every clear rule</li>
 *   <li>{@link #HIGH} — present on the ground but cleared last in a multi-pass cycle</li>
 *   <li>{@link #NORMAL} — default tier for all items not explicitly configured</li>
 *   <li>{@link #LOW} — junk items; cleared first when the cycle starts</li>
 * </ul>
 *
 * <p>The {@code priority} integer field is used for ordering comparisons;
 * it does NOT represent a sequential predicate-chain step.
 */
public enum ItemTier {

    /** Always clear this item regardless of any other protection rule. */
    BLACKLIST(3),

    /** Always protect this item regardless of any other clear rule. */
    PROTECT(-1),

    /** Cleared last in the cycle; lower priority than NORMAL for removal. */
    HIGH(2),

    /** Default tier — cleared on schedule with no special treatment. */
    NORMAL(1),

    /** Cleared first in the cycle; treated as disposable junk. */
    LOW(0);

    /** Numeric priority used for ordering; higher value = cleared sooner. */
    public final int priority;

    ItemTier(int priority) {
        this.priority = priority;
    }

    /**
     * Returns true when this tier means the item should be protected from clearing.
     */
    public boolean isProtected() {
        return this == PROTECT;
    }

    /**
     * Returns true when this tier forces the item to be cleared unconditionally.
     */
    public boolean isBlacklisted() {
        return this == BLACKLIST;
    }
}
