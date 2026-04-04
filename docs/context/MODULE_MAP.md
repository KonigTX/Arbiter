# Module Map — AutoClear

## ClearanceManager.java
```java
public ClearanceManager(ConfigManager, TpsTracker, BossBarHandler, StatsTracker)
public void register()                          // registers END_WORLD_TICK + END_SERVER_TICK
public void resetAllCounters()                  // hot-reload callback
private void onWorldTickEnd(ServerWorld)        // main per-world tick loop
private void onServerTickEnd(MinecraftServer)   // TPS check every 100 ticks
public void executeClear(ServerWorld, ResolvedConfig, boolean emergency)
public void triggerEmergencyClear(MinecraftServer)
public void forceAllWorlds(MinecraftServer)     // /autoclear force (all)
public void forceWorld(ServerWorld)             // /autoclear force <dim>
public int getSecondsUntilClear(RegistryKey<World>)
private void beginWarningPhase(ServerWorld, ResolvedConfig)
```

## ItemFilters.java
```java
public static boolean shouldProtect(ItemEntity, ServerWorld, ResolvedConfig)
// 10-rule chain: BLACKLIST override → PROTECT override → protectedItems →
// protectedNamespaces → namespaceTier PROTECT → rarity → enchanted → named → proximity → clear
public static Identifier getItemId(ItemStack)
public static String getNamespace(ItemStack)
private static boolean isNearPlayer(ItemEntity, ServerWorld, int radiusBlocks)
```

## ItemScanner.java
```java
public static ScanResult scan(ServerWorld, ResolvedConfig)
// Uses world.getEntitiesByType(TypeFilter.instanceOf(ItemEntity.class), e->true)
// Returns List<? extends ItemEntity>
```

## AutoClearCommands.java — existing subcommands
```
/autoclear force [dimension]   → clearanceManager.forceWorld() or forceAllWorlds()
/autoclear status              → prints TPS, seconds-until-clear, item count per world
/autoclear reload              → configManager.load() + resetAllCounters()
/autoclear stats               → statsTracker summary
```

## AutoClearConfig.java — all fields
```java
int clearIntervalSeconds = 300
int warningStartSeconds = 30
int itemCountThreshold = 100
double emergencyTpsThreshold = 15.0
int emergencyClearCooldownSeconds = 60
boolean protectNamedItems = true
boolean protectEnchantedItems = true
String protectRarityMinimum = "RARE"
int playerProximityRadius = 10
List<String> protectedNamespaces
List<String> protectedItems
Map<String,String> namespaceTiers
Map<String,String> itemOverrides
boolean bossbarEnabled = true
String bossbarStyle = "NOTCHED_10"
int bossbarYellowThresholdSeconds = 15
int bossbarRedThresholdSeconds = 5
boolean actionbarWarningsEnabled = false
Map<String,DimensionOverride> dimensions
boolean logClearsToConsole = true
boolean broadcastClearResults = true
```
