package local.simplefactions;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /challenges  — opens the daily challenge GUI (all players)
 * /challenge   — alias
 *
 * Admin subcommands (simplefactions.admin):
 *   /challenge admin skip          — force a new random challenge now
 *   /challenge admin set <id>      — force a specific challenge by id
 */
public class ChallengeCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERM = "simplefactions.admin";

    private final ChallengeManager manager;
    private final ChallengeGUI gui;

    public ChallengeCommand(ChallengeManager manager, ChallengeGUI gui) {
        this.manager = manager;
        this.gui     = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            // Console: print info
            ChallengeManager.ChallengeDefinition cur = manager.getCurrent();
            if (cur == null) { sender.sendMessage("No active challenge."); return true; }
            sender.sendMessage("Active: " + cur.displayName
                    + " | " + ChallengeManager.fmtTime(manager.secondsRemaining()) + " left");
            return true;
        }

        // Admin subcommands
        if (args.length >= 2 && args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission(ADMIN_PERM)) {
                player.sendMessage("§cNo permission.");
                return true;
            }
            return handleAdmin(player, args);
        }

        // Player: open GUI
        gui.openMain(player);
        return true;
    }

    private boolean handleAdmin(Player player, String[] args) {
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "skip" -> {
                manager.adminSkip();
                player.sendMessage("§aChallenged cycled. New challenge: §e"
                        + manager.getCurrent().displayName);
            }
            case "set" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /challenge admin set <id>");
                    player.sendMessage("§7IDs: " + ChallengeManager.POOL.stream()
                            .map(d -> d.id).collect(Collectors.joining(", ")));
                    return true;
                }
                String id = args[2].toLowerCase();
                ChallengeManager.ChallengeDefinition def = ChallengeManager.POOL.stream()
                        .filter(d -> d.id.equals(id)).findFirst().orElse(null);
                if (def == null) {
                    player.sendMessage("§cUnknown id: §f" + id);
                    return true;
                }
                manager.adminSet(def);
                player.sendMessage("§aChallenge set to: §e" + def.displayName);
            }
            default -> {
                player.sendMessage("§cUnknown admin sub: §f" + sub);
                player.sendMessage("§7Available: §eskip§7, §eset <id>");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return List.of("admin");
        if (args.length == 2 && args[0].equalsIgnoreCase("admin"))
            return Arrays.asList("skip", "set");
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("set"))
            return ChallengeManager.POOL.stream()
                    .map(d -> d.id)
                    .filter(id -> id.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        return List.of();
    }
}
