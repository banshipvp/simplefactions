package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
 * 20 challenge definitions in POOL — one is picked at random each day.
 * Scores persist across restarts via challenges.yml.
 * Top 3 can run /claim after the challenge ends.
 *
 * Prizes: #1 = $1,000,000  #2 = $500,000  #3 = $250,000
 */
public class ChallengeManager {

    // ── Tracker types ─────────────────────────────────────────────────────────

    public enum TrackerType {
        BLOCK_BREAK,    // BlockBreakEvent — filtered by materials set (null = all)
        PLAYER_KILL,    // PlayerDeathEvent — killer is a Player
        MOB_KILL,       // EntityDeathEvent — filtered by entityTypes (null = all non-player)
        FISH_CAUGHT,    // PlayerFishEvent  CAUGHT_FISH state
        ENCHANT_DONE,   // EnchantItemEvent
        ARROW_SHOT,     // EntityShootBowEvent shooter is Player
        ANIMAL_BREED    // EntityBreedEvent  breeder is Player
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

        // ── Convenience builders ──────────────────────────────────────────────

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

    // ── 20-Challenge pool ─────────────────────────────────────────────────────

    public static final List<ChallengeDefinition> POOL = List.of(

        // 1  All blocks
        ChallengeDefinition.blocks("block_breaker", "Block Breaker",
            "Mine the most blocks of any kind", Material.STONE),

        // 2  Diamond ore
        ChallengeDefinition.blocks("diamond_rush", "Diamond Rush",
            "Mine the most diamond ore", Material.DIAMOND,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE),

        // 3  Iron ore
        ChallengeDefinition.blocks("iron_age", "Iron Age",
            "Mine the most iron ore", Material.RAW_IRON,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE),

        // 4  Gold ore
        ChallengeDefinition.blocks("gold_rush", "Gold Rush",
            "Mine the most gold ore", Material.RAW_GOLD,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE),

        // 5  Coal ore
        ChallengeDefinition.blocks("coal_fever", "Coal Fever",
            "Mine the most coal ore", Material.COAL,
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE),

        // 6  Obsidian
        ChallengeDefinition.blocks("obsidian_digger", "Obsidian Digger",
            "Mine the most obsidian", Material.OBSIDIAN,
            Material.OBSIDIAN, Material.CRYING_OBSIDIAN),

        // 7  Logs
        ChallengeDefinition.blocks("lumberjack", "Lumberjack",
            "Chop the most wood logs", Material.OAK_LOG,
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
            Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
            Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG,
            Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG,
            Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG),

        // 8  Crops
        ChallengeDefinition.blocks("master_farmer", "Master Farmer",
            "Harvest the most crops", Material.WHEAT,
            Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.MELON, Material.PUMPKIN,
            Material.SUGAR_CANE, Material.CACTUS, Material.BAMBOO),

        // 9  Sand & gravel
        ChallengeDefinition.blocks("sand_storm", "Sand Storm",
            "Collect the most sand and gravel", Material.SAND,
            Material.SAND, Material.RED_SAND, Material.GRAVEL),

        // 10 Nether blocks
        ChallengeDefinition.blocks("nether_explorer", "Nether Explorer",
            "Mine the most nether blocks", Material.NETHERRACK,
            Material.NETHERRACK, Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE,
            Material.GLOWSTONE, Material.SOUL_SAND, Material.SOUL_SOIL,
            Material.BASALT, Material.BLACKSTONE, Material.MAGMA_BLOCK,
            Material.ANCIENT_DEBRIS),

        // 11 Emerald ore
        ChallengeDefinition.blocks("gem_hunter", "Gem Hunter",
            "Mine the most emerald ore", Material.EMERALD,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE),

        // 12 Lapis ore
        ChallengeDefinition.blocks("lapis_miner", "Lapis Miner",
            "Mine the most lapis ore", Material.LAPIS_LAZULI,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE),

        // 13 Redstone ore
        ChallengeDefinition.blocks("redstone_rush", "Redstone Rush",
            "Mine the most redstone ore", Material.REDSTONE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE),

        // 14 Deepslate / stone family
        ChallengeDefinition.blocks("deep_miner", "Deep Miner",
            "Mine the most deepslate and stone", Material.DEEPSLATE,
            Material.DEEPSLATE, Material.COBBLED_DEEPSLATE,
            Material.STONE, Material.COBBLESTONE,
            Material.ANDESITE, Material.DIORITE, Material.GRANITE),

        // 15 Player kills
        ChallengeDefinition.simple("pvp_champion", "PvP Champion",
            "Get the most player kills",
            Material.WITHER_SKELETON_SKULL, TrackerType.PLAYER_KILL),

        // 16 All mob kills
        ChallengeDefinition.mobs("monster_hunter", "Monster Hunter",
            "Kill the most mobs", Material.ROTTEN_FLESH),

        // 17 Undead only
        ChallengeDefinition.mobs("undead_slayer", "Undead Slayer",
            "Kill the most undead mobs", Material.BONE,
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.WITHER_SKELETON,
            EntityType.DROWNED, EntityType.HUSK, EntityType.STRAY,
            EntityType.ZOMBIE_VILLAGER, EntityType.ZOMBIFIED_PIGLIN,
            EntityType.PHANTOM),

        // 18 Fishing
        ChallengeDefinition.simple("master_angler", "Master Angler",
            "Catch the most fish", Material.COD, TrackerType.FISH_CAUGHT),

        // 19 Enchanting
        ChallengeDefinition.simple("enchant_master", "Enchantment Master",
            "Enchant the most items", Material.ENCHANTED_BOOK, TrackerType.ENCHANT_DONE),

        // 20 Arrows
        ChallengeDefinition.simple("eagle_eye", "Eagle Eye",
            "Shoot the most arrows", Material.ARROW, TrackerType.ARROW_SHOT)
    );

    // ── Prizes ────────────────────────────────────────────────────────────────

    public static final long[] PRIZES = {1_000_000L, 500_000L, 250_000L};

    // ── State ─────────────────────────────────────────────────────────────────

    private final EconomyManager economyManager;
    private final Plugin plugin;
    private final File dataFile;

    private ChallengeDefinition current;
    private long cycleStart; // epoch seconds

    private final Map<UUID, Integer> scores = new LinkedHashMap<>();
    private final Map<UUID, String>  names  = new HashMap<>();

    private ChallengeDefinition lastChallenge;
    private List<Map.Entry<UUID, Integer>> lastStandings = new ArrayList<>();
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

    /** Called by ChallengeListener. Only scores if the event matches current challenge. */
    public void increment(UUID uuid, String name,
                          TrackerType type, Material material, EntityType entityType) {
        if (current == null || current.trackerType != type) return;
        if (current.materials   != null && material   != null && !current.materials.contains(material))     return;
        if (current.entityTypes != null && entityType != null && !current.entityTypes.contains(entityType)) return;
        scores.merge(uuid, 1, Integer::sum);
        names.putIfAbsent(uuid, name);
    }

    public List<Map.Entry<UUID, Integer>> getLeaderboard(int limit) {
        return scores.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public int getScore(UUID uuid)             { return scores.getOrDefault(uuid, 0); }
    public Map<UUID, String> getNames()        { return Collections.unmodifiableMap(names); }
    public ChallengeDefinition getLastChallenge() { return lastChallenge; }
    public List<Map.Entry<UUID, Integer>> getLastStandings() { return lastStandings; }

    // ── /claim ────────────────────────────────────────────────────────────────

    public enum ClaimResult { NO_CHALLENGE, NOT_PLACED, ALREADY_CLAIMED, SUCCESS }

    /** Returns [ClaimResult.ordinal, prizeAmount]. */
    public long[] tryClaim(UUID uuid) {
        if (lastChallenge == null)  return new long[]{ClaimResult.NO_CHALLENGE.ordinal(),   0};
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

    /** Force-cycle to a new random challenge. */
    public void adminSkip() { cycle(); }

    public void adminSet(ChallengeDefinition def) {
        current   = def;
        cycleStart = System.currentTimeMillis() / 1000;
        scores.clear(); names.clear();
        saveData();
        broadcastStart();
    }

    // ── Cycle logic ───────────────────────────────────────────────────────────

    private void cycle() {
        if (current != null) {
            lastChallenge  = current;
            lastStandings  = getLeaderboard(Integer.MAX_VALUE);
            claimed.clear();
            notifyWinners();
        }

        // Pick a challenge different from the current one
        List<ChallengeDefinition> pool = new ArrayList<>(POOL);
        if (current != null) pool.removeIf(d -> d.id.equals(current.id));
        current   = pool.get(new Random().nextInt(pool.size()));
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
            UUID uuid   = lastStandings.get(i).getKey();
            int  score  = lastStandings.get(i).getValue();
            long prize  = PRIZES[i];
            var  player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage("§6§l✦ §r§eYou placed " + medals[i]
                        + " §ein yesterday's challenge with §f" + score + " §epoints!");
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
            current   = POOL.stream().filter(d -> d.id.equals(curId)).findFirst().orElse(null);
            cycleStart = cfg.getLong("current.start", System.currentTimeMillis() / 1000);
        }

        var scoresSection = cfg.getConfigurationSection("current.scores");
        if (scoresSection != null) {
            for (String key : scoresSection.getKeys(false)) {
                try {
                    UUID uuid  = UUID.fromString(key);
                    String nm  = scoresSection.getString(key + ".name", key);
                    int    sc  = scoresSection.getInt(key + ".score", 0);
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
            Map<UUID, Integer> temp = new LinkedHashMap<>();
            for (String key : lastSection.getKeys(false)) {
                try { temp.put(UUID.fromString(key), lastSection.getInt(key)); }
                catch (IllegalArgumentException ignored) {}
            }
            lastStandings = temp.entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
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
            for (Map.Entry<UUID, Integer> e : scores.entrySet()) {
                String base = "current.scores." + e.getKey();
                cfg.set(base + ".name",  names.get(e.getKey()));
                cfg.set(base + ".score", e.getValue());
            }
        }
        if (lastChallenge != null) {
            cfg.set("last.id", lastChallenge.id);
            for (Map.Entry<UUID, Integer> e : lastStandings) {
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
