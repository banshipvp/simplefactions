package local.simplefactions;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class HelpCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Use /help <page>");
            return true;
        }

        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        page = Math.max(1, Math.min(3, page));
        sendPage(player, page);
        return true;
    }

    private void sendPage(Player player, int page) {
        List<String> lines = new ArrayList<>();
        lines.add("§8§m                                                     ");
        lines.add("§6§l✦ §e§lSimple Factions Help §8• §7Page §f" + page + "§7/§f3");
        lines.add("§8Use §f/help <page> §8to navigate.");
        lines.add("§8§m                                                     ");

        if (page == 1) {
            lines.add("§6§lGeneral & Essentials");
            lines.add("§8• §f/spawn §8- §7Go to spawn");
            lines.add("§8• §f/warp <name> §8- §7Warp to location");
            lines.add("§8• §f/warps §8- §7List warps");
            lines.add("§8• §f/home §8- §7Go home");
            lines.add("§8• §f/homes §8- §7List homes");
            lines.add("§8• §f/sethome [name] §8- §7Set home");
            lines.add("§8• §f/delhome <name> §8- §7Delete home");
            lines.add(" ");
            lines.add("§6§lFaction Basics");
            lines.add("§8• §f/f create <name> §8- §7Create faction");
            lines.add("§8• §f/f invite <player> §8- §7Invite player");
            lines.add("§8• §f/f join <faction> §8- §7Join faction");
            lines.add("§8• §f/f leave §8- §7Leave faction");
            lines.add("§8• §f/f disband §8- §7Disband faction");
            lines.add("§8• §f/f info §8- §7View faction info");
            lines.add("§8• §f/f who <name> §8- §7View faction details");
            lines.add("§8• §f/f top [page] §8- §7Top factions");
            lines.add("§8• §f/f c <p|f> §8- §7Toggle chat");
        } else if (page == 2) {
            lines.add("§6§lClaims, Warps & TNT");
            lines.add("§8• §f/f map §8- §7Territory map");
            lines.add("§8• §f/f map on|off §8- §7Toggle auto map per chunk");
            lines.add("§8• §f/f mapgui §8- §7GUI claim map");
            lines.add("§8• §f/f claim §8- §7Claim chunk");
            lines.add("§8• §f/f claim radius <1-5> §8- §7Radius claim");
            lines.add("§8• §f/f unclaim §8- §7Unclaim chunk");
            lines.add("§8• §f/f unclaim radius <1-5> §8- §7Radius unclaim");
            lines.add("§8• §f/f unclaimall §8- §7Remove all claims");
            lines.add("§8• §f/f sethome §8- §7Set faction home");
            lines.add("§8• §f/f home §8- §7Teleport faction home");
            lines.add("§8• §f/f setwarp <name> §8- §7Set faction warp");
            lines.add("§8• §f/f warp <name> §8- §7Use faction warp");
            lines.add("§8• §f/f warps §8- §7List faction warps");
            lines.add("§8• §f/f delwarp <name> §8- §7Delete faction warp");
            lines.add("§8• §f/f chest §8- §7Open faction chest");
            lines.add("§8• §f/f upgrade §8- §7Faction upgrades");
            lines.add("§8• §f/f tnt bal|deposit|withdraw|fill|siphon §8- §7Manage TNT bank");
            lines.add("§8• §f/f tnt set <amount|faction amount> §8- §7Admin set TNT bank");
            lines.add(" ");
            lines.add("§6§lFaction Management");
            lines.add("§8• §f/f promote <player> §8- §7Promote member");
            lines.add("§8• §f/f demote <player> §8- §7Demote member");
            lines.add("§8• §f/f kick <player> §8- §7Kick member");
            lines.add("§8• §f/f title <text> §8- §7Set faction title");
        } else {
            lines.add("§6§lEconomy & Events");
            lines.add("§8• §f/bal §8- §7Check your balance");
            lines.add("§8• §f/cf <amount> §8- §7Create a 50/50 coin flip");
            lines.add("§8• §f/cf §8- §7View open flips");
            lines.add("§8• §f/xpbottle <amount> §8- §7Convert XP to bottle");
            lines.add("§8• §f/banknote <amount> §8- §7Redeem bank note (right-click)");
            lines.add("§8• §f/shop §8- §7Open the player shop");
            lines.add("§8• §f/fund §8- §7View server milestones");
            lines.add("§8• §f/fund info <id> §8- §7Milestone details");
            lines.add("§8• §f/fund donate <id> <amount> §8- §7Donate toward a goal");
            lines.add("§8• §f/challenges §8- §7View active daily challenges");
            lines.add("§8• §f/claim §8- §7Claim your top-3 challenge prize");
            lines.add(" ");
            lines.add("§6§lKits & Crates");
            lines.add("§8• §f/gkits §8- §7Open gkits GUI");
            lines.add("§8• §f/gkit <kitname> §8- §7Claim gkit");
            lines.add("§8• §f/kits §8- §7Open rank kits GUI");
            lines.add("§8• §f/spawner §8- §7Mystery spawner");
            lines.add("§8• §f/crate open <tier> §8- §7Open a crate");
            lines.add(" ");
            lines.add("§8§m                                                     ");
            lines.add("§7Pages: §f1 §8(General) §7| §f2 §8(Factions) §7| §f3 §8(Economy)");
        }

        for (String line : lines) {
            player.sendMessage(line);
        }
    }
}
