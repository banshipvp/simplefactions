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
    /** "world:chunkX:chunkZ" -> type */
    private final Map<String, WarzoneType> chunkTypes = new HashMap<>();

    public WarzoneManager(File dataFolder) {
        dataFolder.mkdirs();
        this.dataFile = new File(dataFolder, "warzone_chunks.yml");
        load();
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
        Chunk chunk = loc.getChunk();
        return chunkTypes.get(chunkKey(loc.getWorld().getName(), chunk.getX(), chunk.getZ()));
    }

    public boolean isSafezone(Location loc) {
        return getZoneTypeAt(loc) == WarzoneType.SAFEZONE;
    }

    public boolean isWarzone(Location loc) {
        return getZoneTypeAt(loc) == WarzoneType.WARZONE;
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static String chunkKey(String world, int cx, int cz) {
        return world + ":" + cx + ":" + cz;
    }
}
