package local.simplefactions;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Stores the zone type (WARZONE / SAFEZONE) for each claimed chunk of the
 * "Warzone" system faction.  Data is persisted to warzone_chunks.yml.
 */
public class WarzoneManager {

    public enum WarzoneType {
        WARZONE,
        SAFEZONE
    }

    private final File dataFile;
    private final File regionFile;
    /** "world:chunkX:chunkZ" -> type */
    private final Map<String, WarzoneType> chunkTypes = new HashMap<>();
    private final java.util.List<ZoneRegion> regions = new java.util.ArrayList<>();

    public WarzoneManager(File dataFolder) {
        dataFolder.mkdirs();
        this.dataFile = new File(dataFolder, "warzone_chunks.yml");
        this.regionFile = new File(dataFolder, "warzone_regions.yml");
        load();
        loadRegions();
    }

    // ── Mutators ─────────────────────────────────────────────────────────────

    public void setChunkType(String chunkKey, WarzoneType type) {
        chunkTypes.put(chunkKey, type);
        save();
    }

    public void removeChunkType(String chunkKey) {
        if (chunkTypes.remove(chunkKey) != null) save();
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public WarzoneType getChunkType(String chunkKey) {
        return chunkTypes.get(chunkKey);
    }

    public WarzoneType getZoneTypeAt(Location loc) {
        if (loc.getWorld() == null) return null;
        WarzoneType regionType = getRegionTypeAt(loc);
        if (regionType != null) return regionType;
        Chunk chunk = loc.getChunk();
        return chunkTypes.get(chunkKey(loc.getWorld().getName(), chunk.getX(), chunk.getZ()));
    }

    public boolean isSafezone(Location loc) {
        return getZoneTypeAt(loc) == WarzoneType.SAFEZONE;
    }

    public boolean isWarzone(Location loc) {
        return getZoneTypeAt(loc) == WarzoneType.WARZONE;
    }

    public boolean hasOnlinePlayerInSameWarzoneClaim(Location anchor) {
        return countOnlinePlayersInSameWarzoneClaim(anchor) > 0;
    }

    public int countOnlinePlayersInSameWarzoneClaim(Location anchor) {
        if (anchor == null || anchor.getWorld() == null) {
            return 0;
        }

        String worldName = anchor.getWorld().getName();
        ZoneRegion region = getWarzoneRegionAt(anchor);
        if (region != null) {
            int count = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                Location playerLoc = player.getLocation();
                if (playerLoc.getWorld() == null || !playerLoc.getWorld().getName().equals(worldName)) {
                    continue;
                }
                if (region.contains(playerLoc.getBlockX(), playerLoc.getBlockY(), playerLoc.getBlockZ())) {
                    count++;
                }
            }
            return count;
        }

        Chunk anchorChunk = anchor.getChunk();
        String anchorKey = chunkKey(worldName, anchorChunk.getX(), anchorChunk.getZ());
        if (getChunkType(anchorKey) != WarzoneType.WARZONE) {
            return 0;
        }

        Set<String> connectedWarzoneChunks = getConnectedWarzoneChunkKeys(worldName, anchorChunk.getX(), anchorChunk.getZ());
        if (connectedWarzoneChunks.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location playerLoc = player.getLocation();
            if (playerLoc.getWorld() == null || !playerLoc.getWorld().getName().equals(worldName)) {
                continue;
            }
            Chunk playerChunk = playerLoc.getChunk();
            String playerKey = chunkKey(worldName, playerChunk.getX(), playerChunk.getZ());
            if (connectedWarzoneChunks.contains(playerKey)) {
                count++;
            }
        }

        return count;
    }

    // ── Region Zones ────────────────────────────────────────────────────────

    public void addRegion(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, WarzoneType type) {
        regions.add(new ZoneRegion(world, minX, minY, minZ, maxX, maxY, maxZ, type));
        saveRegions();
    }

    public int removeRegionsIntersecting(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int removed = 0;
        java.util.Iterator<ZoneRegion> it = regions.iterator();
        while (it.hasNext()) {
            ZoneRegion region = it.next();
            if (region.world.equals(world) && region.intersects(minX, minY, minZ, maxX, maxY, maxZ)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) saveRegions();
        return removed;
    }

    private WarzoneType getRegionTypeAt(Location loc) {
        String world = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        for (ZoneRegion region : regions) {
            if (region.world.equals(world) && region.contains(x, y, z)) {
                return region.type;
            }
        }
        return null;
    }

    private ZoneRegion getWarzoneRegionAt(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }

        String world = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        for (ZoneRegion region : regions) {
            if (region.type != WarzoneType.WARZONE) {
                continue;
            }
            if (region.world.equals(world) && region.contains(x, y, z)) {
                return region;
            }
        }

        return null;
    }

    private Set<String> getConnectedWarzoneChunkKeys(String world, int startCx, int startCz) {
        Set<String> connected = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();

        queue.add(new int[]{startCx, startCz});

        while (!queue.isEmpty()) {
            int[] node = queue.poll();
            int cx = node[0];
            int cz = node[1];
            String key = chunkKey(world, cx, cz);

            if (connected.contains(key)) {
                continue;
            }
            if (getChunkType(key) != WarzoneType.WARZONE) {
                continue;
            }

            connected.add(key);
            queue.add(new int[]{cx + 1, cz});
            queue.add(new int[]{cx - 1, cz});
            queue.add(new int[]{cx, cz + 1});
            queue.add(new int[]{cx, cz - 1});
        }

        return connected;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : cfg.getKeys(false)) {
            try {
                chunkTypes.put(key, WarzoneType.valueOf(cfg.getString(key)));
            } catch (Exception ignored) {}
        }
    }

    private void loadRegions() {
        if (!regionFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(regionFile);
        for (String key : cfg.getKeys(false)) {
            String world = cfg.getString(key + ".world");
            String typeStr = cfg.getString(key + ".type");
            if (world == null || typeStr == null) continue;
            try {
                WarzoneType type = WarzoneType.valueOf(typeStr);
                int minX = cfg.getInt(key + ".min.x");
                int minY = cfg.getInt(key + ".min.y");
                int minZ = cfg.getInt(key + ".min.z");
                int maxX = cfg.getInt(key + ".max.x");
                int maxY = cfg.getInt(key + ".max.y");
                int maxZ = cfg.getInt(key + ".max.z");
                regions.add(new ZoneRegion(world, minX, minY, minZ, maxX, maxY, maxZ, type));
            } catch (Exception ignored) {}
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, WarzoneType> e : chunkTypes.entrySet()) {
            cfg.set(e.getKey(), e.getValue().name());
        }
        try {
            cfg.save(dataFile);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void saveRegions() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (int i = 0; i < regions.size(); i++) {
            ZoneRegion region = regions.get(i);
            String key = "region_" + i;
            cfg.set(key + ".world", region.world);
            cfg.set(key + ".type", region.type.name());
            cfg.set(key + ".min.x", region.minX);
            cfg.set(key + ".min.y", region.minY);
            cfg.set(key + ".min.z", region.minZ);
            cfg.set(key + ".max.x", region.maxX);
            cfg.set(key + ".max.y", region.maxY);
            cfg.set(key + ".max.z", region.maxZ);
        }
        try {
            cfg.save(regionFile);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static String chunkKey(String world, int cx, int cz) {
        return world + ":" + cx + ":" + cz;
    }

    /**
     * Returns true if the given chunk (by chunk coordinates) is a WARZONE chunk,
     * checking both the per-chunk map and any region-based zones.
     */
    public boolean isWarzoneChunk(String world, int cx, int cz) {
        WarzoneType t = chunkTypes.get(chunkKey(world, cx, cz));
        if (t == WarzoneType.WARZONE) return true;
        // Check regions: chunk spans blocks [cx*16, cx*16+15] x [cz*16, cz*16+15]
        int bx1 = cx * 16, bx2 = cx * 16 + 15;
        int bz1 = cz * 16, bz2 = cz * 16 + 15;
        for (ZoneRegion r : regions) {
            if (r.world.equals(world) && r.type == WarzoneType.WARZONE
                    && r.intersects(bx1, Integer.MIN_VALUE / 2, bz1,
                                    bx2, Integer.MAX_VALUE / 2, bz2)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the Chebyshev distance (in chunks) from chunk (cx,cz) to the
     * nearest warzone chunk, searching up to {@code maxDist} away.
     * Returns -1 if no warzone chunk is found within that range.
     */
    public int nearestWarzoneDistance(String world, int cx, int cz, int maxDist) {
        for (int dist = 1; dist <= maxDist; dist++) {
            for (int dx = -dist; dx <= dist; dx++) {
                for (int dz = -dist; dz <= dist; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != dist) continue; // border ring only
                    if (isWarzoneChunk(world, cx + dx, cz + dz)) return dist;
                }
            }
        }
        return -1;
    }

    /**
     * Returns a random solid-ground location inside a warzone region or chunk.
     * Tries region-based zones first, then falls back to chunk-based warzone entries.
     * Returns {@code null} if no warzone is defined or no valid surface could be found.
     */
    public Location getRandomWarzoneSpawnLocation() {
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        // Prefer region-based warzone entries (they have explicit bounds)
        java.util.List<ZoneRegion> warzoneRegions = new java.util.ArrayList<>();
        for (ZoneRegion r : regions) {
            if (r.type == WarzoneType.WARZONE) warzoneRegions.add(r);
        }

        if (!warzoneRegions.isEmpty()) {
            ZoneRegion region = warzoneRegions.get(rng.nextInt(warzoneRegions.size()));
            org.bukkit.World world = Bukkit.getWorld(region.world);
            if (world != null) {
                for (int attempt = 0; attempt < 20; attempt++) {
                    int x = rng.nextInt(region.minX, region.maxX + 1);
                    int z = rng.nextInt(region.minZ, region.maxZ + 1);
                    int y = world.getHighestBlockYAt(x, z);
                    Location loc = new Location(world, x + 0.5, y + 1.0, z + 0.5);
                    if (!world.getBlockAt(x, y, z).isLiquid()) return loc;
                }
            }
        }

        // Fall back to chunk-based warzone entries
        java.util.List<String> warzoneChunkKeys = new java.util.ArrayList<>();
        for (Map.Entry<String, WarzoneType> e : chunkTypes.entrySet()) {
            if (e.getValue() == WarzoneType.WARZONE) warzoneChunkKeys.add(e.getKey());
        }

        if (!warzoneChunkKeys.isEmpty()) {
            for (int attempt = 0; attempt < 10; attempt++) {
                String key = warzoneChunkKeys.get(rng.nextInt(warzoneChunkKeys.size()));
                // key format: "world:chunkX:chunkZ"
                String[] parts = key.split(":");
                if (parts.length < 3) continue;
                org.bukkit.World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;
                try {
                    int cx = Integer.parseInt(parts[1]);
                    int cz = Integer.parseInt(parts[2]);
                    int bx = cx * 16 + rng.nextInt(16);
                    int bz = cz * 16 + rng.nextInt(16);
                    int by = world.getHighestBlockYAt(bx, bz);
                    Location loc = new Location(world, bx + 0.5, by + 1.0, bz + 0.5);
                    if (!world.getBlockAt(bx, by, bz).isLiquid()) return loc;
                } catch (NumberFormatException ignored) {}
            }
        }

        return null;
    }

    static class ZoneRegion {
        final String world;
        final int minX;
        final int minY;
        final int minZ;
        final int maxX;
        final int maxY;
        final int maxZ;
        final WarzoneType type;

        ZoneRegion(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, WarzoneType type) {
            this.world = world;
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
            this.type = type;
        }

        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        boolean intersects(int rMinX, int rMinY, int rMinZ, int rMaxX, int rMaxY, int rMaxZ) {
            return rMinX <= maxX && rMaxX >= minX
                    && rMinY <= maxY && rMaxY >= minY
                    && rMinZ <= maxZ && rMaxZ >= minZ;
        }
    }
}
