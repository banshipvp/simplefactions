package local.simplefactions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the faction-fly system:
 *
 * <ul>
 *   <li><b>Sovereign rank</b> ({@code canFly() == true}): flight is always enabled
 *       (no command needed) — they just double-tap space to fly anywhere.</li>
 *   <li><b>Everyone else</b>: flight is enabled only while standing in their own
 *       faction's claimed chunks OR in a safezone. Leaving those areas while
 *       flying immediately cuts flight.</li>
 *   <li><b>Combat</b>: any PvP hit flags both attacker and victim as "in combat"
 *       for 10 seconds. During that window, flight is disabled and cannot be
 *       re-enabled.</li>
 *   <li>Safezone lets <em>all</em> players fly, not just faction members.</li>
 * </ul>
 */
public class FactionFlyListener implements Listener {

    private static final long COMBAT_TICKS = 200L; // 10 seconds

    private final JavaPlugin plugin;
    private final FactionManager factionManager;
    private final WarzoneManager warzoneManager;
    private final PlayerRankManager playerRankManager;

    /** Players currently in PvP combat. */
    private final Set<UUID> combatSet = new HashSet<>();

    /** Active combat-expiry tasks so we can reset them on repeated hits. */
    private final Map<UUID, BukkitTask> combatTasks = new HashMap<>();

    public FactionFlyListener(JavaPlugin plugin,
                              FactionManager factionManager,
                              WarzoneManager warzoneManager,
                              PlayerRankManager playerRankManager) {
        this.plugin = plugin;
        this.factionManager = factionManager;
        this.warzoneManager = warzoneManager;
        this.playerRankManager = playerRankManager;
    }

    // ── Public helper ─────────────────────────────────────────────────────────

    /**
     * Recalculates and applies the correct flight state for a player based on
     * their rank, current chunk, safezone status, and combat state.
     * Call this whenever any of those factors change.
     */
    public void updateFlyState(Player player) {
        UUID uuid = player.getUniqueId();
        boolean inCombat = combatSet.contains(uuid);

        PlayerRank rank = playerRankManager.getRank(uuid);
        boolean hasPermanentFly = rank.canFly();

        Location loc = player.getLocation();
        boolean inSafezone   = warzoneManager.isSafezone(loc);
        boolean inOwnClaims  = isInOwnClaims(player, loc);

        boolean shouldAllow = !inCombat && (hasPermanentFly || inSafezone || inOwnClaims);

        // If losing fly, ground the player first to avoid bad client state
        if (!shouldAllow && player.isFlying()) {
            player.setFlying(false);
        }
        player.setAllowFlight(shouldAllow);
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    /** Restore correct fly state when a player logs in. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Delay 1 tick so the player's location is fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = event.getPlayer();
            if (p.isOnline()) updateFlyState(p);
        }, 1L);
    }

    /** Clean up state on logout and reset fly so it doesn't persist. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cancelCombatTask(uuid);
        combatSet.remove(uuid);
        // Reset flight flags on the player object so they don't carry over
        Player p = event.getPlayer();
        if (p.isFlying()) p.setFlying(false);
        p.setAllowFlight(false);
    }

    /** Re-evaluate fly state when the player respawns after death. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        // Delay 2 ticks — location is set after the event fires
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) updateFlyState(p);
        }, 2L);
    }

    /**
     * Update fly state whenever the player crosses a chunk boundary.
     * Using MONITOR priority so other plugins (e.g. anti-cheat) have already
     * processed the movement.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Only react to chunk changes for performance
        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;
        if (from.getChunk().equals(to.getChunk())) return;

        updateFlyState(event.getPlayer());
    }

    /**
     * PvP damage flags both attacker and victim as in-combat for 10 seconds.
     * Flight is immediately cut for both.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event);
        if (attacker == null) return; // not player vs player

        flagCombat(victim);
        flagCombat(attacker);
    }

    /**
     * Safety valve: if a player somehow tries to enable flight while in combat
     * (e.g., allowFlight was briefly set by another plugin), cancel it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!event.isFlying()) return; // player landing — always allow

        Player player = event.getPlayer();
        if (!combatSet.contains(player.getUniqueId())) return;

        event.setCancelled(true);
        player.sendActionBar(Component.text(
                "⚔ Cannot fly during combat!", NamedTextColor.RED));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Returns true if the player is currently standing in their own faction's claim. */
    private boolean isInOwnClaims(Player player, Location loc) {
        if (loc.getWorld() == null) return false;
        FactionManager.Faction playerFaction = factionManager.getFaction(player.getUniqueId());
        if (playerFaction == null) return false;
        // System factions (Warzone/Safezone) are not "own claims" — handled elsewhere
        if (factionManager.isSystemFaction(playerFaction)) return false;

        String owner = factionManager.getClaimOwner(
                loc.getWorld().getName(),
                loc.getChunk().getX(),
                loc.getChunk().getZ());

        return owner != null && owner.equalsIgnoreCase(playerFaction.getName());
    }

    /** Marks a player as in-combat and resets the 10-second expiry timer. */
    private void flagCombat(Player player) {
        UUID uuid = player.getUniqueId();
        combatSet.add(uuid);

        // Cut flight immediately
        if (player.isFlying()) player.setFlying(false);
        player.setAllowFlight(false);
        player.sendActionBar(Component.text(
                "⚔ In Combat — fly disabled (10s)", NamedTextColor.RED));

        // Reset expiry countdown
        cancelCombatTask(uuid);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            combatSet.remove(uuid);
            combatTasks.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                updateFlyState(p);
            }
        }, COMBAT_TICKS);
        combatTasks.put(uuid, task);
    }

    private void cancelCombatTask(UUID uuid) {
        BukkitTask old = combatTasks.remove(uuid);
        if (old != null) old.cancel();
    }

    /** Resolves the direct player attacker from a damage event (no projectile chasing). */
    private static Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) return attacker;
        return null;
    }
}
