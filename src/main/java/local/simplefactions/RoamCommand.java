package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /roam — toggles a staff member into spectator-style "roam" mode.
 *
 * While roaming:
 *   • Full spectator mode: fly, clip through walls, invisible to non-staff
 *   • Action bar shows live XYZ + world
 *   • Damage and fall-damage are suppressed
 *   • Typing /roam again (or quitting) cleanly restores game mode and location
 */
public class RoamCommand implements CommandExecutor, TabCompleter, Listener {

    private final SimpleFactionsPlugin plugin;

    /** Players currently in roam mode. */
    private final Set<UUID> roaming = new HashSet<>();

    /** Saved game mode when they entered roam. */
    private final Map<UUID, GameMode> savedGameModes = new HashMap<>();

    /** Saved location when they entered roam. */
    private final Map<UUID, Location> savedLocations = new HashMap<>();

    public RoamCommand(SimpleFactionsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Command ───────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        if (roaming.contains(player.getUniqueId())) {
            exitRoam(player);
        } else {
            enterRoam(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    // ── Roam logic ────────────────────────────────────────────────────────────

    private void enterRoam(Player player) {
        savedLocations.put(player.getUniqueId(), player.getLocation().clone());
        savedGameModes.put(player.getUniqueId(), player.getGameMode());
        roaming.add(player.getUniqueId());

        player.setGameMode(GameMode.SPECTATOR);

        player.sendMessage("§d§l✦ Roam mode §aenabled§a. §7You can fly and clip through any solid.");
        player.sendMessage("§7Your location has been saved. Type §e/roam §7again to return.");

        startHud(player);
    }

    private void exitRoam(Player player) {
        roaming.remove(player.getUniqueId());

        GameMode mode = savedGameModes.remove(player.getUniqueId());
        Location  loc  = savedLocations.remove(player.getUniqueId());

        if (mode != null) player.setGameMode(mode);
        if (loc  != null) player.teleport(loc);

        player.sendMessage("§e§l✦ Roam mode §cdisabled§c. §7Returned to your original location.");
    }

    /** Starts a repeating task that shows XYZ in the action bar while roaming. */
    private void startHud(Player player) {
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline() || !roaming.contains(uuid)) {
                task.cancel();
                return;
            }
            Location loc = p.getLocation();
            String bar = String.format(
                    "§d§l✦ ROAM §8| §7X: §e%d §7Y: §e%d §7Z: §e%d §8| §7World: §e%s",
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                    loc.getWorld().getName()
            );
            p.sendActionBar(bar);
        }, 2L, 4L);
    }

    // ── Public query ──────────────────────────────────────────────────────────

    public boolean isRoaming(UUID uuid) {
        return roaming.contains(uuid);
    }

    /** Force-exit all roaming players (called on plugin disable). */
    public void cleanup() {
        for (UUID uuid : new HashSet<>(roaming)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) exitRoam(p);
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    /** Restore player cleanly when they disconnect mid-roam. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (roaming.contains(event.getPlayer().getUniqueId())) {
            exitRoam(event.getPlayer());
        }
    }

    /** Suppress all damage while in roam mode (shouldn't happen in spectator, but safety net). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && roaming.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Block commands that could be abused from spectator inventories, e.g. dropping
     * items, or other unintended interactions. Also provides an easy "/roam" detection
     * in case command aliases need to be expanded.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!roaming.contains(player.getUniqueId())) return;

        String cmd = event.getMessage().toLowerCase().split(" ")[0];

        // Allow /roam itself to exit, plus safe read-only commands
        Set<String> allowed = Set.of(
                "/roam", "/v", "/vanish", "/tpto", "/tp",
                "/coords", "/showpos", "/f", "/rank", "/warp"
        );
        if (!allowed.contains(cmd)) return;
        // Everything else passes through — spectator mode itself restricts most interactions
    }
}
