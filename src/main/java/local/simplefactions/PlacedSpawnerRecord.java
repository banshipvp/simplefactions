package local.simplefactions;

/**
 * Represents a single spawner that has been placed inside a faction's claimed chunks.
 *
 * Value ramp-up schedule (applied to the spawner's {@link SpawnerType} base value):
 *   t = 0 h   →  50 % of base value
 *   t = 24 h  →  75 % of base value
 *   t = 48 h  → 100 % of base value  (fully mature, no further increase)
 *
 * Formula:  currentValue = baseValue × (0.5 + 0.5 × min(1, elapsedHours / 48))
 */
public class PlacedSpawnerRecord {

    /** Block-level location key in the form {@code "world:x:y:z"}. */
    private final String locationKey;

    /** Lowercase Bukkit {@code EntityType.name()} – e.g. {@code "pig"}, {@code "iron_golem"}. */
    private final String entityType;

    /** Lowercase faction name that owns this spawner's chunk at placement time. */
    private final String factionName;

    /** {@link System#currentTimeMillis()} when the spawner was placed. */
    private final long placedAt;

    public PlacedSpawnerRecord(String locationKey, String entityType, String factionName, long placedAt) {
        this.locationKey = locationKey;
        this.entityType  = entityType.toLowerCase();
        this.factionName = factionName.toLowerCase();
        this.placedAt    = placedAt;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public String getLocationKey() { return locationKey; }
    public String getEntityType()  { return entityType; }
    public String getFactionName() { return factionName; }
    public long   getPlacedAt()    { return placedAt; }

    // ── Value calculation ──────────────────────────────────────────────────────

    /**
     * A factor in [0.0, 1.0] representing how long this spawner has been maturing.
     * Reaches 1.0 after exactly 48 hours.
     */
    public double getMaturityFactor() {
        long   elapsedMs    = System.currentTimeMillis() - placedAt;
        double elapsedHours = elapsedMs / 3_600_000.0;
        return Math.min(1.0, elapsedHours / 48.0);
    }

    /**
     * Returns the current dollar value of this spawner given its type's {@code baseValue}.
     * Starts at 50 % and linearly increases to 100 % over 48 hours.
     */
    public double getCurrentValue(double baseValue) {
        return baseValue * (0.5 + 0.5 * getMaturityFactor());
    }

    /**
     * Convenience overload that resolves the base value from {@link SpawnerType}.
     * Returns 0 if the entity type is not recognised.
     */
    public double getCurrentValue() {
        return getCurrentValue(SpawnerType.getBaseValue(entityType));
    }

    /**
     * Human-readable breakdown string, e.g. {@code "Pig Spawner – $25,000 / $50,000 (50%, 0h placed)"}.
     */
    public String getSummary() {
        double base    = SpawnerType.getBaseValue(entityType);
        double current = getCurrentValue(base);
        long   hours   = (System.currentTimeMillis() - placedAt) / 3_600_000L;
        int    pct     = (int) Math.round((0.5 + 0.5 * getMaturityFactor()) * 100);
        SpawnerType st = SpawnerType.fromEntityKey(entityType);
        String name    = st != null ? st.getDisplayName() : entityType;
        return String.format("%s Spawner – $%,.0f / $%,.0f (%d%%, %dh old)",
                name, current, base, pct, hours);
    }
}
