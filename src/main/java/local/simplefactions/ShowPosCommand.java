package local.simplefactions;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class ShowPosCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§6[ShowPos] §r";

    private final WorldEditSelectionListener selectionListener;

    public ShowPosCommand(WorldEditSelectionListener selectionListener) {
        this.selectionListener = selectionListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is player-only.");
            return true;
        }

        if (!player.hasPermission("simplefactions.admin")) {
            player.sendMessage(PREFIX + "§cYou don't have permission.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(PREFIX + "§eUsage: /showpos <on|off>");
            return true;
        }

        UUID uuid = player.getUniqueId();
        String mode = args[0].toLowerCase(Locale.ROOT);

        switch (mode) {
            case "on" -> {
                selectionListener.setEnabled(uuid, true);
                player.sendMessage(PREFIX + "§aEnabled.§7 Selection outline will show after //pos1 and //pos2.");
            }
            case "off" -> {
                selectionListener.setEnabled(uuid, false);
                player.sendMessage(PREFIX + "§cDisabled.§7 Selection outline will not auto-show.");
            }
            default -> player.sendMessage(PREFIX + "§eUsage: /showpos <on|off>");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        String input = args[0].toLowerCase(Locale.ROOT);
        return List.of("on", "off").stream()
                .filter(opt -> opt.startsWith(input))
                .collect(Collectors.toList());
    }
}
