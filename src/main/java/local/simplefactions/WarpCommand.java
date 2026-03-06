package local.simplefactions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class WarpCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String MENU_TITLE = "§6§lWarps";

    private final GlobalWarpManager warpManager;
    private final Map<UUID, Map<Integer, String>> slotMap = new LinkedHashMap<>();

    public WarpCommand(GlobalWarpManager warpManager) {
        this.warpManager = warpManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("setwarp")) {
            if (!canManage(player)) {
                player.sendMessage("§cOnly admin/dev/owner can set warps.");
                return true;
            }
            if (args.length < 1) {
                player.sendMessage("§cUsage: /setwarp <name>");
                return true;
            }
            String name = args[0].toLowerCase(Locale.ROOT);
            warpManager.setWarp(name, player.getLocation());
            warpManager.save();
            player.sendMessage("§aWarp §f" + name + " §aset.");
            return true;
        }

        if (cmd.equals("delwarp")) {
            if (!canManage(player)) {
                player.sendMessage("§cOnly admin/dev/owner can delete warps.");
                return true;
            }
            if (args.length < 1) {
                player.sendMessage("§cUsage: /delwarp <name>");
                return true;
            }
            String name = args[0].toLowerCase(Locale.ROOT);
            if (!warpManager.removeWarp(name)) {
                player.sendMessage("§cWarp not found: §f" + name);
                return true;
            }
            warpManager.save();
            player.sendMessage("§aWarp §f" + name + " §adeleted.");
            return true;
        }

        if (args.length == 0) {
            openMenu(player);
            return true;
        }

        teleportToWarp(player, args[0]);
        return true;
    }

    private void openMenu(Player player) {
        Map<String, Location> warps = warpManager.getAllWarps();
        int count = Math.max(1, warps.size());
        int size = Math.min(54, Math.max(9, ((count - 1) / 9 + 1) * 9));

        Inventory menu = Bukkit.createInventory(null, size, MENU_TITLE);
        Map<Integer, String> slots = new LinkedHashMap<>();

        if (warps.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cNo warps configured");
                empty.setItemMeta(meta);
            }
            menu.setItem(4, empty);
        } else {
            int slot = 0;
            for (Map.Entry<String, Location> entry : warps.entrySet()) {
                menu.setItem(slot, createWarpItem(entry.getKey(), entry.getValue()));
                slots.put(slot, entry.getKey());
                slot++;
                if (slot >= size) break;
            }
        }

        slotMap.put(player.getUniqueId(), slots);
        player.openInventory(menu);
    }

    private ItemStack createWarpItem(String name, Location location) {
        Material icon = switch (name.toLowerCase(Locale.ROOT)) {
            case "nether" -> Material.NETHERRACK;
            case "desert" -> Material.SAND;
            case "plains" -> Material.GRASS_BLOCK;
            case "end" -> Material.END_STONE;
            case "spawn" -> Material.BEACON;
            default -> Material.ENDER_PEARL;
        };

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e" + name);
            meta.setLore(List.of(
                    "§7World: §f" + location.getWorld().getName(),
                    "§7Location: §f" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(),
                    "§aClick to teleport"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void teleportToWarp(Player player, String name) {
        Location location = warpManager.getWarp(name);
        if (location == null || location.getWorld() == null) {
            player.sendMessage("§cWarp not found: §f" + name);
            return;
        }
        player.teleport(location);
        player.sendMessage("§aTeleported to §f" + name + "§a.");
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!MENU_TITLE.equals(event.getView().getTitle())) return;

        event.setCancelled(true);

        Map<Integer, String> slots = slotMap.get(player.getUniqueId());
        if (slots == null) return;
        String warpName = slots.get(event.getSlot());
        if (warpName == null) return;

        teleportToWarp(player, warpName);
        player.closeInventory();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();

            if ((cmd.equals("setwarp") || cmd.equals("delwarp")) && !canManage(sender)) {
                return List.of();
            }

            for (String name : warpManager.getAllWarps().keySet()) {
                if (name.startsWith(prefix)) out.add(name);
            }
            return out;
        }

        return List.of();
    }

    private boolean canManage(CommandSender sender) {
        return sender.hasPermission("simplefactions.warp.manage")
                || sender.hasPermission("group.owner")
                || sender.hasPermission("group.admin")
                || sender.hasPermission("group.dev")
                || sender.isOp();
    }
}
