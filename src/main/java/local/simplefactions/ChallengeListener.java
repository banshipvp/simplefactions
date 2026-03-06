package local.simplefactions;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * Listens for world events and forwards them to ChallengeManager.
 *
 * Supported tracker types handled here (native events):
 *   BLOCK_BREAK, BLOCK_PLACE, PLAYER_KILL, MOB_KILL, FISH_CAUGHT, XP_GAIN
 *
 * The following are handled via cross-plugin hooks (faction-enchants, SimpleEconomy):
 *   BOOK_OPEN, ENCHANT_APPLY, ENVOY_OPEN, COINFLIP_WIN
 */
public class ChallengeListener implements Listener {

    private final ChallengeManager manager;

    public ChallengeListener(ChallengeManager manager) {
        this.manager = manager;
    }

    // ── BLOCK_BREAK ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        manager.increment(p.getUniqueId(), p.getName(),
                ChallengeManager.TrackerType.BLOCK_BREAK,
                e.getBlock().getType(), null);
    }

    // ── BLOCK_PLACE ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        manager.increment(p.getUniqueId(), p.getName(),
                ChallengeManager.TrackerType.BLOCK_PLACE,
                e.getBlock().getType(), null);
    }

    // ── PLAYER_KILL ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        manager.increment(killer.getUniqueId(), killer.getName(),
                ChallengeManager.TrackerType.PLAYER_KILL, null, null);
    }

    // ── MOB_KILL ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntityType() == EntityType.PLAYER) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        manager.increment(killer.getUniqueId(), killer.getName(),
                ChallengeManager.TrackerType.MOB_KILL,
                null, e.getEntityType());
    }

    // ── FISH_CAUGHT ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = e.getPlayer();
        manager.increment(p.getUniqueId(), p.getName(),
                ChallengeManager.TrackerType.FISH_CAUGHT, null, null);
    }

    // ── XP_GAIN ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onXpGain(PlayerExpChangeEvent e) {
        if (e.getAmount() <= 0) return;
        Player p = e.getPlayer();
        manager.increment(p.getUniqueId(), p.getName(),
                ChallengeManager.TrackerType.XP_GAIN, null, null, e.getAmount());
    }
}
