package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerHomeManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, Location>> homes = new LinkedHashMap<>();

    public PlayerHomeManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
    }

    public void load() {
        homes.clear();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;

        for (String uuidKey : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            ConfigurationSection playerHomes = players.getConfigurationSection(uuidKey + ".homes");
            if (playerHomes == null) continue;

            Map<String, Location> byName = new LinkedHashMap<>();
            for (String homeName : playerHomes.getKeys(false)) {
                String path = uuidKey + ".homes." + homeName;
                String worldName = players.getString(path + ".world");
                World world = worldName == null ? null : Bukkit.getWorld(worldName);
                if (world == null) continue;

                double x = players.getDouble(path + ".x");
                double y = players.getDouble(path + ".y");
                double z = players.getDouble(path + ".z");
                float yaw = (float) players.getDouble(path + ".yaw");
                float pitch = (float) players.getDouble(path + ".pitch");

                byName.put(homeName.toLowerCase(), new Location(world, x, y, z, yaw, pitch));
            }

            homes.put(uuid, byName);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Location>> playerEntry : homes.entrySet()) {
            String playerPath = "players." + playerEntry.getKey() + ".homes";
            for (Map.Entry<String, Location> homeEntry : playerEntry.getValue().entrySet()) {
                Location location = homeEntry.getValue();
                if (location.getWorld() == null) continue;
                String base = playerPath + "." + homeEntry.getKey();
                yaml.set(base + ".world", location.getWorld().getName());
                yaml.set(base + ".x", location.getX());
                yaml.set(base + ".y", location.getY());
                yaml.set(base + ".z", location.getZ());
                yaml.set(base + ".yaw", location.getYaw());
                yaml.set(base + ".pitch", location.getPitch());
            }
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save homes.yml: " + e.getMessage());
        }
    }

    public Map<String, Location> getHomes(UUID playerId) {
        Map<String, Location> map = homes.get(playerId);
        if (map == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(map);
    }

    public Location getHome(UUID playerId, String homeName) {
        Map<String, Location> byName = homes.get(playerId);
        if (byName == null) return null;
        return byName.get(homeName.toLowerCase());
    }

    public int getHomeCount(UUID playerId) {
        Map<String, Location> byName = homes.get(playerId);
        return byName == null ? 0 : byName.size();
    }

    public boolean hasHome(UUID playerId, String homeName) {
        return getHome(playerId, homeName) != null;
    }

    public void setHome(UUID playerId, String homeName, Location location) {
        homes.computeIfAbsent(playerId, id -> new LinkedHashMap<>())
                .put(homeName.toLowerCase(), location.clone());
    }
}
