package local.simplefactions;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
