package local.simplefactions;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;

/**
 * Prevents all mobs from targeting any entity (including players).
 * Effectively makes every mob passive on this server.
 */
public class MobCombatListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobTarget(EntityTargetEvent event) {
        // Cancel targeting by any non-player entity
        if (!(event.getEntity() instanceof Player)) {
            event.setCancelled(true);
        }
    }
}
