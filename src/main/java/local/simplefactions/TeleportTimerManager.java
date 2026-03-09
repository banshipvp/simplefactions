package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages delayed teleports with territory restriction checks.
 *
 * <p>Rules:
 * <ul>
 *   <li>Warzone or safezone → teleport <b>blocked</b></li>
 *   <li>Enemy or neutral faction territory → teleport <b>blocked</b></li>
 *   <li>Wilderness, own faction land, or ally land → teleport <b>allowed</b> with countdown</li>
 * </ul>
 *
 * <p>Countdown duration comes from {@link PlayerRank#getTpDelaySeconds()}:
 * <ul>
 *   <li>DEFAULT / SCOUT / MILITANT / TACTICIAN → 7 seconds</li>
 *   <li>WARLORD, SOVEREIGN, staff ranks → 2 seconds</li>
 * </ul>
 *
 * <p>If the player moves their feet during the countdown the teleport is cancelled.
 */
public class TeleportTimerManager implements Listener {

    private final JavaPlugin plugin;
    private final FactionManager factionManager;
    private final WarzoneManager warzoneManager;
    private final PlayerRankManager rankManager;

    /** Active pending teleports keyed by player UUID. */
    private final Map<UUID, PendingTp> pending = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────

    private static final class PendingTp {
        final Location destination;
        final BukkitTask task;
        final int startBlockX;
        final int startBlockY;
        final int startBlockZ;

        PendingTp(Location destination, BukkitTask task, Location startLoc) {
            this.destination  = destination;
            this.task         = task;
            this.startBlockX  = startLoc.getBlockX();
            this.startBlockY  = startLoc.getBlockY();
            this.startBlockZ  = startLoc.getBlockZ();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    public TeleportTimerManager(JavaPlugin plugin,
                                FactionManager factionManager,
                                WarzoneManager warzoneManager,
                                PlayerRankManager rankManager) {
        this.plugin          = plugin;
        this.factionManager  = factionManager;
        this.warzoneManager  = warzoneManager;
        this.rankManager     = rankManager;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Begins a countdown to teleport the player to {@code destination}.
     *
     * <p>Performs territory restriction at the player's <em>current</em> location.
     * If the player is in blocked territory, a denial message is sent and this
     * method returns {@code false} without scheduling anything.
     *
     * @param player      the player to teleport
     * @param destination where to send them when the countdown finishes
     * @param destLabel   human-readable name shown in the countdown bar and success msg
     * @return {@code true} if the teleport was scheduled (or fired instantly);
     *         {@code false} if territory blocks the teleport
     */
    public boolean scheduleTeleport(Player player, Location destination, String destLabel) {
        // ① Territory check at player's current foot location
        if (!checkTerritory(player)) return false;

        // ② Cancel any previously pending TP silently
        cancelPending(player.getUniqueId(), false);

        // ③ Determine delay from rank
        PlayerRank rank = rankManager.getRank(player.getUniqueId());
        int delaySecs = rank.getTpDelaySeconds();

        if (delaySecs <= 0) {
            // Instant teleport (sovereign / staff)
            player.teleport(destination);
            player.sendMessage("§aTeleported to §f" + destLabel + "§a.");
            return true;
        }

        // ④ Schedule countdown
        Location startLoc = player.getLocation().clone();
        UUID uuid = player.getUniqueId();

        final int[] countdown = { delaySecs };

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) {
                    pending.remove(uuid);
                    cancel();
                    return;
                }

                if (countdown[0] > 0) {
                    String sLabel = countdown[0] == 1 ? "second" : "seconds";
                    p.sendActionBar("§6Teleporting to §f" + destLabel
                            + " §6in §e" + countdown[0] + " " + sLabel
                            + " §7— don't move!");
                    countdown[0]--;
                } else {
                    // Time's up — teleport!
                    p.teleport(destination);
                    p.sendMessage("§aTeleported to §f" + destLabel + "§a.");
                    p.sendActionBar("§aTeleported!");
                    pending.remove(uuid);
                    cancel();
                }
            }
        };

        BukkitTask task = runnable.runTaskTimer(plugin, 0L, 20L);
        pending.put(uuid, new PendingTp(destination, task, startLoc));
        return true;
    }

    /**
     * Cancels a pending teleport for this player, notifying them if one existed.
     */
    public void cancelPending(UUID uuid) {
        cancelPending(uuid, true);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void cancelPending(UUID uuid, boolean notify) {
        PendingTp pt = pending.remove(uuid);
        if (pt != null) {
            pt.task.cancel();
            if (notify) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.sendMessage("§cTeleport cancelled.");
            }
        }
    }

    /**
     * Checks whether the player is in a location where teleport is permitted.
     * Sends a denial message and returns {@code false} when blocked.
     */
    private boolean checkTerritory(Player player) {
        Location loc = player.getLocation();

        // Warzone: hard blocked
        if (warzoneManager.isWarzone(loc)) {
            player.sendMessage("§cYou cannot teleport from §4warzone§c territory.");
            return false;
        }

        // Safezone (spawn): allowed
        if (warzoneManager.isSafezone(loc)) {
            return true;
        }

        // Check faction land ownership
        String world = loc.getWorld().getName();
        Chunk  chunk = loc.getChunk();
        FactionManager.Faction landFaction =
                factionManager.getFactionByChunk(world, chunk.getX(), chunk.getZ());

        // Wilderness — always allowed
        if (landFaction == null) return true;

        // System factions (Warzone system-faction) — shouldn't normally reach here
        // since WarzoneManager.isWarzone() already captured it, but guard anyway
        if (factionManager.isSystemFaction(landFaction)) {
            player.sendMessage("§cYou cannot teleport from §4warzone§c territory.");
            return false;
        }

        // Own faction land — allowed
        FactionManager.Faction playerFaction = factionManager.getFaction(player.getUniqueId());
        if (playerFaction != null && playerFaction.getName().equalsIgnoreCase(landFaction.getName())) {
            return true;
        }

        // Ally land — allowed
        if (playerFaction != null && playerFaction.isAlly(landFaction.getName())) {
            return true;
        }

        // Enemy or neutral faction territory — blocked
        player.sendMessage("§cYou cannot teleport from §e" + landFaction.getName() + "§c territory.");
        return false;
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    /**
     * Cancels the pending teleport when the player physically moves their feet.
     * Camera rotation alone does not cancel.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PendingTp pt = pending.get(uuid);
        if (pt == null) return;

        Location from = event.getFrom();
        Location to   = event.getTo();
        if (to == null) return;

        // Only cancel on foot movement, not just head rotation
        if (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            cancelPending(uuid, false);
            event.getPlayer().sendMessage("§cTeleport cancelled §8—§7 you moved!");
            event.getPlayer().sendActionBar("");
        }
    }

    /** Cleans up when a player logs off during a countdown. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer().getUniqueId(), false);
    }
}
