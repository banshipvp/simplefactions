package local.simplefactions;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Tracks every spawner that has been placed inside a faction's claimed chunks.
 *
 * Keys are block-level location strings in the form {@code "world:x:y:z"}.
 * The tracker is held by {@link FactionManager} and persisted to {@code spawners.yml}.
 */
public class SpawnerTracker {

    /** locationKey → record */
    private final Map<String, PlacedSpawnerRecord> records = new HashMap<>();

    // ── Registration ───────────────────────────────────────────────────────────

    /**
     * Register a newly placed spawner.
     *
     * @param world       The world name
     * @param x           Block X coordinate
     * @param y           Block Y coordinate
     * @param z           Block Z coordinate
     * @param entityType  Lowercase entity type key (e.g. {@code "pig"})
     * @param factionName The faction that owns the chunk (name, any case)
     */
    public void addSpawner(String world, int x, int y, int z, String entityType, String factionName) {
        String key = locKey(world, x, y, z);
        records.put(key, new PlacedSpawnerRecord(key, entityType, factionName, System.currentTimeMillis()));
    }

    /**
     * Overload that lets callers supply an explicit placement timestamp (used during data loading).
     */
    public void addSpawner(String world, int x, int y, int z, String entityType, String factionName, long placedAt) {
        String key = locKey(world, x, y, z);
        records.put(key, new PlacedSpawnerRecord(key, entityType, factionName, placedAt));
    }

    /**
     * Unregister a spawner (called when it is mined or destroyed).
     *
     * @return the removed record, or {@code null} if nothing was registered there
     */
    public PlacedSpawnerRecord removeSpawner(String world, int x, int y, int z) {
        return records.remove(locKey(world, x, y, z));
    }

    /** Returns the record at the given block position, or {@code null}. */
    public PlacedSpawnerRecord getSpawner(String world, int x, int y, int z) {
        return records.get(locKey(world, x, y, z));
    }

    // ── Value aggregation ──────────────────────────────────────────────────────

    /**
     * Calculate the total current dollar value of all spawners belonging to a faction.
     * Spawner values ramp from 50 % at placement to 100 % after 48 hours.
     *
     * @param factionName Faction name (case-insensitive)
     */
    public double getTotalValueForFaction(String factionName) {
        if (factionName == null) return 0.0;
        String key = factionName.toLowerCase();
        double total = 0.0;
        for (PlacedSpawnerRecord rec : records.values()) {
            if (rec.getFactionName().equals(key)) {
                total += rec.getCurrentValue();
            }
        }
        return total;
    }

    /**
     * Count how many spawners of a given entity type a faction has.
     *
     * @param factionName Faction name (case-insensitive)
     * @param entityType  Lowercase entity type key
     */
    public int countForFaction(String factionName, String entityType) {
        if (factionName == null) return 0;
        String fKey  = factionName.toLowerCase();
        String eKey  = entityType.toLowerCase();
        int count = 0;
        for (PlacedSpawnerRecord rec : records.values()) {
            if (rec.getFactionName().equals(fKey) && rec.getEntityType().equals(eKey)) {
                count++;
            }
        }
        return count;
    }

    /** Returns an unmodifiable view of all tracked records for persistence. */
    public Collection<PlacedSpawnerRecord> getAllRecords() {
        return Collections.unmodifiableCollection(records.values());
    }

    /** Direct map access for serialisation; do not mutate externally. */
    public Map<String, PlacedSpawnerRecord> getRecordsMap() {
        return records;
    }

    /** Remove all spawners belonging to a specific faction (called on unclaimAll / disband). */
    public void removeFactionSpawners(String factionName) {
        if (factionName == null) return;
        String key = factionName.toLowerCase();
        records.values().removeIf(rec -> rec.getFactionName().equals(key));
    }

    /**
     * Remove all tracked spawners for a faction within one chunk.
     *
     * @return entityType -> number removed
     */
    public Map<String, Integer> removeFactionSpawnersInChunk(String factionName, String world, int chunkX, int chunkZ) {
        Map<String, Integer> removedByType = new HashMap<>();
        if (factionName == null || world == null) return removedByType;

        String factionKey = factionName.toLowerCase();
        Iterator<Map.Entry<String, PlacedSpawnerRecord>> iterator = records.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PlacedSpawnerRecord> entry = iterator.next();
            PlacedSpawnerRecord rec = entry.getValue();
            if (!factionKey.equals(rec.getFactionName())) continue;

            String[] parts = rec.getLocationKey().split(":");
            if (parts.length != 4) continue;
            if (!world.equalsIgnoreCase(parts[0])) continue;

            int bx;
            int bz;
            try {
                bx = Integer.parseInt(parts[1]);
                bz = Integer.parseInt(parts[3]);
            } catch (NumberFormatException ignored) {
                continue;
            }

            if ((bx >> 4) != chunkX || (bz >> 4) != chunkZ) continue;

            iterator.remove();
            removedByType.merge(rec.getEntityType(), 1, Integer::sum);
        }

        return removedByType;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String locKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
