package local.simplefactions;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Defines every spawner type the server supports, including:
 *  - the Bukkit EntityType key (lowercase name used by EntityType.name().toLowerCase())
 *  - a friendly display name
 *  - the BASE dollar value this spawner is worth at full maturity (48 h)
 *
 * The actual in-faction value ramps from 50 % at placement to 100 % after 48 hours.
 */
public enum SpawnerType {

    // ── SIMPLE tier ────────────────────────────────────────────────────────────
    PIG           ("pig",            "Pig",              50_000),
    COW           ("cow",            "Cow",              65_000),
    SHEEP         ("sheep",          "Sheep",            50_000),
    CHICKEN       ("chicken",        "Chicken",          40_000),
    WOLF          ("wolf",           "Wolf",             60_000),

    // ── UNIQUE tier ────────────────────────────────────────────────────────────
    CAVE_SPIDER   ("cave_spider",    "Cave Spider",      80_000),

    // ── ELITE tier ─────────────────────────────────────────────────────────────
    SPIDER        ("spider",         "Spider",           90_000),
    ZOMBIE        ("zombie",         "Zombie",          100_000),
    SKELETON      ("skeleton",       "Skeleton",        120_000),

    // ── ULTIMATE tier ──────────────────────────────────────────────────────────
    CREEPER       ("creeper",        "Creeper",         150_000),
    BLAZE         ("blaze",          "Blaze",           200_000),
    SLIME         ("slime",          "Slime",           175_000),
    ZOMBIFIED_PIGLIN("zombified_piglin","Zombified Piglin",185_000),

    // ── LEGENDARY / GODLY tier ─────────────────────────────────────────────────
    IRON_GOLEM    ("iron_golem",     "Iron Golem",    2_000_000),
    WARDEN        ("warden",         "Warden",          500_000),
    GHAST         ("ghast",          "Ghast",           350_000),
    MAGMA_CUBE    ("magma_cube",     "Magma Cube",      250_000),
    SNOWMAN       ("snowman",        "Snowman",      10_000_000);

    // ───────────────────────────────────────────────────────────────────────────

    private static final Map<String, SpawnerType> BY_KEY =
            Arrays.stream(values())
                  .collect(Collectors.toMap(SpawnerType::getEntityKey, Function.identity()));

    private final String entityKey;   // Bukkit EntityType.name().toLowerCase()
    private final String displayName; // Pretty name shown in GUIs
    private final double baseValue;   // Full $ value at 48-hour maturity

    SpawnerType(String entityKey, String displayName, double baseValue) {
        this.entityKey   = entityKey;
        this.displayName = displayName;
        this.baseValue   = baseValue;
    }

    public String getEntityKey()   { return entityKey; }
    public String getDisplayName() { return displayName; }
    public double getBaseValue()   { return baseValue; }

    /** Lookup by entity key (case-insensitive). Returns {@code null} if unknown. */
    public static SpawnerType fromEntityKey(String key) {
        if (key == null) return null;
        return BY_KEY.get(key.toLowerCase());
    }

    /** Returns the base value for an entity key, or 0 if unknown. */
    public static double getBaseValue(String entityKey) {
        SpawnerType t = fromEntityKey(entityKey);
        return t == null ? 0.0 : t.baseValue;
    }
}
