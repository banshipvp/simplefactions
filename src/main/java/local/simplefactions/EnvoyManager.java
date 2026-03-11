package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public class EnvoyManager {

    private static final List<String> REQUIRED_CENTERS = List.of("spawn", "desert", "plains", "nether");
    private static final long AUTO_SPAWN_INTERVAL_TICKS = 30L * 60L * 20L;
    private static final int MAX_SCALING_PLAYERS_PER_CENTER = 10;

    public static class LootEntry {
        private final ItemStack item;
        private final double chance;

        public LootEntry(ItemStack item, double chance) {
            this.item = item == null ? new ItemStack(Material.PAPER) : item.clone();
            this.chance = chance;
        }

        public ItemStack item() {
            return item.clone();
        }

        public double chance() {
            return chance;
        }
    }

    public static class ActiveEnvoy {
        private final String tier;
        private final String warpName;
        private final Location location;

        public ActiveEnvoy(String tier, String warpName, Location location) {
            this.tier = tier;
            this.warpName = warpName;
            this.location = location == null ? null : location.clone();
        }

        public String tier() {
            return tier;
        }

        public String warpName() {
            return warpName;
        }

        public Location location() {
            return location == null ? null : location.clone();
        }
    }

    private final JavaPlugin plugin;
    private final File lootFile;
    private final File centersFile;
    private final Random random = new Random();

    private final Map<String, List<LootEntry>> lootByTier = new LinkedHashMap<>();
    private final Map<String, ActiveEnvoy> activeEnvoys = new HashMap<>();
    private final Map<String, Location> centers = new HashMap<>();

    private BukkitTask autoSpawnTask;

    public EnvoyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.lootFile = new File(plugin.getDataFolder(), "envoy-loot.yml");
        this.centersFile = new File(plugin.getDataFolder(), "envoy-centers.yml");
    }

    public void load() {
        loadLoot();
        loadCenters();
    }

    public void save() {
        saveLoot();
        saveCenters();
    }

    public List<String> getTiers() {
        return List.of("simple", "rare", "legendary", "godly", "heroic_nether");
    }

    public List<LootEntry> getLoot(String tier) {
        return new ArrayList<>(lootByTier.getOrDefault(normalizeTier(tier), List.of()));
    }

    public boolean addLoot(String tier, ItemStack item, double chance) {
        if (item == null || item.getType() == Material.AIR || chance <= 0) {
            return false;
        }

        String normalized = normalizeTier(tier);
        ensureTier(normalized);
        lootByTier.get(normalized).add(new LootEntry(item.clone(), chance));
        saveLoot();
        return true;
    }

    public boolean removeLootAt(String tier, int index) {
        String normalized = normalizeTier(tier);
        List<LootEntry> entries = lootByTier.get(normalized);
        if (entries == null || index < 0 || index >= entries.size()) {
            return false;
        }

        entries.remove(index);
        saveLoot();
        return true;
    }

    public ItemStack rollReward(String tier) {
        List<LootEntry> entries = lootByTier.get(normalizeTier(tier));
        if (entries == null || entries.isEmpty()) return null;

        double total = 0.0;
        for (LootEntry entry : entries) {
            total += entry.chance();
        }
        if (total <= 0.0) return null;

        double roll = random.nextDouble() * total;
        double cumulative = 0.0;
        for (LootEntry entry : entries) {
            cumulative += entry.chance();
            if (roll <= cumulative) {
                return entry.item();
            }
        }

        return entries.get(entries.size() - 1).item();
    }

    public String normalizeTier(String tier) {
        if (tier == null) return "simple";

        String normalized = tier.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (normalized) {
            case "simple", "common" -> "simple";
            case "rare" -> "rare";
            case "legendary" -> "legendary";
            case "godly", "boss" -> "godly";
            case "heroic", "nether", "heroic_nether" -> "heroic_nether";
            default -> normalized;
        };
    }

    public boolean setCenter(String centerName, Location location) {
        String normalized = normalizeCenterName(centerName);
        if (!isAllowedCenter(normalized) || location == null || location.getWorld() == null) {
            return false;
        }

        centers.put(normalized, location.clone());
        saveCenters();
        return true;
    }

    public Location getCenter(String centerName) {
        Location location = centers.get(normalizeCenterName(centerName));
        return location == null ? null : location.clone();
    }

    public List<String> getMissingCenters() {
        List<String> missing = new ArrayList<>();
        for (String center : REQUIRED_CENTERS) {
            if (!centers.containsKey(center)) {
                missing.add(center);
            }
        }
        return missing;
    }

    public List<ActiveEnvoy> spawnStandardEnvoys() {
        List<ActiveEnvoy> spawned = new ArrayList<>();
        spawned.addAll(spawnAtCenter("spawn", "simple"));
        spawned.addAll(spawnAtCenter("desert", "rare"));
        spawned.addAll(spawnAtCenter("plains", "legendary"));
        return spawned;
    }

    public ActiveEnvoy spawnHeroicNetherEnvoy() {
        List<ActiveEnvoy> spawned = spawnAtCenter("nether", "heroic_nether");
        return spawned.isEmpty() ? null : spawned.get(0);
    }

    public void startAutoSpawnScheduler() {
        stopAutoSpawnScheduler();
        autoSpawnTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::runAutoSpawnCycle,
                AUTO_SPAWN_INTERVAL_TICKS,
                AUTO_SPAWN_INTERVAL_TICKS
        );
    }

    public void stopAutoSpawnScheduler() {
        if (autoSpawnTask != null) {
            autoSpawnTask.cancel();
            autoSpawnTask = null;
        }
    }

    public void clearActiveEnvoys() {
        for (String key : new ArrayList<>(activeEnvoys.keySet())) {
            activeEnvoys.remove(key);
            Block block = blockFromKey(key);
            if (block != null && block.getType() == Material.CHEST) {
                block.setType(Material.AIR);
            }
        }
    }

    public ActiveEnvoy getActiveEnvoy(Block block) {
        return activeEnvoys.get(blockKey(block));
    }

    public ItemStack openEnvoy(Block block) {
        ActiveEnvoy envoy = activeEnvoys.remove(blockKey(block));
        if (envoy == null) return null;

        if (block.getType() == Material.CHEST) {
            block.setType(Material.AIR);
        }

        return rollReward(envoy.tier());
    }

    private List<ActiveEnvoy> spawnAtCenter(String centerName, String tier) {
        List<ActiveEnvoy> spawned = new ArrayList<>();
        Location base = getCenter(centerName);
        if (base == null || base.getWorld() == null) return spawned;

        int playersInClaim = getOnlinePlayersInCenterWarzoneClaim(base);
        if (playersInClaim <= 0) {
            return spawned;
        }

        int chestTarget = getChestCountForPlayers(playersInClaim);
        if (chestTarget <= 0) {
            return spawned;
        }

        Set<String> occupiedKeys = new HashSet<>(activeEnvoys.keySet());
        int attempts = 0;
        int maxAttempts = Math.max(chestTarget * 10, 100);

        while (spawned.size() < chestTarget && attempts++ < maxAttempts) {
            Location spawnLoc = findSpawnLocation(base, spawned.isEmpty(), occupiedKeys);
            if (spawnLoc == null || spawnLoc.getWorld() == null) {
                continue;
            }

            Block block = spawnLoc.getBlock();
            block.setType(Material.CHEST);

            ActiveEnvoy envoy = new ActiveEnvoy(tier, centerName.toLowerCase(Locale.ROOT), block.getLocation());
            String key = blockKey(block);
            activeEnvoys.put(key, envoy);
            occupiedKeys.add(key);
            spawned.add(envoy);
        }

        return spawned;
    }

    private int getOnlinePlayersInCenterWarzoneClaim(Location centerLocation) {
        SimpleFactionsPlugin sf = SimpleFactionsPlugin.getInstance();
        if (sf == null || sf.getWarzoneManager() == null) {
            return 0;
        }

        return sf.getWarzoneManager().countOnlinePlayersInSameWarzoneClaim(centerLocation);
    }

    private int getChestCountForPlayers(int playerCount) {
        int cappedPlayers = Math.min(MAX_SCALING_PLAYERS_PER_CENTER, Math.max(0, playerCount));
        if (cappedPlayers <= 0) {
            return 0;
        }

        int min = 7 + (cappedPlayers - 1) * 3;
        int max = 15 + (cappedPlayers - 1) * 3;
        return random.nextInt((max - min) + 1) + min;
    }

    private Location findSpawnLocation(Location center, boolean allowCenterFirst, Set<String> occupiedKeys) {
        if (center == null || center.getWorld() == null) {
            return null;
        }

        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        if (allowCenterFirst) {
            Block centerBlock = resolveChestBlock(world, cx, cy, cz);
            if (centerBlock != null) {
                String key = blockKey(centerBlock);
                if (!occupiedKeys.contains(key)) {
                    return centerBlock.getLocation();
                }
            }
        }

        for (int i = 0; i < 16; i++) {
            int dx = random.nextInt(25) - 12;
            int dz = random.nextInt(25) - 12;
            Block block = resolveChestBlock(world, cx + dx, cy, cz + dz);
            if (block == null) {
                continue;
            }

            String key = blockKey(block);
            if (!occupiedKeys.contains(key)) {
                return block.getLocation();
            }
        }

        return null;
    }

    private Block resolveChestBlock(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }

        Block block = world.getBlockAt(x, y, z);
        if (!block.isEmpty()) {
            Block above = world.getBlockAt(x, y + 1, z);
            if (above.isEmpty()) {
                block = above;
            } else {
                int highest = world.getHighestBlockYAt(x, z);
                // Never place chests above y=130 to avoid spawning on tall structures.
                if (highest + 1 > 130) {
                    return null;
                }
                block = world.getBlockAt(x, highest + 1, z);
            }
        }

        if (!block.isEmpty()) {
            return null;
        }

        return block;
    }

    private void runAutoSpawnCycle() {
        List<ActiveEnvoy> standard = spawnStandardEnvoys();
        ActiveEnvoy heroic = spawnHeroicNetherEnvoy();

        if (!standard.isEmpty()) {
            Bukkit.broadcastMessage("§6[SimpleEnvoy] §eEnvoys have spawned at §f" + joinWarpNames(standard) + "§e!");
        }

        if (heroic != null) {
            Bukkit.broadcastMessage("§5[SimpleEnvoy] §dHeroic Nether Envoy has spawned at §fnether§d!");
        }

        if (standard.isEmpty() && heroic == null) {
            plugin.getLogger().warning("Skipped envoy auto-spawn cycle: no players in the required warzone claims or centers missing.");
        }
    }

    private String joinWarpNames(List<ActiveEnvoy> envoys) {
        Set<String> names = new LinkedHashSet<>();
        for (ActiveEnvoy envoy : envoys) {
            if (envoy != null && envoy.warpName() != null && !envoy.warpName().isBlank()) {
                names.add(envoy.warpName());
            }
        }
        return names.isEmpty() ? "unknown" : String.join(", ", names);
    }

    private void loadLoot() {
        lootByTier.clear();

        if (!lootFile.exists()) {
            seedDefaults();
            saveLoot();
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(lootFile);
        ConfigurationSection tiers = yaml.getConfigurationSection("tiers");
        if (tiers == null) {
            seedDefaults();
            saveLoot();
            return;
        }

        for (String tierKey : tiers.getKeys(false)) {
            String normalizedTier = normalizeTier(tierKey);
            List<LootEntry> entries = new ArrayList<>();
            ConfigurationSection tierSection = tiers.getConfigurationSection(tierKey);
            if (tierSection == null) continue;

            for (String index : tierSection.getKeys(false)) {
                String base = "tiers." + tierKey + "." + index;
                ItemStack item = yaml.getItemStack(base + ".item");
                double chance = yaml.getDouble(base + ".chance", 0.0);
                if (item == null || chance <= 0) continue;

                entries.add(new LootEntry(item, chance));
            }

            lootByTier.put(normalizedTier, entries);
        }

        ensureTier("simple");
        ensureTier("rare");
        ensureTier("legendary");
        ensureTier("godly");
        ensureTier("heroic_nether");
    }

    private void saveLoot() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<String, List<LootEntry>> entry : lootByTier.entrySet()) {
            String tier = entry.getKey();
            List<LootEntry> entries = entry.getValue();

            for (int i = 0; i < entries.size(); i++) {
                LootEntry loot = entries.get(i);
                String base = "tiers." + tier + "." + i;
                yaml.set(base + ".item", loot.item());
                yaml.set(base + ".chance", loot.chance());
            }
        }

        try {
            yaml.save(lootFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save envoy-loot.yml: " + e.getMessage());
        }
    }

    private void loadCenters() {
        centers.clear();

        if (!centersFile.exists()) {
            saveCenters();
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(centersFile);
        ConfigurationSection section = yaml.getConfigurationSection("centers");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            String normalized = normalizeCenterName(key);
            if (!isAllowedCenter(normalized)) {
                continue;
            }

            String worldName = yaml.getString("centers." + key + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }

            double x = yaml.getDouble("centers." + key + ".x");
            double y = yaml.getDouble("centers." + key + ".y");
            double z = yaml.getDouble("centers." + key + ".z");
            float yaw = (float) yaml.getDouble("centers." + key + ".yaw", 0.0);
            float pitch = (float) yaml.getDouble("centers." + key + ".pitch", 0.0);

            centers.put(normalized, new Location(world, x, y, z, yaw, pitch));
        }
    }

    private void saveCenters() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<String, Location> entry : centers.entrySet()) {
            Location location = entry.getValue();
            if (location == null || location.getWorld() == null) {
                continue;
            }

            String base = "centers." + entry.getKey();
            yaml.set(base + ".world", location.getWorld().getName());
            yaml.set(base + ".x", location.getX());
            yaml.set(base + ".y", location.getY());
            yaml.set(base + ".z", location.getZ());
            yaml.set(base + ".yaw", location.getYaw());
            yaml.set(base + ".pitch", location.getPitch());
        }

        try {
            yaml.save(centersFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save envoy-centers.yml: " + e.getMessage());
        }
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private Block blockFromKey(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) return null;

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;

        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return world.getBlockAt(x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void ensureTier(String tier) {
        lootByTier.computeIfAbsent(normalizeTier(tier), ignored -> new ArrayList<>());
    }

    private String normalizeCenterName(String centerName) {
        return centerName == null ? "" : centerName.toLowerCase(Locale.ROOT).trim();
    }

    private boolean isAllowedCenter(String centerName) {
        return REQUIRED_CENTERS.contains(centerName);
    }

    private void seedDefaults() {
        lootByTier.clear();
        lootByTier.put("simple", new ArrayList<>(List.of(
                new LootEntry(new ItemStack(Material.IRON_INGOT, 16), 50),
                new LootEntry(new ItemStack(Material.GOLDEN_APPLE, 2), 25),
                new LootEntry(new ItemStack(Material.EXPERIENCE_BOTTLE, 16), 25)
        )));
        lootByTier.put("rare", new ArrayList<>(List.of(
                new LootEntry(new ItemStack(Material.DIAMOND, 12), 40),
                new LootEntry(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), 20),
                new LootEntry(new ItemStack(Material.ENDER_PEARL, 16), 40)
        )));
        lootByTier.put("legendary", new ArrayList<>(List.of(
                new LootEntry(new ItemStack(Material.NETHERITE_INGOT, 2), 30),
                new LootEntry(new ItemStack(Material.TOTEM_OF_UNDYING, 1), 20),
                new LootEntry(new ItemStack(Material.DIAMOND_BLOCK, 4), 50)
        )));
        lootByTier.put("godly", new ArrayList<>(List.of(
                new LootEntry(new ItemStack(Material.NETHER_STAR, 1), 20),
                new LootEntry(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4), 40),
                new LootEntry(new ItemStack(Material.ANCIENT_DEBRIS, 8), 40)
        )));
        lootByTier.put("heroic_nether", new ArrayList<>(List.of(
                new LootEntry(new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1), 20),
                new LootEntry(new ItemStack(Material.NETHERITE_INGOT, 6), 40),
                new LootEntry(new ItemStack(Material.ANCIENT_DEBRIS, 24), 40)
        )));
    }
}
