package local.simplefactions;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Intercepts the EssentialsX /xpbottle command and enforces a per-rank cooldown.
 *
 * If the player's rank has {@link PlayerRank#hasXpExhaust()} == true and the
 * cooldown hasn't expired, the command is cancelled and the player is informed.
 *
 * The cooldown duration is taken from {@link PlayerRank#getXpExhaustMs()}.
 */
public class XpBottleListener implements Listener {

    private final PlayerRankManager rankManager;

    public XpBottleListener(PlayerRankManager rankManager) {
        this.rankManager = rankManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase();

        // Match /xpbottle and common aliases
        if (!message.startsWith("/xpbottle") && !message.startsWith("/xpbot") && !message.startsWith("/bottlexp")) {
            return;
        }

        Player player = event.getPlayer();
        PlayerRank rank = rankManager.getRank(player);

        // SOVEREIGN (0 min xp exhaust) or DEFAULT may have no restrict
        if (!rank.hasXpExhaust()) {
            // No cooldown for this rank – record use and allow through
            rankManager.recordXpBottleUse(player.getUniqueId());
            return;
        }

        if (rankManager.checkXpBottleCooldown(player)) {
            // Allowed – record use and let the command proceed
            rankManager.recordXpBottleUse(player.getUniqueId());
        } else {
            // Blocked
            event.setCancelled(true);
            long remaining = rankManager.getRemainingXpCooldownSeconds(player);
            player.sendMessage(ChatColor.RED + "✖ XP Bottle is on cooldown for your rank ("
                    + rank.getDisplayName() + ChatColor.RED + ").");
            player.sendMessage(ChatColor.RED + "  Time remaining: §e" + remaining + "s");
        }
    }
}
