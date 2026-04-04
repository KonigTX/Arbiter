package com.arbiter.config;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonGrammar;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.api.SyntaxError;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages loading, saving, and hot-reloading of {@code config/arbiter.json5}.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Jankson-based JSON5 parse/write with comment preservation.</li>
 *   <li>Creates the file with defaults if it does not exist on first load.</li>
 *   <li>Validates and clamps numeric fields after every load.</li>
 *   <li>Provides {@link #getResolvedConfig(RegistryKey)} for per-world config consumers.</li>
 *   <li>Runs a {@link WatchService} background thread that reloads the file within ~1s of changes.</li>
 * </ul>
 */
public class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Arbiter/ConfigManager");
    private static final String CONFIG_FILENAME = "arbiter.json5";

    private final Jankson jankson;
    private final Path configPath;

    /** Atomic reference allows lock-free reads from game threads while the watcher writes. */
    private final AtomicReference<ArbiterConfig> configRef = new AtomicReference<>(new ArbiterConfig());

    /** Callback invoked on the next server tick after a hot-reload completes. */
    private volatile Runnable onReloadCallback = null;

    public ConfigManager() {
        this.jankson = Jankson.builder().build();
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILENAME);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Loads (or creates) {@code config/arbiter.json5}. Safe to call at any time;
     * the hot-reload thread also calls this when the file changes.
     */
    public void load() {
        if (!Files.exists(configPath)) {
            writeDefaults();
        }

        try (InputStream in = Files.newInputStream(configPath)) {
            JsonObject parsed = jankson.load(in);
            ArbiterConfig loaded = jankson.fromJson(parsed, ArbiterConfig.class);
            applyModePreset(loaded);
            loaded.clampToValidRanges();
            configRef.set(loaded);
            LOGGER.info("Arbiter config loaded from {}", configPath);
        } catch (IOException | SyntaxError e) {
            LOGGER.error("Failed to load arbiter.json5 - using previous/default config", e);
        }
    }

    /**
     * Returns a fully-resolved configuration for the given world.
     * Merges the global config with any dimension override registered for {@code worldKey}.
     */
    public ResolvedConfig getResolvedConfig(RegistryKey<World> worldKey) {
        ArbiterConfig global = configRef.get();
        String dimensionId = worldKey.getValue().toString(); // e.g. "minecraft:the_nether"
        DimensionOverride override = global.dimensions.get(dimensionId);
        return ResolvedConfig.merge(global, override);
    }

    /**
     * Returns the raw global config. Prefer {@link #getResolvedConfig} for actual gameplay logic.
     */
    public ArbiterConfig getRawConfig() {
        return configRef.get();
    }

    /**
     * Overwrites fields in {@code config} according to its {@code mode} value.
     * Must be called after Jankson deserialization and before {@code clampToValidRanges()}.
     * "custom" is a no-op. Unknown strings are logged as a warning.
     */
    public void applyModePreset(ArbiterConfig config) {
        switch (config.mode) {
            case "custom" -> { /* no-op - honour all fields as loaded */ }
            case "aggressive" -> {
                config.clearIntervalSeconds = 60;
                config.warningStartSeconds = 8;
                config.itemCountThreshold = 0;
                config.protectNamedItems = false;
                config.protectEnchantedItems = false;
                config.protectRarityMinimum = "EPIC";
                config.playerProximityRadius = 2;
                config.protectedNamespaces = new java.util.ArrayList<>();
                config.protectedItems = new java.util.ArrayList<>();
                config.itemOverrides = new java.util.HashMap<>();
                config.emergencyTpsThreshold = 18.0;
                config.emergencyClearCooldownSeconds = 30;
                config.bossbarEnabled = true;
                config.bossbarStyle = "PROGRESS";
                config.broadcastClearResults = true;
                config.logClearsToConsole = true;
            }
            case "warden" -> {
                config.clearIntervalSeconds = 180;
                config.warningStartSeconds = 20;
                config.itemCountThreshold = 50;
                config.protectNamedItems = true;
                config.protectEnchantedItems = true;
                config.protectRarityMinimum = "RARE";
                config.playerProximityRadius = 8;
                config.protectedNamespaces = new java.util.ArrayList<>(java.util.List.of("cobblemon"));
                config.protectedItems = new java.util.ArrayList<>(java.util.List.of(
                        "minecraft:nether_star",
                        "minecraft:elytra",
                        "minecraft:dragon_egg",
                        "minecraft:totem_of_undying"
                ));
                config.itemOverrides = new java.util.HashMap<>(java.util.Map.of(
                        "minecraft:dirt", "BLACKLIST",
                        "minecraft:cobblestone", "BLACKLIST"
                ));
                config.emergencyTpsThreshold = 15.0;
                config.emergencyClearCooldownSeconds = 60;
                config.bossbarEnabled = true;
                config.bossbarStyle = "NOTCHED_10";
                config.broadcastClearResults = true;
                config.logClearsToConsole = true;
            }
            case "calm" -> {
                config.clearIntervalSeconds = 600;
                config.warningStartSeconds = 60;
                config.itemCountThreshold = 200;
                config.protectNamedItems = true;
                config.protectEnchantedItems = true;
                config.protectRarityMinimum = "UNCOMMON";
                config.playerProximityRadius = 20;
                config.protectedNamespaces = new java.util.ArrayList<>(java.util.List.of("cobblemon", "create"));
                config.protectedItems = new java.util.ArrayList<>(java.util.List.of(
                        "minecraft:nether_star",
                        "minecraft:elytra",
                        "minecraft:dragon_egg",
                        "minecraft:totem_of_undying",
                        "minecraft:beacon",
                        "minecraft:shulker_shell",
                        "minecraft:heart_of_the_sea"
                ));
                config.itemOverrides = new java.util.HashMap<>();
                config.emergencyTpsThreshold = 8.0;
                config.emergencyClearCooldownSeconds = 120;
                config.bossbarEnabled = true;
                config.bossbarStyle = "NOTCHED_20";
                config.broadcastClearResults = true;
                config.logClearsToConsole = true;
            }
            default -> LOGGER.warn("Arbiter: unknown mode '{}' in config - ignoring (using loaded field values)", config.mode);
        }
    }

    /**
     * Writes a minimal JSON5 file containing only the {@code mode} key to {@code configPath}.
     * On next load, Jankson reads the mode and {@link #applyModePreset} fills all other fields.
     */
    public void saveMode(String modeName) {
        if ("custom".equals(modeName)) {
            writeDefaults(); // restore the full annotated default config
            LOGGER.info("Arbiter: restored default config (mode=custom)");
            return;
        }
        String content = "// Arbiter preset - hot-reload will apply immediately\n"
                + "{ \"mode\": \"" + modeName + "\" }\n";
        try {
            Files.writeString(configPath, content);
            LOGGER.info("Arbiter: saved mode '{}' to {}", modeName, configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to write mode to arbiter.json5", e);
        }
    }

    /**
     * Registers a callback that fires after each successful hot-reload.
     * Used by {@code ClearanceManager} to reset tick counters when intervals change.
     */
    public void setOnReloadCallback(Runnable callback) {
        this.onReloadCallback = callback;
    }

    /**
     * Starts the {@link WatchService} background thread that monitors the config file.
     * Call once during mod initialization.
     */
    public void startWatchThread() {
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Arbiter-ConfigWatcher");
            t.setDaemon(true);
            return t;
        }).submit(this::watchLoop);
        LOGGER.info("Arbiter config watch thread started");
    }

    // ── Config file write ─────────────────────────────────────────────────────

    private void writeDefaults() {
        String output = """
// Arbiter configuration - changes are hot-reloaded automatically (no restart needed)
{
    // ── Preset Mode ─────────────────────────────────────────────────────────
    // Quick-start preset. Options: "custom", "aggressive", "warden", "calm"
    // "custom" uses all fields below as-is. Any other value overwrites them on load.
    // Use: /arbiter mode <name>
    "mode": "custom",

    // ── Timing ──────────────────────────────────────────────────────────────
    // How often to run a scheduled clear, in seconds.
    "clearIntervalSeconds": 300,
    // How many seconds before the clear to show the bossbar countdown.
    "warningStartSeconds": 30,

    // ── Thresholds ───────────────────────────────────────────────────────────
    // Minimum number of item entities on the ground to trigger a clear. 0 = always clear.
    "itemCountThreshold": 100,
    // Auto-clear when TPS drops below this value. 0.0 = disabled.
    "emergencyTpsThreshold": 15.0,
    // Minimum seconds between successive emergency clears.
    "emergencyClearCooldownSeconds": 60,

    // ── Item Protection ──────────────────────────────────────────────────────
    // Protect items that have been renamed at an anvil.
    "protectNamedItems": true,
    // Protect items that carry any enchantment.
    "protectEnchantedItems": true,
    // Minimum vanilla rarity to auto-protect.
    // COMMON = most items | UNCOMMON = saddles/name tags/golden apples
    // RARE = tridents/music discs/ender pearls/elytra | EPIC = enchanted golden apple only
    "protectRarityMinimum": "RARE",
    // Protect items within this many blocks of any online player. 0 = disabled.
    "playerProximityRadius": 10,

    // ── Mod & Item Lists ─────────────────────────────────────────────────────
    // All items from these mod namespaces are always protected.
    "protectedNamespaces": [
        "cobblemon",
    ],
    // Specific item IDs that are always protected (regardless of rarity).
    "protectedItems": [
        "minecraft:nether_star",
        "minecraft:elytra",
        "minecraft:dragon_egg",
        "minecraft:totem_of_undying",
    ],
    // Per-namespace clearing priority. Options: PROTECT, LOW, NORMAL, HIGH, BLACKLIST
    "namespaceTiers": {
        "minecraft": "NORMAL",
        "create": "NORMAL",
    },
    // Per-item overrides - these win over ALL other rules.
    // BLACKLIST = always clear | PROTECT = always keep
    "itemOverrides": {
        "minecraft:dirt": "BLACKLIST",
        "minecraft:cobblestone": "BLACKLIST",
        "minecraft:netherite_ingot": "PROTECT",
    },

    // ── Display ──────────────────────────────────────────────────────────────
    // Show a bossbar countdown before each scheduled clear.
    "bossbarEnabled": true,
    // Bossbar segment style. Options: PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20
    "bossbarStyle": "NOTCHED_10",
    // Seconds at which the bossbar turns yellow.
    "bossbarYellowThresholdSeconds": 15,
    // Seconds at which the bossbar turns red.
    "bossbarRedThresholdSeconds": 5,
    // Show an actionbar message in addition to the bossbar.
    "actionbarWarningsEnabled": false,

    // ── Per-Dimension Overrides ──────────────────────────────────────────────
    // Override any setting per dimension. Omitted fields inherit global values.
    // Example: "minecraft:the_nether": { "clearIntervalSeconds": 180 }
    "dimensions": { },

    // ── Logging & Broadcast ──────────────────────────────────────────────────
    // Log clear results to the server console.
    "logClearsToConsole": true,
    // Broadcast clear results to all players in chat.
    "broadcastClearResults": true,
}
""";
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, output);
            LOGGER.info("Created default config at {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to write default arbiter.json5", e);
        }
    }

    // ── WatchService hot-reload loop ──────────────────────────────────────────

    private void watchLoop() {
        Path configDir = configPath.getParent();
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            configDir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);

            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watcher.take(); // blocks until an event arrives
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                    String changedFile = event.context().toString();
                    if (!CONFIG_FILENAME.equals(changedFile)) continue;

                    // Brief delay to let the editor finish writing the file
                    try { Thread.sleep(500); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    LOGGER.info("Config file modified - reloading arbiter.json5");
                    load();

                    Runnable cb = onReloadCallback;
                    if (cb != null) {
                        cb.run();
                    }
                }

                if (!key.reset()) {
                    LOGGER.warn("Config directory watch key invalidated - hot-reload disabled");
                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to initialise config WatchService - hot-reload disabled", e);
        }
    }
}
