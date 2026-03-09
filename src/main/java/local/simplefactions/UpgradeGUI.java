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

        Inventory gui = Bukkit.createInventory(null, 54, Component.text("§6Faction Upgrades"));

        // Max Members Upgrade
        gui.setItem(10, createUpgradeItem(
            player,
            Material.EMERALD,
            "§b§lMax Members",
            faction,
            "maxmembers",
            "Increase maximum faction members",
            new String[]{
                "§7Upgrade 1: §f+5 members §8(15 total)",
                "§7Upgrade 2: §f+10 members §8(20 total)",
                "§7Upgrade 3: §f+15 members §8(25 total)",
                "§7Upgrade 4: §f+20 members §8(30 total)",
                "§7Upgrade 5: §f+25 members §8(35 total)"
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
                "§7Upgrade 1: §f1.1x §8(+10%)",
                "§7Upgrade 2: §f1.2x §8(+20%)",
                "§7Upgrade 3: §f1.3x §8(+30%)",
                "§7Upgrade 4: §f1.4x §8(+40%)",
                "§7Upgrade 5: §f1.5x §8(+50%)"
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
                "§7Upgrade 1: §f7 warps §8(+2)",
                "§7Upgrade 2: §f9 warps §8(+4)",
                "§7Upgrade 3: §f11 warps §8(+6)",
                "§7Upgrade 4: §f13 warps §8(+8)",
                "§7Upgrade 5: §f15 warps §8(+10)"
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
                "§7Upgrade 1: §f18 slots §8(2 rows)",
                "§7Upgrade 2: §f27 slots §8(3 rows)",
                "§7Upgrade 3: §f36 slots §8(4 rows)",
                "§7Upgrade 4: §f45 slots §8(5 rows)",
                "§7Upgrade 5: §f54 slots §8(6 rows)"
            }
        ));

        // Book Success Rate Upgrade
        gui.setItem(32, createUpgradeItem(
            player,
            Material.ENCHANTED_BOOK,
            "§d§lBook Success Rate",
            faction,
            "successrate",
            "Increase custom enchant book success rate",
            new String[]{
                "§7Upgrade 1: §f+2% success rate §8(2% total)",
                "§7Upgrade 2: §f+4% success rate §8(4% total)",
                "§7Upgrade 3: §f+6% success rate §8(6% total)",
                "§7Upgrade 4: §f+8% success rate §8(8% total)",
                "§7Upgrade 5: §f+10% success rate §8(10% total)"
            }
        ));

        // TNT → Gunpowder auto-convert
        gui.setItem(28, createToggleItem(
            player,
            Material.GUNPOWDER,
            "§e§lAuto: TNT → Gunpowder",
            faction,
            "tnt2gunpowder",
            "Automatically converts TNT collected into Gunpowder",
            "§7Each TNT item converts to §f4 Gunpowder§7 in collection chests."
        ));

        // Ingot → Block auto-compressor
        gui.setItem(30, createToggleItem(
            player,
            Material.IRON_BLOCK,
            "§7§lAuto: Ingot Compressor",
            faction,
            "ingot2block",
            "Compresses 9 ingots into a block automatically",
            "§7Works with Iron, Gold, Copper, Emerald & Diamond ingots."
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

    private ItemStack createToggleItem(Player player, Material material, String name,
                                        FactionManager.Faction faction, String upgradeType,
                                        String description, String perkLine) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        int level   = faction.getUpgradeLevel(upgradeType);
        boolean on  = level >= 1;
        double cost = getUpgradeCost(upgradeType, 1);
        boolean canAfford = economy.has(player, cost);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7" + description);
        lore.add("");
        lore.add(perkLine);
        lore.add("");
        lore.add("§e§lStatus: " + (on ? "§aENABLED" : "§cDISABLED"));
        lore.add("");
        if (on) {
            lore.add("§a§lACTIVE");
        } else {
            lore.add("§6§lPrice: §f$" + formatNumber((long) cost));
            lore.add("");
            lore.add(canAfford ? "§a§lLEFT-CLICK TO UNLOCK" : "§c§lINSUFFICIENT FUNDS");
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
            case "tnt2gunpowder" -> 500_000;
            case "ingot2block" -> 500_000;
            default -> 0;
        };
    }

    private String formatNumber(long num) {
        if (num < 1000) return String.valueOf(num);
        if (num < 1_000_000) return (num / 1000) + "K";
        return (num / 1_000_000) + "M";
    }
}
