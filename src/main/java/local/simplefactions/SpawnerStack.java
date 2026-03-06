package local.simplefactions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a physical spawner stack at one block position.
 *
 * Up to {@link #MAX_STACK} spawners of the same mob type may occupy the same
 * block. Each stacked spawner retains its own placement timestamp so the
 * time-based value ramp-up (50 % → 100 % over 48 h) is applied individually.
 *
 * Spawn speed: 1 spawner = every {@link #BASE_SPAWN_INTERVAL_TICKS} ticks.
 *              N spawners = N spawn attempts every BASE ticks.
 */
public class SpawnerStack {

    public static final int MAX_STACK               = 10;
    public static final int BASE_SPAWN_INTERVAL_TICKS = 400; // 20 s
    public static final int MIN_SPAWN_INTERVAL_TICKS  =  40; // legacy constant (not used)

    private final String locationKey;  // "world:x:y:z"
    private final String entityTypeKey; // Bukkit EntityType.name().toLowerCase()
    private String factionName;         // lowercase owner faction name

    /** One timestamp per spawner in the stack (index 0 = oldest). */
    private final List<Long> placedTimes = new ArrayList<>();

    public SpawnerStack(String locationKey, String entityTypeKey, String factionName) {
        this.locationKey   = locationKey;
        this.entityTypeKey = entityTypeKey.toLowerCase();
        this.factionName   = factionName.toLowerCase();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getLocationKey()  { return locationKey; }
    public String getEntityTypeKey() { return entityTypeKey; }
    public String getFactionName()  { return factionName; }
    public void setFactionName(String n) { this.factionName = n.toLowerCase(); }

    /** Number of spawners currently in this stack. */
    public int getCount() { return placedTimes.size(); }
    public boolean isEmpty() { return placedTimes.isEmpty(); }
    public boolean isFull()  { return placedTimes.size() >= MAX_STACK; }

    /** Read-only view of placement timestamps. */
    public List<Long> getPlacedTimes() { return Collections.unmodifiableList(placedTimes); }

    // ── Mutation ──────────────────────────────────────────────────────────────

    /**
     * Add one spawner to this stack using the current time as the placement
     * timestamp. Returns {@code true} if added, {@code false} if already full.
     */
    public boolean addSpawner() {
        return addSpawner(System.currentTimeMillis());
    }

    public boolean addSpawner(long placedAt) {
        if (isFull()) return false;
        placedTimes.add(placedAt);
        return true;
    }

    /**
     * Remove the most-recently added spawner.
     * @return the removed timestamp, or {@code -1} if the stack is empty.
     */
    public long removeTop() {
        if (placedTimes.isEmpty()) return -1L;
        return placedTimes.remove(placedTimes.size() - 1);
    }

    // ── Value ─────────────────────────────────────────────────────────────────

    /**
     * Total current dollar value of all spawners in this stack.
     * Each individual spawner contributes its own time-ramped value
     * (50 % at placement, 100 % after 48 hours).
     */
    public double getTotalCurrentValue() {
        double base = SpawnerType.getBaseValue(entityTypeKey);
        if (base == 0) return 0;
        long now = System.currentTimeMillis();
        double sum = 0;
        for (long t : placedTimes) {
            double hours  = (now - t) / 3_600_000.0;
            double factor = Math.min(1.0, hours / 48.0);
            sum += base * (0.5 + 0.5 * factor);
        }
        return sum;
    }

    // ── Spawn timing ──────────────────────────────────────────────────────────

    /**
     * Ticks between each spawn cycle.
     * Each cycle, this stack performs {@link #getSpawnAttemptsPerCycle()} attempts.
     */
    public int getSpawnIntervalTicks() {
        return BASE_SPAWN_INTERVAL_TICKS;
    }

    /**
     * Number of spawn attempts to run each cycle.
     * This is exactly equal to stack size, so a stack of 10 acts like 10 spawners.
     */
    public int getSpawnAttemptsPerCycle() {
        return Math.max(1, placedTimes.size());
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    public String getSummary() {
        SpawnerType st = SpawnerType.fromEntityKey(entityTypeKey);
        String name = st != null ? st.getDisplayName() : entityTypeKey;
        return String.format("%s Spawner Stack [%d/%d] – $%,.0f total value",
                name, getCount(), MAX_STACK, getTotalCurrentValue());
    }
}
