package com.bhd.arbiter.commands;

import com.bhd.arbiter.config.ConfigManager;
import com.bhd.arbiter.core.ClearanceManager;
import com.bhd.arbiter.core.ItemScanner;
import com.bhd.arbiter.core.StatsTracker;
import com.bhd.arbiter.core.TpsTracker;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import me.lucko.fabric.api.permissions.v0.Permissions;
import java.util.stream.StreamSupport;
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
 * Registers the full {@code /arbiter} Brigadier command tree.
 *
 * <p>Command tree:
 * <pre>
 * /arbiter
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

    private static final Logger LOGGER = LoggerFactory.getLogger("Arbiter/Commands");

    // Permission nodes
    private static final String PERM_FORCE   = "arbiter.force";
    private static final String PERM_STATUS  = "arbiter.status";
    private static final String PERM_RELOAD  = "arbiter.reload";
    private static final String PERM_STATS   = "arbiter.stats";
    private static final String PERM_DRYRUN   = "arbiter.dryrun";
    private static final String PERM_TESTBAR  = "arbiter.testbar";
    private static final String PERM_MODE     = "arbiter.mode";

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
            literal("arbiter")
                // No top-level execution — require a subcommand
                .then(buildForce(clearanceManager))
                .then(buildStatus(clearanceManager, tpsTracker))
                .then(buildReload(configManager, clearanceManager))
                .then(buildStats(statsTracker))
                .then(buildDryRun(clearanceManager))
                .then(buildTestBar(clearanceManager, configManager))
                .then(buildMode(configManager, clearanceManager))
        );

        LOGGER.debug("/arbiter command tree registered");
    }

    // ── /arbiter force [dimension] ──────────────────────────────────────────

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
            () -> Text.literal("Forcing item clear on all loaded worlds...").formatted(Formatting.YELLOW), true);

        clearanceManager.forceAllWorlds(server);

        ctx.getSource().sendFeedback(
            () -> Text.literal("Item clear complete.").formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int executeForceDimension(CommandContext<ServerCommandSource> ctx, ClearanceManager clearanceManager) {
        String dimensionStr = StringArgumentType.getString(ctx, "dimension");
        MinecraftServer server = ctx.getSource().getServer();

        // Attempt to resolve the dimension identifier
        Identifier dimensionId = Identifier.tryParse(dimensionStr);
        if (dimensionId == null) {
            ctx.getSource().sendError(
                Text.literal("Invalid dimension identifier: '" + dimensionStr + "'"));
            return 0;
        }

        Optional<ServerWorld> worldOpt = StreamSupport.stream(server.getWorlds().spliterator(), false)
                .filter(w -> w.getRegistryKey().getValue().equals(dimensionId))
                .findFirst();

        if (worldOpt.isEmpty()) {
            ctx.getSource().sendError(
                Text.literal("Dimension not loaded or not found: '" + dimensionStr + "'"));
            return 0;
        }

        ServerWorld world = worldOpt.get();
        ctx.getSource().sendFeedback(
            () -> Text.literal("Forcing item clear on dimension: " + dimensionStr).formatted(Formatting.YELLOW),
            true);

        clearanceManager.forceWorld(world);

        ctx.getSource().sendFeedback(
            () -> Text.literal("Item clear complete.").formatted(Formatting.GREEN), true);
        return 1;
    }

    // ── /arbiter status ─────────────────────────────────────────────────────

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
            () -> Text.literal("=== Arbiter Status ===").formatted(Formatting.GOLD), false);
        ctx.getSource().sendFeedback(
            () -> Text.literal("Current TPS: " + String.format("%.2f", tps))
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
                () -> Text.literal("  [" + worldName + "] Next clear in: " + displayTime + " | Items on ground: " + itemCount)
                        .formatted(Formatting.AQUA),
                false);
        }
        return 1;
    }

    // ── /arbiter reload ─────────────────────────────────────────────────────

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
            () -> Text.literal("Reloading Arbiter configuration...").formatted(Formatting.YELLOW), true);

        configManager.load();
        clearanceManager.resetAllCounters();

        ctx.getSource().sendFeedback(
            () -> Text.literal("Arbiter configuration reloaded successfully.").formatted(Formatting.GREEN), true);
        LOGGER.info("/arbiter reload executed by {}", ctx.getSource().getName());
        return 1;
    }

    // ── /arbiter stats ──────────────────────────────────────────────────────

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

    // ── /arbiter dryrun [dimension] ────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildDryRun(
            ClearanceManager clearanceManager) {

        return literal("dryrun")
            .requires(Permissions.require(PERM_DRYRUN, OP_VIEWER))
            .executes(ctx -> executeDryRunAll(ctx, clearanceManager))
            .then(
                argument("dimension", StringArgumentType.string())
                    .executes(ctx -> executeDryRunDimension(ctx, clearanceManager))
            );
    }

    private static int executeDryRunAll(
            CommandContext<ServerCommandSource> ctx, ClearanceManager clearanceManager) {

        MinecraftServer server = ctx.getSource().getServer();
        ctx.getSource().sendFeedback(
            () -> Text.literal("[Arbiter DryRun] Scanning all loaded worlds...")
                    .formatted(Formatting.GOLD), false);

        for (ServerWorld world : server.getWorlds()) {
            ClearanceManager.DryRunResult result = clearanceManager.dryRun(world);
            sendDryRunFeedback(ctx.getSource(), world, result);
        }
        return 1;
    }

    private static int executeDryRunDimension(
            CommandContext<ServerCommandSource> ctx, ClearanceManager clearanceManager) {

        String dimensionStr = StringArgumentType.getString(ctx, "dimension");
        MinecraftServer server = ctx.getSource().getServer();

        Identifier dimensionId = Identifier.tryParse(dimensionStr);
        if (dimensionId == null) {
            ctx.getSource().sendError(
                Text.literal("Invalid dimension identifier: '" + dimensionStr + "'"));
            return 0;
        }

        Optional<ServerWorld> worldOpt = StreamSupport.stream(server.getWorlds().spliterator(), false)
                .filter(w -> w.getRegistryKey().getValue().equals(dimensionId))
                .findFirst();

        if (worldOpt.isEmpty()) {
            ctx.getSource().sendError(
                Text.literal("Dimension not loaded or not found: '" + dimensionStr + "'"));
            return 0;
        }

        ServerWorld world = worldOpt.get();
        ClearanceManager.DryRunResult result = clearanceManager.dryRun(world);
        sendDryRunFeedback(ctx.getSource(), world, result);
        return 1;
    }

    // ── /arbiter testbar [seconds] ─────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildTestBar(
            ClearanceManager clearanceManager, ConfigManager configManager) {

        return literal("testbar")
            .requires(Permissions.require(PERM_TESTBAR, OP_ADMIN))
            .executes(ctx -> executeTestBar(ctx, clearanceManager, configManager, -1))
            .then(
                argument("seconds", IntegerArgumentType.integer(1, 300))
                    .executes(ctx -> executeTestBar(ctx, clearanceManager, configManager,
                            IntegerArgumentType.getInteger(ctx, "seconds")))
            );
    }

    private static int executeTestBar(
            CommandContext<ServerCommandSource> ctx,
            ClearanceManager clearanceManager,
            ConfigManager configManager,
            int secondsArg) {

        int seconds = secondsArg;
        if (seconds <= 0) {
            int configured = configManager.getResolvedConfig(net.minecraft.world.World.OVERWORLD).warningStartSeconds;
            seconds = configured > 0 ? configured : 10;
        }

        MinecraftServer server = ctx.getSource().getServer();
        clearanceManager.startTestBar(server, seconds);

        final int finalSeconds = seconds;
        ctx.getSource().sendFeedback(
            () -> Text.literal("[Arbiter] Testbar started for " + finalSeconds + "s (no clear will fire).")
                    .formatted(Formatting.AQUA), false);
        return 1;
    }

    // ── /arbiter mode <modeName> ────────────────────────────────────────────

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildMode(
            ConfigManager configManager, ClearanceManager clearanceManager) {

        return literal("mode")
            .requires(Permissions.require(PERM_MODE, OP_ADMIN))
            .then(
                argument("modeName", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (String name : new String[]{"aggressive", "warden", "calm", "custom"}) {
                            builder.suggest(name);
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> executeMode(ctx, configManager, clearanceManager,
                            StringArgumentType.getString(ctx, "modeName")))
            );
    }

    private static int executeMode(
            CommandContext<ServerCommandSource> ctx,
            ConfigManager configManager,
            ClearanceManager clearanceManager,
            String modeName) {

        java.util.Set<String> valid = java.util.Set.of("aggressive", "warden", "calm", "custom");
        if (!valid.contains(modeName)) {
            ctx.getSource().sendError(
                Text.literal("Unknown mode '" + modeName + "'. Valid modes: aggressive, warden, calm, custom"));
            return 0;
        }

        configManager.saveMode(modeName);
        configManager.load();
        clearanceManager.resetAllCounters();

        ctx.getSource().sendFeedback(
            () -> Text.literal("[Arbiter] Mode set to '" + modeName + "' — config reloaded.")
                    .formatted(Formatting.GREEN), true);
        return 1;
    }

    private static void sendDryRunFeedback(
            ServerCommandSource source, ServerWorld world, ClearanceManager.DryRunResult result) {

        String worldName = world.getRegistryKey().getValue().toString();

        // Header
        source.sendFeedback(
            () -> Text.literal("[Arbiter DryRun] " + worldName)
                    .formatted(Formatting.AQUA), false);

        // Total found
        final int totalFound = result.totalFound();
        source.sendFeedback(
            () -> Text.literal("  Total items found : " + totalFound)
                    .formatted(Formatting.WHITE), false);

        // Would clear / would protect
        final int wouldClear   = result.wouldClear();
        final int wouldProtect = result.wouldProtect();

        if (result.thresholdSkipped()) {
            source.sendFeedback(
                () -> Text.literal("  Would clear       : " + wouldClear)
                        .formatted(Formatting.WHITE), false);
            source.sendFeedback(
                () -> Text.literal("  Would protect     : " + wouldProtect)
                        .formatted(Formatting.WHITE), false);

            final int threshold = result.threshold();
            source.sendFeedback(
                () -> Text.literal("  Threshold check   : SKIPPED — "
                        + totalFound + " < " + threshold + " (itemCountThreshold)")
                        .formatted(Formatting.YELLOW), false);
        } else {
            source.sendFeedback(
                () -> Text.literal("  Would CLEAR       : " + wouldClear + " items")
                        .formatted(Formatting.RED), false);
            source.sendFeedback(
                () -> Text.literal("  Would protect     : " + wouldProtect)
                        .formatted(Formatting.WHITE), false);
        }

        // Protection breakdown header
        source.sendFeedback(
            () -> Text.literal("  Protection breakdown:")
                    .formatted(Formatting.GRAY), false);

        // Per-rule lines (all entries, including zeros)
        for (java.util.Map.Entry<String, Integer> entry : result.protectionBreakdown().entrySet()) {
            final String label = entry.getKey();
            final int count = entry.getValue();
            // "cleared" is not a protection rule — skip it in the protection block
            if ("cleared".equals(label)) continue;
            source.sendFeedback(
                () -> Text.literal("    " + label + " : " + count)
                        .formatted(Formatting.WHITE), false);
        }
    }
}
