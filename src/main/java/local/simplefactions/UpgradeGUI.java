package local.simplefactions;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class UpgradeGUI {

    private final FactionManager manager;
    private final EconomyManager economy;

    public UpgradeGUI(FactionManager manager, EconomyManager economy) {
        this.manager = manager;
        this.economy = economy;
    }

    public void openUpgradeGUI(Player player) {
        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§cYou are not in a faction.");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, Component.text("§6Faction Upgrades"));

        // Max Members Upgrade
        gui.setItem(10, createUpgradeItem(
            player,
            Material.EMERALD,
            "§b§lMax Members",
            faction,
            "maxmembers",
            "Increase maximum faction members",
            new String[]{
                "§71 Player: §f+5 members",
                "§72 Players: §f+5 members",
                "§73 Players: §f+5 members",
                "§74 Players: §f+5 members",
                "§75 Players: §f+5 members"
            }
        ));

        // Spawner Multiplier Upgrade
        gui.setItem(12, createUpgradeItem(
            player,
            Material.DIAMOND,
            "§d§lSpawner Multiplier",
            faction,
            "spawnermult",
            "Increase spawner value multiplier",
            new String[]{
                "§71 Player: §f+10% value",
                "§72 Players: §f+10% value",
                "§73 Players: §f+10% value",
                "§74 Players: §f+10% value",
                "§75 Players: §f+10% value"
            }
        ));

        // Max Warps Upgrade
        gui.setItem(14, createUpgradeItem(
            player,
            Material.ENDER_PEARL,
            "§5§lMax Warps",
            faction,
            "maxwarps",
            "Increase maximum faction warps",
            new String[]{
                "§71 Player: §f+2 warps",
                "§72 Players: §f+2 warps",
                "§73 Players: §f+2 warps",
                "§74 Players: §f+2 warps",
                "§75 Players: §f+2 warps"
            }
        ));

        // Chest Slots Upgrade
        gui.setItem(16, createUpgradeItem(
            player,
            Material.CHEST,
            "§a§lChest Slots",
            faction,
            "chestslots",
            "Increase faction chest capacity",
            new String[]{
                "§71 Player: §f+9 slots",
                "§72 Players: §f+9 slots",
                "§73 Players: §f+9 slots",
                "§74 Players: §f+9 slots",
                "§75 Players: §f+9 slots"
            }
        ));

        player.openInventory(gui);
    }

    private ItemStack createUpgradeItem(Player player, Material material, String name, FactionManager.Faction faction, String upgradeType, String description, String[] perks) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        int level = faction.getUpgradeLevel(upgradeType);
        double cost = getUpgradeCost(upgradeType, level + 1);
        boolean canAfford = economy.has(player, cost);
        boolean maxed = level >= 5;

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7" + description);
        lore.add("");

        if (maxed) {
            lore.add("§e§lTier: §cMAXED OUT");
        } else {
            lore.add("§e§lTier: §f" + level + "/5");
        }

        lore.add("");
        lore.add("§d§lPerks:");
        for (int i = 0; i < perks.length; i++) {
            if (i < level) {
                lore.add("§a✓ " + perks[i].substring(2)); // Already unlocked
            } else if (i == level) {
                lore.add("§6» " + perks[i].substring(2)); // Next to unlock
            } else {
                lore.add("§7✗ " + perks[i].substring(2)); // Locked
            }
        }

        lore.add("");
        lore.add("§6§lPrice:");
        lore.add("§f$" + formatNumber((long) cost));

        lore.add("");
        if (maxed) {
            lore.add("§c§lMAXED OUT");
        } else if (canAfford) {
            lore.add("§a§lLEFT-CLICK TO UPGRADE");
        } else {
            lore.add("§c§lINSUFFICIENT FUNDS");
        }

        meta.lore(lore.stream().map(Component::text).toList());
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);

        return item;
    }

    private double getUpgradeCost(String upgradeType, int nextLevel) {
        return switch (upgradeType.toLowerCase()) {
            case "maxmembers" -> 100_000 * nextLevel;
            case "spawnermult" -> 150_000 * nextLevel;
            case "maxwarps" -> 50_000 * nextLevel;
            case "chestslots" -> 75_000 * nextLevel;
            default -> 0;
        };
    }

    private String formatNumber(long num) {
        if (num < 1000) return String.valueOf(num);
        if (num < 1_000_000) return (num / 1000) + "K";
        return (num / 1_000_000) + "M";
    }
}
