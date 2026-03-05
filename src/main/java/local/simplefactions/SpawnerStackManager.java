package local.simplefactions;

import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.server.RemoteServerCommandEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages spawner stacks and mob stacking.
 *
 * <h3>Spawner stacking</h3>
 * A block can hold up to {@link SpawnerStack#MAX_STACK} spawners of the same
 * mob type. Stack count is stored here; the physical block remains a single
 * SPAWNER block. Spawn frequency scales linearly with count (1× to 10×).
 *
 * <h3>Mob stacking</h3>
 * Each chunk holds at most one "mob-stack entity" per mob type.  When a
 * spawner fires, its mob merges into the existing chunk stack instead of
 * creating a second entity.  When the representative mob dies, a
 * replacement with (count-1) is immediately created.  When a stacked mob
 * wanders across a chunk boundary, it is merged with the destination chunk's
 * existing stack (if any).
 */
public class SpawnerStackManager implements Listener {

    // ── PersistentDataContainer keys (tagged on stack-representative mobs) ────
    private static final NamespacedKey KEY_STACK_MOB   = new NamespacedKey("simplefactions", "stack_mob");
    private static final NamespacedKey KEY_STACK_TYPE  = new NamespacedKey("simplefactions", "stack_type");
    private static final NamespacedKey KEY_STACK_COUNT = new NamespacedKey("simplefactions", "stack_count");

    private final JavaPlugin plugin;

    // ── Spawner data ──────────────────────────────────────────────────────────
    /** "world:x:y:z" → SpawnerStack */
    private final Map<String, SpawnerStack> stacks = new HashMap<>();
    /** "locationKey" → ticks until next spawn (counts DOWN; reset when ≤ 0) */
    private final Map<String, Integer> spawnCountdowns = new HashMap<>();

    // ── Mob-stack tracking ────────────────────────────────────────────────────
    /** "world:chunkX:chunkZ:entityType" → UUID of the stack entity */
    private final Map<String, UUID> chunkMobLeader = new HashMap<>();
    /** Reverse map: mob UUID → the chunkMobLeader key it is registered under */
    private final Map<UUID, String> mobToChunkKey = new HashMap<>();
    /** mob UUID → how many real mobs this entity represents */
    private final Map<UUID, Integer> mobStackCount = new HashMap<>();
    private boolean suppressStackRespawn = false;

    private BukkitTask spawnTask;
    private BukkitTask mergeTask;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public SpawnerStackManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Tick every 20 ticks (1 second); decrement each stack's countdown
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSpawners, 20L, 20L);

        // Check for cross-chunk mob merges near-instantly
        mergeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMobMerge, 5L, 5L);
    }

    public void stop() {
        if (spawnTask  != null) spawnTask.cancel();
        if (mergeTask  != null) mergeTask.cancel();
    }

    // ── Stack registration ────────────────────────────────────────────────────

    /**
     * Called when a spawner block is placed in faction territory for the first
     * time (count = 1). If a stack of the same type already exists here (edge
     * case), it increments instead.
     */
    public SpawnerStack placeSpawner(String world, int x, int y, int z,
                                     String entityTypeKey, String factionName) {
        String key = locKey(world, x, y, z);
        SpawnerStack existing = stacks.get(key);
        if (existing != null && existing.getEntityTypeKey().equalsIgnoreCase(entityTypeKey)) {
            existing.addSpawner();
            int newInterval = existing.getSpawnIntervalTicks();
            int currentCd = spawnCountdowns.getOrDefault(key, newInterval);
            spawnCountdowns.put(key, Math.min(currentCd, newInterval));
            return existing;
        }
        SpawnerStack stack = new SpawnerStack(key, entityTypeKey, factionName);
        stack.addSpawner();
        stacks.put(key, stack);
        spawnCountdowns.put(key, stack.getSpawnIntervalTicks());
        return stack;
    }

    /**
     * Attempt to add one spawner of {@code entityTypeKey} to the existing
     * stack at this location.
     * @return {@code true} if the stack was incremented, {@code false} if it
     *         does not exist, is the wrong type, or is already at max.
     */
    public boolean tryStack(String world, int x, int y, int z, String entityTypeKey) {
        String key = locKey(world, x, y, z);
        SpawnerStack stack = stacks.get(key);
        if (stack == null) return false;
        if (!stack.getEntityTypeKey().equalsIgnoreCase(entityTypeKey)) return false;
        boolean added = stack.addSpawner();
        if (added) {
            int newInterval = stack.getSpawnIntervalTicks();
            int currentCd = spawnCountdowns.getOrDefault(key, newInterval);
            spawnCountdowns.put(key, Math.min(currentCd, newInterval));
        }
        return added;
    }

    /** @return the stack at this block, or {@code null}. */
    public SpawnerStack getStack(String world, int x, int y, int z) {
        return stacks.get(locKey(world, x, y, z));
    }

    /**
     * Remove the top-most (most recently added) individual spawner from the
     * stack. If the stack is now empty, it is deleted from the map.
     * @return the placement timestamp of the removed spawner, or {@code -1}.
     */
    public long removeTop(String world, int x, int y, int z) {
        String key = locKey(world, x, y, z);
        SpawnerStack stack = stacks.get(key);
        if (stack == null) return -1L;
        long ts = stack.removeTop();
        if (stack.isEmpty()) {
            stacks.remove(key);
            spawnCountdowns.remove(key);
        }
        return ts;
    }

    /**
     * Remove the entire stack entry (faction disband, chunk unclaim, etc.).
     * @return the removed stack, or {@code null}.
     */
    public SpawnerStack removeStack(String world, int x, int y, int z) {
        String key = locKey(world, x, y, z);
        spawnCountdowns.remove(key);
        return stacks.remove(key);
    }

    // ── Value calculation ─────────────────────────────────────────────────────

    public double getTotalValueForFaction(String factionName) {
        if (factionName == null) return 0;
        String name = factionName.toLowerCase();
        double total = 0;
        for (SpawnerStack s : stacks.values()) {
            if (s.getFactionName().equals(name)) {
                total += s.getTotalCurrentValue();
            }
        }
        return total;
    }

    public Collection<SpawnerStack> getAllStacks() {
        return Collections.unmodifiableCollection(stacks.values());
    }

    /** Total number of individual spawners (across all stacks) owned by a faction. */
    public int getTotalCountForFaction(String factionName) {
        if (factionName == null) return 0;
        String name = factionName.toLowerCase();
        int total = 0;
        for (SpawnerStack s : stacks.values()) {
            if (s.getFactionName().equals(name)) total += s.getCount();
        }
        return total;
    }

    /**
     * Spawner breakdown for hover display, sorted by value descending.
     * Map key = display name (e.g. "Zombie Spawner"), value = [count, totalValue].
     */
    public Map<String, double[]> getBreakdownForFaction(String factionName) {
        if (factionName == null) return Collections.emptyMap();
        String name = factionName.toLowerCase();
        Map<String, double[]> raw = new LinkedHashMap<>();
        for (SpawnerStack s : stacks.values()) {
            if (!s.getFactionName().equals(name)) continue;
            SpawnerType st = SpawnerType.fromEntityKey(s.getEntityTypeKey());
            String displayName = (st != null ? st.getDisplayName() : s.getEntityTypeKey()) + " Spawner";
            double[] data = raw.computeIfAbsent(displayName, k -> new double[2]);
            data[0] += s.getCount();
            data[1] += s.getTotalCurrentValue();
        }
        // Sort by value descending
        return raw.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1]))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
    }

    // ── Spawner tick ──────────────────────────────────────────────────────────

    private void tickSpawners() {
        // Snapshot keys to avoid ConcurrentModificationException
        for (String key : new ArrayList<>(stacks.keySet())) {
            SpawnerStack stack = stacks.get(key);
            if (stack == null) continue;

            int cd = spawnCountdowns.getOrDefault(key, stack.getSpawnIntervalTicks()) - 20;
            if (cd <= 0) {
                spawnCountdowns.put(key, stack.getSpawnIntervalTicks());
                spawnForStack(stack);
            } else {
                spawnCountdowns.put(key, cd);
            }
        }
    }

    private void spawnForStack(SpawnerStack stack) {
        String[] parts = stack.getLocationKey().split(":");
        if (parts.length != 4) return;

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return;

        int bx, by, bz;
        try {
            bx = Integer.parseInt(parts[1]);
            by = Integer.parseInt(parts[2]);
            bz = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) { return; }

        // Only spawn if the chunk is loaded
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return;

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(stack.getEntityTypeKey().toUpperCase());
        } catch (IllegalArgumentException e) { return; }

        String chunkKey = chunkMobKey(world.getName(), bx >> 4, bz >> 4, stack.getEntityTypeKey());
        UUID leaderId = chunkMobLeader.get(chunkKey);

        if (leaderId != null) {
            Entity leader = Bukkit.getEntity(leaderId);
            if (leader != null && leader.isValid() && !leader.isDead()) {
                // Merge into existing stack
                int count = mobStackCount.getOrDefault(leaderId, 1) + 1;
                mobStackCount.put(leaderId, count);
                updateMobNametag(leader, stack.getEntityTypeKey(), count);
                return;
            }
            // Leader gone – clean up
            mobStackCount.remove(leaderId);
            mobToChunkKey.remove(leaderId);
            chunkMobLeader.remove(chunkKey);
        }

        // Spawn a new stack representative directly at (or just above) the spawner
        Location spawnLoc = new Location(world, bx + 0.5, by + 1, bz + 0.5);
        Entity mob;
        try {
            mob = world.spawnEntity(spawnLoc, entityType, CreatureSpawnEvent.SpawnReason.CUSTOM);
        } catch (Exception e) {
            plugin.getLogger().warning("[SpawnerStack] Failed to spawn " + entityType + ": " + e.getMessage());
            return;
        }

        // Tag the entity so our systems recognise it
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        pdc.set(KEY_STACK_MOB,   PersistentDataType.BYTE,    (byte) 1);
        pdc.set(KEY_STACK_TYPE,  PersistentDataType.STRING,  stack.getEntityTypeKey());
        pdc.set(KEY_STACK_COUNT, PersistentDataType.INTEGER, 1);

        updateMobNametag(mob, stack.getEntityTypeKey(), 1);

        chunkMobLeader.put(chunkKey, mob.getUniqueId());
        mobToChunkKey.put(mob.getUniqueId(), chunkKey);
        mobStackCount.put(mob.getUniqueId(), 1);
    }

    private void updateMobNametag(Entity mob, String entityTypeKey, int count) {
        SpawnerType st = SpawnerType.fromEntityKey(entityTypeKey);
        String name = st != null ? st.getDisplayName() : entityTypeKey;
        if (count > 1) {
            mob.setCustomName("§e§l" + name + " §7x" + count);
            mob.setCustomNameVisible(true);
        } else {
            mob.setCustomName(null);
            mob.setCustomNameVisible(false);
        }
        // Keep PDC in sync
        mob.getPersistentDataContainer().set(KEY_STACK_COUNT, PersistentDataType.INTEGER, count);
    }

    // ── Mob chunk-merge tick ──────────────────────────────────────────────────

    private void tickMobMerge() {
        for (Map.Entry<UUID, String> entry : new HashMap<>(mobToChunkKey).entrySet()) {
            UUID uid = entry.getKey();
            String registeredKey = entry.getValue();

            Entity entity = Bukkit.getEntity(uid);
            if (entity == null || entity.isDead() || !entity.isValid()) {
                // Clean up stale entries
                chunkMobLeader.remove(registeredKey);
                mobToChunkKey.remove(uid);
                mobStackCount.remove(uid);
                continue;
            }

            // Check current chunk
            Chunk current = entity.getLocation().getChunk();
            String entityType = entity.getPersistentDataContainer()
                    .getOrDefault(KEY_STACK_TYPE, PersistentDataType.STRING, "");
            if (entityType.isEmpty()) continue;

            String currentChunkKey = chunkMobKey(
                    entity.getWorld().getName(), current.getX(), current.getZ(), entityType);

            if (currentChunkKey.equals(registeredKey)) continue; // hasn't moved

            // Entity moved to a new chunk – attempt merge
            UUID existingLeader = chunkMobLeader.get(currentChunkKey);
            if (existingLeader != null && !existingLeader.equals(uid)) {
                Entity leader = Bukkit.getEntity(existingLeader);
                if (leader != null && leader.isValid() && !leader.isDead()) {
                    int thisCount   = mobStackCount.getOrDefault(uid, 1);
                    int mergedCount = mobStackCount.getOrDefault(existingLeader, 1) + thisCount;
                    mobStackCount.put(existingLeader, mergedCount);
                    updateMobNametag(leader, entityType, mergedCount);
                    // Remove the wandering entity
                    chunkMobLeader.remove(registeredKey);
                    mobToChunkKey.remove(uid);
                    mobStackCount.remove(uid);
                    entity.remove();
                    continue;
                }
            }

            // No existing leader in new chunk – re-register this entity there
            chunkMobLeader.remove(registeredKey);
            chunkMobLeader.put(currentChunkKey, uid);
            mobToChunkKey.put(uid, currentChunkKey);
        }
    }

    // ── Entity death ──────────────────────────────────────────────────────────

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!pdc.has(KEY_STACK_MOB, PersistentDataType.BYTE)) return;

        UUID uid = entity.getUniqueId();
        int count = mobStackCount.getOrDefault(uid, 1);

        String registeredKey = mobToChunkKey.remove(uid);
        if (registeredKey != null) chunkMobLeader.remove(registeredKey);
        mobStackCount.remove(uid);

        if (count > 1 && !suppressStackRespawn) {
            // Spawn a replacement representing the remaining (count - 1) mobs
            String rawType = pdc.getOrDefault(KEY_STACK_TYPE, PersistentDataType.STRING, "");
            if (!rawType.isEmpty()) {
                Location loc = entity.getLocation();
                int remaining = count - 1;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        EntityType et = EntityType.valueOf(rawType.toUpperCase());
                        Entity replacement = loc.getWorld().spawnEntity(loc, et,
                                CreatureSpawnEvent.SpawnReason.CUSTOM);
                        PersistentDataContainer rpdc = replacement.getPersistentDataContainer();
                        rpdc.set(KEY_STACK_MOB,   PersistentDataType.BYTE,    (byte) 1);
                        rpdc.set(KEY_STACK_TYPE,  PersistentDataType.STRING,  rawType);
                        rpdc.set(KEY_STACK_COUNT, PersistentDataType.INTEGER, remaining);
                        updateMobNametag(replacement, rawType, remaining);
                        Chunk chunk = replacement.getLocation().getChunk();
                        String cKey = chunkMobKey(loc.getWorld().getName(),
                                chunk.getX(), chunk.getZ(), rawType);
                        chunkMobLeader.put(cKey, replacement.getUniqueId());
                        mobToChunkKey.put(replacement.getUniqueId(), cKey);
                        mobStackCount.put(replacement.getUniqueId(), remaining);
                    } catch (Exception ignored) {}
                }, 1L);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        handlePossibleClearLagCommand(event.getCommand());
    }

    @EventHandler(ignoreCancelled = true)
    public void onRemoteConsoleCommand(RemoteServerCommandEvent event) {
        handlePossibleClearLagCommand(event.getCommand());
    }

    private void handlePossibleClearLagCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) return;
        String cmd = rawCommand.trim().toLowerCase(Locale.ROOT);
        if (cmd.startsWith("/")) cmd = cmd.substring(1);

        if (cmd.startsWith("lagg clear") || cmd.startsWith("lagg killmobs") || cmd.startsWith("clearlag clear")) {
            Bukkit.getScheduler().runTask(plugin, this::clearAllStackedMobsNow);
        }
    }

    private void clearAllStackedMobsNow() {
        suppressStackRespawn = true;
        try {
            for (UUID uid : new ArrayList<>(mobToChunkKey.keySet())) {
                Entity entity = Bukkit.getEntity(uid);
                if (entity != null && entity.isValid() && !entity.isDead()) {
                    entity.remove();
                }
            }
            chunkMobLeader.clear();
            mobToChunkKey.clear();
            mobStackCount.clear();
        } finally {
            suppressStackRespawn = false;
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void saveData(File dataFolder) {
        File f = new File(dataFolder, "spawner-stacks.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();

        for (SpawnerStack s : stacks.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("locationKey", s.getLocationKey());
            entry.put("entityType",  s.getEntityTypeKey());
            entry.put("factionName", s.getFactionName());
            List<String> times = new ArrayList<>();
            for (long t : s.getPlacedTimes()) times.add(String.valueOf(t));
            entry.put("placedTimes", times);
            list.add(entry);
        }

        yaml.set("stacks", list);
        try {
            yaml.save(f);
        } catch (IOException e) {
            plugin.getLogger().severe("[SpawnerStacks] Save failed: " + e.getMessage());
        }
    }

    public void loadData(File dataFolder) {
        File f = new File(dataFolder, "spawner-stacks.yml");
        if (!f.exists()) {
            migrateFromLegacy(dataFolder);
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
        List<?> list = yaml.getList("stacks");
        if (list == null) return;

        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            String locationKey = (String) map.get("locationKey");
            String entityType  = (String) map.get("entityType");
            String factionName = (String) map.get("factionName");
            if (locationKey == null || entityType == null || factionName == null) continue;
            SpawnerStack stack = new SpawnerStack(locationKey, entityType, factionName);
            Object timesObj = map.get("placedTimes");
            if (timesObj instanceof List<?> timesList) {
                for (Object t : timesList) {
                    try { stack.addSpawner(Long.parseLong(t.toString())); }
                    catch (NumberFormatException ignored) {}
                }
            }
            if (!stack.isEmpty()) {
                stacks.put(locationKey, stack);
                spawnCountdowns.put(locationKey, stack.getSpawnIntervalTicks());
            }
        }
        plugin.getLogger().info("[SpawnerStacks] Loaded " + stacks.size() + " spawner stacks.");
    }

    /** Migrate old spawners.yml single-record format. */
    @SuppressWarnings("unchecked")
    private void migrateFromLegacy(File dataFolder) {
        File old = new File(dataFolder, "spawners.yml");
        if (!old.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(old);
        List<?> list = yaml.getList("spawners");
        if (list == null) return;

        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            String locationKey = (String) map.get("locationKey");
            String entityType  = (String) map.get("entityType");
            String factionName = (String) map.get("factionName");
            Object placedAtObj = map.get("placedAt");
            if (locationKey == null || entityType == null || factionName == null || placedAtObj == null) continue;
            long placedAt = ((Number) placedAtObj).longValue();
            SpawnerStack stack = new SpawnerStack(locationKey, entityType, factionName);
            stack.addSpawner(placedAt);
            stacks.put(locationKey, stack);
            spawnCountdowns.put(locationKey, stack.getSpawnIntervalTicks());
        }
        plugin.getLogger().info("[SpawnerStacks] Migrated " + stacks.size() + " legacy spawner records to stacks format.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static String locKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    private static String chunkMobKey(String world, int cx, int cz, String entityType) {
        return world + ":" + cx + ":" + cz + ":" + entityType.toLowerCase();
    }
}
