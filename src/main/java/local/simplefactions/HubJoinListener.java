package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class HubJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final HubCommand hubCommand;

    public HubJoinListener(JavaPlugin plugin, HubCommand hubCommand) {
        this.plugin = plugin;
        this.hubCommand = hubCommand;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Location hub = hubCommand.getHubLocation();
        if (hub == null) return;

        // 2-tick delay so the player's world is fully loaded before teleporting
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            var player = event.getPlayer();
            if (player.isOnline()) {
                player.teleport(hub);
                player.sendMessage("§eWelcome! Use §a/hub §eto return here at any time.");
                player.sendMessage("§eHead through the portal to enter §6Factions§e!");
            }
        }, 2L);
    }
}
