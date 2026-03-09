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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class HomeCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String MENU_TITLE = "§a§lYour Homes";

    private final PlayerHomeManager homeManager;
    private final TeleportTimerManager tpTimer;
    private final Map<UUID, Map<Integer, String>> menuSlots = new LinkedHashMap<>();

    public HomeCommand(PlayerHomeManager homeManager, TeleportTimerManager tpTimer) {
        this.homeManager = homeManager;
        this.tpTimer     = tpTimer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            openMenu(player);
            return true;
        }

        teleportToHome(player, args[0]);
        return true;
    }

    public void openMenu(Player player) {
        Map<String, Location> homes = homeManager.getHomes(player.getUniqueId());
        int count = Math.max(1, homes.size());
        int size = Math.min(54, Math.max(9, ((count - 1) / 9 + 1) * 9));

        Inventory menu = Bukkit.createInventory(null, size, MENU_TITLE);
        Map<Integer, String> slotToName = new LinkedHashMap<>();

        if (homes.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cNo homes set");
                meta.setLore(List.of("§7Use §f/sethome [name] §7to create one."));
                empty.setItemMeta(meta);
            }
            menu.setItem(4, empty);
        } else {
            List<Map.Entry<String, Location>> entries = new ArrayList<>(homes.entrySet());
            entries.sort(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));

            int slot = 0;
            for (Map.Entry<String, Location> entry : entries) {
                menu.setItem(slot, createHomeItem(player, entry.getKey(), entry.getValue()));
                slotToName.put(slot, entry.getKey());
                slot++;
                if (slot >= size) break;
            }
        }

        menuSlots.put(player.getUniqueId(), slotToName);
        player.openInventory(menu);
    }

    private ItemStack createHomeItem(Player player, String name, Location location) {
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a" + name);
            List<String> lore = new ArrayList<>();
            lore.add("§7Owner: §f" + player.getName());
            lore.add("§7World: §f" + location.getWorld().getName());
            lore.add("§7Location: §f" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
            lore.add("§7");
            lore.add("§aClick to teleport");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void teleportToHome(Player player, String homeName) {
        Location location = homeManager.getHome(player.getUniqueId(), homeName);
        if (location == null || location.getWorld() == null) {
            player.sendMessage("§cHome not found: §f" + homeName);
            return;
        }

        tpTimer.scheduleTeleport(player, location, "home §f" + homeName);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!MENU_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        Map<Integer, String> slotToName = menuSlots.get(player.getUniqueId());
        if (slotToName == null) return;

        String homeName = slotToName.get(event.getSlot());
        if (homeName == null) return;

        teleportToHome(player, homeName);
        player.closeInventory();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String name : homeManager.getHomes(player.getUniqueId()).keySet()) {
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(name);
                }
            }
            out.sort(String::compareToIgnoreCase);
            return out;
        }

        return List.of();
    }
}
