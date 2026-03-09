package local.simplefactions;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces a 10-second cooldown between ender-pearl throws.
 *
 * <p>When a player tries to throw an ender pearl while on cooldown the
 * projectile is removed immediately and the player is told how many seconds
 * remain.
 */
public class EnderPearlCooldownListener implements Listener {

    /** How long (in milliseconds) players must wait between throws. */
    private static final long COOLDOWN_MS = 10_000L;

    /** Maps player UUID → timestamp (ms) of their last successful throw. */
    private final Map<UUID, Long> lastThrow = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        if (!(pearl.getShooter() instanceof Player player)) return;

        // Staff with explicit bypass permission skip the cooldown
        if (player.hasPermission("simplefactions.staff")) return;

        long now  = System.currentTimeMillis();
        long last = lastThrow.getOrDefault(player.getUniqueId(), 0L);
        long remaining = COOLDOWN_MS - (now - last);

        if (remaining > 0) {
            event.setCancelled(true);
            pearl.remove(); // ensure the entity is gone server-side
            // Safely return the pearl item
            try {
                ItemStack pearlItem = pearl.getItem();
                if (pearlItem != null && pearlItem.getType() == Material.ENDER_PEARL) {
                    player.getInventory().addItem(pearlItem.clone());
                } else {
                    player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
                }
            } catch (Exception ignored) {
                player.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
            }
            long secsLeft = (remaining + 999) / 1000; // round up
            int ticksLeft = (int) (remaining / 50);
            player.setCooldown(Material.ENDER_PEARL, ticksLeft); // native visual bar
            player.sendMessage(ChatColor.RED + "Ender Pearl cooldown: §f" + secsLeft + "s §cremaining.");
            return;
        }

        lastThrow.put(player.getUniqueId(), now);
        // Set the native cooldown bar for the full duration so client sees it
        player.setCooldown(Material.ENDER_PEARL, (int) (COOLDOWN_MS / 50));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastThrow.remove(event.getPlayer().getUniqueId());
    }
}
