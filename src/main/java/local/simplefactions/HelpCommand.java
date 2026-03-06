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
        lines.add("§7---------------- §6Server Help §7----------------");
        lines.add("§ePage §f" + page + "§7/§f3 §8(Use §f/help <page>§8)");
        lines.add(" ");

        if (page == 1) {
            lines.add("§6Global / Essentials");
            lines.add("§f/spawn §7- Go to spawn");
            lines.add("§f/warp <name> §7- Warp to location");
            lines.add("§f/warps §7- List warps");
            lines.add("§f/home §7- Go home");
            lines.add("§f/homes §7- List homes");
            lines.add("§f/sethome [name] §7- Set home");
            lines.add("§f/delhome <name> §7- Delete home");
            lines.add(" ");
            lines.add("§6SimpleFactions");
            lines.add("§f/f create <name> §7- Create faction");
            lines.add("§f/f invite <player> §7- Invite player");
            lines.add("§f/f join <faction> §7- Join faction");
            lines.add("§f/f leave §7- Leave faction");
            lines.add("§f/f disband §7- Disband faction");
            lines.add("§f/f info §7- View faction info");
            lines.add("§f/f who <name> §7- View faction details");
            lines.add("§f/f top [page] §7- Top factions");
            lines.add("§f/f c <p|f> §7- Toggle chat");
        } else if (page == 2) {
            lines.add("§6SimpleFactions (Claims / Warps)");
            lines.add("§f/f map §7- Territory map");
            lines.add("§f/f map on|off §7- Toggle auto map per chunk");
            lines.add("§f/f mapgui §7- GUI claim map");
            lines.add("§f/f claim §7- Claim chunk");
            lines.add("§f/f claim radius <1-5> §7- Radius claim");
            lines.add("§f/f unclaim §7- Unclaim chunk");
            lines.add("§f/f unclaim radius <1-5> §7- Radius unclaim");
            lines.add("§f/f unclaimall §7- Remove all claims");
            lines.add("§f/f sethome §7- Set faction home");
            lines.add("§f/f home §7- Teleport faction home");
            lines.add("§f/f setwarp <name> §7- Set faction warp");
            lines.add("§f/f warp <name> §7- Use faction warp");
            lines.add("§f/f warps §7- List faction warps");
            lines.add("§f/f delwarp <name> §7- Delete faction warp");
            lines.add("§f/f promote <player> §7- Promote member");
            lines.add("§f/f demote <player> §7- Demote member");
            lines.add("§f/f kick <player> §7- Kick member");
            lines.add("§f/f title <text> §7- Set faction title");
            lines.add("§f/f chest §7- Open faction chest");
            lines.add("§f/f upgrade §7- Faction upgrades");
            lines.add("§f/f tnt bal §7- View faction TNT bank");
            lines.add("§f/f tnt deposit [amount|all] §7- Deposit TNT");
            lines.add("§f/f tnt withdraw [amount|all] §7- Withdraw TNT");
            lines.add("§f/f tnt fill <r> <amt> <max> §7- Fill dispensers");
            lines.add("§f/f tnt set <amount> §7- (Admin) Set your faction TNT bank");
            lines.add("§f/f tnt set <faction> <amount> §7- (Admin) Set a faction's TNT bank");
            lines.add("§f/f tnt siphon <amt|all> [r] §7- Pull TNT from dispensers");
            lines.add(" ");
            lines.add("§6SimpleEconomy");
            lines.add("§f/bal §7- Check your balance");
            lines.add("§f/xpbottle <amount> §7- Convert XP to bottle");
            lines.add("§f/banknote <amount> §7- Create bank note (admin)");
            lines.add("§f/banknote give <player> <amount> §7- Give note (admin)");
        } else {
            lines.add("§6Economy & Gambling");
            lines.add("§f/bal §7- Check your balance");
            lines.add("§f/cf <amount> §7- Create a 50/50 coin flip  §8|  §f/cf §7- View open flips");
            lines.add("§f/xpbottle <amount> §7- Convert XP to bottle");
            lines.add("§f/banknote <amount> §7- Redeem bank note (right-click)");
            lines.add("§f/shop §7- Open the player shop");
            lines.add(" ");
            lines.add("§6Milestones & Events");
            lines.add("§f/fund §7- View server milestones");
            lines.add("§f/fund info <id> §7- Details on a milestone");
            lines.add("§f/fund donate <id> <amount> §7- Donate toward a goal");
            lines.add("§f/challenges §7- View today's daily challenge GUI");
            lines.add("§f/claim §7- Claim your top-3 challenge prize");
            lines.add(" ");
            lines.add("§6SimpleKits");
            lines.add("§f/gkits §7- Open gkits GUI (unlocks when milestone reached!)");
            lines.add("§f/gkit <kitname> §7- Claim gkit");
            lines.add("§f/kits §7- Open rank kits GUI");
            lines.add("§f/spawner §7- Mystery spawner");
            lines.add(" ");
            lines.add("§6SimpleCrates");
            lines.add("§f/crate open <tier> §7- Open a crate");
            lines.add(" ");
            lines.add("§7§oPage 1: General/Factions  Page 2: Claims/Factions  Page 3: Economy/Events");
        }

        for (String line : lines) {
            player.sendMessage(line);
        }
    }
}
