package com.bhd.autoclear.config;

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
 * Manages loading, saving, and hot-reloading of {@code config/autoclear.json5}.
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

    private static final Logger LOGGER = LoggerFactory.getLogger("AutoClear/ConfigManager");
    private static final String CONFIG_FILENAME = "autoclear.json5";

    private final Jankson jankson;
    private final Path configPath;

    /** Atomic reference allows lock-free reads from game threads while the watcher writes. */
    private final AtomicReference<AutoClearConfig> configRef = new AtomicReference<>(new AutoClearConfig());

    /** Callback invoked on the next server tick after a hot-reload completes. */
    private volatile Runnable onReloadCallback = null;

    public ConfigManager() {
        this.jankson = Jankson.builder().build();
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILENAME);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Loads (or creates) {@code config/autoclear.json5}. Safe to call at any time;
     * the hot-reload thread also calls this when the file changes.
     */
    public void load() {
        if (!Files.exists(configPath)) {
            writeDefaults();
        }

        try (InputStream in = Files.newInputStream(configPath)) {
            JsonObject parsed = jankson.load(in);
            AutoClearConfig loaded = jankson.fromJson(parsed, AutoClearConfig.class);
            loaded.clampToValidRanges();
            configRef.set(loaded);
            LOGGER.info("AutoClear config loaded from {}", configPath);
        } catch (IOException | SyntaxError e) {
            LOGGER.error("Failed to load autoclear.json5 — using previous/default config", e);
        }
    }

    /**
     * Returns a fully-resolved configuration for the given world.
     * Merges the global config with any dimension override registered for {@code worldKey}.
     */
    public ResolvedConfig getResolvedConfig(RegistryKey<World> worldKey) {
        AutoClearConfig global = configRef.get();
        String dimensionId = worldKey.getValue().toString(); // e.g. "minecraft:the_nether"
        DimensionOverride override = global.dimensions.get(dimensionId);
        return ResolvedConfig.merge(global, override);
    }

    /**
     * Returns the raw global config. Prefer {@link #getResolvedConfig} for actual gameplay logic.
     */
    public AutoClearConfig getRawConfig() {
        return configRef.get();
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
            Thread t = new Thread(r, "AutoClear-ConfigWatcher");
            t.setDaemon(true);
            return t;
        }).submit(this::watchLoop);
        LOGGER.info("AutoClear config watch thread started");
    }

    // ── Config file write ─────────────────────────────────────────────────────

    private void writeDefaults() {
        try {
            AutoClearConfig defaults = new AutoClearConfig();
            JsonObject json = (JsonObject) jankson.toJson(defaults);
            // Inject top-level comment
            String output = "// AutoClear configuration — changes are hot-reloaded automatically (no restart needed)\n"
                    + json.toJson(JsonGrammar.JSON5);
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, output);
            LOGGER.info("Created default config at {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to write default autoclear.json5", e);
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

                    LOGGER.info("Config file modified — reloading autoclear.json5");
                    load();

                    Runnable cb = onReloadCallback;
                    if (cb != null) {
                        cb.run();
                    }
                }

                if (!key.reset()) {
                    LOGGER.warn("Config directory watch key invalidated — hot-reload disabled");
                    break;
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to initialise config WatchService — hot-reload disabled", e);
        }
    }
}
