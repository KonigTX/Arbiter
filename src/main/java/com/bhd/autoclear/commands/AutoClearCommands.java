package com.bhd.autoclear.commands;

import com.bhd.autoclear.config.ConfigManager;
import com.bhd.autoclear.core.ClearanceManager;
import com.bhd.autoclear.core.ItemScanner;
import com.bhd.autoclear.core.StatsTracker;
import com.bhd.autoclear.core.TpsTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registers the full {@code /autoclear} Brigadier command tree.
 *
 * <p>Command tree:
 * <pre>
 * /autoclear
 *   force                    — clear all loaded worlds immediately (autoclear.force, OP 4)
 *     [dimension]            — optional: e.g., "minecraft:the_nether"
 *   status                   — time until next clear, TPS, item counts (autoclear.status, OP 2)
 *   reload                   — reload config from disk (autoclear.reload, OP 4)
 *   stats                    — session statistics: clears run, items removed (autoclear.stats, OP 2)
 * </pre>
 *
 * <p>Permission nodes use {@link Permissions#require(String, int)} from fabric-permissions-api,
 * which bridges transparently to LuckPerms when present and falls back to OP-level checks otherwise.
 */
public final class AutoClearCommands {

    private static final Logger LOGGER = LoggerFactory.getLogger("AutoClear/Commands");

    // Permission nodes
    private static final String PERM_FORCE  = "autoclear.force";
    private static final String PERM_STATUS = "autoclear.status";
    private static final String PERM_RELOAD = "autoclear.reload";
    private static final String PERM_STATS  = "autoclear.stats";

    // OP fallback levels
    private static final int OP_ADMIN  = 4;
    private static final int OP_VIEWER = 2;

    private AutoClearCommands() {
        // Static registration class — not instantiable
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers the command tree via {@link CommandRegistrationCallback}.
     * Call once during mod initialization, before the server starts.
     *
     * @param configManager    used by {@code reload} subcommand
     * @param clearanceManager used by {@code force} subcommand
     * @param statsTracker     used by {@code stats} subcommand
     * @param tpsTracker       used by {@code status} subcommand
     */
    public static void register(
            ConfigManager configManager,
            ClearanceManager clearanceManager,
            StatsTracker statsTracker,
            TpsTracker tpsTracker) {

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                buildTree(dispatcher, configManager, clearanceManager, statsTracker, tpsTracker));
    }

    // ── Tree construction ─────────────────────────────────────────────────────

    private static void buildTree(
            CommandDispatcher<ServerCommandSource> dispatcher,
            ConfigManager configManager,
            ClearanceManager clearanceManager,
            StatsTracker statsTracker,
            TpsTracker tpsTracker) {

        dispatcher.register(
            literal("autoclear")
                // No top-level execution — require a subcommand
                .then(buildForce(clearanceManager))
                .then(buildStatus(clearanceManager, tpsTracker))
                .then(buildReload(configManager, clearanceManager))
                .then(buildStats(statsTracker))
        );

        LOGGER.debug("/autoclear command tree registered");
    }

    // ── /autoclear force [dimension] ──────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildForce(
            ClearanceManager clearanceManager) {

        return literal("force")
            .requires(Permissions.require(PERM_FORCE, OP_ADMIN))
            .executes(ctx -> executeForceAll(ctx, clearanceManager))
            .then(
                argument("dimension", StringArgumentType.string())
                    .executes(ctx -> executeForceDimension(ctx, clearanceManager))
            );
    }

    private static int executeForceAll(CommandContext<ServerCommandSource> ctx, ClearanceManager clearanceManager) {
        MinecraftServer server = ctx.getSource().getServer();
        ctx.getSource().sendFeedback(
            () -> Text.translatable("autoclear.command.force.all").formatted(Formatting.YELLOW), true);

        clearanceManager.forceAllWorlds(server);

        ctx.getSource().sendFeedback(
            () -> Text.translatable("autoclear.command.force.done").formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int executeForceDimension(CommandContext<ServerCommandSource> ctx, ClearanceManager clearanceManager) {
        String dimensionStr = StringArgumentType.getString(ctx, "dimension");
        MinecraftServer server = ctx.getSource().getServer();

        // Attempt to resolve the dimension identifier
        Identifier dimensionId = Identifier.tryParse(dimensionStr);
        if (dimensionId == null) {
            ctx.getSource().sendError(
                Text.translatable("autoclear.command.force.invalid_dimension", dimensionStr));
            return 0;
        }

        Optional<ServerWorld> worldOpt = server.getWorlds().stream()
                .filter(w -> w.getRegistryKey().getValue().equals(dimensionId))
                .findFirst();

        if (worldOpt.isEmpty()) {
            ctx.getSource().sendError(
                Text.translatable("autoclear.command.force.world_not_found", dimensionStr));
            return 0;
        }

        ServerWorld world = worldOpt.get();
        ctx.getSource().sendFeedback(
            () -> Text.translatable("autoclear.command.force.dimension", dimensionStr).formatted(Formatting.YELLOW),
            true);

        clearanceManager.forceWorld(world);

        ctx.getSource().sendFeedback(
            () -> Text.translatable("autoclear.command.force.done").formatted(Formatting.GREEN), true);
        return 1;
    }

    // ── /autoclear status ─────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildStatus(
            ClearanceManager clearanceManager,
            TpsTracker tpsTracker) {

        return literal("status")
            .requires(Permissions.require(PERM_STATUS, OP_VIEWER))
            .executes(ctx -> executeStatus(ctx, clearanceManager, tpsTracker));
    }

    private static int executeStatus(
            CommandContext<ServerCommandSource> ctx,
            ClearanceManager clearanceManager,
            TpsTracker tpsTracker) {

        MinecraftServer server = ctx.getSource().getServer();
        double tps = tpsTracker.getCurrentTps();

        ctx.getSource().sendFeedback(
            () -> Text.translatable("autoclear.command.status.header").formatted(Formatting.GOLD), false);
        ctx.getSource().sendFeedback(
            () -> Text.translatable("autoclear.command.status.tps", String.format("%.2f", tps))
                    .formatted(tps >= 18.0 ? Formatting.GREEN : tps >= 14.0 ? Formatting.YELLOW : Formatting.RED),
            false);

        for (ServerWorld world : server.getWorlds()) {
            RegistryKey<World> key = world.getRegistryKey();
            String worldName = key.getValue().toString();
            int secondsRemaining = clearanceManager.getSecondsUntilClear(key);
            int itemCount = ItemScanner.countAll(world);

            final String displayTime = secondsRemaining >= 0
                    ? secondsRemaining + "s"
                    : "pending";

            ctx.getSource().sendFeedback(
                () -> Text.translatable("autoclear.command.status.world", worldName, displayTime, itemCount)
                        .formatted(Formatting.AQUA),
                false);
        }
        return 1;
    }

    // ── /autoclear reload ─────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildReload(
            ConfigManager configManager,
            ClearanceManager clearanceManager) {

        return literal("reload")
            .requires(Permissions.require(PERM_RELOAD, OP_ADMIN))
            .executes(ctx -> executeReload(ctx, configManager, clearanceManager));
    }

    private static int executeReload(
            CommandContext<ServerCommandSource> ctx,
            ConfigManager configManager,
            ClearanceManager clearanceManager) {

        ctx.getSource().sendFeedback(
            () -> Text.translatable("autoclear.command.reload.reloading").formatted(Formatting.YELLOW), true);

        configManager.load();
        clearanceManager.resetAllCounters();

        ctx.getSource().sendFeedback(
            () -> Text.translatable("autoclear.command.reload.done").formatted(Formatting.GREEN), true);
        LOGGER.info("/autoclear reload executed by {}", ctx.getSource().getName());
        return 1;
    }

    // ── /autoclear stats ──────────────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildStats(
            StatsTracker statsTracker) {

        return literal("stats")
            .requires(Permissions.require(PERM_STATS, OP_VIEWER))
            .executes(ctx -> executeStats(ctx, statsTracker));
    }

    private static int executeStats(CommandContext<ServerCommandSource> ctx, StatsTracker statsTracker) {
        String statsOutput = statsTracker.getFormattedStats();
        // Split on newline and send each line as a separate feedback message
        for (String line : statsOutput.split("\n")) {
            final String finalLine = line;
            ctx.getSource().sendFeedback(
                () -> Text.literal(finalLine).formatted(Formatting.WHITE), false);
        }
        return 1;
    }
}
