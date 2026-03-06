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

public class GlobalWarpManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Location> warps = new LinkedHashMap<>();

    public GlobalWarpManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "warps.yml");
    }

    public void load() {
        warps.clear();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("warps");
        if (section == null) return;

        for (String warpName : section.getKeys(false)) {
            String base = "warps." + warpName;
            String worldName = yaml.getString(base + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) continue;

            double x = yaml.getDouble(base + ".x");
            double y = yaml.getDouble(base + ".y");
            double z = yaml.getDouble(base + ".z");
            float yaw = (float) yaml.getDouble(base + ".yaw");
            float pitch = (float) yaml.getDouble(base + ".pitch");

            warps.put(warpName.toLowerCase(), new Location(world, x, y, z, yaw, pitch));
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Location> entry : warps.entrySet()) {
            Location location = entry.getValue();
            if (location.getWorld() == null) continue;
            String base = "warps." + entry.getKey();
            yaml.set(base + ".world", location.getWorld().getName());
            yaml.set(base + ".x", location.getX());
            yaml.set(base + ".y", location.getY());
            yaml.set(base + ".z", location.getZ());
            yaml.set(base + ".yaw", location.getYaw());
            yaml.set(base + ".pitch", location.getPitch());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save warps.yml: " + e.getMessage());
        }
    }

    public Map<String, Location> getAllWarps() {
        return Collections.unmodifiableMap(warps);
    }

    public Location getWarp(String name) {
        return warps.get(name.toLowerCase());
    }

    public boolean hasWarp(String name) {
        return getWarp(name) != null;
    }

    public void setWarp(String name, Location location) {
        warps.put(name.toLowerCase(), location.clone());
    }

    public boolean removeWarp(String name) {
        return warps.remove(name.toLowerCase()) != null;
    }
}
