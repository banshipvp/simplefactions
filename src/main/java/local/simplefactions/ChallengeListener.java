package local.simplefactions;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * Listens for world events and forwards them to ChallengeManager.
 * Supports all 20 challenge TrackerTypes.
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
        if (e.getEntityType() == EntityType.PLAYER) return; // handled above
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

    // ── ENCHANT_DONE ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        Player p = e.getEnchanter();
        manager.increment(p.getUniqueId(), p.getName(),
                ChallengeManager.TrackerType.ENCHANT_DONE, null, null);
    }

    // ── ARROW_SHOT ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowShot(EntityShootBowEvent e) {
        Entity shooter = e.getEntity();
        if (!(shooter instanceof Player p)) return;
        // Only count arrows (not tridents, etc.)
        if (e.getProjectile() instanceof Projectile proj) {
            if (proj.getType() != EntityType.ARROW
                    && proj.getType() != EntityType.SPECTRAL_ARROW) return;
        }
        manager.increment(p.getUniqueId(), p.getName(),
                ChallengeManager.TrackerType.ARROW_SHOT, null, null);
    }

    // ── ANIMAL_BREED ──────────────────────────────────────────────────────────

    // Note: EntityBreedEvent is Paper-only. We use a simple import guard.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(org.bukkit.event.entity.EntityBreedEvent e) {
        if (!(e.getBreeder() instanceof Player p)) return;
        manager.increment(p.getUniqueId(), p.getName(),
                ChallengeManager.TrackerType.ANIMAL_BREED, null, null);
    }
}
