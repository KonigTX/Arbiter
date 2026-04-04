package com.arbiter.core;

import com.arbiter.config.ItemTier;
import com.arbiter.config.ResolvedConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.List;

/**
 * Stateless predicate chain that decides whether a ground item should be protected.
 *
 * <p>All methods are static; this class is never instantiated.
 *
 * <p>Evaluation order (first match wins — early return):
 * <ol>
 *   <li>{@code item_overrides} BLACKLIST — always clear, overrides everything.</li>
 *   <li>{@code item_overrides} PROTECT — always protect, overrides everything.</li>
 *   <li>{@code protected_items} list — protect this specific item ID.</li>
 *   <li>{@code protected_namespaces} list — protect entire mod namespace.</li>
 *   <li>{@code namespace_tiers} map — PROTECT tier protects; all other tiers fall through.</li>
 *   <li>Vanilla {@link Rarity} check against {@code protect_rarity_minimum}.</li>
 *   <li>Enchanted item — protect if {@code protect_enchanted_items: true}.</li>
 *   <li>Custom name (anvil rename) — protect if {@code protect_named_items: true}.</li>
 *   <li>Player proximity — protect if within configured radius of any online player.</li>
 *   <li>Default — clear (return false).</li>
 * </ol>
 *
 * <p>Design note: The proximity check is intentionally last because it requires iterating
 * online players — O(players) — whereas rules 1–8 are O(1) lookups.
 */
public final class ItemFilters {

    private ItemFilters() {
        // Utility class — not instantiable
    }

    // ── Primary entry point ───────────────────────────────────────────────────

    /**
     * Returns {@code true} if the item entity should be protected from clearing,
     * {@code false} if it should be removed.
     *
     * @param entity the ground item being evaluated
     * @param world  the world in which the item lives (used for proximity check)
     * @param config the fully-resolved config for that world
     */
    public static boolean shouldProtect(ItemEntity entity, ServerWorld world, ResolvedConfig config) {
        ItemStack stack = entity.getStack();
        if (stack.isEmpty()) {
            // Discard ghost entities with empty stacks
            return false;
        }

        Identifier id = getItemId(stack);

        // ── Rule 1 & 2: item_overrides (highest priority) ─────────────────────
        ItemTier explicitTier = config.itemOverrides.get(id.toString());
        if (explicitTier != null) {
            if (explicitTier.isBlacklisted()) return false; // Rule 1: always clear
            if (explicitTier.isProtected())   return true;  // Rule 2: always protect
            // LOW / NORMAL / HIGH fall through; tier affects priority, not binary protect
        }

        // ── Rule 3: protected_items list (exact ID match) ─────────────────────
        if (config.protectedItems.contains(id.toString())) {
            return true;
        }

        // ── Rule 4: protected_namespaces list ─────────────────────────────────
        if (config.protectedNamespaces.contains(id.getNamespace())) {
            return true;
        }

        // ── Rule 5: namespace_tiers PROTECT entry ─────────────────────────────
        // Non-PROTECT tiers (LOW, NORMAL, HIGH) only affect clearing order — they
        // do not force protection or clearing on their own.
        ItemTier namespaceTier = config.namespaceTiers.get(id.getNamespace());
        if (namespaceTier != null && namespaceTier.isProtected()) {
            return true;
        }

        // ── Rule 6: Vanilla Rarity check ──────────────────────────────────────
        Rarity itemRarity = stack.getOrDefault(DataComponentTypes.RARITY, Rarity.COMMON);
        if (itemRarity.ordinal() >= config.protectRarityMinimum.ordinal()) {
            return true;
        }

        // ── Rule 7: Enchanted item ────────────────────────────────────────────
        if (config.protectEnchantedItems && stack.hasEnchantments()) {
            return true;
        }

        // ── Rule 8: Custom name (anvil rename) ────────────────────────────────
        if (config.protectNamedItems && stack.get(DataComponentTypes.CUSTOM_NAME) != null) {
            return true;
        }

        // ── Rule 9: Player proximity (most expensive — checked last) ──────────
        if (config.playerProximityRadius > 0 && isNearPlayer(entity, world, config.playerProximityRadius)) {
            return true;
        }

        // ── Rule 10: Default — clear ──────────────────────────────────────────
        return false;
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    /**
     * Returns the registry {@link Identifier} for the item in the given stack.
     * Falls back to {@code minecraft:air} for unregistered or empty items.
     */
    public static Identifier getItemId(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem());
    }

    /**
     * Returns the registry namespace string for the item in the given stack,
     * e.g., {@code "minecraft"}, {@code "cobblemon"}, {@code "create"}.
     */
    public static String getNamespace(ItemStack stack) {
        return getItemId(stack).getNamespace();
    }

    /**
     * Returns {@code true} if the item entity is within {@code radiusBlocks} of any
     * online player in the same world.
     *
     * <p>Uses squared distance to avoid the sqrt call — {@code radiusSq = radius * radius}.
     */
    private static boolean isNearPlayer(ItemEntity entity, ServerWorld world, int radiusBlocks) {
        double radiusSq = (double) radiusBlocks * radiusBlocks;
        List<? extends PlayerEntity> players = world.getPlayers();

        for (PlayerEntity player : players) {
            if (player.squaredDistanceTo(entity) <= radiusSq) {
                return true;
            }
        }
        return false;
    }
}
