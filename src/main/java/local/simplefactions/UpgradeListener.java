package local.simplefactions;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class UpgradeListener implements Listener {

    private final FactionManager manager;
    private final UpgradeGUI upgradeGUI;
    private final EconomyManager economy;

    public UpgradeListener(FactionManager manager, UpgradeGUI upgradeGUI, EconomyManager economy) {
        this.manager = manager;
        this.upgradeGUI = upgradeGUI;
        this.economy = economy;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().contains("Faction Upgrades")) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        FactionManager.Faction faction = manager.getFaction(player.getUniqueId());
        if (faction == null) {
            return;
        }

        String upgradeType = switch (slot) {
            case 10 -> "maxmembers";
            case 12 -> "spawnermult";
            case 14 -> "maxwarps";
            case 16 -> "chestslots";
            case 28 -> "tnt2gunpowder";
            case 30 -> "ingot2block";
            case 32 -> "successrate";
            default -> null;
        };

        if (upgradeType == null) {
            return;
        }

        // Determine the cap for this upgrade (toggle upgrades cap at 1)
        int maxLevel = switch (upgradeType) {
            case "tnt2gunpowder", "ingot2block" -> 1;
            default -> 5;
        };

        int currentLevel = faction.getUpgradeLevel(upgradeType);
        if (currentLevel >= maxLevel) {
            player.sendMessage("§cThis upgrade is already " + (maxLevel == 1 ? "enabled" : "maxed out") + "!");
            return;
        }

        double cost = getUpgradeCost(upgradeType, currentLevel + 1);
        if (!economy.has(player, cost)) {
            player.sendMessage("§cInsufficient funds! Need §f$" + formatNumber((long) cost) + "§c.");
            return;
        }

        if (!economy.withdrawPlayer(player, cost)) {
            player.sendMessage("§cFailed to process payment!");
            return;
        }

        faction.setUpgradeLevel(upgradeType, currentLevel + 1);

        boolean isToggle = upgradeType.equals("tnt2gunpowder") || upgradeType.equals("ingot2block");
        player.sendMessage("§a✓ Upgrade purchased!");
        if (isToggle) {
            String label = upgradeType.equals("tnt2gunpowder") ? "TNT → Gunpowder" : "Ingot Compressor";
            player.sendMessage("§f" + label + "§a has been §aenabled§a for your faction.");
        } else {
            player.sendMessage("§f" + upgradeType + "§a is now level §f" + (currentLevel + 1) + "§a.");
        }
        
        // Refresh the GUI
        upgradeGUI.openUpgradeGUI(player);
    }

    private double getUpgradeCost(String upgradeType, int nextLevel) {
        return switch (upgradeType.toLowerCase()) {
            case "maxmembers" -> 100_000 * nextLevel;
            case "spawnermult" -> 150_000 * nextLevel;
            case "maxwarps" -> 50_000 * nextLevel;
            case "chestslots" -> 75_000 * nextLevel;
            case "successrate" -> 200_000 * nextLevel;
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
