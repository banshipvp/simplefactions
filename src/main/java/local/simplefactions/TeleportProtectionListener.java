package local.simplefactions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.time.Duration;

/**
 * Prevents players from teleporting into enemy or neutral faction claims.
 * Safezones, warzones, and wilderness are always freely accessible.
 */
public class TeleportProtectionListener implements Listener {

    private final FactionManager factionManager;
    private final WarzoneManager warzoneManager;

    public TeleportProtectionListener(FactionManager factionManager, WarzoneManager warzoneManager) {
        this.factionManager = factionManager;
        this.warzoneManager = warzoneManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location dest = event.getTo();
        if (dest == null || dest.getWorld() == null) return;

        // Allow teleporting into safezone / warzone freely
        if (warzoneManager.isSafezone(dest) || warzoneManager.isWarzone(dest)) return;

        String worldName = dest.getWorld().getName();
        int cx = dest.getChunk().getX();
        int cz = dest.getChunk().getZ();
        String owner = factionManager.getClaimOwner(worldName, cx, cz);

        // Wilderness — no restriction
        if (owner == null) return;

        // System factions (Warzone, Safezone) — no restriction
        FactionManager.Faction ownerFaction = factionManager.getFactionByName(owner);
        if (ownerFaction != null && factionManager.isSystemFaction(ownerFaction)) return;

        // Player's own faction — allow
        Player player = event.getPlayer();
        FactionManager.Faction playerFaction = factionManager.getFaction(player.getUniqueId());
        if (playerFaction != null && playerFaction.getName().equalsIgnoreCase(owner)) return;

        // Block the teleport: enemy or neutral territory
        event.setCancelled(true);

        player.sendMessage(Component.text(
                "You cannot teleport into enemy or neutral faction claims!", NamedTextColor.RED));

        player.showTitle(Title.title(
                Component.text("✗ Teleport Blocked", NamedTextColor.RED),
                Component.text("Enemy/neutral faction territory", NamedTextColor.GRAY),
                Title.Times.times(
                        Duration.ofMillis(150),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(500))));
    }
}
