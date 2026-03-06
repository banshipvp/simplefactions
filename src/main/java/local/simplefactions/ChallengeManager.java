package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the 24-hour automatic daily challenge system.
 *
 * 10 challenge definitions in POOL — one is picked at random each day.
 * Scores persist across restarts via challenges.yml.
 * Top 3 can run /claim after the challenge ends.
 *
 * Prizes: #1 = $1,000,000  #2 = $500,000  #3 = $250,000
 */
public class ChallengeManager {

    // ── Tracker types ─────────────────────────────────────────────────────────

    public enum TrackerType {
        BLOCK_BREAK,    // BlockBreakEvent — filtered by materials set (null = all)
        BLOCK_PLACE,    // BlockPlaceEvent
        PLAYER_KILL,    // PlayerDeathEvent — killer is a Player
        MOB_KILL,       // EntityDeathEvent — filtered by entityTypes (null = all non-player)
        FISH_CAUGHT,    // PlayerFishEvent CAUGHT_FISH state
        XP_GAIN,        // PlayerExpChangeEvent — amount of XP gained
        BOOK_OPEN,      // Custom enchant mystery book opened from /enchanter (cross-plugin hook)
        ENCHANT_APPLY,  // Custom enchant successfully applied to gear (cross-plugin hook)
        ENVOY_OPEN,     // Envoy chest opened (cross-plugin hook)
        COINFLIP_WIN    // Coinflip money won — tracks dollar amount, not count (cross-plugin hook)
    }

    // ── Challenge definition ──────────────────────────────────────────────────

    public static class ChallengeDefinition {
        public final String id;
        public final String displayName;
        public final String description;
        public final Material icon;
        public final TrackerType trackerType;
        /** Null = match every material for this tracker type. */
        public final Set<Material> materials;
        /** For MOB_KILL: null = all non-player entities. */
        public final Set<EntityType> entityTypes;

        public ChallengeDefinition(String id, String displayName, String description,
                                   Material icon, TrackerType trackerType,
                                   Set<Material> materials, Set<EntityType> entityTypes) {
            this.id          = id;
            this.displayName = displayName;
            this.description = description;
            this.icon        = icon;
            this.trackerType = trackerType;
            this.materials   = materials;
            this.entityTypes = entityTypes;
        }

        static ChallengeDefinition blocks(String id, String name, String desc,
                                          Material icon, Material... mats) {
            Set<Material> set = mats.length == 0 ? null : new HashSet<>(Arrays.asList(mats));
            return new ChallengeDefinition(id, name, desc, icon,
                    TrackerType.BLOCK_BREAK, set, null);
        }

        static ChallengeDefinition mobs(String id, String name, String desc,
                                        Material icon, EntityType... types) {
            Set<EntityType> set = types.length == 0 ? null : new HashSet<>(Arrays.asList(types));
            return new ChallengeDefinition(id, name, desc, icon,
                    TrackerType.MOB_KILL, null, set);
        }

        static ChallengeDefinition simple(String id, String name, String desc,
                                          Material icon, TrackerType type) {
            return new ChallengeDefinition(id, name, desc, icon, type, null, null);
        }
    }

    // ── 10-Challenge pool ─────────────────────────────────────────────────────

    public static final List<ChallengeDefinition> POOL = List.of(

        // 1  Most blocks mined
        ChallengeDefinition.blocks("block_breaker", "Block Breaker",
            "Mine the most blocks of any kind", Material.STONE),

        // 2  Most XP gained
        ChallengeDefinition.simple("xp_grinder", "XP Grinder",
            "Gain the most XP", Material.EXPERIENCE_BOTTLE, TrackerType.XP_GAIN),

        // 3  Most enchanted books opened from /enchanter
        ChallengeDefinition.simple("book_opener", "Book Opener",
            "Open the most enchant mystery books from /enchanter",
            Material.ENCHANTED_BOOK, TrackerType.BOOK_OPEN),

        // 4  Most custom enchants successfully applied to gear
        ChallengeDefinition.simple("enchant_addict", "Enchant Addict",
            "Successfully apply the most custom enchants to gear",
            Material.NETHER_STAR, TrackerType.ENCHANT_APPLY),

        // 5  Most player kills
        ChallengeDefinition.simple("pvp_champion", "PvP Champion",
            "Get the most player kills",
            Material.WITHER_SKELETON_SKULL, TrackerType.PLAYER_KILL),

        // 6  Most fish caught
        ChallengeDefinition.simple("master_angler", "Master Angler",
            "Catch the most fish", Material.COD, TrackerType.FISH_CAUGHT),

        // 7  Most envoy chests opened
        ChallengeDefinition.simple("envoy_looter", "Envoy Looter",
            "Open the most envoy chests", Material.CHEST, TrackerType.ENVOY_OPEN),

        // 8  Most blazes killed
        ChallengeDefinition.mobs("blaze_slayer", "Blaze Slayer",
            "Kill the most blazes", Material.BLAZE_ROD, EntityType.BLAZE),

        // 9  Most blocks placed
        ChallengeDefinition.simple("block_placer", "Block Placer",
            "Place the most blocks", Material.GRASS_BLOCK, TrackerType.BLOCK_PLACE),

        // 10 Most coinflip money won
        ChallengeDefinition.simple("coin_king", "Coin King",
            "Win the most money from coinflips (tracks dollar amount won)",
            Material.GOLD_INGOT, TrackerType.COINFLIP_WIN)
    );

    // ── Prizes ────────────────────────────────────────────────────────────────

    public static final long[] PRIZES = {1_000_000L, 500_000L, 250_000L};

    // ── State ─────────────────────────────────────────────────────────────────

    private final EconomyManager economyManager;
    private final Plugin plugin;
    private final File dataFile;

    private ChallengeDefinition current;
    private long cycleStart; // epoch seconds

    /** Score values. For count-based challenges this is a simple count; for COINFLIP_WIN it's dollar amount. */
    private final Map<UUID, Long>   scores = new LinkedHashMap<>();
    private final Map<UUID, String> names  = new HashMap<>();

    private ChallengeDefinition lastChallenge;
    private List<Map.Entry<UUID, Long>> lastStandings = new ArrayList<>();
    private final Set<UUID> claimed = new HashSet<>();

    private static final long CYCLE_SECONDS = 24L * 60 * 60; // 86 400

    // ── Init ──────────────────────────────────────────────────────────────────

    public ChallengeManager(EconomyManager economyManager, Plugin plugin) {
        this.economyManager = economyManager;
        this.plugin         = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "challenges.yml");
    }

    /** Load persisted data and start the 24-h scheduler. Called from onEnable. */
    public void start() {
        loadData();
        scheduleNext();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public ChallengeDefinition getCurrent() { return current; }

    public long secondsRemaining() {
        long elapsed = System.currentTimeMillis() / 1000 - cycleStart;
        return Math.max(0L, CYCLE_SECONDS - elapsed);
    }

    /**
     * Primary increment — used by ChallengeListener for block/mob/fish/XP events.
     * material and entityType are used as filter keys; pass null when not applicable.
     */
    public void increment(UUID uuid, String name,
                          TrackerType type, Material material, EntityType entityType) {
        increment(uuid, name, type, material, entityType, 1L);
    }

    /**
     * Increment with an explicit amount — used for XP amounts, coinflip dollar wins, etc.
     * Cross-plugin callers (faction-enchants, SimpleEconomy) use the no-filter overloads below.
     */
    public void increment(UUID uuid, String name,
                          TrackerType type, Material material, EntityType entityType, long amount) {
        if (current == null || current.trackerType != type) return;
        if (current.materials   != null && material   != null && !current.materials.contains(material))     return;
        if (current.entityTypes != null && entityType != null && !current.entityTypes.contains(entityType)) return;
        scores.merge(uuid, amount, Long::sum);
        names.putIfAbsent(uuid, name);
    }

    /** Convenience overload for cross-plugin hooks (no material/entity filter). */
    public void increment(UUID uuid, String name, TrackerType type, long amount) {
        increment(uuid, name, type, null, null, amount);
    }

    /** Convenience overload for cross-plugin hooks — count +1. */
    public void increment(UUID uuid, String name, TrackerType type) {
        increment(uuid, name, type, null, null, 1L);
    }

    public List<Map.Entry<UUID, Long>> getLeaderboard(int limit) {
        return scores.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public long getScore(UUID uuid)            { return scores.getOrDefault(uuid, 0L); }
    public Map<UUID, String> getNames()        { return Collections.unmodifiableMap(names); }
    public ChallengeDefinition getLastChallenge()            { return lastChallenge; }
    public List<Map.Entry<UUID, Long>> getLastStandings()    { return lastStandings; }

    // ── /claim ────────────────────────────────────────────────────────────────

    public enum ClaimResult { NO_CHALLENGE, NOT_PLACED, ALREADY_CLAIMED, SUCCESS }

    /** Returns long[2]: [ClaimResult.ordinal, prizeAmount]. */
    public long[] tryClaim(UUID uuid) {
        if (lastChallenge == null)  return new long[]{ClaimResult.NO_CHALLENGE.ordinal(),    0};
        if (claimed.contains(uuid)) return new long[]{ClaimResult.ALREADY_CLAIMED.ordinal(), 0};
        for (int i = 0; i < Math.min(3, lastStandings.size()); i++) {
            if (lastStandings.get(i).getKey().equals(uuid)) {
                long prize = PRIZES[i];
                economyManager.depositPlayer(Bukkit.getOfflinePlayer(uuid), prize);
                claimed.add(uuid);
                saveData();
                return new long[]{ClaimResult.SUCCESS.ordinal(), prize};
            }
        }
        return new long[]{ClaimResult.NOT_PLACED.ordinal(), 0};
    }

    // ── Admin API ─────────────────────────────────────────────────────────────

    public void adminSkip() { cycle(); }

    public void adminSet(ChallengeDefinition def) {
        current    = def;
        cycleStart = System.currentTimeMillis() / 1000;
        scores.clear(); names.clear();
        saveData();
        broadcastStart();
    }

    // ── Cycle logic ───────────────────────────────────────────────────────────

    private void cycle() {
        if (current != null) {
            lastChallenge = current;
            lastStandings = getLeaderboard(Integer.MAX_VALUE);
            claimed.clear();
            notifyWinners();
        }

        List<ChallengeDefinition> pool = new ArrayList<>(POOL);
        if (current != null) pool.removeIf(d -> d.id.equals(current.id));
        current    = pool.get(new Random().nextInt(pool.size()));
        cycleStart = System.currentTimeMillis() / 1000;
        scores.clear();
        names.clear();
        saveData();
        broadcastStart();
    }

    private void broadcastStart() {
        Bukkit.broadcastMessage("§6§l✦ New Daily Challenge §r§8|§e " + current.displayName);
        Bukkit.broadcastMessage("  §7" + current.description);
        Bukkit.broadcastMessage("  §7Prizes: §6$1M §8/ §7$500K §8/ §c$250K §8| §e/challenges §7to view");
    }

    private void notifyWinners() {
        String[] medals = {"§6§l1st", "§7§l2nd", "§c§l3rd"};
        for (int i = 0; i < Math.min(3, lastStandings.size()); i++) {
            UUID uuid  = lastStandings.get(i).getKey();
            long score = lastStandings.get(i).getValue();
            long prize = PRIZES[i];
            var  player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage("§6§l✦ §r§eYou placed " + medals[i]
                        + " §ein yesterday's challenge with §f" + score + "§e!");
                player.sendMessage("  §7Run §e/claim §7to collect §6$" + fmt(prize) + "§7.");
            }
        }
    }

    // ── Scheduler ─────────────────────────────────────────────────────────────

    private void scheduleNext() {
        if (current == null) {
            cycle();
            scheduleRepeating(CYCLE_SECONDS * 20L);
            return;
        }
        long secsLeft = secondsRemaining();
        if (secsLeft <= 0) {
            cycle();
            scheduleRepeating(CYCLE_SECONDS * 20L);
        } else {
            long ticksLeft = secsLeft * 20L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                cycle();
                scheduleRepeating(CYCLE_SECONDS * 20L);
            }, ticksLeft);
        }
    }

    private void scheduleRepeating(long intervalTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::cycle, intervalTicks, intervalTicks);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void loadData() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);

        String curId = cfg.getString("current.id");
        if (curId != null) {
            current    = POOL.stream().filter(d -> d.id.equals(curId)).findFirst().orElse(null);
            cycleStart = cfg.getLong("current.start", System.currentTimeMillis() / 1000);
        }

        var scoresSection = cfg.getConfigurationSection("current.scores");
        if (scoresSection != null) {
            for (String key : scoresSection.getKeys(false)) {
                try {
                    UUID   uuid = UUID.fromString(key);
                    String nm   = scoresSection.getString(key + ".name", key);
                    long   sc   = scoresSection.getLong(key + ".score", 0L);
                    scores.put(uuid, sc);
                    names.put(uuid, nm);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        String lastId = cfg.getString("last.id");
        if (lastId != null) {
            lastChallenge = POOL.stream().filter(d -> d.id.equals(lastId)).findFirst().orElse(null);
        }
        var lastSection = cfg.getConfigurationSection("last.standings");
        if (lastSection != null) {
            Map<UUID, Long> temp = new LinkedHashMap<>();
            for (String key : lastSection.getKeys(false)) {
                try { temp.put(UUID.fromString(key), lastSection.getLong(key)); }
                catch (IllegalArgumentException ignored) {}
            }
            lastStandings = temp.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .collect(Collectors.toList());
        }

        List<String> claimedList = (List<String>) cfg.getList("last.claimed", new ArrayList<>());
        for (String s : claimedList) {
            try { claimed.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
    }

    public void saveData() {
        YamlConfiguration cfg = new YamlConfiguration();
        if (current != null) {
            cfg.set("current.id",    current.id);
            cfg.set("current.start", cycleStart);
            for (Map.Entry<UUID, Long> e : scores.entrySet()) {
                String base = "current.scores." + e.getKey();
                cfg.set(base + ".name",  names.get(e.getKey()));
                cfg.set(base + ".score", e.getValue());
            }
        }
        if (lastChallenge != null) {
            cfg.set("last.id", lastChallenge.id);
            for (Map.Entry<UUID, Long> e : lastStandings) {
                cfg.set("last.standings." + e.getKey(), e.getValue());
            }
            cfg.set("last.claimed", claimed.stream()
                    .map(UUID::toString).collect(Collectors.toList()));
        }
        try { cfg.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("challenges.yml save failed: " + e.getMessage()); }
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    public static String fmt(long amount) {
        if (amount >= 1_000_000_000) return String.format("%.1fB", amount / 1_000_000_000.0);
        if (amount >= 1_000_000)     return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000)         return String.format("%.1fK", amount / 1_000.0);
        return String.valueOf(amount);
    }

    public static String fmtTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
