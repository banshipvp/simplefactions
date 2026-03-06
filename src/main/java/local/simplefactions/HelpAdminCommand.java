package local.simplefactions;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /helpadmin — operator-only quick-reference for all admin commands.
 */
public class HelpAdminCommand implements CommandExecutor {

    private static final String PERM = "simplefactions.admin";

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        sender.sendMessage("§6§l══ Admin Command Reference ══");
        sender.sendMessage(" ");

        sender.sendMessage("§e● Milestones (/fund)");
        sender.sendMessage("  §f/fund add <id> <time|goal> <value> <desc> §7- Add milestone");
        sender.sendMessage("  §f/fund remove <id> §7- Remove milestone");
        sender.sendMessage("  §f/fund forceunlock <id> §7- Force-unlock a milestone");
        sender.sendMessage("  §f/fund forcelock <id> §7- Re-lock a milestone");
        sender.sendMessage("  §f/fund reset §7- Reset server-start timer for TIME milestones");
        sender.sendMessage(" ");

        sender.sendMessage("§e● Challenges (/challenge admin)");
        sender.sendMessage("  §7Challenges rotate §eautomatically every 24 hours§7.");
        sender.sendMessage("  §f/challenge admin skip §7- Force a new random challenge now");
        sender.sendMessage("  §f/challenge admin set <id> §7- Force a specific challenge");
        sender.sendMessage("  §f/challenges §7- View current challenge GUI (opens for all players)");
        sender.sendMessage(" ");

        sender.sendMessage("§e● Shop (SimpleShop)");
        sender.sendMessage("  §f/shopadd §7- Add item to shop (hold item)");
        sender.sendMessage("  §f/shopedit §7- Edit a shop listing");
        sender.sendMessage("  §f/shopremove §7- Remove listing");
        sender.sendMessage(" ");

        sender.sendMessage("§e● GKits (SimpleKits)");
        sender.sendMessage("  §f/gkitunlock player <player> <kit|all> §7- Unlock for player");
        sender.sendMessage("  §f/gkitunlock all <kit> §7- Unlock for all online");
        sender.sendMessage("  §f/gkitlock player <player> §7- Lock player gkits");
        sender.sendMessage("  §f/gkitlock all §7- Lock all gkits globally");
        sender.sendMessage("  §f/gkitgem give <player> <kit|random> [amount] §7- Give gkit gems");
        sender.sendMessage(" ");

        sender.sendMessage("§e● Crates (SimpleCrates)");
        sender.sendMessage("  §f/crate give <player> <tier> [amount] §7- Give crate key");
        sender.sendMessage("  §f/rankvoucher give <player> <rank> [amount] §7- Give rank voucher");
        sender.sendMessage(" ");

        sender.sendMessage("§e● Economy (SimpleEconomy)");
        sender.sendMessage("  §f/banknote give <player> <amount> §7- Give bank note");
        sender.sendMessage("  §f/xpbottle admin give <player> <amount> §7- Give XP bottle");
        sender.sendMessage(" ");

        sender.sendMessage("§e● Rank & Queue");
        sender.sendMessage("  §f/rank set <player> <tier> §7- Set player rank (1-4)");
        sender.sendMessage("  §f/queue list §7- View queue");
        sender.sendMessage(" ");

        sender.sendMessage("§e● Warzone");
        sender.sendMessage("  §f/warzone claim §7- Claim current chunk as warzone");
        sender.sendMessage("  §f/warzone unclaim §7- Unclaim chunk");
        sender.sendMessage("  §f/warzone autoclaim §7- Start auto-claiming");
        sender.sendMessage(" ");

        sender.sendMessage("§e● Server");
        sender.sendMessage("  §f/sethub §7- Set hub teleport point");
        sender.sendMessage("  §f/showpos on|off §7- Toggle WorldEdit selection display");
        sender.sendMessage(" ");
        sender.sendMessage("§8Use §f/help §8for the player command reference.");

        return true;
    }
}
