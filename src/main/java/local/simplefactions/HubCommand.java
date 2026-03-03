package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class HubCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public HubCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sethub")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use /sethub.");
                return true;
            }
            if (!player.hasPermission("simplefactions.sethub")) {
                player.sendMessage("§cYou don't have permission to set the hub.");
                return true;
            }
            saveHubLocation(player.getLocation());
            player.sendMessage("§aHub location set!");
            return true;
        }

        // /hub
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /hub.");
            return true;
        }

        Location hub = getHubLocation();
        if (hub == null) {
            player.sendMessage("§cHub has not been set yet. An admin must run /sethub.");
            return true;
        }

        player.teleport(hub);
        player.sendMessage("§aWelcome to the Hub!");
        return true;
    }

    public void saveHubLocation(Location loc) {
        FileConfiguration config = plugin.getConfig();
        config.set("hub.world",  loc.getWorld().getName());
        config.set("hub.x",     loc.getX());
        config.set("hub.y",     loc.getY());
        config.set("hub.z",     loc.getZ());
        config.set("hub.yaw",   loc.getYaw());
        config.set("hub.pitch", loc.getPitch());
        plugin.saveConfig();
    }

    public Location getHubLocation() {
        FileConfiguration config = plugin.getConfig();
        if (config.contains("hub.world")) {
            World world = Bukkit.getWorld(config.getString("hub.world"));
            if (world != null) {
                return new Location(world,
                        config.getDouble("hub.x"),
                        config.getDouble("hub.y"),
                        config.getDouble("hub.z"),
                        (float) config.getDouble("hub.yaw"),
                        (float) config.getDouble("hub.pitch"));
            }
        }

        String fallbackWorldName = config.getString("worlds.hub",
                config.getString("hub-command-lock.world", "hub"));
        World fallbackWorld = Bukkit.getWorld(fallbackWorldName);
        if (fallbackWorld == null) {
            return null;
        }
        Location spawn = fallbackWorld.getSpawnLocation();
        return spawn.clone().add(0.5, 0.0, 0.5);
    }
}
