package local.simplefactions;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Shows the WorldEdit selection box after setting //pos1 or //pos2.
 */
public class WorldEditSelectionListener implements Listener {

    private final JavaPlugin plugin;
    private final WarzoneCommand warzoneCommand;
    private final Set<UUID> enabledPlayers = new HashSet<>();

    public WorldEditSelectionListener(JavaPlugin plugin, WarzoneCommand warzoneCommand) {
        this.plugin = plugin;
        this.warzoneCommand = warzoneCommand;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.length() < 5) return;

        String lower = message.toLowerCase(Locale.ROOT);
        if (!isWorldEditPosCommand(lower)) return;

        if (!event.getPlayer().hasPermission("simplefactions.admin")) return;
        if (!isEnabled(event.getPlayer().getUniqueId())) return;

        // Let WorldEdit update the selection first, then render the outline.
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                warzoneCommand.showSelectionFor(event.getPlayer(), false, true), 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        enabledPlayers.remove(event.getPlayer().getUniqueId());
    }

    public void setEnabled(UUID playerId, boolean enabled) {
        if (enabled) {
            enabledPlayers.add(playerId);
        } else {
            enabledPlayers.remove(playerId);
        }
    }

    public boolean isEnabled(UUID playerId) {
        return enabledPlayers.contains(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onWandSelect(PlayerInteractEvent event) {
        if (!isEnabled(event.getPlayer().getUniqueId())) return;

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.WOODEN_AXE) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                warzoneCommand.showSelectionFor(event.getPlayer(), false, true), 1L);
    }

    private boolean isWorldEditPosCommand(String message) {
        return message.startsWith("//pos1")
                || message.startsWith("//pos2")
                || message.startsWith("//hpos1")
                || message.startsWith("//hpos2")
                || message.startsWith("/pos1")
                || message.startsWith("/pos2")
                || message.startsWith("/hpos1")
                || message.startsWith("/hpos2");
    }
}
