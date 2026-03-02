package local.simplefactions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces warzone/safezone protection rules:
 *   - PvP cancelled in safezone chunks
 *   - Title shown on zone entry/exit
 *   - Auto-claim when admin walks with autoclaim enabled
 */
public class WarzoneListener implements Listener {

    private final WarzoneManager warzoneManager;
    private final WarzoneCommand warzoneCommand;
    private final FactionManager factionManager;

    /** Last zone type seen per player, to avoid repeated entry messages. */
    private final Map<UUID, WarzoneManager.WarzoneType> playerLastZone = new HashMap<>();

    public WarzoneListener(WarzoneManager warzoneManager,
                           WarzoneCommand warzoneCommand,
                           FactionManager factionManager) {
        this.warzoneManager = warzoneManager;
        this.warzoneCommand = warzoneCommand;
        this.factionManager = factionManager;
    }

    // ── PvP protection ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim  = event.getEntity();
        if (!(damager instanceof Player attacker) || !(victim instanceof Player)) return;

        // Cancel hits if either participant is in a safezone
        if (warzoneManager.isSafezone(attacker.getLocation())
                || warzoneManager.isSafezone(victim.getLocation())) {
            event.setCancelled(true);
            attacker.sendMessage("§c✗ PvP is disabled in the Safezone.");
        }
    }

    // ── Movement — zone entry titles + auto-claim ─────────────────────────────

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Only fire on chunk boundary
        if (event.getTo() == null) return;
        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk   = event.getTo().getChunk();
        if (fromChunk.getX() == toChunk.getX() && fromChunk.getZ() == toChunk.getZ()) return;

        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();
        String world  = player.getWorld().getName();
        int    cx     = toChunk.getX();
        int    cz     = toChunk.getZ();
        String toKey  = WarzoneManager.chunkKey(world, cx, cz);

        // ── Auto-claim ──
        Map<UUID, WarzoneManager.WarzoneType> autoClaiming = warzoneCommand.getAutoClaiming();
        if (autoClaiming.containsKey(uuid)) {
            WarzoneManager.WarzoneType claimAs = autoClaiming.get(uuid);
            String currentOwner = factionManager.getClaimOwner(world, cx, cz);
            if (currentOwner == null) {
                factionManager.claimChunkWarzone(world, cx, cz);
                warzoneManager.setChunkType(toKey, claimAs);
                String color = claimAs == WarzoneManager.WarzoneType.SAFEZONE ? "§b" : "§c";
                player.sendActionBar(Component.text(color + claimAs.name() + " §7chunk auto-claimed!"));
            }
        }

        // ── Entry/exit titles ──
        WarzoneManager.WarzoneType fromType = warzoneManager.getChunkType(
                WarzoneManager.chunkKey(world, fromChunk.getX(), fromChunk.getZ()));
        WarzoneManager.WarzoneType toType   = warzoneManager.getZoneTypeAt(event.getTo());

        if (fromType == toType) return; // no zone transition

        if (toType == WarzoneManager.WarzoneType.WARZONE) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text("WARZONE", NamedTextColor.RED).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD),
                    Component.text("PvP is enabled here", NamedTextColor.GRAY)
            ));
        } else if (toType == WarzoneManager.WarzoneType.SAFEZONE) {
            player.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text("SAFEZONE", NamedTextColor.GREEN).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD),
                    Component.text("PvP is disabled here", NamedTextColor.GRAY)
            ));
        } else if (fromType != null) {
            // Leaving a zone into wilderness
            player.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text("WILDERNESS", NamedTextColor.YELLOW).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD),
                    Component.text("Leaving protected area", NamedTextColor.GRAY)
            ));
        }
    }
}
