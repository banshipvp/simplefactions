package local.simplefactions;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public class SetHomeCommand implements CommandExecutor {

    private final PlayerHomeManager homeManager;
    private final PlayerRankManager rankManager;

    public SetHomeCommand(PlayerHomeManager homeManager, PlayerRankManager rankManager) {
        this.homeManager = homeManager;
        this.rankManager = rankManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        String homeName = args.length == 0 ? "home" : args[0].toLowerCase(Locale.ROOT);
        if (!homeName.matches("[a-zA-Z0-9_-]{1,16}")) {
            player.sendMessage("§cHome name must be 1-16 characters using letters, numbers, _ or -.");
            return true;
        }

        boolean exists = homeManager.hasHome(player.getUniqueId(), homeName);
        int current = homeManager.getHomeCount(player.getUniqueId());
        int max = rankManager.getMaxHomes(player.getUniqueId());

        if (!exists && current >= max && !player.hasPermission("simplefactions.homes.bypasslimit")) {
            player.sendMessage("§cYou have reached your max homes (" + max + ").");
            return true;
        }

        Location location = player.getLocation().clone();
        homeManager.setHome(player.getUniqueId(), homeName, location);
        homeManager.save();

        player.sendMessage("§aHome §f" + homeName + " §aset at §f"
                + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + "§a.");
        return true;
    }
}
