package local.simplefactions;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * /queue [join|leave|list|pos]
 *
 * join – Add self to the priority queue for the factions server.
 * leave – Remove self from the queue.
 * list  – Show the current queue sorted by priority.
 * pos   – Show your current position in the queue.
 * (no sub-command defaults to join)
 */
public class QueueCommand implements CommandExecutor {

    private final HubQueueManager queueManager;
    private final PlayerRankManager rankManager;

    public QueueCommand(HubQueueManager queueManager, PlayerRankManager rankManager) {
        this.queueManager = queueManager;
        this.rankManager  = rankManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command is player-only.");
            return true;
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "join";

        switch (sub) {
            case "join"  -> handleJoin(player);
            case "leave" -> handleLeave(player);
            case "list"  -> handleList(player);
            case "pos", "position", "status" -> handlePos(player);
            default -> sendHelp(player, label);
        }
        return true;
    }

    // ── Sub-commands ──────────────────────────────────────────────────────────

    private void handleJoin(Player player) {
        if (queueManager.isQueued(player.getUniqueId())) {
            int pos = queueManager.getPosition(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "You are already in the queue at position §e" + pos
                    + "§e/§e" + queueManager.size() + "§6.");
            return;
        }

        queueManager.enqueue(player);
        int pos = queueManager.getPosition(player.getUniqueId());
        PlayerRank rank = rankManager.getRank(player);

        player.sendMessage(ChatColor.GREEN + "✔ You joined the §6Factions §aqueue!");
        player.sendMessage(ChatColor.YELLOW + "  Position: §e" + pos + "§7/§e" + queueManager.size()
                + "  §7Rank: " + rank.getDisplayName());
    }

    private void handleLeave(Player player) {
        UUID id = player.getUniqueId();
        if (queueManager.dequeue(id)) {
            player.sendMessage(ChatColor.RED + "✘ You left the §6Factions §cqueue.");
        } else {
            player.sendMessage(ChatColor.GRAY + "You are not currently in the queue.");
        }
    }

    private void handleList(Player player) {
        List<UUID> sorted = queueManager.getSortedQueue();
        if (sorted.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "The queue is currently empty.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "─── Factions Queue (" + sorted.size() + " player"
                + (sorted.size() == 1 ? "" : "s") + ") ───");

        for (int i = 0; i < sorted.size(); i++) {
            UUID uid  = sorted.get(i);
            String name = player.getServer().getOfflinePlayer(uid).getName();
            if (name == null) name = uid.toString().substring(0, 8);
            PlayerRank rank = rankManager.getRank(uid);
            player.sendMessage(ChatColor.YELLOW + "  " + (i + 1) + ". " + rank.getChatColor()
                    + name + ChatColor.GRAY + " [" + rank.getDisplayName() + ChatColor.GRAY + "]");
        }
    }

    private void handlePos(Player player) {
        if (!queueManager.isQueued(player.getUniqueId())) {
            player.sendMessage(ChatColor.GRAY + "You are not in the queue. Use §e/queue join§7 to join.");
            return;
        }
        int pos = queueManager.getPosition(player.getUniqueId());
        player.sendMessage(ChatColor.YELLOW + "You are at position §e" + pos
                + "§7/§e" + queueManager.size() + " §7in the §6Factions §7queue.");
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(ChatColor.GOLD + "─── /queue help ───");
        player.sendMessage(ChatColor.YELLOW + "  /" + label + " join   §7- Join the queue");
        player.sendMessage(ChatColor.YELLOW + "  /" + label + " leave  §7- Leave the queue");
        player.sendMessage(ChatColor.YELLOW + "  /" + label + " list   §7- Show the full queue");
        player.sendMessage(ChatColor.YELLOW + "  /" + label + " pos    §7- Show your position");
    }
}
