package local.simplefactions;

/**
 * Server rank tiers, aligned with SimpleKits' RankTier and LuckPerms group names.
 *
 * Perks per rank
 * ─────────────────────────────────────────────────────────────────
 *  Rank           | Homes | Vaults | XP cooldown | /fly | Q-pri
 * ─────────────────────────────────────────────────────────────────
 *  DEFAULT   (0)  |   1   |   1    |   10 min    |  no  |  0
 *  SCOUT     (1)  |   1   |   1    |   10 min    |  no  |  1
 *  MILITANT  (2)  |   2   |   2    |  8.5 min    |  no  |  2
 *  TACTICIAN (3)  |   4   |   5    |    6 min    |  no  |  3
 *  WARLORD   (4)  |   8   |  10    |    3 min    |  no  |  4
 *  SOVEREIGN (5)  |  15   |  20    |    0 min    | yes  |  5
 * ─────────────────────────────────────────────────────────────────
 *
 * XP exhaust = cooldown (in minutes) between /xpbottle uses.
 * 0 means unlimited (no cooldown).
 */
public enum PlayerRank {

    DEFAULT  (0, "§7Default",     "§7", "default",   1,  1, 10.0, false),
    SCOUT    (1, "§aScout",       "§a", "scout",     1,  1, 10.0, false),
    MILITANT (2, "§eMilitant",    "§e", "militant",  2,  2,  8.5, false),
    TACTICIAN(3, "§6Tactician",   "§6", "tactician", 4,  5,  6.0, false),
    WARLORD  (4, "§5Warlord",     "§d", "warlord",   8, 10,  3.0, false),
    SOVEREIGN(5, "§c§lSovereign", "§c", "sovereign", 15, 20,  0.0, true);

    private final int     level;
    private final String  displayName;
    private final String  chatColor;
    private final String  groupId;           // LuckPerms group name
    private final int     maxHomes;
    private final int     maxVaults;
    private final double  xpExhaustMinutes;  // 0 = no cooldown
    private final boolean fly;

    PlayerRank(int level, String displayName, String chatColor, String groupId,
               int maxHomes, int maxVaults, double xpExhaustMinutes, boolean fly) {
        this.level            = level;
        this.displayName      = displayName;
        this.chatColor        = chatColor;
        this.groupId          = groupId;
        this.maxHomes         = maxHomes;
        this.maxVaults        = maxVaults;
        this.xpExhaustMinutes = xpExhaustMinutes;
        this.fly              = fly;
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public int    getLevel()            { return level; }
    public String getDisplayName()      { return displayName; }
    public String getChatColor()        { return chatColor; }
    public String getGroupId()          { return groupId; }
    public int    getMaxHomes()         { return maxHomes; }
    public int    getMaxVaults()        { return maxVaults; }
    public double getXpExhaustMinutes() { return xpExhaustMinutes; }
    public boolean canFly()             { return fly; }

    /** Queue priority — higher rank goes first (higher value = enters first). */
    public int getQueuePriority()       { return level; }

    /** XP exhaust in milliseconds (0 if unlimited). */
    public long getXpExhaustMs()        { return (long) (xpExhaustMinutes * 60_000); }

    /** True if this rank enforces a cooldown on /xpbottle. */
    public boolean hasXpExhaust()       { return xpExhaustMinutes > 0; }

    // ── Lookups ───────────────────────────────────────────────────────────────

    public static PlayerRank fromLevel(int level) {
        for (PlayerRank rank : values()) {
            if (rank.level == level) return rank;
        }
        return DEFAULT;
    }

    public static PlayerRank fromGroupId(String groupId) {
        if (groupId == null) return DEFAULT;
        for (PlayerRank rank : values()) {
            if (rank.groupId.equalsIgnoreCase(groupId)) return rank;
        }
        return DEFAULT;
    }

    /** Maps old enum names stored in data files to new values. */
    public static PlayerRank fromLegacyName(String name) {
        if (name == null) return DEFAULT;
        return switch (name.toUpperCase()) {
            case "MEMBER"    -> SCOUT;
            case "VIP"       -> MILITANT;
            case "PREMIUM"   -> TACTICIAN;
            case "ELITE"     -> WARLORD;
            case "LEGENDARY", "ADMIN" -> SOVEREIGN;
            default          -> fromGroupId(name);
        };
    }
}
