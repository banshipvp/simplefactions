package local.simplefactions;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Set;

public class HubCommandRestrictionListener implements Listener {

    private static final Set<String> ALLOWED_BASE_COMMANDS = Set.of("hub", "spawn", "server", "message", "msg");

    private final JavaPlugin plugin;
    private final HubCommand hubCommand;

    public HubCommandRestrictionListener(JavaPlugin plugin, HubCommand hubCommand) {
        this.plugin = plugin;
        this.hubCommand = hubCommand;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isInHubWorld(player)) {
            return;
        }

        String message = event.getMessage();
        if (message == null || message.length() < 2 || message.charAt(0) != '/') {
            return;
        }

        String withoutSlash = message.substring(1).trim();
        if (withoutSlash.isEmpty()) {
            return;
        }

        String[] parts = withoutSlash.split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);

        if (!ALLOWED_BASE_COMMANDS.contains(label)) {
            deny(event);
            return;
        }

        if (label.equals("server")) {
            if (parts.length == 1) {
                return;
            }
            if (parts.length == 2 && parts[1].equalsIgnoreCase("factions")) {
                return;
            }
            deny(event);
        }
    }

    private boolean isInHubWorld(Player player) {
        Location hubLocation = hubCommand.getHubLocation();
        if (hubLocation != null && hubLocation.getWorld() != null) {
            return player.getWorld().equals(hubLocation.getWorld());
        }

        String configuredHubWorld = plugin.getConfig().getString("hub-command-lock.world", "hub");
        return player.getWorld().getName().equalsIgnoreCase(configuredHubWorld);
    }

    private void deny(PlayerCommandPreprocessEvent event) {
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cThat command is disabled in Hub.");
        event.getPlayer().sendMessage("§7Allowed: §a/hub§7, §a/spawn§7, §a/server§7, §a/server factions§7, §a/message§7, §a/msg");
    }
}
