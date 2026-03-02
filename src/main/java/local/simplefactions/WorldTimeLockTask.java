package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class WorldTimeLockTask extends BukkitRunnable {

    private final JavaPlugin plugin;

    public WorldTimeLockTask(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        String hubWorld = plugin.getConfig().getString("time-lock.hub-world", "hub");
        long hubTime = plugin.getConfig().getLong("time-lock.hub-time", 18000L);
        setWorldTime(hubWorld, hubTime);

        List<String> factionWorlds = plugin.getConfig().getStringList("time-lock.faction-worlds");
        long factionTime = plugin.getConfig().getLong("time-lock.faction-time", 1000L);
        for (String worldName : factionWorlds) {
            setWorldTime(worldName, factionTime);
        }
    }

    private void setWorldTime(String worldName, long time) {
        if (worldName == null || worldName.isBlank()) {
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setTime(time);
        }
    }
}
