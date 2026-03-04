package local.simplefactions;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages a rank-priority queue for the hub → factions server join.
 *
 * Players join via {@code /queue join} or the portal trigger.
 * Higher-rank players are processed first (SOVEREIGN before DEFAULT).
 * Among players of the same rank, arrival order is preserved (FIFO).
 *
 * Every {@link #PROCESS_INTERVAL_TICKS} a batch of players is sent to the
 * configured target server via the BungeeCord {@code Connect} plugin messaging channel.
 */
public class HubQueueManager implements Listener, PluginMessageListener {

    public static final String BUNGEE_CHANNEL   = "BungeeCord";
    public static final String TARGET_SERVER     = "factions";  // BungeeCord server name
    private static final int   PROCESS_INTERVAL_TICKS = 60;    // 3 seconds
    private static final int   PLAYERS_PER_TICK  = 1;          // 1 player sent per interval

    private final JavaPlugin        plugin;
    private final PlayerRankManager rankManager;

    /**
     * Ordered map: UUID → entry time (millis), maintained in insertion order.
     * We re-sort on each processing tick so late arrivals with a higher rank
     * can overtake earlier arrivals with a lower rank.
     */
    private final Map<UUID, Long> queuedPlayers = new LinkedHashMap<>();

    private BukkitTask processTask;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public HubQueueManager(JavaPlugin plugin, PlayerRankManager rankManager) {
        this.plugin      = plugin;
        this.rankManager = rankManager;
    }

    public void start() {
        // Register the BungeeCord channel so we can send plugin messages
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);

        processTask = Bukkit.getScheduler().runTaskTimer(plugin,
                this::processQueue, PROCESS_INTERVAL_TICKS, PROCESS_INTERVAL_TICKS);

        plugin.getLogger().info("[HubQueue] Started – target server: " + TARGET_SERVER);
    }

    public void stop() {
        if (processTask != null) {
            processTask.cancel();
            processTask = null;
        }
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
    }

    // ── Queue management ──────────────────────────────────────────────────────

    /**
     * Add a player to the queue.
     * @return true if added; false if already queued.
     */
    public boolean enqueue(Player player) {
        if (queuedPlayers.containsKey(player.getUniqueId())) return false;
        queuedPlayers.put(player.getUniqueId(), System.currentTimeMillis());
        return true;
    }

    /**
     * Remove a player from the queue (voluntary leave or disconnect).
     * @return true if they were in the queue.
     */
    public boolean dequeue(UUID playerId) {
        return queuedPlayers.remove(playerId) != null;
    }

    public boolean isQueued(UUID playerId) {
        return queuedPlayers.containsKey(playerId);
    }

    /** Returns all queued players sorted by priority (highest rank first, FIFO tie-break). */
    public List<UUID> getSortedQueue() {
        List<UUID> list = new ArrayList<>(queuedPlayers.keySet());
        list.sort(Comparator
                .comparingInt((UUID id) -> rankManager.getRank(id).getQueuePriority())
                .reversed()
                .thenComparingLong(id -> queuedPlayers.getOrDefault(id, Long.MAX_VALUE)));
        return list;
    }

    /** Returns this player's 1-based position in the priority-sorted queue (0 if not queued). */
    public int getPosition(UUID playerId) {
        List<UUID> sorted = getSortedQueue();
        int pos = sorted.indexOf(playerId);
        return pos == -1 ? 0 : pos + 1;
    }

    public int size() {
        return queuedPlayers.size();
    }

    // ── Processing ────────────────────────────────────────────────────────────

    private void processQueue() {
        if (queuedPlayers.isEmpty()) return;

        List<UUID> sorted = getSortedQueue();
        int sent = 0;

        for (UUID id : sorted) {
            if (sent >= PLAYERS_PER_TICK) break;

            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                // Player offline – remove from queue silently
                queuedPlayers.remove(id);
                continue;
            }

            // Send via BungeeCord
            if (sendToServer(player)) {
                queuedPlayers.remove(id);
                player.sendMessage(ChatColor.GREEN + "✔ Connecting you to §6Factions§a...");
                sent++;
            }
        }
    }

    /**
     * Sends a player to {@link #TARGET_SERVER} via BungeeCord plugin messaging.
     * Returns false if no proxy connection is available.
     */
    private boolean sendToServer(Player player) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(TARGET_SERVER);
            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, out.toByteArray());
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[HubQueue] BungeeCord connect failed for "
                    + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    /**
     * Broadcasts position updates to all queued players.
     * Call periodically if desired; lightweight with small queues.
     */
    public void broadcastPositions() {
        List<UUID> sorted = getSortedQueue();
        for (int i = 0; i < sorted.size(); i++) {
            Player p = Bukkit.getPlayer(sorted.get(i));
            if (p != null) {
                int pos = i + 1;
                p.sendMessage(ChatColor.YELLOW + "[ Queue ] §fPosition: §e" + pos
                        + "§f/§e" + sorted.size()
                        + " §7| Rank: " + rankManager.getRank(p).getDisplayName());
            }
        }
    }

    // ── Player quit ───────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        dequeue(event.getPlayer().getUniqueId());
    }

    // ── PluginMessageListener (unused receipt, required by API) ───────────────

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // We don't need to handle incoming BungeeCord messages currently
    }
}
